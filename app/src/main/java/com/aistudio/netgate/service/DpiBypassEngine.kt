package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.DpiBypassMode
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class DpiStrategy(val title: String) {
    DIRECT("Direct"),
    SNI_MIDPOINT_SPLIT("SNI Split"),
    TLS_RECORD_SPLIT("TLS Record Split"),
    MICRO_CHUNKS("Micro Chunks"),
    HTTP_HEADER_DESYNC("HTTP Host Desync")
}

data class DpiBypassConfig(
    val mode: DpiBypassMode = DpiBypassMode.OFF,
    val chunkSize: Int = 4,
    val fragmentDelayMs: Int = 1,
    val idleKeepAliveEnabled: Boolean = true,
    val splitTlsRecords: Boolean = true
)

/**
 * Stateless + per-host adaptive DPI circumvention engine for Kotlin userspace sockets.
 *
 * - Locates the SNI hostname range inside TLS ClientHello records (RFC 8446 / RFC 6066)
 *   and HTTP Host headers.
 * - Supports TLS record splitting (two handshake records across SNI), TCP segment splitting,
 *   micro-chunking, and HTTP Host header desynchronization.
 * - Tracks per-host Autopilot strategy selection and escalation on connection failure.
 */
object DpiBypassEngine {

    private const val MAX_AUTOPILOT_CACHE = 512

    private data class CachedStrategyEntry(
        val strategy: DpiStrategy,
        val lastUsedSeq: Long
    )

    private val seqCounter = AtomicLong(0L)
    private val autopilotCache = ConcurrentHashMap<String, CachedStrategyEntry>()

    fun resolveStrategy(host: String, config: DpiBypassConfig): DpiStrategy {
        val normalized = normalizeHost(host)
        return when (config.mode) {
            DpiBypassMode.OFF -> DpiStrategy.DIRECT
            DpiBypassMode.AUTOPILOT -> {
                val entry = autopilotCache[normalized]
                if (entry != null) {
                    autopilotCache[normalized] = entry.copy(lastUsedSeq = seqCounter.incrementAndGet())
                    entry.strategy
                } else {
                    if (config.splitTlsRecords) DpiStrategy.TLS_RECORD_SPLIT else DpiStrategy.SNI_MIDPOINT_SPLIT
                }
            }
            DpiBypassMode.SNI_SPLIT -> {
                if (config.splitTlsRecords) DpiStrategy.TLS_RECORD_SPLIT else DpiStrategy.SNI_MIDPOINT_SPLIT
            }
            DpiBypassMode.SNI_CHUNKS -> DpiStrategy.MICRO_CHUNKS
            DpiBypassMode.HTTP_DESYNC -> DpiStrategy.HTTP_HEADER_DESYNC
        }
    }

