package com.aistudio.netgate

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.AppUpdateStatus
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.WebsiteRuleItem
import com.aistudio.netgate.data.model.WebsiteTabSection
import com.aistudio.netgate.service.AppLogCollector
import com.aistudio.netgate.service.AppLogEntry
import com.aistudio.netgate.service.AppLogLevel
import com.aistudio.netgate.service.CrashLogRecorder
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.BackupUiState
import com.aistudio.netgate.ui.WebsiteBlockerUiState
import com.aistudio.netgate.ui.components.AdvancedProtectionSheet
import com.aistudio.netgate.ui.components.AppDetailBottomSheet
import com.aistudio.netgate.ui.components.AppRuleRow
import com.aistudio.netgate.ui.components.DnsLogFilter
import com.aistudio.netgate.ui.components.DnsLogsFilterCard
import com.aistudio.netgate.ui.components.FilterLogEntryCard
import com.aistudio.netgate.ui.components.FirewallHeaderCard
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.components.AppearanceSettingsAccordionBody
import com.aistudio.netgate.ui.components.BottomNavConfigManager
import com.aistudio.netgate.ui.components.BottomNavHeightOption
import com.aistudio.netgate.ui.components.BottomNavIconScale
import com.aistudio.netgate.ui.components.BottomNavTabId
import com.aistudio.netgate.ui.components.BottomNavWidthOption
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.LocalBottomNavConfig
import com.aistudio.netgate.ui.components.VpnDpiLogsSheet
import com.aistudio.netgate.ui.components.websiteManagementItems
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.aistudio.netgate.ui.theme.LocalBlurEngineConfig
import com.aistudio.netgate.ui.theme.LocalDynamicColorEnabled
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.NetGateTheme
import com.aistudio.netgate.ui.theme.liquidFrostDp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class NetGateUiFixesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Settings About and Updates accordion stays expanded across check status transitions`() {
        var prefs by mutableStateOf(FirewallPreferencesState())
        var updateState by mutableStateOf(
            AppUpdateUiState(
                status = AppUpdateStatus.IDLE,
                currentVersionName = "3.9.5",
                currentVersionCode = 17
            )
        )

        composeRule.setContent {
            NetGateTheme(themeMode = prefs.themeMode, dynamicColor = false) {
                FirewallSettingsSheet(
                    preferences = prefs,
                    onThemeModeChange = { prefs = prefs.copy(themeMode = it) },
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
                    onToggleBlurAnimation = {},
                    onToggleShowSystemApps = {},
                    onToggleShowOfflineApps = {},
                    onToggleStatusBarSpeed = {},
                    onToggleDeepMonitor = {},
                    appUpdateState = updateState,
                    onCheckForUpdates = {
                        updateState = updateState.copy(status = AppUpdateStatus.CHECKING)
                    },
                    onClearAllRules = {},
                    onDismiss = {}
                )
            }
        }

        // Expand About & Updates
        composeRule.onNodeWithTag("settings_about_header")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_check_updates_button")
            .performScrollTo()
            .assertIsDisplayed()

        // Tap Check Updates -> transitions IDLE -> CHECKING -> UP_TO_DATE -> ERROR
        composeRule.onNodeWithTag("settings_check_updates_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppUpdateStatus.CHECKING, updateState.status)
        composeRule.onNodeWithTag("settings_check_updates_button").assertIsDisplayed()

        updateState = updateState.copy(
            status = AppUpdateStatus.UP_TO_DATE,
            otaReason = "You are on the latest release"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_check_updates_button").assertIsDisplayed()

        updateState = updateState.copy(
            status = AppUpdateStatus.ERROR,
            errorMessage = "Network unreachable"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_check_updates_button").assertIsDisplayed()
    }

    @Test
    fun `Appearance and Background System expand inline without full-screen navigation and select Morph blur`() {
        var prefs by mutableStateOf(FirewallPreferencesState())

        composeRule.setContent {
            NetGateTheme(
                themeMode = prefs.themeMode,
                blurEngineStyle = prefs.blurEngineStyle,
                blurIntensity = prefs.blurIntensity,
                dynamicColor = false
            ) {
                FirewallSettingsSheet(
                    preferences = prefs,
                    onThemeModeChange = { prefs = prefs.copy(themeMode = it) },
                    onToggleDynamicColor = {},
                    onBlurStyleChange = { prefs = prefs.copy(blurEngineStyle = it) },
                    onBlurIntensityChange = { prefs = prefs.copy(blurIntensity = it) },
                    onToggleBlurAnimation = {},
                    onToggleShowSystemApps = {},
                    onToggleShowOfflineApps = {},
                    onToggleStatusBarSpeed = {},
                    onToggleDeepMonitor = {},
                    onClearAllRules = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_open_appearance")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance_settings_view").assertIsDisplayed()
        composeRule.onAllNodesWithTag("appearance_back_button").assertCountEquals(0)

        composeRule.onNodeWithTag("settings_open_background_system")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("background_system_settings_view")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("background_system_back_button").assertCountEquals(0)

        composeRule.onNodeWithTag("blur_engine_accordion_toggle")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("blur_style_morph")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(BlurEngineStyle.MORPH, prefs.blurEngineStyle)
        composeRule.onNodeWithTag("blur_intensity_slider")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `ThemeMode SYSTEM Auto persists cleanly and tracks light dark mode with dynamic color`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferences(context)
        val initial = prefs.preferencesFlow.first()

        try {
            prefs.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, prefs.preferencesFlow.first().themeMode)

            prefs.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, prefs.preferencesFlow.first().themeMode)

            prefs.setBlurEngineStyle(BlurEngineStyle.MORPH)
            prefs.setBlurIntensity(1.35f)
            val saved = prefs.preferencesFlow.first()
            assertEquals(BlurEngineStyle.MORPH, saved.blurEngineStyle)
            assertEquals(1.35f, saved.blurIntensity, 0.01f)
        } finally {
            prefs.setThemeMode(initial.themeMode)
            prefs.setBlurEngineStyle(initial.blurEngineStyle)
            prefs.setBlurIntensity(initial.blurIntensity)
        }
    }

    @Test
    fun `NetGateTheme Auto mode and Dynamic Color work across system light and dark states`() {
        var dynamicColor by mutableStateOf(true)
        var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        var capturedDark = false
        var capturedDynamic = false
        var capturedBlurStyle = BlurEngineStyle.OFF

        composeRule.setContent {
            NetGateTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                blurEngineStyle = BlurEngineStyle.MORPH,
                blurIntensity = 1.25f
            ) {
                capturedDark = LocalIsDarkTheme.current
                capturedDynamic = LocalDynamicColorEnabled.current
                capturedBlurStyle = LocalBlurEngineConfig.current.style
            }
        }

        composeRule.waitForIdle()
        val systemIsDark = (ApplicationProvider.getApplicationContext<Context>()
            .resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        assertEquals(systemIsDark, capturedDark)
        assertTrue(capturedDynamic)
        assertEquals(BlurEngineStyle.MORPH, capturedBlurStyle)

        themeMode = ThemeMode.DARK
        composeRule.waitForIdle()
        assertTrue(capturedDark)
        assertTrue(capturedDynamic)

        themeMode = ThemeMode.LIGHT
        dynamicColor = false
        composeRule.waitForIdle()
        assertFalse(capturedDark)
        assertFalse(capturedDynamic)
    }

    @Test
    fun `AppDetailBottomSheet renders inside glass container with Morph BlurEngine active`() {
        val app = ManagedApp(
            packageName = "com.example.browser",
            appName = "Browser",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = true,
            blockMobileData = false
        )

        composeRule.setContent {
            NetGateTheme(
                themeMode = ThemeMode.DARK,
                blurEngineStyle = BlurEngineStyle.MORPH,
                dynamicColor = false
            ) {
                AppDetailBottomSheet(
                    app = app,
                    activeNetworkType = ActiveNetworkType.WIFI,
                    isFirewallActive = true,
                    nowMillis = 1_750_000_000_000L,
                    loadIcon = { null },
                    onUpdateRule = { _, _ -> },
                    onScheduleTimer = { _, _ -> },
                    onCancelTimer = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_detail_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Disable Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Disable Mobile Data").assertIsDisplayed()
    }

    @Test
    fun `GlassTabs bottom nav renders stacked icons labels and page dots and switches tabs`() {
        var selected by mutableStateOf(AppDestination.FIREWALL)
        var sniDpiSelected by mutableStateOf(false)

        composeRule.setContent {
            NetGateTheme(
                themeMode = ThemeMode.DARK,
                blurEngineStyle = BlurEngineStyle.MORPH,
                dynamicColor = false
            ) {
                FloatingFrostedNavBar(
                    currentDestination = selected,
                    isConnectionInfoOpen = false,
                    onSelectFirewall = {
                        sniDpiSelected = false
                        selected = AppDestination.FIREWALL
                    },
                    onSelectWebsites = {
                        sniDpiSelected = false
                        selected = AppDestination.WEBSITES
                    },
                    onSelectDataUsage = {
                        sniDpiSelected = false
                        selected = AppDestination.DATA_USAGE
                    },
                    onSelectDnsProtection = {
                        sniDpiSelected = false
                        selected = AppDestination.DNS
                    },
                    onSelectHotspot = {
                        sniDpiSelected = false
                        selected = AppDestination.HOTSPOT
                    },
                    onSelectVpnGuard = {
                        sniDpiSelected = false
                        selected = AppDestination.VPN_GUARD
                    },
                    isSniDpiSelected = sniDpiSelected,
                    onSelectSniDpi = {
                        selected = AppDestination.FIREWALL
                        sniDpiSelected = true
                    }
                )
            }
        }

        composeRule.onNodeWithTag("floating_frosted_nav_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("glass_pill_page_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_firewall").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_nav_vpn_guard").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_nav_hotspot").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_data_usage").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_websites").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppDestination.WEBSITES, selected)
        assertFalse(sniDpiSelected)

        composeRule.onNodeWithTag("nav_tab_sni_dpi").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(sniDpiSelected)

        composeRule.onNodeWithTag("floating_nav_dns").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppDestination.DNS, selected)
        assertFalse(sniDpiSelected)

        composeRule.onNodeWithTag("nav_tab_data_usage").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppDestination.DATA_USAGE, selected)
        assertFalse(sniDpiSelected)
    }

    @Test
    fun `DnsLogsFilterCard and FilterLogEntryCard render aligned counters and chips cleanly`() {
        var filter by mutableStateOf(DnsLogFilter.ALL)
        var query by mutableStateOf("")
        val sampleEntry = DnsQueryLogEntry(
            id = 42L,
            domain = "ads.tracker.example",
            status = DnsQueryStatus.BLOCKED_AD,
            reason = "AdGuard Base Filter",
            timestampMs = 1_750_000_005_000L,
            appName = "Browser"
        )

        composeRule.setContent {
            NetGateTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    DnsLogsFilterCard(
                        searchQuery = query,
                        selectedFilter = filter,
                        totalLogsCount = 12,
                        onSearchQueryChange = { query = it },
                        onSelectFilter = { filter = it },
                        blockedCount = 8,
                        allowedCount = 4
                    )
                    FilterLogEntryCard(
                        entry = sampleEntry,
                        onAllowTemporarily = { _, _ -> },
                        onAllowPermanently = {},
                        onBlockPermanently = {},
                        onReportFalsePositive = {},
                        onNotifyCopied = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("8 Blocked").assertIsDisplayed()
        composeRule.onNodeWithText("4 Allowed").assertIsDisplayed()
        composeRule.onNodeWithTag("dns_log_filter_ads_blocked").performClick()
        composeRule.waitForIdle()
        assertEquals(DnsLogFilter.ADS_BLOCKED, filter)
        composeRule.onNodeWithTag("filter_log_entry_42").assertIsDisplayed()
    }

    @Test
    fun `AppLogCollector ring buffer CrashLogRecorder integration and VpnDpiLogsSheet live stream work end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLogCollector.clear(context)

        // Verify bounded buffer capacity cap
        repeat(AppLogCollector.MAX_ENTRIES + 50) { idx ->
            AppLogCollector.info(tag = "Firewall", title = "Rule #$idx", message = "Processed rule $idx")
        }
        assertEquals(AppLogCollector.MAX_ENTRIES, AppLogCollector.entries.value.size)

        // Reset and verify structured logs + crash report capture + Logcat parser
        AppLogCollector.clear(context)
        AppLogCollector.info("Service", "VPN started", "TUN active on wlan0")
        AppLogCollector.warn("DNS", "QUIC downgraded", "Stripped SVCB ALPN hint")
        AppLogCollector.error("VPN Guard", "Handshake timeout", "Endpoint timed out", RuntimeException("boom"))
        CrashLogRecorder.writeCrashReport(context, Thread.currentThread(), IllegalStateException("Test fatal crash"))

        val recorded = AppLogCollector.entries.value
        assertEquals(4, recorded.size)
        assertTrue(recorded.any { it.level == AppLogLevel.CRASH && (it.stackTrace?.contains("Test fatal crash") == true) })

        val parsed = AppLogCollector.parseThreadtimeLine("11-26 01:02:03.456  1234  5678 W NetGateVpn: DNS fallback triggered")
        assertNotNull(parsed)
        assertEquals(AppLogLevel.WARN, parsed?.first)
        assertEquals("NetGateVpn", parsed?.second)

        val exportedFile = AppLogCollector.exportToCacheFile(context, AppLogCollector.formatForExport(recorded))
        assertNotNull(exportedFile)
        assertTrue(exportedFile!!.exists())

        var appLogsState by mutableStateOf<List<AppLogEntry>>(recorded)
        composeRule.setContent {
            NetGateTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                VpnDpiLogsSheet(
                    logs = emptyList(),
                    appLogs = appLogsState,
                    onClearLogs = {
                        AppLogCollector.clear(context)
                        appLogsState = emptyList()
                    },
                    onCopyLogs = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("vpn_dpi_logs_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_tab_live_app").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_export_button").assertIsDisplayed()

        // Filter to CRASH level
        composeRule.onNodeWithTag("log_level_filter_crash").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("App crash:", substring = true).assertIsDisplayed()

        // Reset level filter to All & filter by tag + search
        composeRule.onNodeWithTag("log_level_filter_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_dpi_log_search_input").performTextInput("QUIC")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("QUIC downgraded").assertIsDisplayed()

        // Clear all logs -> shows proper empty state
        composeRule.onNodeWithTag("vpn_dpi_logs_clear_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_dpi_logs_empty").assertIsDisplayed()
        AppLogCollector.clear(context)
    }

    @Test
    fun `BlurEngine blurRadius and Morph edge contrast respect intensity bounds`() {
        assertEquals(24f * 1.4f, BlurEngineStyle.MORPH.liquidFrostDp(1.4f).value, 0.1f)
        assertEquals(0f, BlurEngineStyle.OFF.liquidFrostDp(1.5f).value, 0.01f)
        assertEquals(BlurEngineStyle.MORPH, BlurEngineStyle.entries.last())
        assertEquals("Frosted-glass", BlurEngineStyle.MORPH.title)
    }

    @Test
    fun `Diagnostics exporter anonymizes browsing hostnames and ignores external PID and GC chatter`() {
        val raw = "DPI/DNS: web.facebook.com and i.instagram.com"
        val safe = AppLogCollector.sanitizeBrowsingHostnames(raw)
        assertTrue(safe.contains("[protected-domain]"))
        assertFalse(safe.contains("facebook.com"))
        assertFalse(safe.contains("instagram.com"))

        // External PID is dropped
        val foreignPid = AppLogCollector.parseThreadtimeLine(
            "11-26 01:02:03.456  9999  5678 W OtherApp: sensitive external chatter",
            targetPid = "1234"
        )
        assertNull(foreignPid)

        // Internal ART GC loop chatter is dropped
        val gcLog = AppLogCollector.parseThreadtimeLine(
            "11-26 01:02:03.456  1234  5678 I .netgate: Background concurrent mark compact GC freed 10MB AllocSpace bytes",
            targetPid = "1234"
        )
        assertNull(gcLog)
    }

    @Test
    fun `Redesigned FirewallHeaderCard and AppRuleRow display pills and wire Websites pill click`() {
        var openedWebsites = false
        var wifiToggled = false
        var dataToggled = false
        val app = ManagedApp(
            packageName = "com.example.oneoneoneone",
            appName = "1.1.1.1",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = false,
            blockMobileData = false
        )

        composeRule.setContent {
            NetGateTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    FirewallHeaderCard(
                        isFirewallActive = true,
                        connectionDetails = ConnectionDetails(
                            activeNetworkType = ActiveNetworkType.WIFI,
                            isWifiRadioEnabled = true,
                            isWifiConnected = true,
                            isMobileDataEnabled = false,
                            isMobileDataConnected = false,
                            wifiBandLabel = "5 GHz"
                        ),
                        totalAppsCount = 222,
                        wifiBlockedCount = 0,
                        dataBlockedCount = 0,
                        activeBlockedNowCount = 0,
                        isAdBlockerEnabled = true,
                        blockedAdsCount = 54L,
                        liveTelemetry = LiveNetworkTelemetry(
                            downloadBytesPerSec = 125L,
                            uploadBytesPerSec = 102L
                        ),
                        isVpnGuardActive = true,
                        vpnGuardProfileLabel = "WireGuard & WARP",
                        blockedWebsitesCount = 1,
                        websiteRulesBadgeText = "1 Blocked",
                        dnsSummaryLabel = "1.1.1.1",
                        onToggleFirewall = {},
                        onOpenConnectionInfo = {},
                        onOpenDnsProtection = {},
                        onOpenWebsitesTab = { openedWebsites = true }
                    )
                    AppRuleRow(
                        app = app,
                        activeNetworkType = ActiveNetworkType.WIFI,
                        isFirewallActive = true,
                        isSelectionMode = false,
                        isSelected = false,
                        nowMillis = 1_750_000_000_000L,
                        loadIcon = { null },
                        onToggleWifi = { wifiToggled = true },
                        onToggleData = { dataToggled = true },
                        onRowClick = {},
                        onRowLongClick = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Firewall Active").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi ON").assertIsDisplayed()
        composeRule.onNodeWithText("Data OFF").assertIsDisplayed()
        composeRule.onNodeWithText("WI-FI OFF").assertIsDisplayed()
        composeRule.onNodeWithText("DATA OFF").assertIsDisplayed()
        composeRule.onNodeWithText("ADS BLK").assertIsDisplayed()
        composeRule.onNodeWithText("1 Blocked").assertIsDisplayed()

        composeRule.onNodeWithTag("header_websites_blocked_pill").performClick()
        assertTrue(openedWebsites)

        composeRule.onNodeWithTag("wifi_toggle_com.example.oneoneoneone").performClick()
        composeRule.onNodeWithTag("data_toggle_com.example.oneoneoneone").performClick()
        assertTrue(wifiToggled)
        assertTrue(dataToggled)
    }

    @Test
    fun `Website management items render blocklist and whitelist rules inside Firewall Websites tab`() {
        var addedDomain = ""
        var addedWhitelist = false
        val state = WebsiteBlockerUiState(
            filterMode = WebsiteFilterMode.BLOCKLIST,
            blocklistRules = listOf(
                WebsiteRuleItem(
                    domain = "tracker.example.com",
                    isWhitelist = false,
                    isEnabled = true,
                    createdAt = 1_750_000_000_000L
                )
            ),
            whitelistRules = listOf(
                WebsiteRuleItem(
                    domain = "trusted.example.org",
                    isWhitelist = true,
                    isEnabled = true,
                    createdAt = 1_750_000_000_000L
                )
            )
        )
        var domainInput by mutableStateOf("")
        var selectedSection by mutableStateOf(WebsiteTabSection.BLOCKLIST)

        composeRule.setContent {
            NetGateTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                LazyColumn(
                    modifier = Modifier.testTag("test_websites_lazy_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    websiteManagementItems(
                        state = state,
                        isFirewallActive = true,
                        connectionDetails = ConnectionDetails(),
                        domainInput = domainInput,
                        selectedSection = selectedSection,
                        filteredBypassApps = emptyList(),
                        appSearchQuery = "",
                        loadIcon = { null },
                        onDomainInputChange = { domainInput = it },
                        onSelectFilterMode = {},
                        onRequestEnableFirewall = {},
                        onOpenDnsProtection = {},
                        onAllowDomainPermanently = {
                            addedDomain = it
                            addedWhitelist = true
                        },
                        onBlockDomainPermanently = {
                            addedDomain = it
                            addedWhitelist = false
                        },
                        onAllowDomainTemporarily = { _, _ -> },
                        onSelectSection = { selectedSection = it },
                        onClearWebsiteRules = {},
                        onClearBypassApps = {},
                        onOpenImport = {},
                        onOpenExport = {},
                        onOpenBackupRestore = {},
                        onAppSearchQueryChange = {},
                        onToggleWebsiteRuleEnabled = { _, _ -> },
                        onDeleteWebsiteRule = {},
                        onToggleBypassApp = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("website_mode_control_card").assertIsDisplayed()
        composeRule.onNodeWithTag("test_websites_lazy_list")
            .performScrollToNode(hasTestTag("website_rule_row_tracker.example.com"))
        composeRule.onNodeWithText("tracker.example.com").assertIsDisplayed()

        composeRule.onNodeWithTag("test_websites_lazy_list")
            .performScrollToNode(hasTestTag("website_domain_input"))
        composeRule.onNodeWithTag("website_domain_input").performTextInput("ads.test.net")
        composeRule.onNodeWithTag("add_blocklist_button").performClick()
        assertEquals("ads.test.net", addedDomain)
        assertFalse(addedWhitelist)

        composeRule.onNodeWithTag("test_websites_lazy_list")
            .performScrollToNode(hasTestTag("website_section_tab_whitelist"))
        composeRule.onNodeWithTag("website_section_tab_whitelist").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_websites_lazy_list")
            .performScrollToNode(hasTestTag("website_rule_row_trusted.example.org"))
        composeRule.onNodeWithText("trusted.example.org").assertIsDisplayed()
    }

    @Test
    fun `AdvancedProtectionSheet renders Encrypted Backup Export and Choose File buttons cleanly`() {
        composeRule.setContent {
            NetGateTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                AdvancedProtectionSheet(
                    advanced = AdvancedPreferencesState(),
                    backupState = BackupUiState(),
                    appUpdateState = AppUpdateUiState(),
                    currentSsid = null,
                    onToggleKillSwitch = {},
                    onToggleAutoReconnect = {},
                    onToggleAutoVpnUnknownWifi = {},
                    onSetWifiTrusted = { _, _ -> },
                    onRequestBatteryExemption = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onExportBackup = {},
                    onImportBackup = { _, _ -> },
                    onClearBackupState = {},
                    onOpenNotificationSettings = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("advanced_backup_export_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_backup_choose_file_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Choose File").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
    }

    @Test
    fun `FloatingFrostedNavBar shows all enabled tabs directly including Wi-Fi and Speed Test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BottomNavConfigManager.init(context)
        BottomNavConfigManager.resetToDefaults(context)

        var clickedWifi = false
        var clickedSpeedTest = false

        composeRule.setContent {
            val navConfig by BottomNavConfigManager.configFlow.collectAsState()
            NetGateTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBottomNavConfig provides navConfig) {
                    FloatingFrostedNavBar(
                        currentDestination = AppDestination.DATA_USAGE,
                        isConnectionInfoOpen = false,
                        onSelectFirewall = {},
                        onSelectWebsites = {},
                        onSelectDataUsage = {},
                        onSelectDnsProtection = {},
                        onSelectHotspot = {},
                        onSelectVpnGuard = {},
                        onSelectWifi = { clickedWifi = true },
                        onSelectSpeedTest = { clickedSpeedTest = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_firewall").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_wifi").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clickedWifi)

        composeRule.onNodeWithTag("nav_tab_speed_test").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clickedSpeedTest)
    }

    @Test
    fun `BottomNavConfigManager modifies options, visibility, and preserves order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BottomNavConfigManager.init(context)
        BottomNavConfigManager.resetToDefaults(context)

        BottomNavConfigManager.setHeightOption(context, BottomNavHeightOption.SPACIOUS)
        assertEquals(BottomNavHeightOption.SPACIOUS, BottomNavConfigManager.configFlow.value.heightOption)

        BottomNavConfigManager.setWidthOption(context, BottomNavWidthOption.EXPANDED)
        assertEquals(BottomNavWidthOption.EXPANDED, BottomNavConfigManager.configFlow.value.widthOption)

        BottomNavConfigManager.setIconScale(context, BottomNavIconScale.LARGE)
        assertEquals(BottomNavIconScale.LARGE, BottomNavConfigManager.configFlow.value.iconScale)

        assertEquals(BottomNavTabId.entries.size, BottomNavConfigManager.configFlow.value.activeOrderedTabs.size)
        assertTrue(BottomNavConfigManager.toggleTabVisibility(context, BottomNavTabId.WIFI))
        assertEquals(BottomNavTabId.entries.size - 1, BottomNavConfigManager.configFlow.value.activeOrderedTabs.size)
        assertTrue(BottomNavConfigManager.toggleTabVisibility(context, BottomNavTabId.WIFI))
        assertEquals(BottomNavTabId.entries.size, BottomNavConfigManager.configFlow.value.activeOrderedTabs.size)

        val initialOrder = BottomNavConfigManager.configFlow.value.orderedTabIds
        BottomNavConfigManager.moveTabDown(context, initialOrder[0])
        assertEquals(initialOrder[0], BottomNavConfigManager.configFlow.value.orderedTabIds[1])

        BottomNavConfigManager.moveTabUp(context, initialOrder[0])
        assertEquals(initialOrder[0], BottomNavConfigManager.configFlow.value.orderedTabIds[0])

        BottomNavConfigManager.resetToDefaults(context)
        assertEquals(BottomNavHeightOption.STANDARD, BottomNavConfigManager.configFlow.value.heightOption)
        assertEquals(BottomNavTabId.entries.toSet(), BottomNavConfigManager.configFlow.value.visibleTabIds)
    }

    @Test
    fun `AppearanceSettingsSheet renders Bottom Navigation Bar settings accordion and options`() {
        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                AppearanceSettingsAccordionBody(
                    preferences = FirewallPreferencesState(),
                    onThemeModeChange = {},
                    onThemeStyleChange = {},
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
                    onBlurIntensityChange = {},
                    onToggleBlurAnimation = {},
                    onAppIconChange = {},
                    onOpenBackgroundAnimation = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_bottom_nav_card").assertExists()
        composeRule.onNodeWithTag("settings_bottom_nav_header").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_height_compact").assertExists()
        composeRule.onNodeWithTag("nav_height_standard").assertExists()
        composeRule.onNodeWithTag("nav_height_spacious").assertExists()

        composeRule.onNodeWithTag("nav_width_compact").assertExists()
        composeRule.onNodeWithTag("nav_width_standard").assertExists()
        composeRule.onNodeWithTag("nav_width_expanded").assertExists()

        composeRule.onNodeWithTag("nav_scale_small").assertExists()
        composeRule.onNodeWithTag("nav_scale_standard").assertExists()
        composeRule.onNodeWithTag("nav_scale_large").assertExists()

        composeRule.onNodeWithTag("nav_reset_defaults_button").assertExists()
    }
}
