package com.aistudio.netgate.service.dns

import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.service.policy.NetworkPolicy
import com.aistudio.netgate.service.policy.PolicyContext
import com.aistudio.netgate.service.policy.PolicyDecision
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Behaviour-preservation tests for the extracted DNS pipeline:
 * packet parsing, response construction, transport selection, timeouts and fallback.
 */
class DnsEngineTest {

    // ---- Fixtures -------------------------------------------------------------------------

    private fun buildDnsQuery(
        domain: String,
        qType: Int = DnsRecordType.A,
        isIpv6: Boolean = false,
        txId: Int = 0x1234
    ): ByteArray {
        val dns = ByteArrayOutputStream()
        dns.write((txId ushr 8) and 0xFF)
        dns.write(txId and 0xFF)
        dns.write(0x01); dns.write(0x00) // RD
        dns.write(0x00); dns.write(0x01) // QDCOUNT
        dns.write(ByteArray(6))
        for (label in domain.split('.').filter { it.isNotEmpty() }) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dns.write(bytes.size)
            dns.write(bytes)
        }
        dns.write(0x00)
        dns.write((qType ushr 8) and 0xFF); dns.write(qType and 0xFF)
        dns.write(0x00); dns.write(0x01) // IN
        val query = dns.toByteArray()

        val srcPort = 53535
        val dstPort = 53
        return if (isIpv6) {
            val header = 40
            val udpLen = 8 + query.size
            val packet = ByteArray(header + udpLen)
            packet[0] = 0x60
            packet[4] = ((udpLen ushr 8) and 0xFF).toByte()
            packet[5] = (udpLen and 0xFF).toByte()
            packet[6] = 17
            packet[7] = 64
            // src fd00::1, dst fd00:1:fd00:1:fd00:1:fd00:1
            packet[8] = 0xFD.toByte(); packet[23] = 0x01
            val dst = "fd00:0001:fd00:0001:fd00:0001:fd00:0001".replace(":", "")
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            System.arraycopy(dst, 0, packet, 24, 16)
            packet[40] = ((srcPort ushr 8) and 0xFF).toByte()
            packet[41] = (srcPort and 0xFF).toByte()
            packet[42] = 0; packet[43] = dstPort.toByte()
            packet[44] = ((udpLen ushr 8) and 0xFF).toByte()
            packet[45] = (udpLen and 0xFF).toByte()
            System.arraycopy(query, 0, packet, 48, query.size)
            packet
        } else {
            val packet = ByteArray(20 + 8 + query.size)
            packet[0] = 0x45
            val total = packet.size
            packet[2] = ((total ushr 8) and 0xFF).toByte()
            packet[3] = (total and 0xFF).toByte()
            packet[8] = 64
            packet[9] = 17
            // src 192.168.1.50, dst 10.1.10.2
            packet[12] = 192.toByte(); packet[13] = 168.toByte(); packet[14] = 1; packet[15] = 50
            packet[16] = 10; packet[17] = 1; packet[18] = 10; packet[19] = 2
            packet[20] = ((srcPort ushr 8) and 0xFF).toByte()
            packet[21] = (srcPort and 0xFF).toByte()
            packet[22] = 0; packet[23] = dstPort.toByte()
            val udpLen = 8 + query.size
            packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
            packet[25] = (udpLen and 0xFF).toByte()
            System.arraycopy(query, 0, packet, 28, query.size)
            packet
        }
    }

    private fun parseQuery(domain: String, qType: Int = DnsRecordType.A, isIpv6: Boolean = false): ParsedDnsQuery {
        val packet = buildDnsQuery(domain, qType, isIpv6)
        return requireNotNull(DnsPacketParser.parseUdpDnsQuery(packet, packet.size))
    }

    /** Fabricates a positive upstream answer that reuses the question from [queryPayload]. */
    private fun answerFor(queryPayload: ByteArray, ipBytes: ByteArray, ttl: Int = 60): ByteArray {
        val qType = ((queryPayload[queryPayload.size - 4].toInt() and 0xFF) shl 8) or
            (queryPayload[queryPayload.size - 3].toInt() and 0xFF)
        val out = queryPayload.copyOf()
        val rdBit = queryPayload[2].toInt() and 0x01
        out[2] = (0x80 or rdBit).toByte()
        out[3] = 0x80.toByte()
        out[6] = 0; out[7] = 1
        out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        val rr = ByteArrayOutputStream()
        rr.write(0xC0); rr.write(0x0C)
        rr.write((qType ushr 8) and 0xFF); rr.write(qType and 0xFF)
        rr.write(0x00); rr.write(0x01)
        rr.write((ttl ushr 24) and 0xFF); rr.write((ttl ushr 16) and 0xFF)
        rr.write((ttl ushr 8) and 0xFF); rr.write(ttl and 0xFF)
        rr.write((ipBytes.size ushr 8) and 0xFF); rr.write(ipBytes.size and 0xFF)
        rr.write(ipBytes)
        return out + rr.toByteArray()
    }

    private class FakeDohClient : DnsDohClient {
        val requests = mutableListOf<String>()
        var responder: (ByteArray, String) -> ByteArray? = { _, _ -> null }
        override fun forward(payload: ByteArray, endpointUrl: String): ByteArray? {
            requests += endpointUrl
            return responder(payload, endpointUrl)
        }
    }

    private class FakeTcpClient : DnsTcpQueryClient {
        val requests = mutableListOf<String>()
        var responder: (String) -> ByteArray? = { null }
        override fun query(
            payload: ByteArray,
            serverIp: String,
            policy: DnsSocketPolicy,
            config: DnsTransportConfig
        ): ByteArray? {
            requests += serverIp
            return responder(serverIp)
        }
    }

    private class FakeUdpClient : DnsUdpQueryClient {
        val requests = mutableListOf<String>()
        var responder: (String) -> ByteArray? = { null }
        override fun query(
            payload: ByteArray,
            serverIp: String,
            policy: DnsSocketPolicy,
            config: DnsTransportConfig
        ): ByteArray? {
            requests += serverIp
            return responder(serverIp)
        }
    }

    private fun context(
        mode: DnsTransportMode = DnsTransportMode.DOH_WITH_FALLBACK,
        useDoh: Boolean = true
    ): DnsResolutionContext {
        return DnsResolutionContext(
            transportMode = mode,
            useDohUpstream = useDoh,
            dohUrl = "https://dns.google/dns-query",
            upstreamTitle = "Google",
            udpDnsServers = listOf("1.1.1.1", "8.8.8.8"),
            antiDnsPoisonEnabled = true,
            socketPolicy = DnsSocketPolicy(
                protectSocket = { true },
                protectDatagramSocket = { true },
                selfDisallowedOnTunnel = false
            )
        )
    }

    private val aRecord = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)
    private val aaaaRecord = ByteArray(16).also { it[15] = 1 }

    // ---- Parsing --------------------------------------------------------------------------

    @Test
    fun `parses ipv4 udp query fields`() {
        val query = parseQuery("example.com")
        assertEquals("example.com", query.domain)
        assertEquals(false, query.isIpv6)
        assertEquals(53535, query.srcPort)
        assertEquals(53, query.dstPort)
        assertArrayEquals(byteArrayOf(10, 1, 10, 2), query.dstIp)
        assertEquals(DnsRecordType.A, DnsPacketParser.extractQueryType(query))
    }

    @Test
    fun `parses ipv6 udp query fields`() {
        val query = parseQuery("ipv6.example.com", DnsRecordType.AAAA, isIpv6 = true)
        assertEquals("ipv6.example.com", query.domain)
        assertTrue(query.isIpv6)
        assertEquals(16, query.srcIp.size)
        assertEquals(16, query.dstIp.size)
        assertEquals(DnsRecordType.AAAA, DnsPacketParser.extractQueryType(query))
    }

    @Test
    fun `malformed packets are rejected without throwing`() {
        assertNull(DnsPacketParser.parseUdpDnsQuery(ByteArray(0), 0))
        assertNull(DnsPacketParser.parseUdpDnsQuery(ByteArray(10), 10))
        // Too short for IPv4 + UDP + DNS header
        val truncated = buildDnsQuery("example.com").copyOf(24)
        assertNull(DnsPacketParser.parseUdpDnsQuery(truncated, truncated.size))
        // Non-UDP protocol
        val notUdp = buildDnsQuery("example.com").also { it[9] = 6 }
        assertNull(DnsPacketParser.parseUdpDnsQuery(notUdp, notUdp.size))
        // QR=1 (a response, not a query)
        val response = buildDnsQuery("example.com").also { it[28 + 2] = 0x80.toByte() }
        assertNull(DnsPacketParser.parseUdpDnsQuery(response, response.size))
        // QDCOUNT=0
        val noQuestion = buildDnsQuery("example.com").also { it[28 + 4] = 0; it[28 + 5] = 0 }
        assertNull(DnsPacketParser.parseUdpDnsQuery(noQuestion, noQuestion.size))
        // Compression pointer inside the question name
        val pointer = buildDnsQuery("example.com").also { it[28 + 12] = 0xC0.toByte() }
        assertNull(DnsPacketParser.parseUdpDnsQuery(pointer, pointer.size))
        // Truncated question (name runs past the packet)
        val cut = buildDnsQuery("verylongsubdomain.example.com").copyOf(28 + 16)
        assertNull(DnsPacketParser.parseUdpDnsQuery(cut, cut.size))
        // Extraction helpers must not throw on garbage
        assertTrue(DnsPacketParser.extractResolvedIpsFromDnsResponse(ByteArray(3)).isEmpty())
    }

    // ---- Response construction ------------------------------------------------------------

    @Test
    fun `blocked response is nxdomain with valid ipv4 checksums`() {
        val query = parseQuery("blocked.com")
        val packet = DnsResponseBuilder.buildBlockedDnsResponsePacket(query)

        assertEquals(20 + 8 + query.questionEndOffset, packet.size)
        val dns = packet.copyOfRange(28, packet.size)
        assertEquals(3, dns[3].toInt() and 0x0F) // NXDOMAIN
        assertEquals(0x80, dns[2].toInt() and 0x80) // QR
        assertEquals(1, ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF))
        assertEquals(0, ((dns[6].toInt() and 0xFF) shl 8) or (dns[7].toInt() and 0xFF))

        // IPv4 header checksum verifies to zero when recomputed over the full header
        assertEquals(0, DnsResponseBuilder.computeChecksum(packet, 0, 20))
        // Ports are swapped back to the client
        assertEquals(53, ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF))
        assertEquals(53535, ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF))
    }

    @Test
    fun `quic downgrade answers nodata for ipv6 clients`() {
        val query = parseQuery("video.example.com", DnsRecordType.SVCB_HTTPS, isIpv6 = true)
        val packet = DnsResponseBuilder.buildNoDataDnsResponsePacket(query)

        assertEquals(40 + 8 + query.questionEndOffset, packet.size)
        assertEquals(0x60, packet[0].toInt() and 0xF0)
        val dns = packet.copyOfRange(48, packet.size)
        assertEquals(0, dns[3].toInt() and 0x0F) // NODATA
        assertEquals(17, packet[6].toInt() and 0xFF)
    }

    @Test
    fun `safe rewrite rebinds alias answer to original question`() {
        val doh = FakeDohClient()
        doh.responder = { payload, _ -> answerFor(payload, aRecord) }
        val engine = DnsEngine(
            DnsTransportManager(DnsUpstreamResolver(doh, FakeTcpClient(), FakeUdpClient()))
        )

        val query = parseQuery("www.google.com")
        val policy = NetworkPolicy(isSafeSearchEnabled = true)
        val outcome = engine.process(
            query = query,
            policy = policy,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = false
        )

        assertTrue(outcome is DnsOutcome.SafeRewrite)
        outcome as DnsOutcome.SafeRewrite
        assertEquals("SafeSearch Google", outcome.label)
        assertEquals("forcesafesearch.google.com", outcome.aliasDomain)
        // The client sees its own question (www.google.com) and one A record
        val dns = outcome.responsePacket.copyOfRange(28, outcome.responsePacket.size)
        assertEquals(1, ((dns[6].toInt() and 0xFF) shl 8) or (dns[7].toInt() and 0xFF))
        assertArrayEquals(
            query.dnsPayload.copyOfRange(12, query.questionEndOffset),
            dns.copyOfRange(12, query.questionEndOffset)
        )
        assertArrayEquals(aRecord, dns.copyOfRange(dns.size - 4, dns.size))
    }

    // ---- Pipeline policy ----------------------------------------------------------------

    @Test
    fun `blocked domain returns nxdomain outcome with category`() {
        val engine = DnsEngine()
        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            blockedDomains = setOf("blocked.com")
        )
        val outcome = engine.process(
            query = parseQuery("blocked.com"),
            policy = policy,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = false
        )

        assertTrue(outcome is DnsOutcome.Blocked)
        outcome as DnsOutcome.Blocked
        assertEquals(PolicyDecision.BlockCategory.WEBSITE_BLOCKLIST, outcome.category)
        assertEquals(3, outcome.responsePacket[28 + 3].toInt() and 0x0F)
    }

    @Test
    fun `quic svcb downgrade requires dpi bypass and honours whitelist`() {
        val udp = FakeUdpClient()
        val engine = DnsEngine(
            DnsTransportManager(DnsUpstreamResolver(FakeDohClient(), FakeTcpClient(), udp))
        )

        val downgraded = engine.process(
            query = parseQuery("youtube.com", DnsRecordType.SVCB_HTTPS),
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = true
        )
        assertTrue(downgraded is DnsOutcome.QuicDowngraded)

        // Disabled: no SVCB stripping, the query goes upstream
        udp.responder = { answerFor(parseQuery("youtube.com").dnsPayload, aRecord) }
        val passed = engine.process(
            query = parseQuery("youtube.com", DnsRecordType.SVCB_HTTPS),
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(mode = DnsTransportMode.UDP_THEN_TCP, useDoh = false),
            quicDowngradeEnabled = false
        )
        assertTrue(passed is DnsOutcome.Answered)
    }

    // ---- Transport selection, fallback and labels -----------------------------------------

    @Test
    fun `primary doh is preferred in doh with fallback mode`() {
        val doh = FakeDohClient()
        val tcp = FakeTcpClient()
        val udp = FakeUdpClient()
        val engine = DnsEngine(DnsTransportManager(DnsUpstreamResolver(doh, tcp, udp)))

        val query = parseQuery("example.com")
        doh.responder = { payload, _ -> answerFor(payload, aRecord) }

        val outcome = engine.process(
            query = query,
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = false
        )

        assertTrue(outcome is DnsOutcome.Answered)
        assertEquals("Google DoH", (outcome as DnsOutcome.Answered).transportLabel)
        assertEquals(listOf("https://dns.google/dns-query"), doh.requests)
        assertTrue(tcp.requests.isEmpty())
        assertTrue(udp.requests.isEmpty())
    }

    @Test
    fun `doh failure falls back to dotcp then validated udp`() {
        val doh = FakeDohClient()
        val tcp = FakeTcpClient()
        val udp = FakeUdpClient()
        val engine = DnsEngine(DnsTransportManager(DnsUpstreamResolver(doh, tcp, udp)))
        val query = parseQuery("example.com")

        // 1. DoH and DoTCP both fail -> validated UDP on the first resolver
        udp.responder = { answerFor(query.dnsPayload, aRecord) }
        val viaUdp = engine.process(
            query = query,
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = false
        )
        assertTrue(viaUdp is DnsOutcome.Answered)
        assertEquals("Google UDP", (viaUdp as DnsOutcome.Answered).transportLabel)
        assertEquals(listOf("1.1.1.1"), udp.requests)

        // 2. DoTCP answers with a public IP -> used before UDP
        tcp.responder = { answerFor(query.dnsPayload, byteArrayOf(1, 1, 1, 1)) }
        val viaTcp = engine.process(
            query = query,
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(),
            quicDowngradeEnabled = false
        )
        assertEquals("Google DoTCP", (viaTcp as DnsOutcome.Answered).transportLabel)
    }

    @Test
    fun `poisoned udp escalates to backup doh and reports the trip`() {
        val doh = FakeDohClient()
        val tcp = FakeTcpClient()
        val udp = FakeUdpClient()
        val engine = DnsEngine(DnsTransportManager(DnsUpstreamResolver(doh, tcp, udp)))
        val query = parseQuery("example.com")

        udp.responder = { answerFor(query.dnsPayload, byteArrayOf(10, 0, 0, 7)) }
        doh.responder = { payload, endpoint ->
            if (endpoint.contains("quad9")) answerFor(payload, aRecord) else null
        }

        val outcome = engine.process(
            query = query,
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(mode = DnsTransportMode.UDP_THEN_TCP, useDoh = false),
            quicDowngradeEnabled = false
        )

        assertTrue(outcome is DnsOutcome.Answered)
        outcome as DnsOutcome.Answered
        assertEquals("Quad9 DoH (Poison Guard)", outcome.transportLabel)
        assertEquals(1, outcome.antiPoisonTrips)
        assertEquals(
            listOf("https://dns.google/dns-query", "https://dns.quad9.net/dns-query"),
            doh.requests
        )
    }

    @Test
    fun `dns over tcp mode uses dotcp first`() {
        val doh = FakeDohClient()
        val tcp = FakeTcpClient()
        val engine = DnsEngine(DnsTransportManager(DnsUpstreamResolver(doh, tcp, FakeUdpClient())))
        val query = parseQuery("example.com")
        tcp.responder = { answerFor(query.dnsPayload, aRecord) }

        val outcome = engine.process(
            query = query,
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(mode = DnsTransportMode.DNS_OVER_TCP),
            quicDowngradeEnabled = false
        )

        assertEquals("Google DoTCP", (outcome as DnsOutcome.Answered).transportLabel)
        assertTrue(doh.requests.isEmpty())
    }

    @Test
    fun `all transports failing yields dropped without a packet`() {
        val engine = DnsEngine(
            DnsTransportManager(
                DnsUpstreamResolver(FakeDohClient(), FakeTcpClient(), FakeUdpClient())
            )
        )
        val outcome = engine.process(
            query = parseQuery("example.com"),
            policy = NetworkPolicy.EMPTY,
            policyContext = PolicyContext.forFirewall(protocol = "DNS"),
            resolutionContext = context(mode = DnsTransportMode.UDP_THEN_TCP, useDoh = false),
            quicDowngradeEnabled = false
        )
        assertEquals(DnsOutcome.Dropped, outcome)
    }

    @Test
    fun `transport timeouts and backup endpoints are unchanged`() {
        val config = DnsTransportConfig()
        assertEquals(1600, config.dohConnectTimeoutMs)
        assertEquals(1600, config.dohReadTimeoutMs)
        assertEquals(1500, config.tcpConnectTimeoutMs)
        assertEquals(1800, config.tcpReadTimeoutMs)
        assertEquals(1600, config.udpTimeoutMs)
        assertEquals(listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"), config.fallbackUdpServers)
        assertEquals(
            listOf(
                "Google" to "https://dns.google/dns-query",
                "Quad9" to "https://dns.quad9.net/dns-query"
            ),
            config.backupDohEndpoints
        )
    }

    @Test
    fun `backup doh skips an endpoint identical to the active one`() {
        val doh = FakeDohClient()
        val resolver = DnsUpstreamResolver(doh, FakeTcpClient(), FakeUdpClient())
        doh.responder = { payload, _ -> answerFor(payload, aRecord) }

        val result = resolver.forwardBackupDoh(
            payload = parseQuery("example.com").dnsPayload,
            context = context()
        )

        assertNotNull(result)
        assertEquals("Quad9 DoH", result!!.label)
        assertEquals(listOf("https://dns.quad9.net/dns-query"), doh.requests)
    }

    @Test
    fun `resolved ip extraction reads a and aaaa answers`() {
        val v4Query = parseQuery("example.com")
        val v4Response = answerFor(v4Query.dnsPayload, aRecord)
        assertEquals(listOf("93.184.216.34"), DnsPacketParser.extractResolvedIpsFromDnsResponse(v4Response))

        val v6Query = parseQuery("example.com", DnsRecordType.AAAA, isIpv6 = true)
        val v6Response = answerFor(v6Query.dnsPayload, aaaaRecord)
        assertTrue(DnsPacketParser.extractResolvedIpsFromDnsResponse(v6Response).single().contains(":"))
    }

    @Test
    fun `tcp reset builder is safe on malformed input`() {
        assertNull(DnsResponseBuilder.buildTcpResetPacket(ByteArray(0), 0))
        assertNull(DnsResponseBuilder.buildTcpResetPacket(ByteArray(40), 40))
        val query = buildDnsQuery("example.com")
        assertNull(DnsResponseBuilder.buildTcpResetPacket(query, query.size))
    }
}