    fun recordSuccess(host: String, strategy: DpiStrategy) {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty() || strategy == DpiStrategy.DIRECT) return
        putCacheEntry(normalized, strategy)
    }

    /**
     * Promotes a failing domain to a stronger strategy under Autopilot.
     * Returns the escalated strategy if available, or null when exhausted.
     */
    fun escalateOnFailure(host: String, current: DpiStrategy): DpiStrategy? {
        val normalized = normalizeHost(host)
        val next = when (current) {
            DpiStrategy.DIRECT -> DpiStrategy.TLS_RECORD_SPLIT
            DpiStrategy.TLS_RECORD_SPLIT -> DpiStrategy.SNI_MIDPOINT_SPLIT
            DpiStrategy.SNI_MIDPOINT_SPLIT -> DpiStrategy.MICRO_CHUNKS
            DpiStrategy.HTTP_HEADER_DESYNC -> DpiStrategy.MICRO_CHUNKS
            DpiStrategy.MICRO_CHUNKS -> null
        } ?: return null

        if (normalized.isNotEmpty()) {
            putCacheEntry(normalized, next)
        }
        return next
    }

    fun getAutopilotLearnedCount(): Int = autopilotCache.size

    fun getCachedStrategy(host: String): DpiStrategy? =
        autopilotCache[normalizeHost(host)]?.strategy

    fun clearAutopilotCache() {
        autopilotCache.clear()
    }

    /**
     * Parses a TLS ClientHello record to return the exact byte range [start until endExclusive]
     * of the SNI `server_name` hostname string.
     */
    fun locateTlsSniRange(packet: ByteArray, length: Int): IntRange? {
        val safeLen = minOf(length, packet.size)
        if (safeLen < 43) return null
        if ((packet[0].toInt() and 0xFF) != 0x16) return null // TLS Handshake
        if ((packet[5].toInt() and 0xFF) != 0x01) return null // ClientHello

        // Record header (5) + Handshake header (4) + ClientHello version (2) + Random (32)
        var pos = 5 + 4 + 2 + 32
        if (pos >= safeLen) return null

        val sessionIdLen = packet[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > safeLen) return null

        val cipherSuitesLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen
        if (pos + 1 > safeLen) return null

        val compressionLen = packet[pos].toInt() and 0xFF
        pos += 1 + compressionLen
        if (pos + 2 > safeLen) return null

        val extensionsLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
        pos += 2
        val extensionsEnd = minOf(safeLen, pos + extensionsLen)

        while (pos + 4 <= extensionsEnd) {
            val extType = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            val extLen = ((packet[pos + 2].toInt() and 0xFF) shl 8) or (packet[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + extLen > extensionsEnd) break

            if (extType == 0x0000 && extLen >= 5) { // server_name (SNI)
                val sniListLen = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                var sniPos = pos + 2
                val sniEnd = minOf(pos + extLen, sniPos + sniListLen)
                while (sniPos + 3 <= sniEnd) {
                    val nameType = packet[sniPos].toInt() and 0xFF
                    val nameLen = ((packet[sniPos + 1].toInt() and 0xFF) shl 8) or (packet[sniPos + 2].toInt() and 0xFF)
                    sniPos += 3
                    if (sniPos + nameLen > sniEnd) break
                    if (nameType == 0x00 && nameLen in 1..253) { // host_name
                        return sniPos until (sniPos + nameLen)
                    }
                    sniPos += nameLen
                }
            }
            pos += extLen
        }
        return null
    }

    fun computeTlsSegmentSizes(
        length: Int,
        sniRange: IntRange?,
        strategy: DpiStrategy,
        chunkSize: Int
    ): List<Int> {
        val safeLen = length.coerceAtLeast(0)
        if (safeLen == 0) return emptyList()
        if (safeLen == 1 || strategy == DpiStrategy.DIRECT) {
            return listOf(safeLen)
        }

        return when (strategy) {
            DpiStrategy.TLS_RECORD_SPLIT,
            DpiStrategy.SNI_MIDPOINT_SPLIT,
            DpiStrategy.HTTP_HEADER_DESYNC -> {
                val splitPoint = if (sniRange != null && !sniRange.isEmpty()) {
                    // Split right inside the SNI hostname (e.g., 1 byte after start or near midpoint)
                    val offsetWithinSni = maxOf(1, sniRange.count() / 2)
                    (sniRange.first + offsetWithinSni).coerceIn(1, safeLen - 1)
                } else {
                    minOf(safeLen / 2, 16).coerceIn(1, safeLen - 1)
                }
                listOf(splitPoint, safeLen - splitPoint)
            }
            DpiStrategy.MICRO_CHUNKS -> {
                val step = chunkSize.coerceIn(1, 32)
                val fragmentedPrefixLimit = minOf(safeLen, 512)
                val segments = ArrayList<Int>(fragmentedPrefixLimit / step + 2)
                var offset = 0
                while (offset < fragmentedPrefixLimit) {
                    val seg = minOf(step, fragmentedPrefixLimit - offset)
                    segments.add(seg)
                    offset += seg
                }
                if (offset < safeLen) {
                    segments.add(safeLen - offset)
                }
                segments
            }
            DpiStrategy.DIRECT -> listOf(safeLen)
        }
    }

    /**
     * Convenience overload that disables Nagle's algorithm on [socket] before writing.
     * Note: Any outbound [Socket] used by a VpnService app should also be protected via
     * `vpnService.protect(socket)` prior to `connect()` to avoid routing loops.
     */
    fun writeTlsClientHello(
        socket: Socket,
        packet: ByteArray,
        length: Int,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ) {
        runCatching { socket.tcpNoDelay = true }
        writeTlsClientHello(
            output = socket.getOutputStream(),
            packet = packet,
            length = length,
            strategy = strategy,
            chunkSize = chunkSize,
            fragmentDelayMs = fragmentDelayMs
        )
    }

    /**
     * Writes an outbound TLS ClientHello buffer to [output] using [strategy].
     * When [strategy] == [DpiStrategy.TLS_RECORD_SPLIT], wraps the handshake into two
     * separate TLS Handshake records (0x16) split inside the SNI field.
     */
    fun writeTlsClientHello(
        output: OutputStream,
        packet: ByteArray,
        length: Int,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ) {
        val safeLen = minOf(length, packet.size)
        if (safeLen <= 0) return

        val sniRange = if (strategy != DpiStrategy.DIRECT && strategy != DpiStrategy.MICRO_CHUNKS) {
            locateTlsSniRange(packet, safeLen)
        } else {
            null
        }

        val delay = fragmentDelayMs.coerceIn(0, 15).toLong()

        // Check if this is a single TLS Handshake record (content type 0x16)
        val recordPayloadLen = if (safeLen >= 5) {
            ((packet[3].toInt() and 0xFF) shl 8) or (packet[4].toInt() and 0xFF)
        } else {
            -1
        }
        val isSingleTlsHandshakeRecord =
            (packet[0].toInt() and 0xFF) == 0x16 &&
                recordPayloadLen > 0 &&
                (5 + recordPayloadLen) == safeLen &&
                sniRange != null &&
                sniRange.first > 5 &&
                sniRange.last < safeLen - 1

        if (strategy == DpiStrategy.TLS_RECORD_SPLIT && isSingleTlsHandshakeRecord) {
            val splitPos = ((sniRange!!.first + 1).coerceIn(6, safeLen - 2))
            val body1Len = splitPos - 5
            val body2Len = safeLen - splitPos

            val rec1 = ByteArray(5 + body1Len).apply {
                this[0] = 0x16 // Handshake
                this[1] = packet[1]
                this[2] = packet[2]
                this[3] = ((body1Len ushr 8) and 0xFF).toByte()
                this[4] = (body1Len and 0xFF).toByte()
                System.arraycopy(packet, 5, this, 5, body1Len)
            }

            val rec2 = ByteArray(5 + body2Len).apply {
                this[0] = 0x16 // Handshake
                this[1] = packet[1]
                this[2] = packet[2]
                this[3] = ((body2Len ushr 8) and 0xFF).toByte()
                this[4] = (body2Len and 0xFF).toByte()
                System.arraycopy(packet, splitPos, this, 5, body2Len)
            }

            output.write(rec1)
            output.flush()
            if (delay > 0L) {
                runCatching { Thread.sleep(delay) }
            }
            output.write(rec2)
            output.flush()
            return
        }

        val segments = computeTlsSegmentSizes(safeLen, sniRange, strategy, chunkSize)
        var offset = 0
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

    fun transformPlainHttpHeaders(headerText: String, strategy: DpiStrategy): String {
        if (strategy != DpiStrategy.HTTP_HEADER_DESYNC) return headerText
        return headerText.replace(Regex("(?im)^Host:"), "hOsT:")
    }

    fun writeHttpRequest(
        socket: Socket,
        headerBytes: ByteArray,
        strategy: DpiStrategy,
        chunkSize: Int,
        fragmentDelayMs: Int
    ) {
        runCatching { socket.tcpNoDelay = true }
        writeHttpRequest(
            output = socket.getOutputStream(),
            headerBytes = headerBytes,
            strategy = strategy,
            chunkSize = chunkSize,
            fragmentDelayMs = fragmentDelayMs
        )
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

        val effectiveBytes = if (strategy == DpiStrategy.HTTP_HEADER_DESYNC) {
            val text = String(headerBytes, Charsets.ISO_8859_1)
            transformPlainHttpHeaders(text, strategy).toByteArray(Charsets.ISO_8859_1)
        } else {
            headerBytes
        }

        val delay = fragmentDelayMs.coerceIn(0, 15).toLong()
        if (strategy == DpiStrategy.MICRO_CHUNKS) {
            val step = chunkSize.coerceIn(1, 32)
            var offset = 0
            while (offset < effectiveBytes.size) {
                val seg = minOf(step, effectiveBytes.size - offset)
                output.write(effectiveBytes, offset, seg)
                output.flush()
                offset += seg
                if (delay > 0L && offset < effectiveBytes.size) {
                    runCatching { Thread.sleep(delay) }
                }
            }
            return
        }

        val headerStr = String(effectiveBytes, Charsets.ISO_8859_1)
        val hostIdx = headerStr.indexOf("host:", ignoreCase = true)
        val splitPoint = if (hostIdx >= 0) {
            val lineEnd = headerStr.indexOf("\r\n", startIndex = hostIdx).let {
                if (it < 0) effectiveBytes.size else it
            }
            val valueStart = (hostIdx + 5).coerceAtMost(lineEnd)
            ((valueStart + lineEnd) / 2).coerceIn(1, effectiveBytes.size - 1)
        } else {
            minOf(effectiveBytes.size / 2, 16).coerceIn(1, effectiveBytes.size - 1)
        }

        output.write(effectiveBytes, 0, splitPoint)
        output.flush()
        if (delay > 0L) {
            runCatching { Thread.sleep(delay) }
        }
        output.write(effectiveBytes, splitPoint, effectiveBytes.size - splitPoint)
        output.flush()
    }

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

    private fun putCacheEntry(normalizedHost: String, strategy: DpiStrategy) {
        if (autopilotCache.size >= MAX_AUTOPILOT_CACHE && !autopilotCache.containsKey(normalizedHost)) {
            val oldestKey = autopilotCache.entries.minByOrNull { it.value.lastUsedSeq }?.key
            if (oldestKey != null) {
                autopilotCache.remove(oldestKey)
            }
        }
        autopilotCache[normalizedHost] = CachedStrategyEntry(
            strategy = strategy,
            lastUsedSeq = seqCounter.incrementAndGet()
        )
    }

    private fun normalizeHost(host: String): String =
        host.trim().trimEnd('.').lowercase()
}
