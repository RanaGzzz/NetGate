package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.ActiveNetworkType
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsTransportMode
import com.example.data.model.DpiBypassMode
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.WebsiteFilterMode
import com.example.service.DnsPacketFilter
import com.example.service.DpiBypassConfig
import com.example.service.DpiBypassEngine
import com.example.service.DpiStrategy
import com.example.service.FirewallTunnelMode
import com.example.service.PerAppFirewallEngine
import com.example.ui.WebsiteBlockerContent
import com.example.ui.WebsiteBlockerUiState
import com.example.ui.components.DpiBypassShieldCard
import com.example.ui.theme.NetGateTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DpiBypassTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        DpiBypassEngine.clearAutopilotCache()
    }

    @After
    fun tearDown() {
        DpiBypassEngine.clearAutopilotCache()
    }

    @Test
    fun `locateTlsSniRange extracts exact server_name byte span from ClientHello`() {
        val hostname = "stream.censored.org"
        val clientHello = buildSyntheticTlsClientHello(hostname)

        val range = DpiBypassEngine.locateTlsSniRange(clientHello, clientHello.size)
        assertNotNull(range)
        val extracted = clientHello.copyOfRange(range!!.first, range.last + 1).decodeToString()
        assertEquals(hostname, extracted)
    }

    @Test
    fun `writeTlsClientHello splits across SNI midpoint without altering bytes`() {
        val hostname = "blocked.example.com"
        val clientHello = buildSyntheticTlsClientHello(hostname)
        val sniRange = DpiBypassEngine.locateTlsSniRange(clientHello, clientHello.size)!!

        val recorder = RecordingOutputStream()
        DpiBypassEngine.writeTlsClientHello(
            output = recorder,
            packet = clientHello,
            length = clientHello.size,
            strategy = DpiStrategy.SNI_MIDPOINT_SPLIT,
            chunkSize = 4,
            fragmentDelayMs = 0
        )

        assertEquals(2, recorder.writeSizes.size)
        assertEquals(2, recorder.flushCount)
        assertTrue(recorder.writeSizes[0] in (sniRange.first + 1)..sniRange.last)
        assertArrayEquals(clientHello, recorder.toByteArray())
    }

    @Test
    fun `writeTlsClientHello fragments into deterministic micro chunks`() {
        val clientHello = buildSyntheticTlsClientHello("chat.messaging.net")
        val recorder = RecordingOutputStream()

        DpiBypassEngine.writeTlsClientHello(
            output = recorder,
            packet = clientHello,
            length = clientHello.size,
            strategy = DpiStrategy.MICRO_CHUNKS,
            chunkSize = 4,
            fragmentDelayMs = 0
        )

        assertTrue(recorder.writeSizes.size >= 10)
        assertTrue(recorder.writeSizes.dropLast(1).all { it <= 4 })
        assertArrayEquals(clientHello, recorder.toByteArray())
    }

    @Test
    fun `transformPlainHttpHeaders and writeHttpRequest desync Host header cleanly`() {
        val rawHttp = "GET /watch?v=123 HTTP/1.1\r\nHost: video.example.org\r\nConnection: keep-alive\r\n\r\n"
        val transformed = DpiBypassEngine.transformPlainHttpHeaders(rawHttp, DpiStrategy.HTTP_HEADER_DESYNC)
        assertTrue(transformed.contains("hOsT: video.example.org"))

        val recorder = RecordingOutputStream()
        val bytes = transformed.toByteArray(Charsets.ISO_8859_1)
        DpiBypassEngine.writeHttpRequest(
            output = recorder,
            headerBytes = bytes,
            strategy = DpiStrategy.HTTP_HEADER_DESYNC,
            chunkSize = 4,
            fragmentDelayMs = 0
        )

        assertEquals(2, recorder.writeSizes.size)
        assertArrayEquals(bytes, recorder.toByteArray())
    }

    @Test
    fun `autopilot resolves default SNI split, escalates to micro chunks on failure, and flushes cleanly`() {
        val cfg = DpiBypassConfig(mode = DpiBypassMode.AUTOPILOT)
        val host = "Example.ORG."
        assertEquals(DpiStrategy.SNI_MIDPOINT_SPLIT, DpiBypassEngine.resolveStrategy(host, cfg))

        val escalated = DpiBypassEngine.escalateOnFailure(host, DpiStrategy.SNI_MIDPOINT_SPLIT)
        assertEquals(DpiStrategy.MICRO_CHUNKS, escalated)
        assertEquals(DpiStrategy.MICRO_CHUNKS, DpiBypassEngine.resolveStrategy("example.org", cfg))
        assertEquals(1, DpiBypassEngine.getAutopilotLearnedCount())

        assertNull(DpiBypassEngine.escalateOnFailure(host, DpiStrategy.MICRO_CHUNKS))

        DpiBypassEngine.clearAutopilotCache()
        assertEquals(0, DpiBypassEngine.getAutopilotLearnedCount())
    }

    @Test
    fun `isLikelyForgedDnsResponse catches ISP loopback and RFC1918 sinkholes while allowing valid public IPs`() {
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("youtube.com", listOf("127.0.0.1")))
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("discord.com", listOf("0.0.0.0")))
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("reddit.com", listOf("192.168.1.50")))
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("wikipedia.org", listOf("10.10.0.5")))
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("x.com", listOf("172.16.0.1")))
        assertTrue(DpiBypassEngine.isLikelyForgedDnsResponse("signal.org", listOf("::1")))

        assertFalse(DpiBypassEngine.isLikelyForgedDnsResponse("youtube.com", listOf("142.250.190.46")))
        assertFalse(DpiBypassEngine.isLikelyForgedDnsResponse("printer.local", listOf("192.168.1.50")))
        assertFalse(DpiBypassEngine.isLikelyForgedDnsResponse("localhost", listOf("127.0.0.1")))
    }

    @Test
    fun `dns packet filter detects QTYPE 65 HTTPS RR and generates NODATA downgrade response`() {
        val dnsPayload = buildDnsQueryPayload("cdn.example.com", qtype = 65)
        val ipv4UdpPacket = buildIpv4UdpPacket(dnsPayload, dstPort = 53)
        val parsed = DnsPacketFilter.parseUdpDnsQuery(ipv4UdpPacket, ipv4UdpPacket.size)
        assertNotNull(parsed)
        assertEquals("cdn.example.com", parsed!!.domain)
        assertEquals(65, DnsPacketFilter.extractQueryType(parsed))

        val responseIpPacket = DnsPacketFilter.buildNoDataDnsResponsePacket(parsed)
        val dnsOffset = 28 // IPv4 (20) + UDP (8)
        assertTrue(responseIpPacket.size >= dnsOffset + 12)
        assertEquals(dnsPayload[0], responseIpPacket[dnsOffset])
        assertEquals(dnsPayload[1], responseIpPacket[dnsOffset + 1])
        // QR = 1, RCODE = 0 (NoError)
        assertTrue((responseIpPacket[dnsOffset + 2].toInt() and 0x80) != 0)
        assertEquals(0, responseIpPacket[dnsOffset + 3].toInt() and 0x0F)
        // ANCOUNT = 0
        assertEquals(
            0,
            ((responseIpPacket[dnsOffset + 6].toInt() and 0xFF) shl 8) or
                (responseIpPacket[dnsOffset + 7].toInt() and 0xFF)
        )
    }

    @Test
    fun `enabling dpiBypassMode switches PerAppFirewallEngine into hybrid filtering tunnel`() {
        val prefs = FirewallPreferencesState(
            isAdBlockerEnabled = false,
            websiteFilterMode = WebsiteFilterMode.OFF,
            isDeepMonitorEnabled = false,
            isHttpsProxyEnabled = false,
            dpiBypassMode = DpiBypassMode.AUTOPILOT
        )

        val plan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = ActiveNetworkType.WIFI,
            rules = emptyList(),
            preferences = prefs,
            selfPackageName = "com.example",
            isPackageInstalled = { true },
            resolvePackageUid = { 10100 }
        )

        assertEquals(FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER, plan.mode)
        assertTrue(plan.shouldRunTunnel)
    }

    @Test
    fun `dpi bypass UI card renders metrics and invokes controls cleanly`() {
        var selectedDpiMode by mutableStateOf(DpiBypassMode.AUTOPILOT)
        var selectedChunkSize by mutableStateOf(4)
        var selectedTransport by mutableStateOf(DnsTransportMode.DOH_WITH_FALLBACK)
        var quicBlocked by mutableStateOf(true)
        var antiPoison by mutableStateOf(true)
        var keepAlive by mutableStateOf(true)
        var flushed by mutableStateOf(false)

        composeRule.setContent {
            NetGateTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DpiBypassShieldCard(
                        dpiBypassMode = selectedDpiMode,
                        dpiFragmentChunkSize = selectedChunkSize,
                        dpiIdleKeepAliveEnabled = keepAlive,
                        blockQuicForDpiBypass = quicBlocked,
                        antiDnsPoisonEnabled = antiPoison,
                        dnsTransportMode = selectedTransport,
                        dpiBypassedConnectionsCount = 42L,
                        dpiAutopilotLearnedCount = 7,
                        antiDnsPoisonBlockedCount = 2L,
                        quicStrippedCount = 15L,
                        isFirewallActive = true,
                        onSelectDpiMode = { selectedDpiMode = it },
                        onSelectChunkSize = { selectedChunkSize = it },
                        onToggleIdleKeepAlive = { keepAlive = it },
                        onToggleBlockQuic = { quicBlocked = it },
                        onToggleAntiDnsPoison = { antiPoison = it },
                        onSelectDnsTransportMode = { selectedTransport = it },
                        onFlushCache = { flushed = true },
                        onRequestEnableFirewall = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("dpi_bypass_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dpi_mode_sni_chunks").performScrollTo().performClick()
        assertEquals(DpiBypassMode.SNI_CHUNKS, selectedDpiMode)

        composeRule.onNodeWithTag("dpi_chunk_8").performScrollTo().performClick()
        assertEquals(8, selectedChunkSize)

        composeRule.onNodeWithTag("dns_transport_dns_over_tcp").performScrollTo().performClick()
        assertEquals(DnsTransportMode.DNS_OVER_TCP, selectedTransport)

        composeRule.onNodeWithTag("block_quic_switch").performScrollTo().performClick()
        assertFalse(quicBlocked)

        composeRule.onNodeWithTag("anti_dns_poison_switch").performScrollTo().performClick()
        assertFalse(antiPoison)

        composeRule.onNodeWithTag("dpi_idle_keep_alive_switch").performScrollTo().performClick()
        assertFalse(keepAlive)

        composeRule.onNodeWithTag("flush_dpi_cache_button").performScrollTo().performClick()
        assertTrue(flushed)
    }

    @Test
    fun `website blocker renders clean separate tabs and isolates each section`() {
        composeRule.setContent {
            NetGateTheme {
                WebsiteBlockerContent(
                    state = WebsiteBlockerUiState(),
                    allManagedApps = emptyList(),
                    isFirewallActive = true,
                    connectionDetails = ConnectionDetails(),
                    loadIcon = { null },
                    onSelectFilterMode = {},
                    onAddWebsiteRule = { _, _ -> },
                    onToggleWebsiteRuleEnabled = { _, _ -> },
                    onDeleteWebsiteRule = {},
                    onClearWebsiteRules = {},
                    onToggleBypassApp = {},
                    onClearBypassApps = {},
                    onToggleAdBlocker = {},
                    onToggleAdFilterList = { _, _ -> },
                    onSyncAdFilterLists = {},
                    onToggleEssentialPassthrough = {},
                    onToggleUseDohUpstream = {},
                    onToggleHttpsProxy = {},
                    onSelectProxyPort = {},
                    onToggleProxyStripTracking = {},
                    onToggleSafeSearch = {},
                    onSelectYoutubeMode = {},
                    onToggleYoutubeAdBlock = {},
                    onSelectSniPreset = {},
                    onSaveCustomSniProxy = { _, _ -> },
                    onSelectDpiMode = {},
                    onSelectDpiChunkSize = {},
                    onToggleDpiIdleKeepAlive = {},
                    onToggleBlockQuic = {},
                    onToggleAntiDnsPoison = {},
                    onSelectDnsTransportMode = {},
                    onFlushDpiCache = {},
                    onAddCustomAdFilterList = { _, _, _ -> },
                    onDeleteCustomAdFilterList = {},
                    onCopiedProxyEndpoint = {},
                    onClearDnsLogs = {},
                    onRequestEnableFirewall = {},
                    onOpenDnsProtection = {}
                )
            }
        }

        // Default tab (Ad Blocker) isolates Ad Blocker UI cleanly
        composeRule.onNodeWithTag("ad_blocker_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("safesearch_youtube_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dpi_bypass_card").assertDoesNotExist()

        // Websites tab
        composeRule.onNodeWithTag("website_main_tab_bar")
            .performScrollToNode(hasTestTag("website_main_tab_websites"))
        composeRule.onNodeWithTag("website_main_tab_websites").performClick()
        composeRule.onNodeWithTag("website_mode_control_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_hero_card").assertDoesNotExist()

        // Safe & Kids tab
        composeRule.onNodeWithTag("website_main_tab_bar")
            .performScrollToNode(hasTestTag("website_main_tab_safe_search"))
        composeRule.onNodeWithTag("website_main_tab_safe_search").performClick()
        composeRule.onNodeWithTag("safesearch_youtube_card").assertIsDisplayed()

        // SNI & DPI tab
        composeRule.onNodeWithTag("website_main_tab_bar")
            .performScrollToNode(hasTestTag("website_main_tab_proxy_dpi"))
        composeRule.onNodeWithTag("website_main_tab_proxy_dpi").performClick()
        composeRule.onNodeWithTag("https_filtering_proxy_card").assertIsDisplayed()
        composeRule.onNodeWithTag("website_proxy_dpi_list")
            .performScrollToNode(hasTestTag("dpi_bypass_card"))
        composeRule.onNodeWithTag("dpi_bypass_card").assertIsDisplayed()

        // DNS Logs tab
        composeRule.onNodeWithTag("website_main_tab_bar")
            .performScrollToNode(hasTestTag("website_main_tab_dns_logs"))
        composeRule.onNodeWithTag("website_main_tab_dns_logs").performClick()
        composeRule.onNodeWithTag("dns_log_search_input").assertIsDisplayed()
    }

    private class RecordingOutputStream : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        val writeSizes = mutableListOf<Int>()
        var flushCount = 0

        override fun write(b: Int) {
            writeSizes.add(1)
            buffer.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            writeSizes.add(len)
            buffer.write(b, off, len)
        }

        override fun flush() {
            flushCount++
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    private fun buildSyntheticTlsClientHello(hostname: String): ByteArray {
        val hostBytes = hostname.toByteArray(Charsets.US_ASCII)
        val sniEntryLen = 1 + 2 + hostBytes.size
        val sniExtData = ByteArrayOutputStream().apply {
            write((sniEntryLen shr 8) and 0xFF)
            write(sniEntryLen and 0xFF)
            write(0x00)
            write((hostBytes.size shr 8) and 0xFF)
            write(hostBytes.size and 0xFF)
            write(hostBytes)
        }.toByteArray()

        val extensions = ByteArrayOutputStream().apply {
            write(0x00)
            write(0x00)
            write((sniExtData.size shr 8) and 0xFF)
            write(sniExtData.size and 0xFF)
            write(sniExtData)
        }.toByteArray()

        val body = ByteArrayOutputStream().apply {
            write(0x03)
            write(0x03)
            repeat(32) { write(0x11) }
            write(0x00)
            write(0x00)
            write(0x02)
            write(0x13)
            write(0x01)
            write(0x01)
            write(0x00)
            write((extensions.size shr 8) and 0xFF)
            write(extensions.size and 0xFF)
            write(extensions)
        }.toByteArray()

        val handshake = ByteArrayOutputStream().apply {
            write(0x01)
            write((body.size shr 16) and 0xFF)
            write((body.size shr 8) and 0xFF)
            write(body.size and 0xFF)
            write(body)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(0x16)
            write(0x03)
            write(0x01)
            write((handshake.size shr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
    }

    private fun buildDnsQueryPayload(domain: String, qtype: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        domain.split('.').forEach { label ->
            val lb = label.toByteArray(Charsets.US_ASCII)
            out.write(lb.size)
            out.write(lb)
        }
        out.write(0x00)
        out.write((qtype shr 8) and 0xFF)
        out.write(qtype and 0xFF)
        out.write(0x00)
        out.write(0x01)
        return out.toByteArray()
    }

    private fun buildIpv4UdpPacket(payload: ByteArray, dstPort: Int): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)
        out[0] = 0x45.toByte()
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[8] = 64.toByte()
        out[9] = 17.toByte()
        out[12] = 10; out[13] = 0; out[14] = 0; out[15] = 2
        out[16] = 1; out[17] = 1; out[18] = 1; out[19] = 1
        out[20] = (54321 ushr 8).toByte()
        out[21] = (54321 and 0xFF).toByte()
        out[22] = (dstPort ushr 8).toByte()
        out[23] = (dstPort and 0xFF).toByte()
        out[24] = (udpLen ushr 8).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 28, payload.size)
        return out
    }
}
