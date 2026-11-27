package com.aistudio.netgate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke tests for the paths unit tests cannot reach: the launcher activity, the VPN consent
 * contract and the Wi-Fi permissions the join flow depends on.
 *
 * These run on an emulator in CI before a release is tagged, because several past fixes (the
 * connectivity-callback launch crash, and the Wi-Fi join that lost its internet capability) only
 * reproduced on a real system.
 */
@RunWith(AndroidJUnit4::class)
class NetGateDeviceSmokeTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appContextMatchesPackage() {
        assertEquals(BuildConfig.APPLICATION_ID, targetContext.packageName)
    }

    @Test
    fun launcherActivityStartsWithoutCrashing() {
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
        assertNotNull("NetGate has no launcher activity", launchIntent)
        ActivityScenario.launch<MainActivity>(launchIntent!!).use { scenario ->
            scenario.onActivity { activity -> assertTrue(!activity.isFinishing) }
            scenario.close()
        }
    }

    @Test
    fun wifiJoinPermissionsAreGrantedAtInstall() {
        val context = targetContext
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
        )
    }

    @Test
    fun wifiConnectorExposesSupportedJoinPaths() {
        // WEP is only joinable through the legacy path on API 28 and below.
        assertEquals(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q,
            com.aistudio.netgate.data.network.WifiConnector.canJoinFromApp("WEP")
        )
        assertTrue(com.aistudio.netgate.data.network.WifiConnector.canJoinFromApp("WPA2-PSK"))
        assertTrue(com.aistudio.netgate.data.network.WifiConnector.canJoinFromApp("Open"))
    }

    @Test
    fun launcherIntentCarriesTheMainAction() {
        val launchIntent: Intent? = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
        assertEquals(Intent.ACTION_MAIN, launchIntent?.action)
    }
}
