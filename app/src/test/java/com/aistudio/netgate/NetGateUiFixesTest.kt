package com.aistudio.netgate

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.service.AppLogCollector
import com.aistudio.netgate.service.AppLogEntry
import com.aistudio.netgate.service.AppLogLevel
import com.aistudio.netgate.service.CrashLogRecorder
import com.aistudio.netgate.ui.components.AppDetailBottomSheet
import com.aistudio.netgate.ui.components.DnsLogFilter
import com.aistudio.netgate.ui.components.DnsLogsFilterCard
import com.aistudio.netgate.ui.components.FilterLogEntryCard
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.VpnDpiLogsSheet
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
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
    fun `GlassPillBottomNav renders stacked icons labels and page dots and switches tabs`() {
        var selected by mutableStateOf(AppDestination.FIREWALL)

        composeRule.setContent {
            NetGateTheme(
                themeMode = ThemeMode.DARK,
                blurEngineStyle = BlurEngineStyle.MORPH,
                dynamicColor = false
            ) {
                FloatingFrostedNavBar(
                    currentDestination = selected,
                    isConnectionInfoOpen = false,
                    onSelectFirewall = { selected = AppDestination.FIREWALL },
                    onSelectWebsites = { selected = AppDestination.WEBSITES },
                    onSelectDataUsage = { selected = AppDestination.DATA_USAGE },
                    onSelectDnsProtection = { selected = AppDestination.DNS },
                    onSelectHotspot = { selected = AppDestination.HOTSPOT },
                    onSelectVpnGuard = { selected = AppDestination.VPN_GUARD }
                )
            }
        }

        composeRule.onNodeWithTag("floating_frosted_nav_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("glass_pill_page_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_websites").performClick()
        composeRule.waitForIdle()
        assertEquals(AppDestination.WEBSITES, selected)

        composeRule.onNodeWithTag("floating_nav_dns").performClick()
        composeRule.waitForIdle()
        assertEquals(AppDestination.DNS, selected)
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
    }
}
