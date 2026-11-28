package com.aistudio.netgate.service.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/** TTL handling, transaction-ID rewriting and the bound on the DNS response cache. */
class DnsResponseCacheTest {

    @Before
    fun clearCache() {
        DnsResponseCache.clear()
    }

    private fun buildResponse(
        qname: String,
        qtype: Int = DnsRecordType.A,
        txId: Int = 0x1234,
        ttlSeconds: Long = 300L,
        answers: Int = 1
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((txId ushr 8) and 0xFF)
        out.write(txId and 0xFF)
        out.write(0x81); out.write(0x80)
        out.write(0x00); out.write(0x01) // QDCOUNT
        out.write(0x00); out.write(answers) // ANCOUNT
        out.write(0x00); out.write(0x00) // NSCOUNT
        out.write(0x00); out.write(0x00) // ARCOUNT
        for (label in qname.split('.').filter { it.isNotEmpty() }) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write((qtype ushr 8) and 0xFF); out.write(qtype and 0xFF)
        out.write(0x00); out.write(0x01)

        repeat(answers) {
            out.write(0xC0); out.write(0x0C) // name pointer to the question
            out.write(0x00); out.write(0x01) // A
            out.write(0x00); out.write(0x01) // IN
            out.write(((ttlSeconds ushr 24) and 0xFF).toInt())
            out.write(((ttlSeconds ushr 16) and 0xFF).toInt())
            out.write(((ttlSeconds ushr 8) and 0xFF).toInt())
            out.write((ttlSeconds and 0xFF).toInt())
            out.write(0x00); out.write(0x04)
            out.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        }
        return out.toByteArray()
    }

    @Test
    fun `reads the smallest answer ttl`() {
        val response = buildResponse("example.com", ttlSeconds = 120L)
        assertEquals(120L, DnsResponseCache.minAnswerTtlSeconds(response))
    }

    @Test
    fun `an answerless response is never cached`() {
        val response = buildResponse("example.com", answers = 0)
        assertNull(DnsResponseCache.minAnswerTtlSeconds(response))
        assertEquals(false, DnsResponseCache.put("example.com", DnsRecordType.A, response, nowMs = 0L))
        assertNull(DnsResponseCache.get("example.com", DnsRecordType.A, nowMs = 0L))
    }

    @Test
    fun `a short ttl is served for at least thirty seconds`() {
        val response = buildResponse("short.test", ttlSeconds = 5L)
        assertTrue(DnsResponseCache.put("short.test", DnsRecordType.A, response, nowMs = 0L))

        assertNotNull(DnsResponseCache.get("short.test", DnsRecordType.A, nowMs = 29_000L))
        assertNull(DnsResponseCache.get("short.test", DnsRecordType.A, nowMs = 30_001L))
    }

    @Test
    fun `a long ttl is capped at ten minutes`() {
        val response = buildResponse("long.test", ttlSeconds = 86_400L)
        assertTrue(DnsResponseCache.put("long.test", DnsRecordType.A, response, nowMs = 0L))

        assertNotNull(DnsResponseCache.get("long.test", DnsRecordType.A, nowMs = 599_000L))
        assertNull(DnsResponseCache.get("long.test", DnsRecordType.A, nowMs = 601_000L))
    }

    @Test
    fun `a hit is stamped with the new query transaction id`() {
        val stored = buildResponse("example.com", txId = 0x1234, ttlSeconds = 300L)
        DnsResponseCache.put("example.com", DnsRecordType.A, stored, nowMs = 0L)

        val query = ByteArray(12).apply {
            this[0] = 0xAB.toByte()
            this[1] = 0xCD.toByte()
        }
        val served = DnsResponseCache.getForQuery("example.com", DnsRecordType.A, query, nowMs = 1_000L)

        assertNotNull(served)
        assertEquals(0xAB, served!![0].toInt() and 0xFF)
        assertEquals(0xCD, served[1].toInt() and 0xFF)
        // Everything after the transaction ID is the upstream answer, unchanged.
        assertArrayEquals(stored.copyOfRange(2, stored.size), served.copyOfRange(2, served.size))
    }

    @Test
    fun `keys are per qname and qtype`() {
        DnsResponseCache.put("example.com", DnsRecordType.A, buildResponse("example.com"), nowMs = 0L)
        assertNull(DnsResponseCache.get("example.com", DnsRecordType.AAAA, nowMs = 0L))
        assertNull(DnsResponseCache.get("other.example.com", DnsRecordType.A, nowMs = 0L))
    }

    @Test
    fun `the cache is bounded`() {
        repeat(DnsResponseCache.MAX_ENTRIES + 10) { index ->
            DnsResponseCache.put(
                qname = "host$index.test",
                qtype = DnsRecordType.A,
                responsePayload = buildResponse("host$index.test"),
                nowMs = 0L
            )
        }
        assertEquals(DnsResponseCache.MAX_ENTRIES, DnsResponseCache.size())
    }

    @Test
    fun `clear empties the cache`() {
        DnsResponseCache.put("example.com", DnsRecordType.A, buildResponse("example.com"), nowMs = 0L)
        DnsResponseCache.clear()
        assertEquals(0, DnsResponseCache.size())
        assertNull(DnsResponseCache.get("example.com", DnsRecordType.A, nowMs = 0L))
    }
}
