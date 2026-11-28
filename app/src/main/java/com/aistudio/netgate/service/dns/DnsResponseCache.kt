package com.aistudio.netgate.service.dns

/**
 * Small bounded cache for successful upstream DNS answers.
 *
 * Only answers that came back from a real resolver on the upstream-forward path are stored; the
 * engine consults it after policy evaluation, so blocked, SafeSearch-rewritten, YouTube-restricted
 * and QUIC-downgraded queries never read from it or write to it. Entries are keyed by
 * (qname, qtype) and live for the smallest answer TTL, clamped to [MIN_TTL_SECONDS] ..
 * [MAX_TTL_SECONDS] so a zero-TTL answer is not served stale and a week-long TTL cannot pin an
 * address after an upstream change.
 *
 * The cache is process-wide on purpose: it must be cleared from a static flush path as well as
 * from the running service. Access is synchronised and the payload is never handed out by
 * reference, because the caller rewrites the transaction ID before answering.
 */
object DnsResponseCache {

    /** Hard bound on remembered answers (an LRU eviction order is kept by LinkedHashMap). */
    const val MAX_ENTRIES = 1024

    const val MIN_TTL_SECONDS = 30L
    const val MAX_TTL_SECONDS = 600L

    private class Entry(val payload: ByteArray, val expiresAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun key(qname: String, qtype: Int): String = "${qname.trim().lowercase()}|$qtype"

    /** A copy of the cached answer, or null when absent or expired. */
    @Synchronized
    fun get(qname: String, qtype: Int, nowMs: Long): ByteArray? {
        val key = key(qname, qtype)
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs <= nowMs) {
            entries.remove(key)
            return null
        }
        return entry.payload.copyOf()
    }

    /**
     * A cached answer stamped with the transaction ID of [queryPayload], ready to be sent back to
     * the client. Returns null when nothing is cached or the entry expired.
     */
    fun getForQuery(
        qname: String,
        qtype: Int,
        queryPayload: ByteArray,
        nowMs: Long
    ): ByteArray? {
        val cached = get(qname, qtype, nowMs) ?: return null
        if (cached.size >= 2 && queryPayload.size >= 2) {
            cached[0] = queryPayload[0]
            cached[1] = queryPayload[1]
        }
        return cached
    }

    /**
     * Stores [responsePayload] when it carries at least one answer with a readable TTL.
     * Returns true when the answer was cached.
     */
    @Synchronized
    fun put(qname: String, qtype: Int, responsePayload: ByteArray, nowMs: Long): Boolean {
        val ttlSeconds = minAnswerTtlSeconds(responsePayload) ?: return false
        val clamped = ttlSeconds.coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)
        entries[key(qname, qtype)] = Entry(
            payload = responsePayload.copyOf(),
            expiresAtMs = nowMs + clamped * 1000L
        )
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size

    /**
     * Smallest TTL across the answer records of a DNS response, or null when the message is
     * malformed or has no answers. Compression pointers are followed one level deep, which is all
     * a name in the answer section can use to point at the question.
     */
    fun minAnswerTtlSeconds(response: ByteArray): Long? {
        if (response.size < 12) return null
        val questionCount = readUInt16(response, 4)
        val answerCount = readUInt16(response, 6)
        if (answerCount <= 0) return null

        var offset = 12
        repeat(questionCount) {
            offset = skipName(response, offset) ?: return null
            if (offset + 4 > response.size) return null
            offset += 4
        }

        var minTtl: Long? = null
        repeat(answerCount) {
            offset = skipName(response, offset) ?: return null
            if (offset + 10 > response.size) return null
            val ttl = readUInt32(response, offset + 4) ?: return null
            val rdLength = readUInt16(response, offset + 8)
            offset += 10 + rdLength
            if (offset > response.size) return null
            if (minTtl == null || ttl < minTtl!!) minTtl = ttl
        }
        return minTtl
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long? {
        if (offset + 4 > data.size) return null
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    /** Returns the offset just past a (possibly compressed) DNS name, or null when truncated. */
    private fun skipName(data: ByteArray, start: Int): Int? {
        var offset = start
        while (true) {
            if (offset >= data.size) return null
            val length = data[offset].toInt() and 0xFF
            when {
                length == 0 -> return offset + 1
                (length and 0xC0) == 0xC0 -> return offset + 2
                length > 63 -> return null
                else -> offset += 1 + length
            }
        }
    }
}
