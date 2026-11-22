package com.example.data.network

import com.example.data.model.ActiveNetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the shared network/DNS enumeration: multiple networks, VPN transport detection,
 * missing link properties and offline conditions. No Android framework is touched.
 */
class NetworkSnapshotTest {

    private fun caps(
        transports: Set<NetworkTransport>,
        hasInternet: Boolean = true,
        notVpn: Boolean = true
    ): NetworkCapabilitiesInfo = NetworkCapabilitiesInfo(
        hasInternet = hasInternet,
        notVpn = notVpn,
        transports = transports
    )

    private fun link(
        dns: List<String> = emptyList(),
        ipv4: List<String> = emptyList(),
        ipv6: List<String> = emptyList(),
        interfaceName: String = "wlan0",
        proxyHost: String? = null
    ): LinkPropertiesSnapshot = LinkPropertiesSnapshot(
        interfaceName = interfaceName,
        dnsServers = dns,
        ipv4Addresses = ipv4,
        ipv6Addresses = ipv6,
        mtu = 1500,
        httpProxyHost = proxyHost,
        httpProxyPort = if (proxyHost != null) 8899 else 0
    )

    private fun wifi() = NetworkInterfaceSnapshot(
        handle = "wifi",
        capabilities = caps(setOf(NetworkTransport.WIFI)),
        linkProperties = link(
            dns = listOf("192.168.1.1", "fe80::1"),
            ipv4 = listOf("192.168.1.50"),
            ipv6 = listOf("fe80::abcd"),
            interfaceName = "wlan0"
        )
    )

    private fun cellular() = NetworkInterfaceSnapshot(
        handle = "cell",
        capabilities = caps(setOf(NetworkTransport.CELLULAR)),
        linkProperties = link(dns = listOf("10.20.30.40"), interfaceName = "rmnet0")
    )

    private fun vpn() = NetworkInterfaceSnapshot(
        handle = "vpn",
        capabilities = caps(setOf(NetworkTransport.VPN), notVpn = false),
        linkProperties = link(
            dns = listOf("10.1.10.2", "fd00:1::2"),
            interfaceName = "tun0",
            proxyHost = "127.0.0.1"
        )
    )

    @Test
    fun `multiple networks keep VPN and physical DNS separate`() {
        val snapshot = NetworkSnapshot(
            interfaces = listOf(wifi(), cellular(), vpn()),
            activeNetworkHandle = "wifi",
            capturedAtMs = 1L
        )

        assertEquals(listOf("10.1.10.2", "fd00:1::2"), snapshot.vpnDnsServers())
        assertEquals(
            listOf("192.168.1.1", "fe80::1", "10.20.30.40"),
            snapshot.physicalDnsServers()
        )
        assertEquals(1, snapshot.vpnInterfaces.size)
        assertEquals(2, snapshot.physicalInterfaces.size)
        assertEquals("wifi", snapshot.activePhysicalNetwork()?.handle)
        assertEquals(ActiveNetworkType.WIFI, snapshot.activeNetworkType())
        assertTrue(snapshot.isVpnActive)
        assertFalse(snapshot.isOffline)
    }

    @Test
    fun `IPv4 and IPv6 DNS and addresses are preserved`() {
        val snapshot = NetworkSnapshot(interfaces = listOf(wifi(), vpn()), activeNetworkHandle = "wifi")

        assertEquals(listOf("192.168.1.1", "fe80::1"), wifi().dnsServers)
        assertEquals(listOf("192.168.1.50"), wifi().ipv4Addresses)
        assertEquals(listOf("fe80::abcd"), wifi().ipv6Addresses)
        assertTrue(snapshot.hasVpnIpv6Dns)
        assertTrue(snapshot.vpnDnsServers().contains("fd00:1::2"))
        assertTrue(snapshot.vpnDnsServers().contains("10.1.10.2"))
        assertTrue(snapshot.hasVpnHttpProxy)
    }

    @Test
    fun `VPN transport is detected by flag or by missing NOT_VPN capability`() {
        val vpnByTransport = NetworkInterfaceSnapshot(
            handle = "v1",
            capabilities = caps(setOf(NetworkTransport.VPN), notVpn = true),
            linkProperties = link(interfaceName = "tun0")
        )
        val vpnByMissingCapability = NetworkInterfaceSnapshot(
            handle = "v2",
            capabilities = caps(emptySet(), notVpn = false),
            linkProperties = link(interfaceName = "tun1")
        )
        val physical = NetworkInterfaceSnapshot(
            handle = "w",
            capabilities = caps(setOf(NetworkTransport.WIFI), notVpn = true)
        )

        assertTrue(vpnByTransport.isVpn)
        assertTrue(vpnByMissingCapability.isVpn)
        assertFalse(physical.isVpn)

        val snapshot = NetworkSnapshot(interfaces = listOf(vpnByTransport, vpnByMissingCapability, physical))
        assertEquals(2, snapshot.vpnInterfaces.size)
        assertEquals(1, snapshot.physicalInterfaces.size)
    }

