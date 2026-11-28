package com.aistudio.netgate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Self-healing policy: the tunnel failure is visible to the UI and retried a bounded number of
 * times with growing delays.
 */
class TunnelSelfHealingTest {

    @Test
    fun `retry delays grow and stop after three attempts`() {
        assertEquals(1_000L, TunnelRetryPolicy.delayBeforeRetry(0))
        assertEquals(3_000L, TunnelRetryPolicy.delayBeforeRetry(1))
        assertEquals(10_000L, TunnelRetryPolicy.delayBeforeRetry(2))

        // Budget spent: no fourth retry, and negative counts never produce a delay.
        assertNull(TunnelRetryPolicy.delayBeforeRetry(3))
        assertNull(TunnelRetryPolicy.delayBeforeRetry(99))
        assertNull(TunnelRetryPolicy.delayBeforeRetry(-1))
    }

    @Test
    fun `runtime state carries a nullable tunnel error`() {
        assertNull(VpnRuntimeState().tunnelError)
        assertNull(VpnRuntimeState().copy().tunnelError)
        assertEquals(
            "DNS proxy tunnel could not start — retrying",
            VpnRuntimeState().copy(tunnelError = "DNS proxy tunnel could not start — retrying").tunnelError
        )
        // A healthy publish clears it again.
        assertNull(VpnRuntimeState(tunnelError = "boom").copy(tunnelError = null).tunnelError)
    }
}
