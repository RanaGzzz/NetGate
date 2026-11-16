package com.example.service

import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.WebsiteFilterMode
import java.net.InetAddress

data class ParsedDnsQuery(
    val domain: String,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray,
    val questionEndOffset: Int
)

object DnsPacketFilter {

    private val LABEL_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun normalizeDomainInput(raw: String): String? {
        var cleaned = raw.trim().lowercase()
        if (cleaned.isEmpty()) return null

        cleaned = cleaned
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .removePrefix("*.")
            .removePrefix("www.")
            .trim('.')

        if (cleaned.length !in 3..253 || !cleaned.contains('.')) return null
        val labels = cleaned.split('.')
        if (labels.size < 2 || labels.any { !LABEL_REGEX.matches(it) }) return null
        return cleaned
    }

    fun matchesDomainRule(queriedDomain: String, ruleDomain: String): Boolean {
        val q = queriedDomain.trim().trimEnd('.').lowercase()
        val r = ruleDomain.trim().trimEnd('.').lowercase()
        if (q.isEmpty() || r.isEmpty()) return false
        return q == r || q.endsWith(".$r")
    }

    fun shouldBlockDomain(
        domain: String,
        mode: WebsiteFilterMode,
        rules: List<WebsiteRuleEntity>
    ): Boolean {
        return when (mode) {
            WebsiteFilterMode.OFF -> false
            WebsiteFilterMode.BLOCKLIST -> {
                val isWhitelisted = rules.any {
                    it.isEnabled && it.ruleType == WebsiteRuleType.ALLOW &&
                        matchesDomainRule(domain, it.domain)
                }
                if (isWhitelisted) {
                    false
                } else {
                    rules.any {
                        it.isEnabled && it.ruleType == WebsiteRuleType.BLOCK &&
                            matchesDomainRule(domain, it.domain)
                    }
                }
            }
            WebsiteFilterMode.WHITELIST_ONLY -> {
                val isWhitelisted = rules.any {
                    it.isEnabled && it.ruleType == WebsiteRuleType.ALLOW &&
                        matchesDomainRule(domain, it.domain)
                }
                !isWhitelisted
            }
        }
    }

    fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl + 8 > length) return null

        val totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (totalLen < ihl + 8 || totalLen > length) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        val dnsStart = ihl + 8
        val dnsEnd = (ihl + udpLen).coerceAtMost(totalLen)
        if (dnsEnd - dnsStart < 12) return null

        val dnsPayload = packet.copyOfRange(dnsStart, dnsEnd)
        val qr = (dnsPayload[2].toInt() ushr 7) and 0x01
        if (qr != 0) return null // Ignore responses

        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < dnsPayload.size) {
            val labelLen = dnsPayload[offset].toInt() and 0xFF
            offset++
            if (labelLen == 0) break
            // Reject compressed pointer in QNAME header start safely
            if ((labelLen and 0xC0) != 0) return null
            if (offset + labelLen > dnsPayload.size) return null
            labels.add(String(dnsPayload, offset, labelLen, Charsets.US_ASCII))
            offset += labelLen
        }

        if (labels.isEmpty() || offset + 4 > dnsPayload.size) return null
        val questionEndOffset = offset + 4
        val domain = labels.joinToString(".").lowercase()

        return ParsedDnsQuery(
            domain = domain,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayload = dnsPayload,
            questionEndOffset = questionEndOffset
        )
    }

    fun buildBlockedDnsResponsePacket(query: ParsedDnsQuery): ByteArray {
        val respSize = query.questionEndOffset.coerceIn(12, query.dnsPayload.size)
        val dnsResp = ByteArray(respSize)
        System.arraycopy(query.dnsPayload, 0, dnsResp, 0, respSize)

        // QR = 1 (response), preserve RD bit
        val rdBit = query.dnsPayload[2].toInt() and 0x01
        dnsResp[2] = (0x80 or rdBit).toByte()
        // RA = 1, RCODE = 3 (NXDOMAIN)
        dnsResp[3] = 0x83.toByte()
        // QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        dnsResp[4] = 0
        dnsResp[5] = 1
        dnsResp[6] = 0
        dnsResp[7] = 0
        dnsResp[8] = 0
        dnsResp[9] = 0
        dnsResp[10] = 0
        dnsResp[11] = 0

        return buildDnsResponsePacket(query, dnsResp)
    }

    fun buildDnsResponsePacket(query: ParsedDnsQuery, dnsResponsePayload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + dnsResponsePayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        // IPv4 Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0x00
        packet[5] = 0x00
        packet[6] = 0x40.toByte() // Don't Fragment
        packet[7] = 0x00
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // UDP
        // Checksum initially 0
        packet[10] = 0
        packet[11] = 0
        // Swap Source <-> Destination IP
        System.arraycopy(query.dstIp, 0, packet, 12, 4)
        System.arraycopy(query.srcIp, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header (swap Source <-> Destination port)
        packet[20] = ((query.dstPort ushr 8) and 0xFF).toByte()
        packet[21] = (query.dstPort and 0xFF).toByte()
        packet[22] = ((query.srcPort ushr 8) and 0xFF).toByte()
        packet[23] = (query.srcPort and 0xFF).toByte()
        packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        packet[26] = 0
        packet[27] = 0

        System.arraycopy(dnsResponsePayload, 0, packet, 28, dnsResponsePayload.size)

        val udpChecksum = computeUdpChecksum(packet, query.dstIp, query.srcIp, udpLen)
        packet[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[27] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    fun ipBytesToInetAddress(ip: ByteArray): InetAddress? = runCatching {
        InetAddress.getByAddress(ip)
    }.getOrNull()

    private fun computeChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + len
        while (i + 1 < end) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeUdpChecksum(
        packet: ByteArray,
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpLen: Int
    ): Int {
        var sum = 0L
        // Pseudo-header: srcIp (4), dstIp (4), zero+proto (2), udpLen (2)
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 17L // Protocol UDP
        sum += udpLen.toLong()

        // UDP header + payload
        var i = 20
        val end = 20 + udpLen
        while (i + 1 < end) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val result = (sum.inv() and 0xFFFFL).toInt()
        return if (result == 0) 0xFFFF else result
    }
}
