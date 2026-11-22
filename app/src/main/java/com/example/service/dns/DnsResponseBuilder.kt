package com.example.service.dns

import java.io.ByteArrayOutputStream

/**
 * Builds DNS payloads and the IPv4/IPv6 UDP, TCP-RST and ICMP packets that carry them.
 *
 * Extracted verbatim from the previous `DnsPacketFilter` implementation: header flags,
 * counts, TTL clamping, pointer compression (`0xC00C`) and checksum maths are unchanged.
 */
object DnsResponseBuilder {

    /** Builds the alias query while preserving the original QTYPE/QCLASS. */
    fun buildRewrittenQueryPayload(originalQuery: ParsedDnsQuery, aliasDomain: String): ByteArray? {
        if (originalQuery.questionEndOffset > originalQuery.dnsPayload.size ||
            originalQuery.questionEndOffset < 16
        ) {
            return null
        }
        val labels = aliasDomain.trim().trimEnd('.').lowercase().split('.').filter { it.isNotEmpty() }
        if (labels.isEmpty()) return null

        val out = ByteArrayOutputStream(64)
        // Copy 12-byte DNS header, force QDCOUNT=1 and zero answer/authority/additional counts
        out.write(originalQuery.dnsPayload, 0, 4)
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(ByteArray(6)) // ANCOUNT=0, NSCOUNT=0, ARCOUNT=0

        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            if (bytes.size > 63) return null
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
        out.write(0x00)
        // Preserve original QTYPE + QCLASS (last 4 bytes of question)
        out.write(originalQuery.dnsPayload, originalQuery.questionEndOffset - 4, 4)
        return out.toByteArray()
    }

    /** Rebinds the alias answer's A/AAAA records to the original queried name. */
    fun buildRewrittenAnswerPayload(
        originalQuery: ParsedDnsQuery,
        upstreamAliasResponse: ByteArray
    ): ByteArray? {
        if (upstreamAliasResponse.size < 12) return null
        val qdCount = ((upstreamAliasResponse[4].toInt() and 0xFF) shl 8) or
            (upstreamAliasResponse[5].toInt() and 0xFF)
        val anCount = ((upstreamAliasResponse[6].toInt() and 0xFF) shl 8) or
            (upstreamAliasResponse[7].toInt() and 0xFF)
        val rcode = upstreamAliasResponse[3].toInt() and 0x0F

        val qType = ((originalQuery.dnsPayload[originalQuery.questionEndOffset - 4].toInt() and 0xFF) shl 8) or
            (originalQuery.dnsPayload[originalQuery.questionEndOffset - 3].toInt() and 0xFF)
        val qClass = ((originalQuery.dnsPayload[originalQuery.questionEndOffset - 2].toInt() and 0xFF) shl 8) or
            (originalQuery.dnsPayload[originalQuery.questionEndOffset - 1].toInt() and 0xFF)

        var pos = 12
        repeat(qdCount) {
            pos = DnsPacketParser.skipDnsName(upstreamAliasResponse, pos) ?: return null
            if (pos + 4 > upstreamAliasResponse.size) return null
            pos += 4
        }

        val matchingIps = mutableListOf<ByteArray>()
        var minTtl = 300L
        repeat(anCount) {
            pos = DnsPacketParser.skipDnsName(upstreamAliasResponse, pos) ?: return@repeat
            if (pos + 10 > upstreamAliasResponse.size) return@repeat
            val rrType = ((upstreamAliasResponse[pos].toInt() and 0xFF) shl 8) or
                (upstreamAliasResponse[pos + 1].toInt() and 0xFF)
            val rrClass = ((upstreamAliasResponse[pos + 2].toInt() and 0xFF) shl 8) or
                (upstreamAliasResponse[pos + 3].toInt() and 0xFF)
            val ttl = DnsPacketParser.readUInt32(upstreamAliasResponse, pos + 4).coerceIn(30L, 3600L)
            val rdLen = ((upstreamAliasResponse[pos + 8].toInt() and 0xFF) shl 8) or
                (upstreamAliasResponse[pos + 9].toInt() and 0xFF)
            pos += 10
            if (pos + rdLen > upstreamAliasResponse.size) return@repeat

            val isExpectedIp = rrClass == qClass && (
                (qType == DnsRecordType.A && rrType == DnsRecordType.A && rdLen == 4) ||
                    (qType == DnsRecordType.AAAA && rrType == DnsRecordType.AAAA && rdLen == 16)
                )
            if (isExpectedIp) {
                matchingIps.add(upstreamAliasResponse.copyOfRange(pos, pos + rdLen))
                minTtl = minOf(minTtl, ttl)
            }
            pos += rdLen
        }

        val out = ByteArrayOutputStream(originalQuery.questionEndOffset + matchingIps.size * 32)
        // TxID from original query
        out.write(originalQuery.dnsPayload[0].toInt() and 0xFF)
        out.write(originalQuery.dnsPayload[1].toInt() and 0xFF)
        // Flags: QR=1, RD preserved, RA=1, preserve upstream RCODE
        val rdBit = originalQuery.dnsPayload[2].toInt() and 0x01
        out.write(0x80 or rdBit)
        out.write(0x80 or (rcode and 0x0F))
        // QDCOUNT = 1
        out.write(0x00); out.write(0x01)
        // ANCOUNT = matchingIps.size
        out.write((matchingIps.size ushr 8) and 0xFF); out.write(matchingIps.size and 0xFF)
        // NSCOUNT = 0, ARCOUNT = 0
        out.write(ByteArray(4))

        // Original question bytes (offset 12 .. questionEndOffset)
        out.write(
            originalQuery.dnsPayload,
            12,
            originalQuery.questionEndOffset - 12
        )

        // Append Answer RRs bound via pointer 0xC00C to original QNAME at offset 12
        for (ipBytes in matchingIps) {
            out.write(0xC0); out.write(0x0C)
            out.write((qType ushr 8) and 0xFF); out.write(qType and 0xFF)
            out.write((qClass ushr 8) and 0xFF); out.write(qClass and 0xFF)
            out.write(((minTtl ushr 24) and 0xFF).toInt())
            out.write(((minTtl ushr 16) and 0xFF).toInt())
            out.write(((minTtl ushr 8) and 0xFF).toInt())
            out.write((minTtl and 0xFF).toInt())
            out.write((ipBytes.size ushr 8) and 0xFF); out.write(ipBytes.size and 0xFF)
            out.write(ipBytes)
        }
        return out.toByteArray()
    }

