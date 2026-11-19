package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.StartupSplashOverlay
import com.example.ui.theme.NetGateTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupSplashTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `startup splash overlay displays NetGate branding and dismisses on skip`() {
        var dismissed = false

        composeRule.setContent {
            NetGateTheme {
                StartupSplashOverlay(
                    isFirewallActive = true,
                    onDismissed = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("startup_splash_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("NetGate").assertIsDisplayed()
        composeRule.onNodeWithText("Per-App Firewall • DNS & Ad Shield").assertIsDisplayed()

        composeRule.onNodeWithTag("startup_skip_button").performClick()
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }
}
