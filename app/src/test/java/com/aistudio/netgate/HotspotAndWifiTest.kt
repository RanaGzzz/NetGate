package com.aistudio.netgate

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.HotspotClientInfo
import com.aistudio.netgate.data.model.HotspotProxyRuntimeState
import com.aistudio.netgate.data.model.WifiIdleStatus
import com.aistudio.netgate.service.HotspotPolicyEvaluator
import com.aistudio.netgate.service.WifiIdleStateMachine
import com.aistudio.netgate.ui.components.HotspotAndWifiContent
import com.aistudio.netgate.ui.theme.NetGateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HotspotAndWifiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `hotspot policy evaluator enforces manual block and per client data caps`() {
        val blockedIps = setOf("192.168.43.42")
        val caps = mapOf(
            "192.168.43.15" to 100L * 1024L * 1024L, // 100 MB cap
            "192.168.43.20" to 0L // Unlimited
        )

        // Manually blocked client
        assertEquals(
            "Blocked by NetGate Hotspot Firewall",
            HotspotPolicyEvaluator.evaluateClientBlockReason(
                clientIp = "192.168.43.42",
                blockedIps = blockedIps,
                clientCaps = caps,
                bytesSent = 1024L,
                bytesReceived = 2048L
            )
        )

        // Client under 100 MB cap is allowed
        assertNull(
            HotspotPolicyEvaluator.evaluateClientBlockReason(
                clientIp = "192.168.43.15",
                blockedIps = blockedIps,
                clientCaps = caps,
                bytesSent = 20L * 1024L * 1024L,
                bytesReceived = 40L * 1024L * 1024L
            )
        )

        // Client at or above 100 MB cap is blocked
        assertEquals(
            "Hotspot Data Cap Reached",
            HotspotPolicyEvaluator.evaluateClientBlockReason(
                clientIp = "192.168.43.15",
                blockedIps = blockedIps,
                clientCaps = caps,
                bytesSent = 30L * 1024L * 1024L,
                bytesReceived = 70L * 1024L * 1024L
            )
        )

        // Unlimited client is allowed regardless of bytes
        assertNull(
            HotspotPolicyEvaluator.evaluateClientBlockReason(
                clientIp = "192.168.43.20",
                blockedIps = blockedIps,
                clientCaps = caps,
                bytesSent = 500L * 1024L * 1024L,
                bytesReceived = 900L * 1024L * 1024L
            )
        )
    }

    @Test
    fun `hotspot interface matcher recognizes softap and tethering interfaces`() {
        assertTrue(HotspotPolicyEvaluator.isHotspotInterfaceName("ap0"))
        assertTrue(HotspotPolicyEvaluator.isHotspotInterfaceName("wlan1"))
        assertTrue(HotspotPolicyEvaluator.isHotspotInterfaceName("swlan0"))
        assertTrue(HotspotPolicyEvaluator.isHotspotInterfaceName("rndis0"))
        assertTrue(HotspotPolicyEvaluator.isHotspotInterfaceName("softap0"))

        assertFalse(HotspotPolicyEvaluator.isHotspotInterfaceName("wlan0"))
        assertFalse(HotspotPolicyEvaluator.isHotspotInterfaceName("eth0"))
        assertFalse(HotspotPolicyEvaluator.isHotspotInterfaceName("lo"))
        assertFalse(HotspotPolicyEvaluator.isHotspotInterfaceName(null))
    }

    @Test
    fun `wifi idle state machine accumulates idle time resets on burst and triggers at threshold`() {
        val sm = WifiIdleStateMachine(idleBytesPerSecThreshold = 2048L)

        // Tick 1: 30s idle (500 B/s < 2048 B/s) -> 30s / 120s accumulated
        val t1 = sm.onTick(
            isWifiActive = true,
            isAlreadyLocked = false,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 500L,
            deltaMillis = 30_000L
        )
        assertTrue(t1.isTracking)
        assertEquals(30_000L, t1.idleElapsedMillis)
        assertEquals(120_000L, t1.thresholdMillis)
        assertFalse(t1.shouldTriggerLock)

        // Tick 2: Traffic burst (5000 B/s >= 2048 B/s) resets accumulated counter
        val t2 = sm.onTick(
            isWifiActive = true,
            isAlreadyLocked = false,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 5_000L,
            deltaMillis = 15_000L
        )
        assertTrue(t2.isTracking)
        assertEquals(0L, t2.idleElapsedMillis)
        assertFalse(t2.shouldTriggerLock)

        // Tick 3 & 4: Two 60s idle ticks reach 2m threshold and trigger lock
        val t3 = sm.onTick(
            isWifiActive = true,
            isAlreadyLocked = false,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 0L,
            deltaMillis = 60_000L
        )
        assertEquals(60_000L, t3.idleElapsedMillis)
        assertFalse(t3.shouldTriggerLock)

        val t4 = sm.onTick(
            isWifiActive = true,
            isAlreadyLocked = false,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 1024L,
            deltaMillis = 60_000L
        )
        assertTrue(t4.shouldTriggerLock)
        assertEquals(120_000L, t4.idleElapsedMillis)

        // Stops tracking when already locked or off Wi-Fi
        val t5 = sm.onTick(
            isWifiActive = true,
            isAlreadyLocked = true,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 0L,
            deltaMillis = 60_000L
        )
        assertFalse(t5.isTracking)
        assertEquals(0L, t5.idleElapsedMillis)
        assertFalse(t5.shouldTriggerLock)

        val t6 = sm.onTick(
            isWifiActive = false,
            isAlreadyLocked = false,
            autoLockEnabled = true,
            thresholdMinutes = 2,
            totalBytesPerSec = 0L,
            deltaMillis = 60_000L
        )
        assertFalse(t6.isTracking)
        assertFalse(t6.shouldTriggerLock)
    }

    @Test
    fun `hotspot and wifi automation preferences persist reliably in datastore`() = runTest {
        val prefs = FirewallPreferences(context)

        prefs.setHotspotProxyEnabled(true)
        prefs.setHotspotProxyPort(8990)
        prefs.setHotspotApplyNetGateFilters(false)
        prefs.setHotspotClientBlocked("192.168.43.88", true)
        prefs.setHotspotClientDataCap("192.168.43.88", 500L * 1024L * 1024L)
        prefs.setHotspotClientLabel("192.168.43.88", "Work Laptop")

        prefs.setGlobalWifiBlocked(true)
        prefs.setGlobalWifiTimer(expiresAtMillis = 1_800_000_000_000L, durationMillis = 900_000L)
        prefs.setAutoBlockWifiOnIdle(true)
        prefs.setAutoBlockWifiIdleMinutes(15)
        prefs.setAutoBlockWifiOpenPanelNotify(false)

        val state = prefs.preferencesFlow.first()
        assertTrue(state.hotspotProxyEnabled)
        assertEquals(8990, state.hotspotProxyPort)
        assertFalse(state.hotspotApplyNetGateFilters)
        assertTrue("192.168.43.88" in state.hotspotBlockedClientIps)
        assertEquals(500L * 1024L * 1024L, state.hotspotClientCaps["192.168.43.88"])
        assertEquals("Work Laptop", state.hotspotClientLabels["192.168.43.88"])

        assertTrue(state.globalWifiBlocked)
        assertEquals(1_800_000_000_000L, state.globalWifiTimerExpiresAtMillis)
        assertEquals(900_000L, state.globalWifiTimerDurationMillis)
        assertTrue(state.autoBlockWifiOnIdleEnabled)
        assertEquals(15, state.autoBlockWifiIdleMinutes)
        assertFalse(state.autoBlockWifiOpenPanelNotify)

        // Clear client block & label
        prefs.setHotspotClientBlocked("192.168.43.88", false)
        prefs.setHotspotClientLabel("192.168.43.88", "")
        prefs.clearGlobalWifiTimer()
        val updated = prefs.preferencesFlow.first()
        assertFalse("192.168.43.88" in updated.hotspotBlockedClientIps)
        assertNull(updated.hotspotClientLabels["192.168.43.88"])
        assertEquals(0L, updated.globalWifiTimerExpiresAtMillis)
    }

    @Test
    fun `hotspot and wifi content renders proxy endpoint clients wifi timer and idle lock controls`() {
        var toggledClientIp: String? = null
        var toggledClientBlocked: Boolean? = null
        var scheduledMinutes: Int? = null
        var toggledIdleLock: Boolean? = null

        val sampleClients = listOf(
            HotspotClientInfo(
                clientIp = "192.168.43.105",
                label = "MacBook Pro",
                isManuallyBlocked = false,
                dataCapBytes = 500L * 1024L * 1024L,
                bytesSent = 25L * 1024L * 1024L,
                bytesReceived = 120L * 1024L * 1024L,
                requestCount = 412,
                blockedCount = 18,
                lastHost = "github.com",
                lastSeenAt = 1_700_000_000_000L
            ),
            HotspotClientInfo(
                clientIp = "192.168.43.210",
                label = "",
                isManuallyBlocked = true,
                dataCapBytes = 0L,
                bytesSent = 5L * 1024L * 1024L,
                bytesReceived = 45L * 1024L * 1024L,
                requestCount = 94,
                blockedCount = 7,
                lastHost = "ads.example.com",
                lastSeenAt = 1_700_000_000_000L
            )
        )

        val prefs = FirewallPreferencesState(
            hotspotProxyEnabled = true,
            hotspotProxyPort = 8890,
            hotspotApplyNetGateFilters = true,
            globalWifiBlocked = false,
            globalWifiTimerExpiresAtMillis = 0L,
            autoBlockWifiOnIdleEnabled = true,
            autoBlockWifiIdleMinutes = 10
        )

        val hotspotState = HotspotProxyRuntimeState(
            isProxyRunning = true,
            isHotspotInterfaceActive = true,
            detectedHotspotIp = "192.168.43.1",
            detectedHotspotInterface = "ap0",
            isExternalVpnActive = true,
            isNetGateVpnActive = false,
            activePort = 8890,
            totalRelayedSentBytes = 30L * 1024L * 1024L,
            totalRelayedRecvBytes = 165L * 1024L * 1024L,
            osTetheringBytes = 240L * 1024L * 1024L,
            clients = sampleClients
        )

        val idleStatus = WifiIdleStatus(
            isTracking = true,
            idleElapsedMillis = 180_000L,
            thresholdMillis = 600_000L
        )

        composeRule.setContent {
            NetGateTheme {
                HotspotAndWifiContent(
                    preferences = prefs,
                    hotspotState = hotspotState,
                    connectionDetails = ConnectionDetails(activeNetworkType = ActiveNetworkType.WIFI),
                    idleStatus = idleStatus,
                    nowMillis = 1_700_000_000_000L,
                    onToggleHotspotProxy = {},
                    onSelectHotspotPort = {},
                    onToggleApplyNetGateFilters = {},
                    onToggleClientBlocked = { ip, blocked ->
                        toggledClientIp = ip
                        toggledClientBlocked = blocked
                    },
                    onSetClientDataCap = { _, _ -> },
                    onSetClientLabel = { _, _ -> },
                    onResetClientStats = {},
                    onClearAllHotspotStats = {},
                    onRefreshHotspot = {},
                    onCopiedEndpoint = {},
                    onToggleGlobalWifiLock = {},
                    onScheduleWifiTimer = { minutes -> scheduledMinutes = minutes },
                    onCancelWifiTimer = {},
                    onToggleAutoIdleLock = { enabled -> toggledIdleLock = enabled },
                    onSelectIdleMinutes = {},
                    onToggleOpenPanelNotify = {}
                )
            }
        }

        composeRule.onNodeWithTag("hotspot_proxy_card").assertIsDisplayed()
        composeRule.onNodeWithText("Hotspot Proxy Sharing").assertIsDisplayed()
        composeRule.onNodeWithTag("hotspot_endpoint_pill").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.43.1:8890").assertIsDisplayed()

        composeRule.onNodeWithTag("hotspot_clients_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hotspot_client_row_192.168.43.105").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MacBook Pro").assertIsDisplayed()

        composeRule.onNodeWithTag("hotspot_client_toggle_192.168.43.105").performScrollTo().performClick()
        assertEquals("192.168.43.105", toggledClientIp)
        assertEquals(true, toggledClientBlocked)

        composeRule.onNodeWithTag("wifi_automation_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Global Wi-Fi Traffic Lock").assertIsDisplayed()

        composeRule.onNodeWithTag("wifi_timer_preset_15").performScrollTo().performClick()
        assertEquals(15, scheduledMinutes)

        composeRule.onNodeWithTag("wifi_idle_auto_switch").performScrollTo().performClick()
        assertNotNull(toggledIdleLock)
        assertFalse(toggledIdleLock!!)
    }
}
