package com.aistudio.netgate

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.ThemeMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.ui.components.BottomNavConfigManager
import com.aistudio.netgate.ui.components.BottomNavTabId
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.FrostedBackdrop
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.components.GlassTabItem
import com.aistudio.netgate.ui.components.GlassTabVariant
import com.aistudio.netgate.ui.components.GlassTabs
import com.aistudio.netgate.ui.components.LocalBottomNavConfig
import com.aistudio.netgate.ui.theme.GlassBlurStyle
import com.aistudio.netgate.ui.theme.LocalBackdropLiquidState
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalOverlayLiquidState
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.NetGateTheme
import com.aistudio.netgate.ui.theme.useBlurStyle
import com.aistudio.netgate.ui.theme.wcagContrastRatio
import io.github.fletchmckee.liquid.LiquidState
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
@Config(sdk = [35])
class ThemeStyleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme styles persist cleanly in FirewallPreferences`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = FirewallPreferences(context)

        assertEquals(NetGateThemeStyle.NETGATE_DEFAULT, prefs.preferencesFlow.first().themeStyle)

        prefs.setThemeStyle(NetGateThemeStyle.LIQUID_GLASS)
        assertEquals(NetGateThemeStyle.LIQUID_GLASS, prefs.preferencesFlow.first().themeStyle)

        prefs.setThemeStyle(NetGateThemeStyle.MATERIAL_3_EXPRESSIVE)
        assertEquals(NetGateThemeStyle.MATERIAL_3_EXPRESSIVE, prefs.preferencesFlow.first().themeStyle)

        prefs.setThemeStyle(NetGateThemeStyle.MATERIAL_YOU_LITE)
        assertEquals(NetGateThemeStyle.MATERIAL_YOU_LITE, prefs.preferencesFlow.first().themeStyle)
    }

    @Test
    fun `NetGateTheme provides style motion spec and renders surfaces in Light and Dark without shader crash`() {
        var activeStyle by mutableStateOf(NetGateThemeStyle.LIQUID_GLASS)
        var activeMode by mutableStateOf(ThemeMode.DARK)
        var capturedRefraction = false
        var capturedPrismEdges = false
        var capturedLiquidTouch = false
        var capturedStyle = NetGateThemeStyle.NETGATE_DEFAULT
        var capturedBackdropLiquid: LiquidState? = null
        var capturedOverlayLiquid: LiquidState? = null

        composeRule.setContent {
            NetGateTheme(
                themeMode = activeMode,
                themeStyle = activeStyle,
                dynamicColor = false
            ) {
                val motion = LocalExpressiveMotion.current
                capturedStyle = LocalThemeStyle.current
                capturedRefraction = motion.enableRefractionShader
                capturedPrismEdges = motion.enablePrismEdges
                capturedLiquidTouch = motion.enableLiquidTouchPhysics

                FrostedBackdrop(isFirewallActive = true) {
                    capturedBackdropLiquid = LocalBackdropLiquidState.current
                    capturedOverlayLiquid = LocalOverlayLiquidState.current
                    FrostedSurface(
                        enableLiquidTouch = true,
                        modifier = Modifier.testTag("test_frosted_surface")
                    ) {
                        Text(
                            text = capturedStyle.badgeLabel,
                            modifier = Modifier.testTag("test_badge_label")
                        )
                    }
                    FloatingFrostedNavBar(
                        currentDestination = com.aistudio.netgate.data.model.AppDestination.FIREWALL,
                        isConnectionInfoOpen = false,
                        onSelectFirewall = {},
                        onSelectWebsites = {},
                        onSelectDataUsage = {},
                        onSelectDnsProtection = {},
                        onSelectHotspot = {},
                        onSelectVpnGuard = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.LIQUID_GLASS, capturedStyle)
        assertTrue(capturedRefraction)
        assertTrue(capturedPrismEdges)
        assertTrue(capturedLiquidTouch)
        assertNotNull(capturedBackdropLiquid)
        assertNotNull(capturedOverlayLiquid)
        composeRule.onNodeWithTag("test_badge_label").assertTextEquals("Refractive")

        activeStyle = NetGateThemeStyle.MATERIAL_3_EXPRESSIVE
        activeMode = ThemeMode.LIGHT
        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.MATERIAL_3_EXPRESSIVE, capturedStyle)
        assertFalse(capturedRefraction)
        assertTrue(capturedLiquidTouch)
        assertNull(capturedBackdropLiquid)
        assertNull(capturedOverlayLiquid)
        composeRule.onNodeWithTag("test_badge_label").assertTextEquals("Bouncy")

        activeStyle = NetGateThemeStyle.MATERIAL_YOU_LITE
        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.MATERIAL_YOU_LITE, capturedStyle)
        assertFalse(capturedRefraction)
        assertFalse(capturedLiquidTouch)
        assertNull(capturedBackdropLiquid)
        assertNull(capturedOverlayLiquid)
        composeRule.onNodeWithTag("test_badge_label").assertTextEquals("Lite")
    }

    @Test
    fun `FirewallSettingsSheet allows switching to Liquid Glass Material 3 Expressive and Material You Lite`() {
        var currentPrefs by mutableStateOf(FirewallPreferencesState())

        composeRule.setContent {
            NetGateTheme(
                themeMode = currentPrefs.themeMode,
                themeStyle = currentPrefs.themeStyle,
                dynamicColor = false
            ) {
                FirewallSettingsSheet(
                    preferences = currentPrefs,
                    onThemeModeChange = { mode -> currentPrefs = currentPrefs.copy(themeMode = mode) },
                    onThemeStyleChange = { style -> currentPrefs = currentPrefs.copy(themeStyle = style) },
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
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

        composeRule.onNodeWithTag("settings_open_appearance").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("theme_style_settings_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("theme_style_preview_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_style_accordion_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("theme_style_liquid_glass").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.LIQUID_GLASS, currentPrefs.themeStyle)

        composeRule.onNodeWithTag("theme_style_material_3_expressive").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.MATERIAL_3_EXPRESSIVE, currentPrefs.themeStyle)

        composeRule.onNodeWithTag("theme_mode_accordion_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_mode_dark").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(com.aistudio.netgate.data.model.ThemeMode.DARK, currentPrefs.themeMode)

        composeRule.onNodeWithTag("theme_style_material_you_lite").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(NetGateThemeStyle.MATERIAL_YOU_LITE, currentPrefs.themeStyle)

        composeRule.onNodeWithTag("settings_open_appearance").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_open_appearance").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `GlassTabs reacts to every BlurEngineStyle via useBlurStyle and preserves WCAG contrast`() {
        var blurStyle by mutableStateOf(BlurEngineStyle.OFF)
        var themeMode by mutableStateOf(ThemeMode.DARK)
        var selectedIndex by mutableStateOf(0)
        var capturedBlur: GlassBlurStyle? = null

        composeRule.setContent {
            NetGateTheme(
                themeMode = themeMode,
                blurEngineStyle = blurStyle,
                blurIntensity = 1.0f,
                dynamicColor = false
            ) {
                capturedBlur = useBlurStyle()
                GlassTabs(
                    items = listOf(
                        GlassTabItem(label = "All Apps", testTag = "test_tab_apps"),
                        GlassTabItem(label = "Websites", testTag = "test_tab_sites")
                    ),
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    variant = GlassTabVariant.TOP_SEGMENTED
                )
            }
        }

        BlurEngineStyle.entries.forEach { style ->
            listOf(ThemeMode.DARK, ThemeMode.LIGHT).forEach { mode ->
                blurStyle = style
                themeMode = mode
                composeRule.waitForIdle()
                val snapshot = capturedBlur
                assertNotNull(snapshot)
                assertEquals(style, snapshot!!.blurStyle)
                assertEquals(style != BlurEngineStyle.OFF, snapshot.isBlurActive)
                val activeContrast = wcagContrastRatio(snapshot.activeContentColor, snapshot.indicatorColor)
                val inactiveContrast = wcagContrastRatio(snapshot.inactiveContentColor, snapshot.containerColor)
                assertTrue("Active contrast $activeContrast < 4.5 for $style / $mode", activeContrast >= 4.5f)
                assertTrue("Inactive contrast $inactiveContrast < 4.5 for $style / $mode", inactiveContrast >= 4.5f)
            }
        }

        composeRule.onNodeWithTag("test_tab_sites").performClick()
        composeRule.waitForIdle()
        assertEquals(1, selectedIndex)
    }

    @Test
    fun `BottomNav shows all enabled tabs directly on scrollable bar`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        BottomNavConfigManager.init(context)
        BottomNavConfigManager.resetToDefaults(context)

        var selectedDestination by mutableStateOf(AppDestination.FIREWALL)
        var clickedSniDpi by mutableStateOf(false)
        var clickedWifi by mutableStateOf(false)
        var clickedSpeedTest by mutableStateOf(false)

        composeRule.setContent {
            val navConfig by BottomNavConfigManager.configFlow.collectAsState()
            NetGateTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBottomNavConfig provides navConfig) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FloatingFrostedNavBar(
                            currentDestination = selectedDestination,
                            isConnectionInfoOpen = false,
                            onSelectFirewall = { selectedDestination = AppDestination.FIREWALL },
                            onSelectWebsites = { selectedDestination = AppDestination.WEBSITES },
                            onSelectDataUsage = { selectedDestination = AppDestination.DATA_USAGE },
                            onSelectDnsProtection = { selectedDestination = AppDestination.DNS },
                            onSelectHotspot = { selectedDestination = AppDestination.HOTSPOT },
                            onSelectVpnGuard = { selectedDestination = AppDestination.VPN_GUARD },
                            onSelectSniDpi = { clickedSniDpi = true },
                            onSelectWifi = {
                                clickedWifi = true
                                selectedDestination = AppDestination.DATA_USAGE
                            },
                            onSelectSpeedTest = {
                                clickedSpeedTest = true
                                selectedDestination = AppDestination.DATA_USAGE
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_tab_firewall").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_sni_dpi").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(clickedSniDpi)

        composeRule.onNodeWithTag("nav_tab_wifi").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(clickedWifi)

        composeRule.onNodeWithTag("nav_tab_speed_test").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(clickedSpeedTest)

        assertEquals(BottomNavTabId.entries.size, BottomNavConfigManager.configFlow.value.activeOrderedTabs.size)
        assertTrue(BottomNavConfigManager.toggleTabVisibility(context, BottomNavTabId.USAGE))
        assertFalse(BottomNavTabId.USAGE in BottomNavConfigManager.configFlow.value.activeOrderedTabs)
        assertTrue(BottomNavConfigManager.toggleTabVisibility(context, BottomNavTabId.USAGE))
        assertTrue(BottomNavTabId.USAGE in BottomNavConfigManager.configFlow.value.activeOrderedTabs)

        BottomNavConfigManager.resetToDefaults(context)
        assertEquals(BottomNavTabId.entries.toSet(), BottomNavConfigManager.configFlow.value.visibleTabIds)
    }
}
