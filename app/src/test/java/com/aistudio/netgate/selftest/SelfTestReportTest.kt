package com.aistudio.netgate.selftest

import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestReportTest {

    @Test
    fun `redact preserves NetGate stack trace and strips other packages`() {
        val stack = """
            java.lang.IllegalStateException: boom
                at com.aistudio.netgate.service.NetGateVpnService.rebuildTunnel(NetGateVpnService.kt:10)
                at com.example.otherapp.Foo.bar(Foo.kt:5)
                at java.lang.Thread.run(Thread.java:819)
                at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
        """.trimIndent()

        val redacted = SelfTestReport.redact(stack)

        assertTrue(redacted.contains("com.aistudio.netgate.service.NetGateVpnService.rebuildTunnel"))
        assertTrue(redacted.contains("java.lang.IllegalStateException"))
        assertTrue(redacted.contains("kotlin.coroutines.jvm.internal.BaseContinuationImpl"))
        assertTrue(!redacted.contains("com.example.otherapp"))
        assertTrue(redacted.contains("<package>"))
    }

    @Test
    fun `redact keeps log timestamps`() {
        val text = "12:34:56.789 start and 09:08:07 end"
        val redacted = SelfTestReport.redact(text)
        assertTrue(redacted.contains("12:34:56.789"))
        assertTrue(redacted.contains("09:08:07"))
        assertTrue(!redacted.contains("<ip>"))
    }

    @Test
    fun `redact redacts hyphenated hostname prefixes as one token`() {
        val text = "Resolved amazon-tam-api.example.com"
        val redacted = SelfTestReport.redact(text)
        assertTrue(redacted.contains("<host>"))
        assertTrue(!redacted.contains("amazon-tam"))
    }

    @Test
    fun `redact strips ips and hostnames`() {
        val text = "Connected to 192.0.2.1 and api.example.com"
        val redacted = SelfTestReport.redact(text)
        assertTrue(!redacted.contains("192.0.2.1"))
        assertTrue(redacted.contains("<ip>"))
        assertTrue(redacted.contains("<host>") || redacted.contains("<package>"))
    }
}