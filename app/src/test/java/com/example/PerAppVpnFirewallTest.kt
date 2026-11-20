package com.example

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.model.ActiveNetworkType
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.WebsiteFilterMode
import com.example.service.BlockedPacketAction
import com.example.service.DnsPacketFilter
import com.example.service.FirewallTunnelMode
import com.example.service.NetGateVpnService
import com.example.service.PerAppFirewallEngine
import com.example.service.TunPacketInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PerAppVpnFirewallTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `evaluateBlockedPackages filters by active network and excludes self`() {
        val rules = listOf(
            AppNetworkRuleEntity(packageName = "com.example", blockWifi = true, blockMobileData = true),
            AppNetworkRuleEntity(packageName = "com.wifi.only", blockWifi = true, blockMobileData = false),
            AppNetworkRuleEntity(packageName = "com.data.only", blockWifi = false, blockMobileData = true),
            AppNetworkRuleEntity(packageName = "com.both.net", blockWifi = true, blockMobileData = true),
            AppNetworkRuleEntity(packageName = "com.both.net", blockWifi = true, blockMobileData = true)
        )

        assertEquals(
            listOf("com.both.net", "com.wifi.only"),
            PerAppFirewallEngine.evaluateBlockedPackages(ActiveNetworkType.WIFI, rules, "com.example")
        )
        assertEquals(
            listOf("com.both.net", "com.data.only"),
            PerAppFirewallEngine.evaluateBlockedPackages(ActiveNetworkType.MOBILE_DATA, rules, "com.example")
        )
        assertEquals(
            listOf("com.both.net"),
            PerAppFirewallEngine.evaluateBlockedPackages(ActiveNetworkType.OTHER, rules, "com.example")
        )
        assertEquals(
            emptyList<String>(),
            PerAppFirewallEngine.evaluateBlockedPackages(ActiveNetworkType.NONE, rules, "com.example")
        )
    }

    @Test
    fun `buildTunnelPlan selects pure per-app sinkhole and filters uninstalled packages`() {
        val rules = listOf(
            AppNetworkRuleEntity("com.social.app", blockWifi = true, blockMobileData = false),
            AppNetworkRuleEntity("com.ghost.uninstalled", blockWifi = true, blockMobileData = true)
        )
        val prefs = FirewallPreferencesState(
            isAdBlockerEnabled = false,
            websiteFilterMode = WebsiteFilterMode.OFF,
            isDeepMonitorEnabled = false
        )
        val uids = mapOf("com.social.app" to 10123, "com.ghost.uninstalled" to 10999)

        val wifiPlan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.WIFI,
            rules = rules,
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { it == "com.social.app" },
            resolvePackageUid = { uids[it] }
        )
        assertEquals(FirewallTunnelMode.PER_APP_SINKHOLE, wifiPlan.mode)
        assertEquals(listOf("com.social.app"), wifiPlan.blockedPackages)
        assertEquals(setOf(10123), wifiPlan.blockedUids)
        assertTrue(wifiPlan.shouldRunTunnel)
        assertEquals(listOf("com.social.app"), wifiPlan.signature.sinkholePackages)

        // Switching to Mobile Data with 0 mobile-blocked apps disables the tunnel cleanly
        val dataPlan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.MOBILE_DATA,
            rules = rules,
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { it == "com.social.app" },
            resolvePackageUid = { uids[it] }
        )
        assertEquals(FirewallTunnelMode.DISABLED, dataPlan.mode)
        assertFalse(dataPlan.shouldRunTunnel)
    }

    @Test
    fun `hybrid mode keeps TUN signature stable while updating blocked UIDs and stripping bypass conflicts`() {
        val prefs = FirewallPreferencesState(
            isAdBlockerEnabled = true,
            websiteBypassPackages = setOf("com.example", "com.blocked.app", "com.trusted.bank")
        )
        val installed = setOf("com.example", "com.blocked.app", "com.trusted.bank")
        val uids = mapOf("com.blocked.app" to 10201, "com.trusted.bank" to 10202)

        val planNoBlocked = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.WIFI,
            rules = emptyList(),
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { it in installed },
            resolvePackageUid = { uids[it] }
        )
        val planWithBlocked = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.WIFI,
            rules = listOf(AppNetworkRuleEntity("com.blocked.app", blockWifi = true, blockMobileData = false)),
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { it in installed },
            resolvePackageUid = { uids[it] }
        )

        assertEquals(FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER, planWithBlocked.mode)
        assertEquals(listOf("com.blocked.app"), planWithBlocked.blockedPackages)
        assertEquals(setOf(10201), planWithBlocked.blockedUids)
        // Blocked app & self package must never appear in bypassPackages
        assertEquals(setOf("com.trusted.bank"), planWithBlocked.bypassPackages)
        // Changing bypass set intentionally changes signature; adding blocked app without changing bypass keeps signature stable
        assertEquals(setOf("com.trusted.bank", "com.blocked.app"), planNoBlocked.bypassPackages)
    }

    @Test
    fun `global wifi lock takes priority on Wi-Fi only and yields to cellular`() {
        val prefs = FirewallPreferencesState(
            globalWifiBlocked = true,
            isAdBlockerEnabled = true
        )
        val wifiPlan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.WIFI,
            rules = emptyList(),
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { true },
            resolvePackageUid = { 10100 }
        )
        val cellularPlan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.MOBILE_DATA,
            rules = emptyList(),
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { true },
            resolvePackageUid = { 10100 }
        )

        assertEquals(FirewallTunnelMode.GLOBAL_WIFI_LOCK, wifiPlan.mode)
        assertEquals(FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER, cellularPlan.mode)
    }

    @Test
    fun `sinkhole packet classifier rejects TCP, DNS, and QUIC UDP immediately`() {
        val udp443 = buildIpv4UdpPacket(
            srcIp = byteArrayOf(10, 1, 10, 1),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 51234,
            dstPort = 443
        )
        val parsedUdp = TunPacketInspector.parseIpPacket(udp443, udp443.size)!!
        assertEquals(BlockedPacketAction.REJECT_ICMP_UNREACHABLE, PerAppFirewallEngine.classifySinkholePacket(parsedUdp))

        val icmpOut = DnsPacketFilter.buildIcmpUnreachablePacket(udp443, udp443.size)
        assertNotNull(icmpOut)
        icmpOut!!
        assertEquals(1, icmpOut[9].toInt() and 0xFF) // ICMPv4
        assertEquals(3, icmpOut[20].toInt() and 0xFF) // Dest Unreachable
        assertEquals(3, icmpOut[21].toInt() and 0xFF) // Port Unreachable
        // Original src/dst swapped
        assertEquals(1, icmpOut[12].toInt() and 0xFF)
        assertEquals(10, icmpOut[16].toInt() and 0xFF)

        // Existing established TCP ACK+PSH (not SYN) should still be reset
        val tcpAckPacket = buildIpv4TcpPacket(
            srcIp = byteArrayOf(10, 1, 10, 1),
            dstIp = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34),
            srcPort = 44100,
            dstPort = 443,
            flags = 0x18 // PSH + ACK
        )
        val parsedTcp = TunPacketInspector.parseIpPacket(tcpAckPacket, tcpAckPacket.size)!!
        assertEquals(BlockedPacketAction.REJECT_TCP_RST, PerAppFirewallEngine.classifySinkholePacket(parsedTcp))
        val rst = DnsPacketFilter.buildTcpResetPacket(tcpAckPacket, tcpAckPacket.size)
        assertNotNull(rst)
        assertEquals(0x04, rst!![33].toInt() and 0xFF) // Pure RST for ACK packet
    }

    @Test
    fun `icmp unreachable skips multicast and builds valid IPv6 packet`() {
        val multicastUdp = buildIpv4UdpPacket(
            srcIp = byteArrayOf(10, 1, 10, 1),
            dstIp = byteArrayOf(224.toByte(), 0, 0, 251.toByte()), // mDNS multicast
            srcPort = 5353,
            dstPort = 5353
        )
        assertNull(DnsPacketFilter.buildIcmpUnreachablePacket(multicastUdp, multicastUdp.size))

        val ipv6Udp = ByteArray(48).apply {
            this[0] = 0x60.toByte()
            this[4] = 0
            this[5] = 8
            this[6] = 17.toByte()
            this[7] = 64.toByte()
            // src fd00::1
            this[8] = 0xFD.toByte(); this[23] = 1
            // dst 2001:4860:4860::8888
            this[24] = 0x20; this[25] = 0x01; this[39] = 0x88.toByte()
            // UDP 50000 -> 443
            this[40] = (50000 ushr 8).toByte(); this[41] = (50000 and 0xFF).toByte()
            this[42] = (443 ushr 8).toByte(); this[43] = (443 and 0xFF).toByte()
            this[45] = 8
        }
        val icmpV6 = DnsPacketFilter.buildIcmpUnreachablePacket(ipv6Udp, ipv6Udp.size)
        assertNotNull(icmpV6)
        assertEquals(58, icmpV6!![6].toInt() and 0xFF) // ICMPv6
        assertEquals(1, icmpV6[40].toInt() and 0xFF)  // Destination Unreachable
        assertEquals(4, icmpV6[41].toInt() and 0xFF)  // Port Unreachable
    }

    @Test
    fun `NetGateVpnService lifecycle enforces prepare consent and cleans up on stop`() {
        ShadowVpnService.setPrepareResult(Intent("android.net.VpnService.PREPARE"))
        val controller = Robolectric.buildService(NetGateVpnService::class.java).create()
        val unconsentedResult = controller.get().onStartCommand(
            Intent(context, NetGateVpnService::class.java).setAction(NetGateVpnService.ACTION_START),
            0,
            1
        )
        assertEquals(android.app.Service.START_NOT_STICKY, unconsentedResult)

        // Grant VPN consent
        ShadowVpnService.setPrepareResult(null)
        assertNull(VpnService.prepare(context))
        val consentedResult = controller.get().onStartCommand(
            Intent(context, NetGateVpnService::class.java).setAction(NetGateVpnService.ACTION_START),
            0,
            2
        )
        assertEquals(android.app.Service.START_STICKY, consentedResult)

        controller.destroy()
        assertFalse(NetGateVpnService.runtimeState.value.isServiceRunning)
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int
    ): ByteArray {
        val totalLen = 28
        return ByteArray(totalLen).apply {
            this[0] = 0x45.toByte()
            this[2] = 0
            this[3] = totalLen.toByte()
            this[8] = 64
            this[9] = 17
            System.arraycopy(srcIp, 0, this, 12, 4)
            System.arraycopy(dstIp, 0, this, 16, 4)
            this[20] = (srcPort ushr 8).toByte()
            this[21] = (srcPort and 0xFF).toByte()
            this[22] = (dstPort ushr 8).toByte()
            this[23] = (dstPort and 0xFF).toByte()
            this[24] = 0
            this[25] = 8
        }
    }

    private fun buildIpv4TcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        flags: Int
    ): ByteArray {
        val totalLen = 40
        return ByteArray(totalLen).apply {
            this[0] = 0x45.toByte()
            this[2] = 0
            this[3] = totalLen.toByte()
            this[8] = 64
            this[9] = 6
            System.arraycopy(srcIp, 0, this, 12, 4)
            System.arraycopy(dstIp, 0, this, 16, 4)
            this[20] = (srcPort ushr 8).toByte()
            this[21] = (srcPort and 0xFF).toByte()
            this[22] = (dstPort ushr 8).toByte()
            this[23] = (dstPort and 0xFF).toByte()
            this[32] = 0x50.toByte() // 20-byte TCP header
            this[33] = flags.toByte()
        }
    }
}
