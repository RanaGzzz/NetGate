package com.example.service

import com.example.data.model.DpiBypassMode
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

enum class DpiStrategy(val title: String) {
    DIRECT("Direct"),
    SNI_MIDPOINT_SPLIT("SNI Split"),
    MICRO_CHUNKS("Micro Chunks"),
    HTTP_HEADER_DESYNC("HTTP Host Desync")
}

data class DpiBypassConfig(
    val mode: DpiBypassMode = DpiBypassMode.OFF,
    val chunkSize: Int = 4,
    val fragmentDelayMs: Int = 1,
    val idleKeepAliveEnabled: Boolean = true
)

/**
 * Stateless DPI circumvention engine for Kotlin userspace sockets.
 *
 * Reshapes outbound TLS ClientHello packets (splitting across the SNI extension midpoint or
 * micro-chunking handshake segments with TCP_NODELAY) and plain HTTP Host headers so stateless
 * Deep Packet Inspection middleboxes cannot inspect target hostnames in flight—all without root
 * privileges or remote commercial VPN servers.
 */
object DpiBypassEngine {

    private const val MAX_AUTOPILOT_CACHE = 512
    private val autopilotCache = ConcurrentHashMap<String, DpiStrategy>()

    fun resolveStrategy(host: String, config: DpiBypassConfig): DpiStrategy {
        val normalized = normalizeHost(host)
        return when (config.mode) {
            DpiBypassMode.OFF -> DpiStrategy.DIRECT
            DpiBypassMode.AUTOPILOT -> {
                autopilotCache[normalized] ?: DpiStrategy.SNI_MIDPOINT_SPLIT
            }
            DpiBypassMode.SNI_SPLIT -> DpiStrategy.SNI_MIDPOINT_SPLIT
            DpiBypassMode.SNI_CHUNKS -> DpiStrategy.MICRO_CHUNKS
            DpiBypassMode.HTTP_DESYNC -> DpiStrategy.HTTP_HEADER_DESYNC
        }
    }

