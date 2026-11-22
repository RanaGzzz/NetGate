package com.example.service.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transition and concurrency regression tests for [VpnSessionCoordinator].
 *
 * These tests use fake endpoints only, so they run on the JVM and exercise the real coordinator
 * logic (mutex, bounded waits, ownership rules and failure handling) without Android or WireGuard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VpnSessionCoordinatorTest {

    private val timeouts = VpnTransitionTimeouts(
        firewallStopTimeoutMs = 2_500L,
        firewallStartTimeoutMs = 2_500L,
        tunnelStopTimeoutMs = 3_000L,
        pollIntervalMs = 50L
    )

    private class FakeEndpoints {
        var firewallRunning = false
        var firewallStops = true
        var firewallStarts = true
        var tunnelRunning = false
        var tunnelStopClearsState = true
        var tunnelStartFailure: Throwable? = null
        var tunnelStopFailure: Throwable? = null
        var activationDelayMs = 0L
        var activations = 0
        var deactivations = 0
        val events = mutableListOf<String>()

        val ports = VpnSessionPorts(
            firewallRunning = { firewallRunning },
            stopFirewall = {
                events += "stop-firewall"
                if (firewallStops) firewallRunning = false
            },
            startFirewall = {
                events += "start-firewall"
                if (firewallStarts) firewallRunning = true
            },
            tunnelRunning = { tunnelRunning },
            activateTunnel = {
                events += "activate-begin"
                activations++
                if (activationDelayMs > 0L) delay(activationDelayMs)
                tunnelStartFailure?.let { throw it }
                tunnelRunning = true
                events += "activate-end"
            },
            deactivateTunnel = {
                events += "deactivate-tunnel"
                deactivations++
                tunnelStopFailure?.let { throw it }
                if (tunnelStopClearsState) tunnelRunning = false
            }
        )
    }

    private fun newCoordinator() = VpnSessionCoordinator(timeouts)

    @Test
    fun `connect stops the running firewall before activating the tunnel`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()

        val result = coordinator.connectTunnel(endpoints.ports, permissionGranted = true)

        assertTrue(result is VpnTransitionResult.Success)
        assertEquals(
            listOf("stop-firewall", "activate-begin", "activate-end"),
            endpoints.events
        )
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
        assertEquals(VpnTransitionPhase.ACTIVE_TUNNEL, coordinator.state.value.phase)
        assertTrue(coordinator.state.value.firewallRestorePending)
        assertTrue(coordinator.state.value.firewallWasRunningBeforeTunnel)
    }

    @Test
    fun `connect never activates the tunnel when the firewall does not stop in time`() = runTest {
        // Regression for the old `repeat(30) { if (!running) return@repeat; delay(50) }` loop,
        // which always slept 1.5 s and then activated the tunnel anyway.
        val endpoints = FakeEndpoints().apply {
            firewallRunning = true
            firewallStops = false
        }
        val coordinator = newCoordinator()

        val result = coordinator.connectTunnel(endpoints.ports, permissionGranted = true)

        assertTrue(result is VpnTransitionResult.Failed)
        val failure = result as VpnTransitionResult.Failed
        assertEquals(VpnTransitionResult.Stage.LOCAL_FIREWALL_STOP, failure.stage)
        assertTrue(failure.timedOut)
        assertEquals(0, endpoints.activations)
        assertEquals(listOf("stop-firewall"), endpoints.events)
        assertEquals(VpnTransitionPhase.ERROR, coordinator.state.value.phase)
        assertEquals(VpnSlotOwner.LOCAL_FIREWALL, coordinator.state.value.owner)
        // The wait is bounded by the configured budget, not by a fixed number of iterations.
        assertEquals(timeouts.firewallStopTimeoutMs, testScheduler.currentTime)
    }

    @Test
    fun `permission denial is rejected before any endpoint is touched`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()

        val result = coordinator.connectTunnel(endpoints.ports, permissionGranted = false)

        assertTrue(result is VpnTransitionResult.Rejected)
        assertEquals(
            VpnTransitionResult.RejectionReason.VPN_PERMISSION_DENIED,
            (result as VpnTransitionResult.Rejected).reason
        )
        assertTrue(endpoints.events.isEmpty())
        assertTrue(endpoints.firewallRunning)
        assertEquals(0, endpoints.activations)
        assertEquals(VpnTransitionPhase.ERROR, coordinator.state.value.phase)
    }

    @Test
    fun `a second connect while the tunnel owns the slot is rejected`() = runTest {
        val endpoints = FakeEndpoints()
        val coordinator = newCoordinator()

        assertTrue(
            coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
                is VpnTransitionResult.Success
        )
        val second = coordinator.connectTunnel(endpoints.ports, permissionGranted = true)

        assertTrue(second is VpnTransitionResult.Rejected)
        assertEquals(
            VpnTransitionResult.RejectionReason.OWNERSHIP_CONFLICT,
            (second as VpnTransitionResult.Rejected).reason
        )
        assertEquals(1, endpoints.activations)
    }

    @Test
    fun `concurrent connects are serialized and only one tunnel is created`() = runTest {
        val endpoints = FakeEndpoints().apply { activationDelayMs = 100L }
        val coordinator = newCoordinator()

        val first = launch { coordinator.connectTunnel(endpoints.ports, permissionGranted = true) }
        val second = launch { coordinator.connectTunnel(endpoints.ports, permissionGranted = true) }
        first.join()
        second.join()

        assertEquals(1, endpoints.activations)
        assertEquals(1, endpoints.events.count { it == "activate-end" })
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
    }

    @Test
    fun `connect and disconnect never overlap activation and teardown`() = runTest {
        val endpoints = FakeEndpoints().apply { activationDelayMs = 200L }
        val coordinator = newCoordinator()

        val connect = launch { coordinator.connectTunnel(endpoints.ports, permissionGranted = true) }
        runCurrent()
        val disconnect = launch { coordinator.disconnectTunnel(endpoints.ports) }
        connect.join()
        disconnect.join()

        val activationEnd = endpoints.events.indexOf("activate-end")
        val teardown = endpoints.events.indexOf("deactivate-tunnel")
        assertTrue("activation should finish before teardown", activationEnd >= 0)
        assertTrue("teardown must run after activation", teardown > activationEnd)
        assertEquals(1, endpoints.activations)
        assertEquals(1, endpoints.deactivations)
        assertEquals(VpnSlotOwner.NONE, coordinator.state.value.owner)
    }

    @Test
    fun `disconnect without a restore request leaves the firewall paused`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.events.clear()

        val result = coordinator.disconnectTunnel(endpoints.ports, restoreFirewallRequested = false)

        assertTrue(result is VpnTransitionResult.Success)
        assertEquals(listOf("deactivate-tunnel"), endpoints.events)
        assertFalse(endpoints.firewallRunning)
        assertTrue(coordinator.state.value.firewallRestorePending)
        assertEquals(VpnSlotOwner.NONE, coordinator.state.value.owner)
    }

    @Test
    fun `disconnect restores the firewall only after the tunnel reported down`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.events.clear()

        val result = coordinator.disconnectTunnel(endpoints.ports, restoreFirewallRequested = true)

        assertTrue(result is VpnTransitionResult.Success)
        assertTrue((result as VpnTransitionResult.Success).restoredFirewall)
        assertEquals(listOf("deactivate-tunnel", "start-firewall"), endpoints.events)
        assertTrue(endpoints.firewallRunning)
        assertEquals(VpnSlotOwner.LOCAL_FIREWALL, coordinator.state.value.owner)
        assertFalse(coordinator.state.value.firewallRestorePending)
    }

    @Test
    fun `disconnect does not restore a firewall that was never paused`() = runTest {
        val endpoints = FakeEndpoints()
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.events.clear()

        coordinator.disconnectTunnel(endpoints.ports, restoreFirewallRequested = true)

        assertFalse(endpoints.events.contains("start-firewall"))
        assertFalse(endpoints.firewallRunning)
        assertEquals(VpnSlotOwner.NONE, coordinator.state.value.owner)
    }

    @Test
    fun `tunnel start failure restores the paused firewall and reports the stage`() = runTest {
        val endpoints = FakeEndpoints().apply {
            firewallRunning = true
            tunnelStartFailure = IllegalStateException("TUN_CREATION_ERROR")
        }
        val coordinator = newCoordinator()

        val result = coordinator.connectTunnel(endpoints.ports, permissionGranted = true)

        assertTrue(result is VpnTransitionResult.Failed)
        val failure = result as VpnTransitionResult.Failed
        assertEquals(VpnTransitionResult.Stage.TUNNEL_START, failure.stage)
        assertNotNull(failure.cause)
        assertEquals("TUN_CREATION_ERROR", failure.cause?.message)
        assertEquals(1, endpoints.deactivations)
        assertTrue(endpoints.firewallRunning)
        assertEquals(VpnSlotOwner.LOCAL_FIREWALL, coordinator.state.value.owner)
        assertFalse(coordinator.state.value.firewallRestorePending)
    }

    @Test
    fun `tunnel shutdown failure keeps the slot and never restores the firewall`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.tunnelStopFailure = IllegalStateException("GO shutdown failed")
        endpoints.events.clear()

        val result = coordinator.disconnectTunnel(endpoints.ports, restoreFirewallRequested = true)

        assertTrue(result is VpnTransitionResult.Failed)
        assertEquals(
            VpnTransitionResult.Stage.TUNNEL_STOP,
            (result as VpnTransitionResult.Failed).stage
        )
        assertFalse(endpoints.events.contains("start-firewall"))
        assertFalse(endpoints.firewallRunning)
        assertEquals(VpnTransitionPhase.ERROR, coordinator.state.value.phase)
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
        assertTrue(coordinator.state.value.firewallRestorePending)
    }

    @Test
    fun `tunnel stop timeout is bounded and the firewall stays down`() = runTest {
        val endpoints = FakeEndpoints().apply {
            firewallRunning = true
            tunnelStopClearsState = false
        }
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.events.clear()
        val startedAt = testScheduler.currentTime

        val result = coordinator.disconnectTunnel(endpoints.ports, restoreFirewallRequested = true)

        assertTrue(result is VpnTransitionResult.Failed)
        assertTrue((result as VpnTransitionResult.Failed).timedOut)
        assertEquals(VpnTransitionResult.Stage.TUNNEL_STOP, result.stage)
        assertEquals(timeouts.tunnelStopTimeoutMs, testScheduler.currentTime - startedAt)
        assertFalse(endpoints.events.contains("start-firewall"))
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
    }

    @Test
    fun `unexpected disconnect releases ownership without starting the firewall`() = runTest {
        val endpoints = FakeEndpoints().apply { firewallRunning = true }
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.tunnelRunning = false
        endpoints.events.clear()

        val result = coordinator.handleUnexpectedTunnelDisconnect()

        assertTrue(result is VpnTransitionResult.UnexpectedDisconnect)
        assertEquals(VpnSlotOwner.NONE, coordinator.state.value.owner)
        assertTrue(coordinator.state.value.unexpectedDisconnect)
        assertTrue(coordinator.state.value.firewallRestorePending)
        assertFalse(endpoints.events.contains("start-firewall"))
        assertFalse(endpoints.firewallRunning)
    }

    @Test
    fun `local firewall cannot claim the slot while the tunnel owns it`() = runTest {
        val endpoints = FakeEndpoints()
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)

        assertFalse(coordinator.claimLocalFirewall())
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
    }

    @Test
    fun `local firewall cannot claim the slot during a transition`() = runTest {
        val endpoints = FakeEndpoints().apply { activationDelayMs = 200L }
        val coordinator = newCoordinator()

        val connect = launch { coordinator.connectTunnel(endpoints.ports, permissionGranted = true) }
        runCurrent()
        advanceTimeBy(50L)
        runCurrent()

        assertFalse(coordinator.claimLocalFirewall())
        assertEquals(VpnTransitionPhase.STARTING_TUNNEL, coordinator.state.value.phase)

        connect.join()
        assertTrue(coordinator.state.value.transitionInFlight.not())
    }

    @Test
    fun `local firewall claim is released when the firewall stops`() = runTest {
        val coordinator = newCoordinator()

        assertTrue(coordinator.claimLocalFirewall())
        assertEquals(VpnSlotOwner.LOCAL_FIREWALL, coordinator.state.value.owner)

        coordinator.releaseLocalFirewall()
        assertEquals(VpnSlotOwner.NONE, coordinator.state.value.owner)
        assertTrue(coordinator.claimLocalFirewall())
    }

    @Test
    fun `restoreLocalFirewall refuses to start while the tunnel is up`() = runTest {
        val endpoints = FakeEndpoints()
        val coordinator = newCoordinator()
        coordinator.connectTunnel(endpoints.ports, permissionGranted = true)
        endpoints.events.clear()

        val restored = coordinator.restoreLocalFirewall(endpoints.ports)

        assertFalse(restored)
        assertTrue(endpoints.events.isEmpty())
        assertEquals(VpnSlotOwner.TUNNEL, coordinator.state.value.owner)
    }
}
