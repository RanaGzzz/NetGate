package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.model.ActiveNetworkType
import com.example.data.model.BatchAction
import com.example.data.model.ManagedApp
import com.example.data.repository.FirewallRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: NetGateDatabase
    private lateinit var repository: FirewallRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NetGateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FirewallRepository(
            context = context,
            ruleDao = database.appNetworkRuleDao(),
            trafficUsageDao = database.appTrafficUsageDao(),
            preferences = FirewallPreferences(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches NetGate`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("NetGate", appName)
    }

    @Test
    fun `batch actions block and restore wifi and mobile data`() = runTest {
        val targets = setOf("com.social.app", "com.video.app", "com.game.app")

        repository.applyBatchAction(targets, BatchAction.BLOCK_WIFI)
        var rules = repository.getAllRulesOnce().associateBy { it.packageName }
        assertEquals(3, rules.size)
        targets.forEach { pkg ->
            assertTrue(rules[pkg]!!.blockWifi)
            assertFalse(rules[pkg]!!.blockMobileData)
        }

        repository.applyBatchAction(setOf("com.video.app"), BatchAction.BLOCK_DATA)
        rules = repository.getAllRulesOnce().associateBy { it.packageName }
        assertTrue(rules["com.video.app"]!!.blockWifi)
        assertTrue(rules["com.video.app"]!!.blockMobileData)

        repository.applyBatchAction(setOf("com.social.app", "com.video.app"), BatchAction.ALLOW_ALL)
        rules = repository.getAllRulesOnce().associateBy { it.packageName }
        assertEquals(1, rules.size)
        assertTrue(rules.containsKey("com.game.app"))
    }

    @Test
    fun `managed app evaluates active network blocking accurately`() {
        val wifiOnlyBlocked = ManagedApp(
            packageName = "com.example.stream",
            appName = "Stream",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = true,
            blockMobileData = false
        )

        assertTrue(wifiOnlyBlocked.isBlockedOn(ActiveNetworkType.WIFI))
        assertFalse(wifiOnlyBlocked.isBlockedOn(ActiveNetworkType.MOBILE_DATA))
        assertFalse(wifiOnlyBlocked.isBlockedOn(ActiveNetworkType.NONE))
    }

    @Test
    fun `room join aggregates total data consumed by blocked versus allowed apps`() = runTest {
        val usageDao = database.appTrafficUsageDao()
        val now = 1_700_000_000_000L

        usageDao.replaceSnapshot(
            listOf(
                com.example.data.local.AppTrafficUsageEntity(
                    packageName = "com.video.app",
                    wifiBytes = 600_000_000L,
                    mobileBytes = 150_000_000L,
                    periodDays = 7,
                    syncedAt = now
                ),
                com.example.data.local.AppTrafficUsageEntity(
                    packageName = "com.social.app",
                    wifiBytes = 100_000_000L,
                    mobileBytes = 50_000_000L,
                    periodDays = 7,
                    syncedAt = now
                ),
                com.example.data.local.AppTrafficUsageEntity(
                    packageName = "com.browser.app",
                    wifiBytes = 400_000_000L,
                    mobileBytes = 100_000_000L,
                    periodDays = 7,
                    syncedAt = now
                )
            )
        )

        // Block com.video.app on Wi-Fi -> 750 MB blocked, 650 MB allowed
        repository.setAppRule("com.video.app", blockWifi = true, blockMobileData = false)

        val summaryAfterOneBlock = repository.trafficSummaryFlow.first()
        assertEquals(750_000_000L, summaryAfterOneBlock.blockedTotalBytes)
        assertEquals(600_000_000L, summaryAfterOneBlock.blockedWifiBytes)
        assertEquals(150_000_000L, summaryAfterOneBlock.blockedMobileBytes)
        assertEquals(650_000_000L, summaryAfterOneBlock.allowedTotalBytes)
        assertEquals(1, summaryAfterOneBlock.blockedAppsCount)
        assertEquals(2, summaryAfterOneBlock.allowedAppsCount)

        // Also block com.social.app on Data -> 900 MB blocked, 500 MB allowed
        repository.setAppRule("com.social.app", blockWifi = false, blockMobileData = true)

        val summaryAfterTwoBlocks = repository.trafficSummaryFlow.first()
        assertEquals(900_000_000L, summaryAfterTwoBlocks.blockedTotalBytes)
        assertEquals(500_000_000L, summaryAfterTwoBlocks.allowedTotalBytes)
        assertEquals(2, summaryAfterTwoBlocks.blockedAppsCount)
        assertEquals(1, summaryAfterTwoBlocks.allowedAppsCount)
    }
}