    /** NXDOMAIN (RCODE 3) with no answer records. */
    fun buildBlockedDnsResponsePacket(query: ParsedDnsQuery): ByteArray {
        return buildEmptyAnswerDnsResponsePacket(query, rcode = 3)
    }

    /** NODATA (RCODE 0) with no answer records, used for QUIC SVCB downgrade. */
    fun buildNoDataDnsResponsePacket(query: ParsedDnsQuery): ByteArray {
        return buildEmptyAnswerDnsResponsePacket(query, rcode = 0)
    }

    private fun buildEmptyAnswerDnsResponsePacket(query: ParsedDnsQuery, rcode: Int): ByteArray {
        val respSize = query.questionEndOffset.coerceIn(12, query.dnsPayload.size)
        val dnsResp = ByteArray(respSize)
        System.arraycopy(query.dnsPayload, 0, dnsResp, 0, respSize)

        val rdBit = query.dnsPayload[2].toInt() and 0x01
        dnsResp[2] = (0x80 or rdBit).toByte()
        dnsResp[3] = (0x80 or (rcode and 0x0F)).toByte() // RA=1, RCODE=0 (NODATA) or 3 (NXDOMAIN)
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

    /** Wraps a DNS payload in a UDP/IP packet addressed back to the querying socket. */
    fun buildDnsResponsePacket(query: ParsedDnsQuery, dnsResponsePayload: ByteArray): ByteArray {
        return if (query.isIpv6) {
            buildIpv6UdpResponsePacket(query, dnsResponsePayload)
        } else {
            buildIpv4UdpResponsePacket(query, dnsResponsePayload)
        }
    }

    private fun buildIpv4UdpResponsePacket(query: ParsedDnsQuery, dnsResponsePayload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + dnsResponsePayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x45.toByte()
        packet[1] = 0x00
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0x00
        packet[5] = 0x00
        packet[6] = 0x40.toByte()
        packet[7] = 0x00
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()
        packet[10] = 0
        packet[11] = 0
        System.arraycopy(query.dstIp, 0, packet, 12, 4)
        System.arraycopy(query.srcIp, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        packet[20] = ((query.dstPort ushr 8) and 0xFF).toByte()
        packet[21] = (query.dstPort and 0xFF).toByte()
        packet[22] = ((query.srcPort ushr 8) and 0xFF).toByte()
        packet[23] = (query.srcPort and 0xFF).toByte()
        packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        packet[26] = 0
        packet[27] = 0

        System.arraycopy(dnsResponsePayload, 0, packet, 28, dnsResponsePayload.size)

        val udpChecksum = computeTransportChecksum(
            packet = packet,
            transportOffset = ipHeaderLen,
            transportLen = udpLen,
            srcIp = query.dstIp,
            dstIp = query.srcIp,
            protocol = 17,
            isUdp = true
        )
        packet[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[27] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    private fun buildIpv6UdpResponsePacket(query: ParsedDnsQuery, dnsResponsePayload: ByteArray): ByteArray {
        val ipHeaderLen = 40
        val udpLen = 8 + dnsResponsePayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x60.toByte() // Version 6
        packet[4] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[5] = (udpLen and 0xFF).toByte()
        packet[6] = 17.toByte() // Next Header: UDP
        packet[7] = 64.toByte() // Hop Limit
        System.arraycopy(query.dstIp, 0, packet, 8, 16)
        System.arraycopy(query.srcIp, 0, packet, 24, 16)

        packet[40] = ((query.dstPort ushr 8) and 0xFF).toByte()
        packet[41] = (query.dstPort and 0xFF).toByte()
        packet[42] = ((query.srcPort ushr 8) and 0xFF).toByte()
        packet[43] = (query.srcPort and 0xFF).toByte()
        packet[44] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[45] = (udpLen and 0xFF).toByte()
        packet[46] = 0
        packet[47] = 0

        System.arraycopy(dnsResponsePayload, 0, packet, 48, dnsResponsePayload.size)

        val udpChecksum = computeTransportChecksum(
            packet = packet,
            transportOffset = ipHeaderLen,
            transportLen = udpLen,
            srcIp = query.dstIp,
            dstIp = query.srcIp,
            protocol = 17,
            isUdp = true
        )
        packet[46] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[47] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    /**
     * Builds an immediate TCP RST/RST-ACK packet when a browser attempts DoH (port 443) or
     * DoT (port 853) to a routed DNS resolver IP, forcing instant fallback to NetGate's OS DNS.
     */
    fun buildTcpResetPacket(packet: ByteArray, length: Int): ByteArray? {
        return runCatching { buildTcpResetPacketUnsafe(packet, length) }.getOrNull()
    }

    private fun buildTcpResetPacketUnsafe(packet: ByteArray, length: Int): ByteArray? {
        if (length < 40 || length > packet.size) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F

        val isIpv6: Boolean
        val ipHeaderLen: Int
        val totalLen: Int
        val srcIp: ByteArray
        val dstIp: ByteArray

        when (version) {
            4 -> {
                isIpv6 = false
                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (ihl < 20 || ihl + 20 > length) return null
                totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                if (totalLen < ihl + 20 || totalLen > length) return null
                if ((packet[9].toInt() and 0xFF) != 6) return null // TCP
                ipHeaderLen = ihl
                srcIp = packet.copyOfRange(12, 16)
                dstIp = packet.copyOfRange(16, 20)
            }
            6 -> {
                if (length < 60) return null
                isIpv6 = true
                ipHeaderLen = 40
                val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                totalLen = ipHeaderLen + payloadLen
                if (payloadLen < 20 || totalLen > length) return null
                if ((packet[6].toInt() and 0xFF) != 6) return null // TCP
                srcIp = packet.copyOfRange(8, 24)
                dstIp = packet.copyOfRange(24, 40)
            }
            else -> return null
        }

        val tcpFlags = packet[ipHeaderLen + 13].toInt() and 0xFF
        if ((tcpFlags and 0x04) != 0) return null // Never send RST in response to RST

        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        val seqNum = DnsPacketParser.readUInt32(packet, ipHeaderLen + 4)
        val ackNum = DnsPacketParser.readUInt32(packet, ipHeaderLen + 8)
        val dataOffsetBytes = ((packet[ipHeaderLen + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffsetBytes < 20 || ipHeaderLen + dataOffsetBytes > totalLen) return null
        val tcpPayloadLen = (totalLen - ipHeaderLen - dataOffsetBytes).coerceAtLeast(0)

        val isSyn = (tcpFlags and 0x02) != 0
        val isAck = (tcpFlags and 0x10) != 0
        val respFlags = if (isAck && !isSyn) 0x04 else 0x14 // RST or RST+ACK
        val respSeq = if (isAck && !isSyn) ackNum else 0L
        val respAck = if (isAck && !isSyn) 0L else (seqNum + tcpPayloadLen + if (isSyn) 1L else 0L) and 0xFFFFFFFFL

        val outIpLen = if (isIpv6) 40 else 20
        val tcpLen = 20
        val outPacket = ByteArray(outIpLen + tcpLen)

        if (isIpv6) {
            outPacket[0] = 0x60.toByte()
            outPacket[4] = 0
            outPacket[5] = tcpLen.toByte()
            outPacket[6] = 6.toByte()
            outPacket[7] = 64.toByte()
            System.arraycopy(dstIp, 0, outPacket, 8, 16)
            System.arraycopy(srcIp, 0, outPacket, 24, 16)
        } else {
            val outTotal = outIpLen + tcpLen
            outPacket[0] = 0x45.toByte()
            outPacket[2] = ((outTotal ushr 8) and 0xFF).toByte()
            outPacket[3] = (outTotal and 0xFF).toByte()
            outPacket[6] = 0x40.toByte()
            outPacket[8] = 64.toByte()
            outPacket[9] = 6.toByte()
            System.arraycopy(dstIp, 0, outPacket, 12, 4)
            System.arraycopy(srcIp, 0, outPacket, 16, 4)
            val ipCs = computeChecksum(outPacket, 0, 20)
            outPacket[10] = ((ipCs ushr 8) and 0xFF).toByte()
            outPacket[11] = (ipCs and 0xFF).toByte()
        }

        outPacket[outIpLen] = ((dstPort ushr 8) and 0xFF).toByte()
        outPacket[outIpLen + 1] = (dstPort and 0xFF).toByte()
        outPacket[outIpLen + 2] = ((srcPort ushr 8) and 0xFF).toByte()
        outPacket[outIpLen + 3] = (srcPort and 0xFF).toByte()
        writeUInt32(outPacket, outIpLen + 4, respSeq)
        writeUInt32(outPacket, outIpLen + 8, respAck)
        outPacket[outIpLen + 12] = 0x50.toByte() // 20-byte TCP header
        outPacket[outIpLen + 13] = respFlags.toByte()

        val tcpChecksum = computeTransportChecksum(
            packet = outPacket,
            transportOffset = outIpLen,
            transportLen = tcpLen,
            srcIp = dstIp,
            dstIp = srcIp,
            protocol = 6,
            isUdp = false
        )
        outPacket[outIpLen + 16] = ((tcpChecksum ushr 8) and 0xFF).toByte()
        outPacket[outIpLen + 17] = (tcpChecksum and 0xFF).toByte()

        return outPacket
    }

    /**
     * Builds an RFC 792 (ICMPv4 Type 3 Code 3) or RFC 4443 (ICMPv6 Type 1 Code 4) Port
     * Unreachable packet for blocked non-DNS UDP packets (such as QUIC/443 or WebRTC),
     * forcing immediate socket failure instead of long UDP timeout stalls.
     */
    fun buildIcmpUnreachablePacket(packet: ByteArray, length: Int): ByteArray? {
        return runCatching { buildIcmpUnreachablePacketUnsafe(packet, length) }.getOrNull()
    }

    private fun buildIcmpUnreachablePacketUnsafe(packet: ByteArray, length: Int): ByteArray? {
        if (length < 28 || packet.size < length) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F

        return when (version) {
            4 -> {
                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (ihl < 20 || ihl + 8 > length) return null
                val totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                if (totalLen < ihl + 8 || totalLen > length) return null
                val protocol = packet[9].toInt() and 0xFF
                if (protocol != 17) return null

                // Skip multicast (224.0.0.0/4) and limited broadcast (255.255.255.255)
                val firstDstOctet = packet[16].toInt() and 0xFF
                if ((firstDstOctet and 0xF0) == 0xE0) return null
                if (firstDstOctet == 0xFF &&
                    (packet[17].toInt() and 0xFF) == 0xFF &&
                    (packet[18].toInt() and 0xFF) == 0xFF &&
                    (packet[19].toInt() and 0xFF) == 0xFF
                ) {
                    return null
                }

                val quotedLen = minOf(totalLen, ihl + 8)
                val icmpLen = 8 + quotedLen
                val outTotal = 20 + icmpLen
                val out = ByteArray(outTotal)

                out[0] = 0x45.toByte()
                out[2] = ((outTotal ushr 8) and 0xFF).toByte()
                out[3] = (outTotal and 0xFF).toByte()
                out[6] = 0x40.toByte()
                out[8] = 64.toByte()
                out[9] = 1.toByte() // ICMPv4
                System.arraycopy(packet, 16, out, 12, 4) // src = original dst
                System.arraycopy(packet, 12, out, 16, 4) // dst = original src
                val ipCs = computeChecksum(out, 0, 20)
                out[10] = ((ipCs ushr 8) and 0xFF).toByte()
                out[11] = (ipCs and 0xFF).toByte()

                out[20] = 3.toByte() // Destination Unreachable
                out[21] = 3.toByte() // Port Unreachable
                System.arraycopy(packet, 0, out, 28, quotedLen)

                val icmpCs = computeChecksum(out, 20, icmpLen)
                out[22] = ((icmpCs ushr 8) and 0xFF).toByte()
                out[23] = (icmpCs and 0xFF).toByte()
                out
            }
            6 -> {
                if (length < 48) return null
                val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                val totalLen = 40 + payloadLen
                if (payloadLen < 8 || totalLen > length) return null
                val protocol = packet[6].toInt() and 0xFF
                if (protocol != 17) return null

                // Skip IPv6 multicast (ff00::/8)
                if ((packet[24].toInt() and 0xFF) == 0xFF) return null

                val quotedLen = minOf(totalLen, 1232)
                val icmpLen = 8 + quotedLen
                val out = ByteArray(40 + icmpLen)

                out[0] = 0x60.toByte()
                out[4] = ((icmpLen ushr 8) and 0xFF).toByte()
                out[5] = (icmpLen and 0xFF).toByte()
                out[6] = 58.toByte() // ICMPv6
                out[7] = 64.toByte()
                val srcIp = packet.copyOfRange(24, 40) // original dst becomes outer src
                val dstIp = packet.copyOfRange(8, 24)  // original src becomes outer dst
                System.arraycopy(srcIp, 0, out, 8, 16)
                System.arraycopy(dstIp, 0, out, 24, 16)

                out[40] = 1.toByte() // Destination Unreachable
                out[41] = 4.toByte() // Port Unreachable
                System.arraycopy(packet, 0, out, 48, quotedLen)

                val icmpCs = computeTransportChecksum(
                    packet = out,
                    transportOffset = 40,
                    transportLen = icmpLen,
                    srcIp = srcIp,
                    dstIp = dstIp,
                    protocol = 58,
                    isUdp = false
                )
                out[42] = ((icmpCs ushr 8) and 0xFF).toByte()
                out[43] = (icmpCs and 0xFF).toByte()
                out
            }
            else -> null
        }
    }

    private fun writeUInt32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    fun computeChecksum(buf: ByteArray, offset: Int, len: Int): Int {
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

    private fun computeTransportChecksum(
        packet: ByteArray,
        transportOffset: Int,
        transportLen: Int,
        srcIp: ByteArray,
        dstIp: ByteArray,
        protocol: Int,
        isUdp: Boolean
    ): Int {
        var sum = 0L
        for (i in srcIp.indices step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += protocol.toLong()
        sum += transportLen.toLong()

        var i = transportOffset
        val end = transportOffset + transportLen
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
        return if (isUdp && result == 0) 0xFFFF else result
    }
}
