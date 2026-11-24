package com.aistudio.netgate.service.dns

import java.net.InetAddress

/**
 * Stateless parser for DNS packets carried over UDP.
 *
 * This is a direct extraction of the previous `DnsPacketFilter` parsing code. Validation
 * thresholds, offsets and rejection rules are unchanged; every public entry point is
 * additionally guarded so malformed or truncated packets return null/empty instead of
 * throwing.
 */
object DnsPacketParser {

    /** Parses an IPv4 or IPv6 UDP/53 query. Returns null for anything else. */
    fun parseUdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        return runCatching { parseUdpDnsQueryUnsafe(packet, length) }.getOrNull()
    }

    /** Backwards-compatible alias for IPv4-only call sites. */
    fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        return parseUdpDnsQuery(packet, length)
    }

    private fun parseUdpDnsQueryUnsafe(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28 || length > packet.size) return null
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
                if (ihl < 20 || ihl + 8 > length) return null
                totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                if (totalLen < ihl + 8 || totalLen > length) return null
                val protocol = packet[9].toInt() and 0xFF
                if (protocol != 17) return null // UDP
                ipHeaderLen = ihl
                srcIp = packet.copyOfRange(12, 16)
                dstIp = packet.copyOfRange(16, 20)
            }
            6 -> {
                if (length < 48) return null
                isIpv6 = true
                ipHeaderLen = 40
                val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                totalLen = ipHeaderLen + payloadLen
                if (payloadLen < 8 || totalLen > length) return null
                val nextHeader = packet[6].toInt() and 0xFF
                if (nextHeader != 17) return null // UDP directly following IPv6 fixed header
                srcIp = packet.copyOfRange(8, 24)
                dstIp = packet.copyOfRange(24, 40)
            }
            else -> return null
        }

        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLen = ((packet[ipHeaderLen + 4].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 5].toInt() and 0xFF)
        val dnsStart = ipHeaderLen + 8
        val dnsEnd = (ipHeaderLen + udpLen).coerceAtMost(totalLen)
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
            isIpv6 = isIpv6,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayload = dnsPayload,
            questionEndOffset = questionEndOffset
        )
    }

    /** QTYPE of the first question, or 0 when the query is malformed. */
    fun extractQueryType(query: ParsedDnsQuery): Int {
        if (query.questionEndOffset < 16 || query.questionEndOffset > query.dnsPayload.size) return 0
        val idx = query.questionEndOffset - 4
        return ((query.dnsPayload[idx].toInt() and 0xFF) shl 8) or
            (query.dnsPayload[idx + 1].toInt() and 0xFF)
    }

    /**
     * Extracts A/AAAA addresses from a wire-format DNS response. Returns an empty list for
     * truncated, malformed or answer-less messages.
     */
    fun extractResolvedIpsFromDnsResponse(dnsPayload: ByteArray): List<String> {
        return runCatching { extractResolvedIpsUnsafe(dnsPayload) }.getOrDefault(emptyList())
    }

    private fun extractResolvedIpsUnsafe(dnsPayload: ByteArray): List<String> {
        if (dnsPayload.size < 12) return emptyList()
        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        val anCount = ((dnsPayload[6].toInt() and 0xFF) shl 8) or (dnsPayload[7].toInt() and 0xFF)
        if (anCount <= 0 || anCount > 64) return emptyList()

        var pos = 12
        repeat(qdCount.coerceAtMost(8)) {
            pos = skipDnsName(dnsPayload, pos) ?: return emptyList()
            if (pos + 4 > dnsPayload.size) return emptyList()
            pos += 4
        }

        val ips = mutableListOf<String>()
        repeat(anCount) {
            pos = skipDnsName(dnsPayload, pos) ?: return ips
            if (pos + 10 > dnsPayload.size) return ips
            val rrType = ((dnsPayload[pos].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF)
            val rrClass = ((dnsPayload[pos + 2].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 3].toInt() and 0xFF)
            val rdLen = ((dnsPayload[pos + 8].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 9].toInt() and 0xFF)
            pos += 10
            if (pos + rdLen > dnsPayload.size) return ips
            if (rrClass == 1 && ((rrType == DnsRecordType.A && rdLen == 4) || (rrType == DnsRecordType.AAAA && rdLen == 16))) {
                val ip = runCatching {
                    InetAddress.getByAddress(dnsPayload.copyOfRange(pos, pos + rdLen)).hostAddress
                }.getOrNull()
                if (!ip.isNullOrBlank()) {
                    ips.add(ip)
                }
            }
            pos += rdLen
        }
        return ips.distinct()
    }

    /** Skips a (possibly compressed) DNS name, returning the offset just past it. */
    fun skipDnsName(buf: ByteArray, startOffset: Int): Int? {
        var offset = startOffset
        var hops = 0
        while (offset < buf.size && hops < 64) {
            val len = buf[offset].toInt() and 0xFF
            if (len == 0) return offset + 1
            if ((len and 0xC0) == 0xC0) {
                return if (offset + 2 <= buf.size) offset + 2 else null
            }
            offset += 1 + len
            hops++
        }
        return null
    }

    fun ipBytesToInetAddress(ip: ByteArray): InetAddress? = runCatching {
        InetAddress.getByAddress(ip)
    }.getOrNull()

    fun readUInt32(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)
    }
}
