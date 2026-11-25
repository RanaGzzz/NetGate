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
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.FrostedBackdrop
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.theme.LocalBackdropLiquidState
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalOverlayLiquidState
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.NetGateTheme
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

        composeRule.onNodeWithTag("appearance_back_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_open_appearance").performScrollTo().assertIsDisplayed()
    }
}
