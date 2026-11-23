package com.example

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FirewallPreferences
import com.example.data.model.AppDestination
import com.example.data.model.AppIconStyle
import com.example.data.model.DataUsageSubTab
import com.example.data.model.DpiBypassMode
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.LiveNetworkTelemetry
import com.example.data.model.SavedWifiNetworkInfo
import com.example.data.model.StatusBarSpeedStyle
import com.example.data.model.WifiDiagnosticsUiState
import com.example.data.model.WifiSavedAccessStatus
import com.example.data.model.WifiSignalQuality
import com.example.data.model.classifyWifiSignalQuality
import com.example.data.model.frequencyToWifiChannel
import com.example.data.model.isGoodWifiSignal
import com.example.data.model.rssiToSignalPercent
import com.example.data.model.wifiBandFromFrequency
import com.example.data.model.wifiCapabilitiesToSecurityLabel
import com.example.data.model.wifiStandardLabelFromCode
import com.example.data.network.SpeedTestState
import com.example.ui.DataUsageContent
import com.example.ui.DataUsageUiState
import com.example.ui.components.FirewallSettingsSheet
import com.example.ui.components.FloatingFrostedNavBar
import com.example.ui.theme.NetGateTheme
import com.example.widget.NetGateWidgetProvider
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
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class WifiAndVisualUpgradesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `wifi signal quality, band, channel and standard helpers match engineering thresholds`() {
        assertEquals(WifiSignalQuality.EXCELLENT, classifyWifiSignalQuality(-45))
        assertEquals(WifiSignalQuality.EXCELLENT, classifyWifiSignalQuality(-55))
        assertEquals(WifiSignalQuality.GOOD, classifyWifiSignalQuality(-56))
        assertEquals(WifiSignalQuality.GOOD, classifyWifiSignalQuality(-67))
        assertEquals(WifiSignalQuality.FAIR, classifyWifiSignalQuality(-68))
        assertEquals(WifiSignalQuality.WEAK, classifyWifiSignalQuality(-76))
        assertEquals(WifiSignalQuality.UNAVAILABLE, classifyWifiSignalQuality(null))

        assertTrue(isGoodWifiSignal(-55))
        assertTrue(isGoodWifiSignal(-67))
        assertFalse(isGoodWifiSignal(-68))
        assertFalse(isGoodWifiSignal(null))

        assertEquals(100, rssiToSignalPercent(-30))
        assertEquals(0, rssiToSignalPercent(-100))
        assertEquals(0, rssiToSignalPercent(null))

        assertEquals("2.4 GHz", wifiBandFromFrequency(2437))
        assertEquals("5 GHz", wifiBandFromFrequency(5180))
        assertEquals("6 GHz", wifiBandFromFrequency(5955))
        assertEquals(6, frequencyToWifiChannel(2437))
        assertEquals(36, frequencyToWifiChannel(5180))
        assertEquals(1, frequencyToWifiChannel(5955))
        assertNull(frequencyToWifiChannel(null))

        assertEquals("Wi-Fi 7 (802.11be)", wifiStandardLabelFromCode(8))
        assertEquals("Wi-Fi 6 (802.11ax)", wifiStandardLabelFromCode(6))
        assertEquals("Wi-Fi 5 (802.11ac)", wifiStandardLabelFromCode(5))
        assertEquals("Wi-Fi 4 (802.11n)", wifiStandardLabelFromCode(4))

        assertEquals("WPA3-SAE", wifiCapabilitiesToSecurityLabel("[WPA2-PSK-CCMP][SAE-CCMP][ESS]"))
        assertEquals("WPA2-PSK", wifiCapabilitiesToSecurityLabel("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun `FirewallPreferences persists observed Wi-Fi networks and rejects unknown ssid placeholders`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferences(context)

        prefs.clearObservedWifi()
        assertTrue(prefs.observedWifiFlow.first().isEmpty())

        prefs.recordObservedWifi(
            SavedWifiNetworkInfo(
                ssid = "<unknown ssid>",
                rssiDbm = -58
            )
        )
        assertTrue(prefs.observedWifiFlow.first().isEmpty())

        prefs.recordObservedWifi(
            SavedWifiNetworkInfo(
                ssid = "StudioFiber_6E",
                bssid = "aa:bb:cc:dd:ee:01",
                standardLabel = "Wi-Fi 6E / 6 (802.11ax)",
                frequencyMhz = 5955,
                bandLabel = "6 GHz",
                channel = 1,
                rssiDbm = -54,
                linkSpeedMbps = 2400,
                rxLinkSpeedMbps = 1920,
                quality = WifiSignalQuality.EXCELLENT,
                securityLabel = "WPA3-SAE",
                isSavedOnDevice = true,
                lastSeenAtMs = 1_750_000_000_000L
            )
        )

        val stored = prefs.observedWifiFlow.first()
        assertEquals(1, stored.size)
        val first = stored.first()
        assertEquals("StudioFiber_6E", first.ssid)
        assertEquals("Wi-Fi 6E / 6 (802.11ax)", first.standardLabel)
        assertEquals(5955, first.frequencyMhz)
        assertEquals("6 GHz", first.bandLabel)
        assertEquals(1, first.channel)
        assertEquals(-54, first.rssiDbm)
        assertEquals(2400, first.linkSpeedMbps)
        assertEquals(1920, first.rxLinkSpeedMbps)
        assertEquals(WifiSignalQuality.EXCELLENT, first.quality)

        prefs.clearObservedWifi()
        assertTrue(prefs.observedWifiFlow.first().isEmpty())
    }

    @Test
    fun `DataUsageContent renders Wi-Fi tab with connected telemetry, quality guide and saved Wi-Fi networks`() {
        val connected = SavedWifiNetworkInfo(
            ssid = "HomeMesh_5G",
            bssid = "11:22:33:44:55:66",
            standardLabel = "Wi-Fi 6 (802.11ax)",
            frequencyMhz = 5180,
            bandLabel = "5 GHz",
            channel = 36,
            rssiDbm = -60,
            linkSpeedMbps = 1200,
            rxLinkSpeedMbps = 866,
            quality = WifiSignalQuality.GOOD,
            securityLabel = "WPA3-SAE",
            isCurrentlyConnected = true,
            isSavedOnDevice = true,
            sourceLabel = "Connected Now"
        )
        val savedOther = SavedWifiNetworkInfo(
            ssid = "Office_Corp",
            standardLabel = "Wi-Fi 5 (802.11ac)",
            frequencyMhz = 5745,
            bandLabel = "5 GHz",
            channel = 149,
            rssiDbm = -72,
            linkSpeedMbps = 433,
            quality = WifiSignalQuality.FAIR,
            securityLabel = "WPA2-PSK",
            isSavedOnDevice = true,
            sourceLabel = "Saved on Device"
        )

        val usageState = DataUsageUiState(
            selectedSubTab = DataUsageSubTab.WIFI,
            wifiDiagnostics = WifiDiagnosticsUiState(
                isWifiEnabled = true,
                hasLocationPermission = true,
                accessStatus = WifiSavedAccessStatus.SCOPED_ANDROID_10_PLUS,
                connectedWifi = connected,
                savedNetworks = listOf(connected, savedOther),
                statusNote = "Device profiles merged cleanly."
            )
        )

        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                DataUsageContent(
                    state = usageState,
                    liveTelemetry = LiveNetworkTelemetry(),
                    isFirewallActive = true,
                    isDeepMonitorEnabled = false,
                    isHttpsProxyActive = false,
                    isStatusBarSpeedEnabled = false,
                    statusBarSpeedStyle = StatusBarSpeedStyle.DOWNLOAD_UPLOAD,
                    showStatusBarDailyUsage = false,
                    loadIcon = { null },
                    onSelectSubTab = {},
                    onTimeRangeChange = {},
                    onChartModeChange = {},
                    onFilterCategoryChange = {},
                    onRefreshUsage = {},
                    onClearHistory = {},
                    onInspectApp = {},
                    onToggleStatusBarSpeed = {},
                    onSelectStatusBarStyle = {},
                    onToggleShowDailyUsage = {},
                    onResetLiveSession = {},
                    onRequestEnableFirewall = {},
                    onToggleDeepMonitor = {},
                    onSetShowLiveSitesFeed = {},
                    onSiteFilterChange = {},
                    onSiteSearchQueryChange = {},
                    onToggleCountryFilter = {},
                    onClearConnectionHistory = {},
                    onQuickBlockSite = {},
                    onQuickAllowSite = {},
                    onRunSecurityAudit = {},
                    onOpenFlaggedSitesFromAudit = {},
                    speedTestState = SpeedTestState(),
                    activeNetworkLabel = "Wi-Fi (HomeMesh_5G)",
                    onStartSpeedTest = {},
                    onStopSpeedTest = {},
                    onRunVideoTest = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wifi_analysis_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("connected_wifi_hero_card").assertIsDisplayed()
        composeRule.onNodeWithText("HomeMesh_5G").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi 6 (802.11ax)").assertIsDisplayed()
        composeRule.onNodeWithText("Good: -67 dBm or stronger").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_analysis_screen").performScrollToNode(hasTestTag("wifi_quality_guide_card"))
        composeRule.onNodeWithTag("wifi_quality_guide_card").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_analysis_screen").performScrollToNode(hasTestTag("saved_wifi_header_card"))
        composeRule.onNodeWithTag("saved_wifi_header_card").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_analysis_screen").performScrollToNode(hasTestTag("saved_wifi_card_office_corp"))
        composeRule.onNodeWithTag("saved_wifi_card_office_corp").assertIsDisplayed()
    }

    @Test
    fun `AppIconStyle keeps Lime Shield and uses 3 newly generated NetGate icon variants`() {
        assertEquals("Lime Shield", AppIconStyle.EMERALD.title)
        assertEquals("Cyber Gate", AppIconStyle.NEON.title)
        assertEquals("Tunnel Core", AppIconStyle.VIOLET.title)
        assertEquals("Packet Bastion", AppIconStyle.AMBER.title)

        var selectedIcon by mutableStateOf(AppIconStyle.EMERALD)
        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                FirewallSettingsSheet(
                    preferences = FirewallPreferencesState(appIconStyle = selectedIcon),
                    onThemeModeChange = {},
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
                    onToggleBlurAnimation = {},
                    onToggleShowSystemApps = {},
                    onToggleShowOfflineApps = {},
                    onToggleStatusBarSpeed = {},
                    onToggleDeepMonitor = {},
                    onAppIconChange = { selectedIcon = it },
                    onClearAllRules = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("app_icon_neon").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppIconStyle.NEON, selectedIcon)

        composeRule.onNodeWithTag("app_icon_violet").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppIconStyle.VIOLET, selectedIcon)

        composeRule.onNodeWithTag("app_icon_amber").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppIconStyle.AMBER, selectedIcon)
    }

    @Test
    fun `FloatingFrostedNavBar renders VPN Guard destination without key branding`() {
        var clickedVpn = false
        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                FloatingFrostedNavBar(
                    currentDestination = AppDestination.VPN_GUARD,
                    isConnectionInfoOpen = false,
                    onSelectFirewall = {},
                    onSelectWebsites = {},
                    onSelectDataUsage = {},
                    onSelectDnsProtection = {},
                    onSelectHotspot = {},
                    onSelectVpnGuard = { clickedVpn = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_nav_vpn_guard").assertIsDisplayed().performClick()
        assertTrue(clickedVpn)
    }

    @Test
    fun `NetGateWidgetProvider inflates Liquid Glass and Material You layers in day mode`() {
        assertWidgetLiquidGlassInflation(useDynamicColor = true)
        assertWidgetLiquidGlassInflation(useDynamicColor = false)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h915dp-night")
    fun `NetGateWidgetProvider inflates Liquid Glass and Material You layers cleanly in night mode`() {
        assertWidgetLiquidGlassInflation(useDynamicColor = true)
        assertWidgetLiquidGlassInflation(useDynamicColor = false)
    }

    private fun assertWidgetLiquidGlassInflation(useDynamicColor: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferencesState(
            useDynamicColor = useDynamicColor,
            isFirewallEnabled = true,
            dpiBypassMode = DpiBypassMode.AUTOPILOT,
            isAdBlockerEnabled = false,
            hotspotProxyEnabled = false
        )

        val remoteViews = NetGateWidgetProvider.buildRemoteViews(
            context = context,
            prefs = prefs,
            vpnRunning = true,
            hotspotRunning = false
        )
        val inflated = remoteViews.apply(context, FrameLayout(context))

        assertTrue(inflated.background is LayerDrawable)
        val rootLayers = inflated.background as LayerDrawable
        assertEquals(5, rootLayers.numberOfLayers)

        val vpnTile = inflated.findViewById<View>(R.id.widget_tile_vpn)
        assertNotNull(vpnTile)
        assertTrue(vpnTile.background is LayerDrawable)
        assertEquals(3, (vpnTile.background as LayerDrawable).numberOfLayers)

        val adBlockTile = inflated.findViewById<View>(R.id.widget_tile_adblock)
        assertNotNull(adBlockTile)
        assertTrue(adBlockTile.background is LayerDrawable)
        assertEquals(3, (adBlockTile.background as LayerDrawable).numberOfLayers)

        assertEquals(
            "Protected • 2 ON",
            inflated.findViewById<TextView>(R.id.widget_status_pill).text.toString()
        )
    }
}