    fun recordSuccess(host: String, strategy: DpiStrategy) {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty() || strategy == DpiStrategy.DIRECT) return
        if (autopilotCache.size >= MAX_AUTOPILOT_CACHE && !autopilotCache.containsKey(normalized)) {
            autopilotCache.keys.firstOrNull()?.let { autopilotCache.remove(it) }
        }
        autopilotCache[normalized] = strategy
    }

    /**
     * Promotes a failing domain to a stronger fragmentation strategy under Autopilot.
     * Returns the escalated strategy if an escalation was available, or null if exhausted.
     */
    fun escalateOnFailure(host: String, current: DpiStrategy): DpiStrategy? {
        val normalized = normalizeHost(host)
        val next = when (current) {
            DpiStrategy.DIRECT -> DpiStrategy.SNI_MIDPOINT_SPLIT
            DpiStrategy.SNI_MIDPOINT_SPLIT -> DpiStrategy.MICRO_CHUNKS
            DpiStrategy.MICRO_CHUNKS -> null
            DpiStrategy.HTTP_HEADER_DESYNC -> DpiStrategy.MICRO_CHUNKS
        } ?: return null

        if (normalized.isNotEmpty()) {
            if (autopilotCache.size >= MAX_AUTOPILOT_CACHE && !autopilotCache.containsKey(normalized)) {
                autopilotCache.keys.firstOrNull()?.let { autopilotCache.remove(it) }
            }
            autopilotCache[normalized] = next
        }
        return next
    }

    fun getAutopilotLearnedCount(): Int = autopilotCache.size

    fun getCachedStrategy(host: String): DpiStrategy? = autopilotCache[normalizeHost(host)]

    fun clearAutopilotCache() {
        autopilotCache.clear()
    }

    /**
     * Parses an unencrypted TLS ClientHello packet to return the exact inclusive byte range
     * `[start..endInclusive]` of the SNI `server_name` hostname string, enabling surgical
     * TCP segmentation right across the middle of the SNI field.
     */
    fun locateTlsSniRange(packet: ByteArray, length: Int): IntRange? {
        if (length < 43 || packet.size < length) return null
        if ((packet[0].toInt() and 0xFF) != 0x16) return null // TLS Handshake
        if ((packet[5].toInt() and 0xFF) != 0x01) return null // ClientHello

        var pos = 5 + 4 + 2 + 32 // Record(5) + HandshakeHdr(4) + Version(2) + Random(32)
        if (pos >= length) return null

        val sessionIdLen = packet[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > length) return null

        val cipherSuitesLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen
        if (pos + 1 > length) return null

        val compressionLen = packet[pos].toInt() and 0xFF
        pos += 1 + compressionLen
        if (pos + 2 > length) return null

        val extensionsLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
        pos += 2
        val extensionsEnd = (pos + extensionsLen).coerceAtMost(length)

        while (pos + 4 <= extensionsEnd) {
            val extType = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            val extLen = ((packet[pos + 2].toInt() and 0xFF) shl 8) or (packet[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + extLen > extensionsEnd) break

            if (extType == 0x0000 && extLen >= 5) { // server_name extension
                val sniListLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                var sniPos = pos + 2
                val sniEnd = (sniPos + sniListLen).coerceAtMost(pos + extLen)
                while (sniPos + 3 <= sniEnd) {
                    val nameType = packet[sniPos].toInt() and 0xFF
                    val nameLen = ((packet[sniPos + 1].toInt() and 0xFF) shl 8) or (packet[sniPos + 2].toInt() and 0xFF)
                    sniPos += 3
                    if (sniPos + nameLen > sniEnd) break
                    if (nameType == 0x00 && nameLen in 1..253) {
                        return sniPos until (sniPos + nameLen)
                    }
                    sniPos += nameLen
                }
            }
            pos += extLen
        }
        return null
    }

    /**
     * Computes deterministic TCP write segment sizes for [writeTlsClientHello].
     */
    fun computeTlsSegmentSizes(
        length: Int,
        sniRange: IntRange?,
        strategy: DpiStrategy,
        chunkSize: Int
    ): List<Int> {
        if (length <= 1 || strategy == DpiStrategy.DIRECT) {
            return if (length > 0) listOf(length) else emptyList()
        }

        return when (strategy) {
            DpiStrategy.SNI_MIDPOINT_SPLIT, DpiStrategy.HTTP_HEADER_DESYNC -> {
                val splitPoint = if (sniRange != null && sniRange.first < sniRange.last) {
                    (sniRange.first + (sniRange.last - sniRange.first + 1) / 2).coerceIn(1, length - 1)
                } else {
                    minOf(length / 2, 16).coerceIn(1, length - 1)
                }
                listOf(splitPoint, length - splitPoint)
            }
            DpiStrategy.MICRO_CHUNKS -> {
                val step = chunkSize.coerceIn(1, 32)
                val fragmentedPrefixLimit = minOf(length, 512)
                val segments = ArrayList<Int>(fragmentedPrefixLimit / step + 2)
                var offset = 0
                while (offset < fragmentedPrefixLimit) {
                    val seg = minOf(step, fragmentedPrefixLimit - offset)
                    segments.add(seg)
                    offset += seg
                }
                if (offset < length) {
                    segments.add(length - offset)
                }
                segments
            }
            DpiStrategy.DIRECT -> listOf(length)
        }
    }

    /**
     * Writes an outbound TLS ClientHello buffer to [output] using the specified DPI bypass
     * segmentation strategy, flushing each fragment so the OS emits separate TCP segments.
     */
    fun writeTlsClientHello(
        output: OutputStream,
        packet: ByteArray,
        length: Int,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ) {
        if (length <= 0) return
        val sniRange = if (strategy == DpiStrategy.SNI_MIDPOINT_SPLIT || strategy == DpiStrategy.HTTP_HEADER_DESYNC) {
            locateTlsSniRange(packet, length)
        } else {
            null
        }
        val segments = computeTlsSegmentSizes(length, sniRange, strategy, chunkSize)
        var offset = 0
        val delay = fragmentDelayMs.coerceIn(0, 15).toLong()
        for (i in segments.indices) {
            val segLen = segments[i]
            output.write(packet, offset, segLen)
            output.flush()
            offset += segLen
            if (delay > 0L && i < segments.lastIndex) {
                runCatching { Thread.sleep(delay) }
            }
        }
    }

    /**
     * Rewrites/fragments a plain HTTP request header block when DPI bypass is active:
     * in [DpiStrategy.HTTP_HEADER_DESYNC] it randomizes `Host:` header casing (`hOsT:`)
     * and splits TCP segments right inside the Host value.
     */
    fun transformPlainHttpHeaders(headerText: String, strategy: DpiStrategy): String {
        if (strategy != DpiStrategy.HTTP_HEADER_DESYNC) return headerText
        return headerText.replace(Regex("(?im)^Host:"), "hOsT:")
    }

    fun writeHttpRequest(
        output: OutputStream,
        headerBytes: ByteArray,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ) {
        if (headerBytes.isEmpty()) return
        if (strategy == DpiStrategy.DIRECT) {
            output.write(headerBytes)
            output.flush()
            return
        }

        val delay = fragmentDelayMs.coerceIn(0, 15).toLong()
        if (strategy == DpiStrategy.MICRO_CHUNKS) {
            val step = chunkSize.coerceIn(1, 32)
            var offset = 0
            while (offset < headerBytes.size) {
                val seg = minOf(step, headerBytes.size - offset)
                output.write(headerBytes, offset, seg)
                output.flush()
                offset += seg
                if (delay > 0L && offset < headerBytes.size) {
                    runCatching { Thread.sleep(delay) }
                }
            }
            return
        }

        // Split right inside the Host: header value
        val headerStr = String(headerBytes, Charsets.ISO_8859_1)
        val hostIdx = headerStr.indexOf("host:", ignoreCase = true)
        val splitPoint = if (hostIdx >= 0) {
            val lineEnd = headerStr.indexOf("\r\n", startIndex = hostIdx).let {
                if (it < 0) headerBytes.size else it
            }
            val valueStart = (hostIdx + 5).coerceAtMost(lineEnd)
            ((valueStart + lineEnd) / 2).coerceIn(1, headerBytes.size - 1)
        } else {
            minOf(headerBytes.size / 2, 16).coerceIn(1, headerBytes.size - 1)
        }

        output.write(headerBytes, 0, splitPoint)
        output.flush()
        if (delay > 0L) {
            runCatching { Thread.sleep(delay) }
        }
        output.write(headerBytes, splitPoint, headerBytes.size - splitPoint)
        output.flush()
    }

    /**
     * Detects common ISP DNS poisoning responses where a public internet domain resolves
     * to a loopback, unspecified, or RFC1918 private sinkhole IP.
     */
    fun isLikelyForgedDnsResponse(domain: String, resolvedIps: List<String>): Boolean {
        if (resolvedIps.isEmpty()) return false
        val d = domain.trim().trimEnd('.').lowercase()
        if (d.isEmpty() ||
            d == "localhost" ||
            d.endsWith(".local") ||
            d.endsWith(".lan") ||
            d.endsWith(".home") ||
            d.endsWith(".arpa") ||
            d.endsWith(".internal")
        ) {
            return false
        }
        return resolvedIps.any { ip ->
            val clean = ip.trim().lowercase()
            clean == "0.0.0.0" ||
                clean == "::" ||
                clean == "::1" ||
                clean.startsWith("127.") ||
                clean.startsWith("10.") ||
                clean.startsWith("192.168.") ||
                isRfc172Private(clean)
        }
    }

    private fun isRfc172Private(ip: String): Boolean {
        if (!ip.startsWith("172.")) return false
        val parts = ip.split('.')
        if (parts.size < 2) return false
        val second = parts[1].toIntOrNull() ?: return false
        return second in 16..31
    }

    private fun normalizeHost(host: String): String =
        host.trim().trimEnd('.').lowercase()
}
