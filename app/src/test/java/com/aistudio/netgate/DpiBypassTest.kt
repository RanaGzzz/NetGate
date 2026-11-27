package com.aistudio.netgate

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
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.service.DnsPacketFilter
import com.aistudio.netgate.service.DpiBypassConfig
import com.aistudio.netgate.service.DpiBypassEngine
import com.aistudio.netgate.service.DpiStrategy
import com.aistudio.netgate.service.FirewallTunnelMode
import com.aistudio.netgate.service.LocalFilteringProxyServer
import com.aistudio.netgate.service.PerAppFirewallEngine
import com.aistudio.netgate.service.ProxyFilterDecision
import com.aistudio.netgate.ui.SniDpiContent
import com.aistudio.netgate.ui.WebsiteBlockerContent
import com.aistudio.netgate.ui.WebsiteBlockerUiState
import com.aistudio.netgate.ui.components.DpiBypassShieldCard
import com.aistudio.netgate.ui.theme.NetGateTheme
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
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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
    fun `writeTlsClientHello reframes the handshake into two TLS records split at the SNI`() {
        val hostname = "blocked.example.com"
        // Real ClientHellos carry extensions after the SNI block; padding keeps the SNI off the
        // record boundary, which is required for record re-framing.
        val clientHello = buildSyntheticTlsClientHello(hostname, extraExtensionPadding = 24)
        val sniRange = DpiBypassEngine.locateTlsSniRange(clientHello, clientHello.size)!!

        val recorder = RecordingOutputStream()
        DpiBypassEngine.writeTlsClientHello(
            output = recorder,
            packet = clientHello,
            length = clientHello.size,
            strategy = DpiStrategy.TLS_RECORD_SPLIT,
            chunkSize = 4,
            fragmentDelayMs = 0
        )

        assertEquals(2, recorder.writeSizes.size)
        val out = recorder.toByteArray()
        // Each fragment is a complete TLS handshake record (content type 0x16, TLS 1.x).
        assertEquals(0x16, out[0].toInt() and 0xFF)
        val firstLen = ((out[3].toInt() and 0xFF) shl 8) or (out[4].toInt() and 0xFF)
        val secondStart = 5 + firstLen
        assertEquals(0x16, out[secondStart].toInt() and 0xFF)
        val secondLen = ((out[secondStart + 3].toInt() and 0xFF) shl 8) or
            (out[secondStart + 4].toInt() and 0xFF)
        assertEquals(out.size, secondStart + 5 + secondLen)

        // The split lands inside the SNI hostname and the handshake body survives intact.
        assertTrue(firstLen + 5 in (sniRange.first + 1)..(sniRange.last + 1))
        val reassembled = out.copyOfRange(5, secondStart) + out.copyOfRange(secondStart + 5, out.size)
        assertArrayEquals(clientHello.copyOfRange(5, clientHello.size), reassembled)
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
        assertEquals(DpiStrategy.TLS_RECORD_SPLIT, DpiBypassEngine.resolveStrategy(host, cfg))

        val escalated = DpiBypassEngine.escalateOnFailure(host, DpiStrategy.TLS_RECORD_SPLIT)
        assertEquals(DpiStrategy.SNI_MIDPOINT_SPLIT, escalated)
        assertEquals(DpiStrategy.SNI_MIDPOINT_SPLIT, DpiBypassEngine.resolveStrategy("example.org", cfg))
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
            selfPackageName = "com.aistudio.netgate",
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

        // Default tab (Dashboard) combines Content & Safety Blocker, SafeSearch & YouTube Guard,
        // DoH/Passthrough, Smart Protection Profiles, and Blocked Activity
        composeRule.onNodeWithTag("ad_blocker_tab_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_dashboard_list")
            .performScrollToNode(hasTestTag("safesearch_youtube_card"))
        composeRule.onNodeWithTag("safesearch_youtube_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_dashboard_list")
            .performScrollToNode(hasTestTag("ad_upstream_passthrough_card"))
        composeRule.onNodeWithTag("ad_upstream_passthrough_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_dashboard_list")
            .performScrollToNode(hasTestTag("smart_protection_profile_card"))
        composeRule.onNodeWithTag("smart_protection_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_blocker_dashboard_list")
            .performScrollToNode(hasTestTag("ad_analytics_dashboard_card"))
        composeRule.onNodeWithTag("ad_analytics_dashboard_card").assertIsDisplayed()

        // Websites & SNI/DPI moved out of Ad & Network Guard
        composeRule.onNodeWithTag("website_mode_control_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dpi_bypass_card").assertDoesNotExist()

        // Advanced tab renders Filter Lists
        composeRule.onNodeWithTag("ad_blocker_tab_advanced").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ad_blocker_advanced_list").assertIsDisplayed()
        composeRule.onNodeWithTag("open_add_custom_filter_button").assertIsDisplayed()
    }

    @Test
    fun `sni dpi screen renders collapsible accordions standalone guards and live logs tab`() {
        composeRule.setContent {
            NetGateTheme {
                SniDpiContent(
                    state = WebsiteBlockerUiState(dpiBypassMode = DpiBypassMode.AUTOPILOT),
                    isFirewallActive = true,
                    onToggleHttpsProxy = {},
                    onSelectProxyPort = {},
                    onToggleProxyStripTracking = {},
                    onSelectSniPreset = {},
                    onSaveCustomSniProxy = { _, _ -> },
                    onSelectDpiMode = {},
                    onSelectDpiChunkSize = {},
                    onToggleDpiIdleKeepAlive = {},
                    onToggleBlockQuic = {},
                    onToggleAntiDnsPoison = {},
                    onSelectDnsTransportMode = {},
                    onFlushDpiCache = {},
                    onCopiedProxyEndpoint = {},
                    onClearDnsLogs = {},
                    onRequestEnableFirewall = {},
                    onOpenDpiLogs = {},
                    onAllowDomainTemporarily = { _, _ -> },
                    onAllowDomainPermanently = {},
                    onBlockDomainPermanently = {},
                    onReportFalsePositive = {},
                    onNotifyCopied = {}
                )
            }
        }

        composeRule.onNodeWithTag("sni_dpi_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("https_filtering_proxy_card").assertIsDisplayed()

        // Expand SNI Presets, Ports & Tracking accordion and verify all controls exist
        composeRule.onNodeWithTag("sni_presets_accordion_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("sni_preset_standard_direct"))
        composeRule.onNodeWithTag("sni_preset_standard_direct").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("proxy_port_chip_8899"))
        composeRule.onNodeWithTag("proxy_port_chip_8899").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("proxy_strip_tracking_switch"))
        composeRule.onNodeWithTag("proxy_strip_tracking_switch").assertIsDisplayed()

        // Scroll to DPI Bypass Strategy card and expand Bypass Tuning & Chunk Size accordion
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dpi_bypass_card"))
        composeRule.onNodeWithTag("dpi_bypass_card").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dpi_tuning_accordion_toggle"))
        composeRule.onNodeWithTag("dpi_tuning_accordion_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dpi_mode_autopilot"))
        composeRule.onNodeWithTag("dpi_mode_autopilot").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dpi_chunk_4"))
        composeRule.onNodeWithTag("dpi_chunk_4").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dns_transport_doh_with_fallback"))
        composeRule.onNodeWithTag("dns_transport_doh_with_fallback").assertIsDisplayed()

        // Standalone Network Guards card
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("dpi_network_guards_card"))
        composeRule.onNodeWithTag("dpi_network_guards_card").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("block_quic_switch"))
        composeRule.onNodeWithTag("block_quic_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("anti_dns_poison_switch"))
        composeRule.onNodeWithTag("anti_dns_poison_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("sni_dpi_settings_list")
            .performScrollToNode(hasTestTag("flush_dpi_cache_button"))
        composeRule.onNodeWithTag("flush_dpi_cache_button").assertIsDisplayed()

        // Switch to Live Logs tab
        composeRule.onNodeWithTag("sni_dpi_tab_live_logs").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dns_log_search_input").assertIsDisplayed()
    }

    @Test
    fun `proxy server sorts IPv4 first and handles large multi-chunk TLS ClientHello under Autopilot`() {
        val v6 = InetAddress.getByName("2606:4700:4700::1111") as Inet6Address
        val v4 = InetAddress.getByName("1.1.1.1")
        val sorted = LocalFilteringProxyServer.sortIpv4First(listOf(v6, v4))
        assertEquals(v4, sorted.first())

        val mockServer = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val receivedLen = AtomicInteger(0)
        val serverThread = thread {
            mockServer.use { srv ->
                val conn = srv.accept()
                conn.use { sock ->
                    val input = sock.getInputStream()
                    val hdr = ByteArray(5)
                    var n = 0
                    while (n < 5) {
                        val r = input.read(hdr, n, 5 - n)
                        if (r <= 0) break
                        n += r
                    }
                    val recLen = ((hdr[3].toInt() and 0xFF) shl 8) or (hdr[4].toInt() and 0xFF)
                    val rest = ByteArray(recLen)
                    var got = 0
                    while (got < recLen) {
                        val r = input.read(rest, got, recLen - got)
                        if (r <= 0) break
                        got += r
                    }
                    receivedLen.set(5 + got)
                    sock.getOutputStream().write(byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x02, 0x02, 0x00))
                    sock.getOutputStream().flush()
                }
            }
        }

        val proxy = LocalFilteringProxyServer(
            port = 0,
            stripTrackingParams = true,
            protectSocket = { true },
            evaluateRequest = { _, _ -> ProxyFilterDecision(allow = true, reason = "OK") },
            resolveTargetAddresses = { listOf(InetAddress.getByName("127.0.0.1")) },
            // Single-record probing: TLS record splitting is covered separately, this test targets
            // full multi-KB ClientHello assembly before SNI-split segmentation.
            dpiBypassConfigProvider = {
                DpiBypassConfig(
                    mode = DpiBypassMode.AUTOPILOT,
                    fragmentDelayMs = 0,
                    splitTlsRecords = false
                )
            },
            onRequestLogged = { _, _ -> }
        )
        assertTrue(proxy.start())
        try {
            val largeClientHello = buildSyntheticTlsClientHello("pq.example.com", extraExtensionPadding = 2600)
            assertTrue(largeClientHello.size > 2400)

            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 4_000
                val out = client.getOutputStream()
                val inp = client.getInputStream()
                out.write("CONNECT pq.example.com:${mockServer.localPort} HTTP/1.1\r\nHost: pq.example.com\r\n\r\n".toByteArray())
                out.flush()

                val respBuf = ByteArray(512)
                val hdrLen = inp.read(respBuf)
                assertTrue(String(respBuf, 0, hdrLen).contains("200"))

                // Send large ClientHello in two separate writes
                out.write(largeClientHello, 0, 1200)
                out.flush()
                Thread.sleep(25)
                out.write(largeClientHello, 1200, largeClientHello.size - 1200)
                out.flush()

                val serverReply = ByteArray(64)
                val replyLen = inp.read(serverReply)
                assertEquals(7, replyLen)
                assertEquals(largeClientHello.size, receivedLen.get())
            }
        } finally {
            proxy.stop()
            runCatching { mockServer.close() }
            serverThread.join(2000)
        }
    }

    @Test
    fun `proxy server falls back to DIRECT when Autopilot probes fail to receive ServerHello`() {
        val mockServer = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val attemptCount = AtomicInteger(0)
        val serverThread = thread {
            mockServer.use { srv ->
                // Attempt 1 (SNI split) & Attempt 2 (Micro chunks): immediately close; Attempt 3 (DIRECT): reply!
                while (attemptCount.get() < 3) {
                    val conn = runCatching { srv.accept() }.getOrNull() ?: break
                    val attempt = attemptCount.incrementAndGet()
                    conn.use { sock ->
                        val buf = ByteArray(4096)
                        runCatching { sock.getInputStream().read(buf) }
                        if (attempt >= 3) {
                            sock.getOutputStream().write(byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x02, 0x02, 0x00))
                            sock.getOutputStream().flush()
                        }
                    }
                }
            }
        }

        val proxy = LocalFilteringProxyServer(
            port = 0,
            stripTrackingParams = true,
            protectSocket = { true },
            evaluateRequest = { _, _ -> ProxyFilterDecision(allow = true, reason = "OK") },
            resolveTargetAddresses = { listOf(InetAddress.getByName("127.0.0.1")) },
            dpiBypassConfigProvider = { DpiBypassConfig(mode = DpiBypassMode.AUTOPILOT, fragmentDelayMs = 0) },
            onRequestLogged = { _, _ -> }
        )
        assertTrue(proxy.start())
        try {
            val hello = buildSyntheticTlsClientHello("strict.cdn.example")
            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 5_000
                val out = client.getOutputStream()
                val inp = client.getInputStream()
                out.write("CONNECT strict.cdn.example:${mockServer.localPort} HTTP/1.1\r\n\r\n".toByteArray())
                out.flush()
                val hdrBuf = ByteArray(512)
                val hdrLen = inp.read(hdrBuf)
                assertTrue(String(hdrBuf, 0, hdrLen).contains("200"))

                out.write(hello)
                out.flush()

                val serverReply = ByteArray(64)
                val replyLen = inp.read(serverReply)
                assertEquals(7, replyLen)
                assertEquals(3, attemptCount.get())
            }
        } finally {
            proxy.stop()
            runCatching { mockServer.close() }
            serverThread.join(2000)
        }
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

    private fun buildSyntheticTlsClientHello(
        hostname: String,
        extraExtensionPadding: Int = 0
    ): ByteArray {
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
            if (extraExtensionPadding > 0) {
                write(0x00)
                write(0x15) // padding extension
                write((extraExtensionPadding shr 8) and 0xFF)
                write(extraExtensionPadding and 0xFF)
                write(ByteArray(extraExtensionPadding))
            }
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
