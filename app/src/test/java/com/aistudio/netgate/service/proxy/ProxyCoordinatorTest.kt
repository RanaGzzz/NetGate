package com.aistudio.netgate.service.proxy

import com.aistudio.netgate.service.LocalFilteringProxyServer
import com.aistudio.netgate.service.ProxyFilterDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Port validation, conflict prevention and lifecycle tests for [ProxyCoordinator].
 *
 * These use the real [LocalFilteringProxyServer] on loopback, so bind behavior, the ephemeral
 * fallback and simultaneous operation are exercised for real (no Android, no Robolectric).
 */
class ProxyCoordinatorTest {

    private fun newServer(bindPort: Int, config: ProxyConfiguration): LocalFilteringProxyServer =
        LocalFilteringProxyServer(
            port = bindPort,
            bindHost = config.bindHost,
            stripTrackingParams = config.stripTrackingParams,
            protectSocket = { true },
            evaluateRequest = { _, _ -> ProxyFilterDecision(allow = true, reason = "allowed") },
            onRequestLogged = { _, _ -> },
            onClientTraffic = { _, _, _, _, _ -> }
        )

    private fun freePort(host: String = "127.0.0.1"): Int =
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(host, 0))
            socket.localPort
        }

    private fun occupiedSocket(host: String): ServerSocket {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(host, 0), 8)
        return socket
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
    }.isSuccess

    @Test
    fun `local proxy falls back to an ephemeral loopback port when the configured port is occupied`() {
        occupiedSocket("127.0.0.1").use { blocker ->
            val coordinator = ProxyCoordinator()
            val configured = ProxyConfiguration.localFirewall(blocker.localPort, stripTrackingParams = false)
            var factoryCalls = 0

            val result = coordinator.start(configured) { port, config ->
                factoryCalls += 1
                newServer(port, config)
            }

            assertTrue("expected Started, got $result", result is ProxyStartResult.Started)
            val started = result as ProxyStartResult.Started
            assertTrue(started.usedEphemeralFallback)
            assertEquals(ProxyStartResult.Started::class, started::class)
            assertNotEquals(blocker.localPort, started.boundPort)
            assertTrue(started.boundPort in ProxyConfiguration.MIN_PORT..ProxyConfiguration.MAX_PORT)
            assertTrue(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
            assertEquals(started.boundPort, coordinator.boundPort(ProxyRole.LOCAL_FIREWALL))

            // A rebuild with the same preferences must reuse the fallback listener, not rebind it.
            val again = coordinator.start(configured) { port, config ->
                factoryCalls += 1
                newServer(port, config)
            }
            assertTrue("expected AlreadyRunning, got $again", again is ProxyStartResult.AlreadyRunning)
            assertEquals(started.boundPort, (again as ProxyStartResult.AlreadyRunning).boundPort)
            assertEquals(1, factoryCalls)

            coordinator.stopAll()
        }
    }

    @Test
    fun `hotspot proxy reports an occupied port instead of silently moving`() {
        occupiedSocket("0.0.0.0").use { blocker ->
            val coordinator = ProxyCoordinator()
            val configured = ProxyConfiguration.hotspot(blocker.localPort, stripTrackingParams = false)

            val result = coordinator.start(configured) { port, config -> newServer(port, config) }

            assertTrue("expected Failure, got $result", result is ProxyStartResult.Failure)
            assertEquals(blocker.localPort, (result as ProxyStartResult.Failure).port)
            assertFalse(coordinator.isActive(ProxyRole.HOTSPOT))
            assertNull(coordinator.boundPort(ProxyRole.HOTSPOT))
            coordinator.stopAll()
        }
    }

    @Test
    fun `identical configured ports are prevented for the hotspot proxy`() {
        val coordinator = ProxyCoordinator()
        val sharedPort = freePort()
        val localConfig = ProxyConfiguration.localFirewall(sharedPort, stripTrackingParams = false)
        val local = coordinator.start(localConfig) { port, config -> newServer(port, config) }
        assertTrue(local is ProxyStartResult.Started)
        assertEquals(sharedPort, (local as ProxyStartResult.Started).boundPort)

        var hotspotFactoryCalls = 0
        try {
            val hotspotConfig = ProxyConfiguration.hotspot(sharedPort, stripTrackingParams = false)
            val hotspot = coordinator.start(hotspotConfig) { port, config ->
                hotspotFactoryCalls += 1
                newServer(port, config)
            }

            assertTrue("expected PortConflict, got $hotspot", hotspot is ProxyStartResult.PortConflict)
            val conflict = hotspot as ProxyStartResult.PortConflict
            assertEquals(ProxyRole.LOCAL_FIREWALL, conflict.owner)
            assertEquals(sharedPort, conflict.port)
            assertEquals(0, hotspotFactoryCalls)
            assertTrue(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
            assertEquals(sharedPort, coordinator.boundPort(ProxyRole.LOCAL_FIREWALL))
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `local proxy takes an ephemeral port when the hotspot already owns the configured port`() {
        val coordinator = ProxyCoordinator()
        val sharedPort = freePort("0.0.0.0")
        val hotspotConfig = ProxyConfiguration.hotspot(sharedPort, stripTrackingParams = true)
        try {
            val hotspot = coordinator.start(hotspotConfig) { port, config -> newServer(port, config) }
            assertTrue("expected Started, got $hotspot", hotspot is ProxyStartResult.Started)

            val localConfig = ProxyConfiguration.localFirewall(sharedPort, stripTrackingParams = false)
            val local = coordinator.start(localConfig) { port, config -> newServer(port, config) }

            assertTrue("expected Started, got $local", local is ProxyStartResult.Started)
            val started = local as ProxyStartResult.Started
            assertTrue(started.usedEphemeralFallback)
            assertNotEquals(sharedPort, started.boundPort)
            assertTrue(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
            assertTrue(coordinator.isActive(ProxyRole.HOTSPOT))
            assertEquals(2, coordinator.activePorts().size)
            assertNotEquals(
                coordinator.boundPort(ProxyRole.LOCAL_FIREWALL),
                coordinator.boundPort(ProxyRole.HOTSPOT)
            )
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `both proxies serve simultaneously on their own ports`() {
        val coordinator = ProxyCoordinator()
        val localConfig = ProxyConfiguration.localFirewall(freePort(), stripTrackingParams = false)
        val hotspotConfig = ProxyConfiguration.hotspot(freePort("0.0.0.0"), stripTrackingParams = false)
        try {
            val local = coordinator.start(localConfig) { port, config -> newServer(port, config) }
            val hotspot = coordinator.start(hotspotConfig) { port, config -> newServer(port, config) }

            assertTrue(local is ProxyStartResult.Started)
            assertTrue(hotspot is ProxyStartResult.Started)
            val localPort = (local as ProxyStartResult.Started).boundPort
            val hotspotPort = (hotspot as ProxyStartResult.Started).boundPort

            assertTrue(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
            assertTrue(coordinator.isActive(ProxyRole.HOTSPOT))
            assertTrue("local proxy should accept connections", canConnect(localPort))
            assertTrue("hotspot proxy should accept connections", canConnect(hotspotPort))
            assertNotEquals(localPort, hotspotPort)
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `starting twice with the same configuration reuses the listener`() {
        val coordinator = ProxyCoordinator()
        val config = ProxyConfiguration.localFirewall(freePort(), stripTrackingParams = true)
        var factoryCalls = 0
        try {
            val first = coordinator.start(config) { port, cfg ->
                factoryCalls += 1
                newServer(port, cfg)
            }
            val second = coordinator.start(config) { port, cfg ->
                factoryCalls += 1
                newServer(port, cfg)
            }

            assertTrue(first is ProxyStartResult.Started)
            assertTrue("expected AlreadyRunning, got $second", second is ProxyStartResult.AlreadyRunning)
            assertEquals(
                (first as ProxyStartResult.Started).boundPort,
                (second as ProxyStartResult.AlreadyRunning).boundPort
            )
            assertEquals(1, factoryCalls)
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `changing the configuration stops the old listener before binding the new port`() {
        val coordinator = ProxyCoordinator()
        val firstPort = freePort()
        val secondPort = freePort()
        try {
            val first = coordinator.start(
                ProxyConfiguration.localFirewall(firstPort, stripTrackingParams = false)
            ) { port, config -> newServer(port, config) }
            assertTrue(first is ProxyStartResult.Started)
            assertTrue(canConnect(firstPort))

            val second = coordinator.start(
                ProxyConfiguration.localFirewall(secondPort, stripTrackingParams = true)
            ) { port, config -> newServer(port, config) }

            assertTrue(second is ProxyStartResult.Started)
            assertEquals(secondPort, (second as ProxyStartResult.Started).boundPort)
            assertFalse("old listener must be closed", canConnect(firstPort))
            assertTrue(canConnect(secondPort))
            assertEquals(1, coordinator.activePorts().size)
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `stopping one proxy leaves the other running`() {
        val coordinator = ProxyCoordinator()
        try {
            val local = coordinator.start(
                ProxyConfiguration.localFirewall(freePort(), stripTrackingParams = false)
            ) { port, config -> newServer(port, config) } as ProxyStartResult.Started
            val hotspot = coordinator.start(
                ProxyConfiguration.hotspot(freePort("0.0.0.0"), stripTrackingParams = false)
            ) { port, config -> newServer(port, config) } as ProxyStartResult.Started

            coordinator.stop(ProxyRole.HOTSPOT)

            assertFalse(coordinator.isActive(ProxyRole.HOTSPOT))
            assertFalse(canConnect(hotspot.boundPort))
            assertTrue(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
            assertTrue(canConnect(local.boundPort))
        } finally {
            coordinator.stopAll()
        }
    }

    @Test
    fun `stopAll releases every port and shuts every listener down`() {
        val coordinator = ProxyCoordinator()
        val localPort = freePort()
        val hotspotPort = freePort("0.0.0.0")
        val local = coordinator.start(
            ProxyConfiguration.localFirewall(localPort, stripTrackingParams = false)
        ) { port, config -> newServer(port, config) } as ProxyStartResult.Started
        val hotspot = coordinator.start(
            ProxyConfiguration.hotspot(hotspotPort, stripTrackingParams = false)
        ) { port, config -> newServer(port, config) } as ProxyStartResult.Started
        assertTrue(canConnect(local.boundPort))
        assertTrue(canConnect(hotspot.boundPort))

        coordinator.stopAll()

        assertTrue(coordinator.activePorts().isEmpty())
        assertFalse(coordinator.isActive(ProxyRole.LOCAL_FIREWALL))
        assertFalse(coordinator.isActive(ProxyRole.HOTSPOT))
        assertFalse(canConnect(local.boundPort))
        assertFalse(canConnect(hotspot.boundPort))
    }

    @Test
    fun `port validation is centralized and clamps like the existing preference path`() {
        assertEquals(ProxyConfiguration.MIN_PORT, ProxyConfiguration.localFirewall(80, false).port)
        assertEquals(ProxyConfiguration.MAX_PORT, ProxyConfiguration.hotspot(70_000, false).port)
        assertEquals(8899, ProxyConfiguration.localFirewall(8899, false).validatedPort)
        assertTrue(ProxyConfiguration.isValidPort(1024))
        assertTrue(ProxyConfiguration.isValidPort(65535))
        assertFalse(ProxyConfiguration.isValidPort(80))
        assertFalse(ProxyConfiguration.isValidPort(0))

        val loopback = ProxyConfiguration.localFirewall(8899, false)
        assertEquals(ProxyConfiguration.LOOPBACK_HOST, loopback.bindHost)
        assertEquals(ProxyRole.LOCAL_FIREWALL, loopback.role)
        val hotspot = ProxyConfiguration.hotspot(8890, false)
        assertEquals(ProxyConfiguration.HOTSPOT_BIND_HOST, hotspot.bindHost)
        assertEquals(ProxyRole.HOTSPOT, hotspot.role)

        val rejected = runCatching {
            ProxyConfiguration(ProxyRole.HOTSPOT, 8890, " ", false)
        }.isFailure
        assertTrue("blank bind host must be rejected", rejected)
    }

    @Test
    fun `port registry tracks ownership and frees it on release`() {
        val registry = ProxyPortRegistry()
        val port = freePort()

        assertNull(registry.claim(ProxyRole.LOCAL_FIREWALL, port))
        assertEquals(ProxyRole.LOCAL_FIREWALL, registry.claimedByOther(ProxyRole.HOTSPOT, port))
        assertEquals(port, registry.activePort(ProxyRole.LOCAL_FIREWALL))

        // The hotspot cannot claim a port owned by the local firewall.
        assertEquals(ProxyRole.LOCAL_FIREWALL, registry.claim(ProxyRole.HOTSPOT, port))
        assertNull(registry.activePort(ProxyRole.HOTSPOT))

        // Re-claiming your own port is a no-op, not a conflict.
        assertNull(registry.claim(ProxyRole.LOCAL_FIREWALL, port))

        registry.release(ProxyRole.LOCAL_FIREWALL)
        assertNull(registry.claimedByOther(ProxyRole.HOTSPOT, port))
        assertNull(registry.claim(ProxyRole.HOTSPOT, port))
        assertEquals(port, registry.activePort(ProxyRole.HOTSPOT))
        assertEquals(mapOf(ProxyRole.HOTSPOT to port), registry.snapshot())
    }

    @Test
    fun `port availability probe detects an occupied port`() {
        occupiedSocket("127.0.0.1").use { blocker ->
            assertFalse(ProxyPortRegistry.isPortAvailable("127.0.0.1", blocker.localPort))
        }
        val free = freePort()
        assertTrue(ProxyPortRegistry.isPortAvailable("127.0.0.1", free))
    }
}
