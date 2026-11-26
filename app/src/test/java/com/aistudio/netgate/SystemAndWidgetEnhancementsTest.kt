package com.aistudio.netgate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.TextView
import org.robolectric.Shadows
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.BundledChangelog
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.service.NetworkMonitorService
import com.aistudio.netgate.service.StatusBarIconRenderer
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.theme.NetGateTheme
import com.aistudio.netgate.widget.NetGateWidgetProvider
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
class SystemAndWidgetEnhancementsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `excludeFromRecents persists in FirewallPreferences`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferences(context)

        assertFalse(prefs.preferencesFlow.first().excludeFromRecents)

        prefs.setExcludeFromRecents(true)
        assertTrue(prefs.preferencesFlow.first().excludeFromRecents)

        prefs.setExcludeFromRecents(false)
        assertFalse(prefs.preferencesFlow.first().excludeFromRecents)
    }

    @Test
    fun `NetworkMonitorService upgrades notification channel to IMPORTANCE_DEFAULT without sound or vibration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                NetworkMonitorService.LEGACY_CHANNEL_ID,
                "Legacy",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        NetworkMonitorService.ensureNotificationChannel(context)

        assertNull(manager.getNotificationChannel(NetworkMonitorService.LEGACY_CHANNEL_ID))
        val channel = manager.getNotificationChannel(NetworkMonitorService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(NetworkMonitorService.isChannelImportanceTooLow(context))
    }

    @Test
    fun `NetworkMonitorService flags status bar suppression when channel importance is lowered`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NetworkMonitorService.CHANNEL_ID,
                "Speed Icon",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        assertTrue(NetworkMonitorService.areNotificationsBlocked(context))
    }

    @Test
    fun `StatusBarIconRenderer renders valid alpha bitmaps for all speed styles`() {
        val telemetry = LiveNetworkTelemetry(
            downloadBytesPerSec = 1_540_000L,
            uploadBytesPerSec = 320_000L
        )
        StatusBarSpeedStyle.entries.forEach { style ->
            val bmp = StatusBarIconRenderer.renderSpeedBitmap(telemetry, style)
            assertEquals(96, bmp.width)
            assertEquals(96, bmp.height)
            assertTrue(bmp.hasAlpha())
        }
    }

    @Test
    fun `BackgroundPowerHelper builds battery optimization intents and manifest permission is declared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val direct = BackgroundPowerHelper.buildDirectBatteryOptimizationIntent(context)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, direct.action)
        assertEquals("package:${context.packageName}", direct.dataString)

        val fallback = BackgroundPowerHelper.buildBatteryOptimizationListIntent()
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, fallback.action)

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
    }

    @Test
    fun `FirewallSettingsSheet moves Status Bar Speed Icon, Exclude from Recents and Background & Battery into Background & System menu`() {
        var currentPrefs by mutableStateOf(FirewallPreferencesState())

        composeRule.setContent {
            NetGateTheme(themeMode = currentPrefs.themeMode, dynamicColor = false) {
                FirewallSettingsSheet(
                    preferences = currentPrefs,
                    onThemeModeChange = {},
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
                    onToggleBlurAnimation = {},
                    onToggleShowSystemApps = {},
                    onToggleShowOfflineApps = {},
                    onToggleStatusBarSpeed = { enabled ->
                        currentPrefs = currentPrefs.copy(isStatusBarSpeedEnabled = enabled)
                    },
                    onToggleDeepMonitor = {},
                    onToggleExcludeFromRecents = { enabled ->
                        currentPrefs = currentPrefs.copy(excludeFromRecents = enabled)
                    },
                    onClearAllRules = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_open_background_system")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("background_system_settings_view").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_status_bar_speed_header")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_status_bar_speed_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(currentPrefs.isStatusBarSpeedEnabled)

        composeRule.onNodeWithTag("settings_exclude_from_recents_header")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_exclude_from_recents_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(currentPrefs.excludeFromRecents)

        composeRule.onNodeWithTag("settings_battery_saver_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_battery_saver_header")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_battery_optimization_button")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("background_system_back_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_open_background_system")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `NetGateWidgetProvider toggles DPI, AdBlock, and Hotspot preferences cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferences(context)

        // Reset clean state
        prefs.setDpiBypassMode(DpiBypassMode.OFF)
        prefs.setAdBlockerEnabled(false)
        prefs.setHotspotProxyEnabled(false)

        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_DPI)
        assertEquals(DpiBypassMode.AUTOPILOT, prefs.preferencesFlow.first().dpiBypassMode)

        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_ADBLOCK)
        assertTrue(prefs.preferencesFlow.first().isAdBlockerEnabled)

        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_HOTSPOT)
        assertTrue(prefs.preferencesFlow.first().hotspotProxyEnabled)

        // Toggle back off
        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_DPI)
        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_ADBLOCK)
        NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_HOTSPOT)

        val finalState = prefs.preferencesFlow.first()
        assertEquals(DpiBypassMode.OFF, finalState.dpiBypassMode)
        assertFalse(finalState.isAdBlockerEnabled)
        assertFalse(finalState.hotspotProxyEnabled)
    }

    @Test
    fun `NetGateWidgetProvider buildRemoteViews inflates and updates all 4 tile states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FirewallPreferencesState(
            isFirewallEnabled = true,
            dpiBypassMode = DpiBypassMode.AUTOPILOT,
            isAdBlockerEnabled = true,
            hotspotProxyEnabled = true
        )

        val remoteViews = NetGateWidgetProvider.buildRemoteViews(
            context = context,
            prefs = prefs,
            vpnRunning = true,
            hotspotRunning = true
        )
        val container = FrameLayout(context)
        val inflated = remoteViews.apply(context, container)

        assertEquals(
            "Protected • 4 ON",
            inflated.findViewById<TextView>(R.id.widget_status_pill).text.toString()
        )
        assertEquals("Active", inflated.findViewById<TextView>(R.id.widget_state_vpn).text.toString())
        assertEquals("Autopilot", inflated.findViewById<TextView>(R.id.widget_state_dpi).text.toString())
        assertEquals("On", inflated.findViewById<TextView>(R.id.widget_state_adblock).text.toString())
        assertEquals("Sharing", inflated.findViewById<TextView>(R.id.widget_state_hotspot).text.toString())
    }

    @Test
    fun `NetGateWidgetProvider opens MainActivity for VPN consent when VpnService prepare returns intent`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = FirewallPreferences(context)
        prefs.setFirewallEnabled(false)

        if (VpnService.prepare(context) != null) {
            NetGateWidgetProvider.handleWidgetAction(context, NetGateWidgetProvider.ACTION_TOGGLE_VPN)
            val startedIntent = Shadows.shadowOf(context).nextStartedActivity
            assertNotNull(startedIntent)
            assertEquals(MainActivity::class.java.name, startedIntent.component?.className)
            assertTrue(startedIntent.getBooleanExtra(MainActivity.EXTRA_REQUEST_VPN_CONSENT, false))
        }
    }

    @Test
    fun `BuildConfig and BundledChangelog reflect v3_9_5 release metadata`() {
        assertEquals("3.9.5", BuildConfig.VERSION_NAME)
        assertEquals(17, BuildConfig.VERSION_CODE)
        assertEquals("NetGate 3.9.5", BundledChangelog.entries.first().versionLabel)
    }
}