    @Test
    fun `missing link properties never break DNS discovery or selection`() {
        val capsOnlyWifi = NetworkInterfaceSnapshot(
            handle = "wifi-no-lp",
            capabilities = caps(setOf(NetworkTransport.WIFI)),
            linkProperties = null
        )
        val capsOnlyVpn = NetworkInterfaceSnapshot(
            handle = "vpn-no-lp",
            capabilities = caps(setOf(NetworkTransport.VPN), notVpn = false),
            linkProperties = null
        )
        val snapshot = NetworkSnapshot(
            interfaces = listOf(capsOnlyWifi, capsOnlyVpn),
            activeNetworkHandle = "wifi-no-lp"
        )

        assertNull(capsOnlyWifi.interfaceName)
        assertTrue(capsOnlyWifi.dnsServers.isEmpty())
        assertFalse(capsOnlyWifi.hasIpv6Dns)
        assertTrue(snapshot.vpnDnsServers().isEmpty())
        assertTrue(snapshot.physicalDnsServers().isEmpty())
        assertFalse(snapshot.hasVpnIpv6Dns)
        assertFalse(snapshot.hasVpnHttpProxy)
        // Selection still works from capabilities alone.
        assertEquals("wifi-no-lp", snapshot.activePhysicalNetwork()?.handle)
        assertEquals(ActiveNetworkType.WIFI, snapshot.activeNetworkType())
        assertFalse(snapshot.isOffline)
    }

    @Test
    fun `offline device reports no active network or DNS`() {
        val empty = NetworkSnapshot()
        assertTrue(empty.isOffline)
        assertNull(empty.activePhysicalNetwork())
        assertEquals(ActiveNetworkType.NONE, empty.activeNetworkType())
        assertTrue(empty.vpnDnsServers().isEmpty())
        assertTrue(empty.physicalDnsServers().isEmpty())
        assertFalse(empty.hasVpnIpv6Dns)
        assertFalse(empty.isVpnActive)
    }

    @Test
    fun `networks without internet capability do not count as online`() {
        val noInternet = NetworkInterfaceSnapshot(
            handle = "wifi-no-internet",
            capabilities = caps(setOf(NetworkTransport.WIFI), hasInternet = false),
            linkProperties = link(dns = listOf("192.168.1.1"))
        )
        val snapshot = NetworkSnapshot(interfaces = listOf(noInternet), activeNetworkHandle = "wifi-no-internet")

        assertTrue(snapshot.isOffline)
        assertEquals(ActiveNetworkType.NONE, snapshot.activeNetworkType())
        assertTrue(snapshot.physicalDnsServers().isEmpty())
    }

    @Test
    fun `active physical network follows the previous preference order`() {
        val wifi = wifi()
        val cell = cellular()
        val ethernet = NetworkInterfaceSnapshot(
            handle = "eth",
            capabilities = caps(setOf(NetworkTransport.ETHERNET)),
            linkProperties = link(interfaceName = "eth0")
        )

        // The active network wins even when it is not Wi-Fi.
        val activeCellular = NetworkSnapshot(
            interfaces = listOf(wifi, cell, vpn()),
            activeNetworkHandle = "cell"
        )
        assertEquals("cell", activeCellular.activePhysicalNetwork()?.handle)
        assertEquals(ActiveNetworkType.MOBILE_DATA, activeCellular.activeNetworkType())

        // Without an active handle, Wi-Fi is preferred over cellular.
        val noActive = NetworkSnapshot(interfaces = listOf(cell, wifi, vpn()))
        assertEquals("wifi", noActive.activePhysicalNetwork()?.handle)

        // A VPN-only active network is never reported as the physical network.
        val vpnActive = NetworkSnapshot(
            interfaces = listOf(vpn(), cell, wifi),
            activeNetworkHandle = "vpn"
        )
        assertEquals("wifi", vpnActive.activePhysicalNetwork()?.handle)

        val ethernetOnly = NetworkSnapshot(interfaces = listOf(ethernet), activeNetworkHandle = "eth")
        assertEquals(ActiveNetworkType.OTHER, ethernetOnly.activeNetworkType())
    }

    @Test
    fun `duplicate DNS entries across networks are reported once`() {
        val duplicateWifi = NetworkInterfaceSnapshot(
            handle = "wifi2",
            capabilities = caps(setOf(NetworkTransport.WIFI)),
            linkProperties = link(dns = listOf("192.168.1.1"), interfaceName = "wlan1")
        )
        val snapshot = NetworkSnapshot(interfaces = listOf(wifi(), duplicateWifi))

        assertEquals(listOf("192.168.1.1", "fe80::1"), snapshot.physicalDnsServers())
    }

    @Test
    fun `snapshot cache reuses a capture within its TTL and refreshes after`() {
        var now = 1_000L
        var captures = 0
        val provider = NetworkSnapshotProvider(cacheTtlMs = 500L, clock = { now })

        val first = provider.cachedOrCapture {
            captures += 1
            NetworkSnapshot(capturedAtMs = now)
        }
        now += 400L
        val second = provider.cachedOrCapture {
            captures += 1
            NetworkSnapshot(capturedAtMs = now)
        }
        assertSame("duplicate snapshots must be avoided inside the TTL", first, second)
        assertEquals(1, captures)

        now += 200L
        val third = provider.cachedOrCapture {
            captures += 1
            NetworkSnapshot(capturedAtMs = now)
        }
        assertNotSame(first, third)
        assertEquals(2, captures)

        provider.invalidate()
        provider.cachedOrCapture {
            captures += 1
            NetworkSnapshot(capturedAtMs = now)
        }
        assertEquals(3, captures)
    }

    @Test
    fun `zero TTL provider always captures`() {
        var captures = 0
        val provider = NetworkSnapshotProvider(cacheTtlMs = 0L)

        provider.cachedOrCapture { captures += 1; NetworkSnapshot() }
        provider.cachedOrCapture { captures += 1; NetworkSnapshot() }

        assertEquals(2, captures)
    }
}
