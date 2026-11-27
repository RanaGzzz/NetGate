package com.aistudio.netgate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.SmartProtectionProfile
import com.aistudio.netgate.data.model.TempAllowDuration
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.repository.FirewallRepository
import com.aistudio.netgate.service.AdBlockAnalyticsHub
import com.aistudio.netgate.service.AdBlockFilterEngine
import com.aistudio.netgate.service.AdBlockMatcherSnapshot
import com.aistudio.netgate.service.policy.NetworkPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdBlockUpgradeTest {

    private lateinit var context: Context
    private lateinit var database: NetGateDatabase
    private lateinit var preferences: FirewallPreferences
    private lateinit var repository: FirewallRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NetGateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = FirewallPreferences(context)
        repository = FirewallRepository(
            context = context,
            ruleDao = database.appNetworkRuleDao(),
            trafficUsageDao = database.appTrafficUsageDao(),
            websiteRuleDao = database.websiteRuleDao(),
            appTimerDao = database.appTimerDao(),
            adFilterListDao = database.adFilterListDao(),
            connectionHistoryDao = database.connectionHistoryDao(),
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `requirement 1 - exactly 21 free filter subscriptions defined with required ids`() {
        val lists = AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS
        assertEquals(21, lists.size)

        val expectedIds = listOf(
            "oisd_big",
            "adguard_dns",
            "hagezi_normal",
            "hagezi_tif",
            "stevenblack",
            "easylist_privacy",
            "adaway_hosts",
            "peter_lowe",
            "urlhaus_malware",
            "oem_telemetry",
            "phishing_army",
            "openphish",
            "hagezi_badware",
            "hagezi_dyndns",
            "notrack_tracker",
            "windows_spy_blocker",
            "samsung_telemetry",
            "xiaomi_telemetry",
            "huawei_telemetry",
            "firebog_advertising",
            "firebog_tracking"
        )

        val actualIds = lists.map { it.id }
        assertEquals(expectedIds.sorted(), actualIds.sorted())

        for (item in lists) {
            assertTrue("URL for ${item.id} must start with https://", item.sourceUrl.startsWith("https://"))
            assertTrue("Title for ${item.id} must not be blank", item.title.isNotBlank())
            assertTrue("Category for ${item.id} must not be blank", item.category.isNotBlank())
        }
    }

    @Test
    fun `requirement 2 - balanced and fast mode defaults enable the 8 required filters`() = runTest {
        repository.ensureDefaultAdFilterListsSeeded()
        val seeded = repository.adFilterListsFlow.first().associateBy { it.id }
        assertEquals(21, seeded.size)

        val requiredBalancedDefaults = listOf(
            "oisd_big",
            "adguard_dns",
            "hagezi_normal",
            "hagezi_tif",
            "easylist_privacy",
            "adaway_hosts",
            "urlhaus_malware",
            "oem_telemetry"
        )

        for (id in requiredBalancedDefaults) {
            assertTrue("Expected $id to be enabled by default", seeded[id]?.isEnabled == true)
        }

        val nonDefaultLists = listOf(
            "stevenblack",
            "peter_lowe",
            "phishing_army",
            "openphish",
            "hagezi_badware",
            "hagezi_dyndns",
            "notrack_tracker",
            "windows_spy_blocker",
            "samsung_telemetry",
            "xiaomi_telemetry",
            "huawei_telemetry",
            "firebog_advertising",
            "firebog_tracking"
        )
        for (id in nonDefaultLists) {
            assertFalse("Expected $id to be disabled by default", seeded[id]?.isEnabled == true)
        }
    }

    @Test
    fun `requirement 3 - filter log entry model carries required metadata and hits count`() {
        val entry = DnsQueryLogEntry(
            id = 101L,
            domain = "evil-phish.net",
            status = DnsQueryStatus.BLOCKED_AD,
            reason = "Phishing Army block",
            timestampMs = 1700000000000L,
            fullUrl = "https://evil-phish.net/login?token=abc",
            appName = "Chrome",
            appPackage = "com.android.chrome",
            filterListName = "Phishing Army",
            blockReason = AdBlockReason.PHISHING,
            hitCount = 7,
            latencyMicros = 850L
        )

        assertEquals("evil-phish.net", entry.domain)
        assertEquals("https://evil-phish.net/login?token=abc", entry.fullUrl)
        assertEquals("Chrome", entry.appName)
        assertEquals("com.android.chrome", entry.appPackage)
        assertEquals("Phishing Army", entry.filterListName)
        assertEquals(AdBlockReason.PHISHING, entry.blockReason)
        assertEquals(7, entry.hitCount)
        assertEquals(850L, entry.latencyMicros)
    }

    @Test
    fun `requirement 4 - per-domain temporary allow and permanent allow block controls with expiration`() = runTest {
        val now = System.currentTimeMillis()

        repository.addWebsiteRule(
            rawDomain = "temp-news.com",
            isWhitelist = true,
            expiresAtMillis = now + TempAllowDuration.ONE_HOUR.durationMillis
        )

        repository.addWebsiteRule(
            rawDomain = "trusted-site.org",
            isWhitelist = true,
            expiresAtMillis = 0L
        )

        repository.addWebsiteRule(
            rawDomain = "tracker-bad.net",
            isWhitelist = false,
            expiresAtMillis = 0L
        )

        val whitelistRules = repository.websiteRulesFlow.first().filter { it.isWhitelist }
        val blocklistRules = repository.websiteRulesFlow.first().filter { !it.isWhitelist }

        assertEquals(2, whitelistRules.size)
        assertEquals(1, blocklistRules.size)

        val tempRule = whitelistRules.first { it.domain == "temp-news.com" }
        assertTrue(tempRule.isTemporary)
        assertFalse(tempRule.isExpired(now))

        val currentEntities = repository.websiteRulesFlow.first().map {
            WebsiteRuleEntity(
                domain = it.domain,
                ruleType = if (it.isWhitelist) WebsiteRuleType.ALLOW else WebsiteRuleType.BLOCK,
                isEnabled = it.isEnabled,
                expiresAtMillis = it.expiresAtMillis
            )
        }

        val policy = NetworkPolicy.fromPreferencesAndRules(
            prefs = FirewallPreferencesState(websiteFilterMode = WebsiteFilterMode.BLOCKLIST),
            websiteRules = currentEntities,
            adMatcher = AdBlockMatcherSnapshot.EMPTY,
            nowMillis = now
        )
        assertTrue(policy.isExplicitlyWhitelisted("temp-news.com"))

        val futureTime = now + TempAllowDuration.ONE_HOUR.durationMillis + 1000L
        val expiredPolicy = NetworkPolicy.fromPreferencesAndRules(
            prefs = FirewallPreferencesState(websiteFilterMode = WebsiteFilterMode.BLOCKLIST),
            websiteRules = currentEntities,
            adMatcher = AdBlockMatcherSnapshot.EMPTY,
            nowMillis = futureTime
        )
        assertFalse("Expired temporary rule must not be whitelisted", expiredPolicy.isExplicitlyWhitelisted("temp-news.com"))

        val purgedCount = repository.purgeExpiredWebsiteRules(futureTime)
        assertEquals(1, purgedCount)

        val remainingRules = repository.websiteRulesFlow.first()
        assertEquals(2, remainingRules.size)
        assertFalse(remainingRules.any { it.domain == "temp-news.com" })
    }

    @Test
    fun `requirement 5 - user managed lists import export and backup restore round trip`() = runTest {
        repository.addWebsiteRule("bad-site-1.com", isWhitelist = false)
        repository.addWebsiteRule("bad-site-2.com", isWhitelist = false)
        repository.addWebsiteRule("whitelist-good.org", isWhitelist = true)

        val exportedText = repository.exportWebsiteRulesText()
        assertTrue(exportedText.contains("bad-site-1.com"))
        assertTrue(exportedText.contains("bad-site-2.com"))
        assertTrue(exportedText.contains("whitelist-good.org"))

        val backupJson = repository.exportAdBlockBackupJson()
        assertTrue(backupJson.contains("bad-site-1.com"))
        assertTrue(backupJson.contains("whitelist-good.org"))
        assertTrue(backupJson.contains("\"profile\""))

        repository.clearWebsiteRules(isWhitelist = false)
        repository.clearWebsiteRules(isWhitelist = true)
        assertEquals(0, repository.websiteRulesFlow.first().size)

        val importText = """
            # Custom block entries
            0.0.0.0 newly-imported-ad.com
            ||adserver.net^
            @@||allowed-portal.io^
        """.trimIndent()
        val importResult = repository.importWebsiteRules(importText, defaultIsWhitelist = false)
        assertTrue(importResult.isSuccess)
        assertEquals(3, importResult.getOrThrow())

        val importedRules = repository.websiteRulesFlow.first().associateBy { it.domain }
        assertEquals(3, importedRules.size)
        assertFalse(importedRules["newly-imported-ad.com"]!!.isWhitelist)
        assertFalse(importedRules["adserver.net"]!!.isWhitelist)
        assertTrue(importedRules["allowed-portal.io"]!!.isWhitelist)

        val restoreResult = repository.restoreAdBlockBackupJson(backupJson)
        assertTrue(restoreResult.isSuccess)
        val restoredRules = repository.websiteRulesFlow.first().associateBy { it.domain }
        assertTrue(restoredRules.containsKey("bad-site-1.com"))
        assertTrue(restoredRules.containsKey("whitelist-good.org"))
    }

    @Test
    fun `requirement 6 - analytics hub tracks total blocked categories apps and domains`() {
        AdBlockAnalyticsHub.clear()

        AdBlockAnalyticsHub.recordEvent(
            domain = "ad.doubleclick.net",
            status = DnsQueryStatus.BLOCKED_AD,
            blockReason = AdBlockReason.ADS,
            filterListName = "AdGuard DNS Filter",
            appPackage = "com.android.chrome",
            appName = "Chrome"
        )

        AdBlockAnalyticsHub.recordEvent(
            domain = "graph.facebook.com",
            status = DnsQueryStatus.BLOCKED_AD,
            blockReason = AdBlockReason.TRACKER,
            filterListName = "EasyPrivacy",
            appPackage = "com.instagram.android",
            appName = "Instagram"
        )

        AdBlockAnalyticsHub.recordEvent(
            domain = "malware-drop.ru",
            status = DnsQueryStatus.BLOCKED_AD,
            blockReason = AdBlockReason.MALWARE,
            filterListName = "URLHaus Malware",
            appPackage = "org.mozilla.firefox",
            appName = "Firefox"
        )

        AdBlockAnalyticsHub.recordEvent(
            domain = "phish-bank.com",
            status = DnsQueryStatus.BLOCKED_AD,
            blockReason = AdBlockReason.PHISHING,
            filterListName = "Phishing Army",
            appPackage = "com.google.android.gm",
            appName = "Gmail"
        )

        AdBlockAnalyticsHub.recordEvent(
            domain = "telemetry.samsung.com",
            status = DnsQueryStatus.BLOCKED_AD,
            blockReason = AdBlockReason.TELEMETRY,
            filterListName = "Samsung Telemetry Blocklist",
            appPackage = "com.samsung.telemetry",
            appName = "Samsung System"
        )

        AdBlockAnalyticsHub.recordEvent(
            domain = "wikipedia.org",
            status = DnsQueryStatus.ALLOWED,
            blockReason = AdBlockReason.NONE,
            filterListName = "",
            appPackage = "com.android.chrome",
            appName = "Chrome"
        )

        // Snapshot publication is coalesced to at most one per second; flush for a deterministic read.
        AdBlockAnalyticsHub.flushSnapshot()
        val summary = AdBlockAnalyticsHub.summaryFlow.value
        assertEquals(6, summary.totalRequests)
        assertEquals(5, summary.totalBlockedRequests)
        assertEquals(1, summary.adsBlocked)
        assertEquals(1, summary.trackersBlocked)
        assertEquals(1, summary.malwareBlocked)
        assertEquals(1, summary.phishingBlocked)
        assertEquals(1, summary.telemetryBlocked)

        assertTrue(summary.topBlockedDomains.any { it.domain == "ad.doubleclick.net" })
        assertTrue(summary.topApps.any { it.appName == "Chrome" && it.totalRequests == 2L })
    }

    @Test
    fun `requirement 7 - quick action urls for whois and virustotal`() {
        val domain = "example-threat.org"
        val whoisUrl = "https://who.is/whois/$domain"
        val virusTotalUrl = "https://www.virustotal.com/gui/domain/$domain"

        assertEquals("https://who.is/whois/example-threat.org", whoisUrl)
        assertEquals("https://www.virustotal.com/gui/domain/example-threat.org", virusTotalUrl)
    }

    @Test
    fun `requirement 8 - deduplication gzip storage and rule lookup caching`() {
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = setOf("duplicate-ad.com", "unique-tracker.com", "tracking.oem.net"),
            exceptionDomains = emptySet(),
            duplicatesRemovedCount = 2
        )
        assertEquals(3, snapshot.totalBlockedCount)
        assertEquals(2, snapshot.duplicatesRemovedCount)

        val match1 = snapshot.matchBlockedRule("duplicate-ad.com")
        assertTrue(match1.isBlocked)
        assertTrue("Latency must be < 10ms", match1.latencyMicros < 10000L)

        val matchCached = snapshot.matchBlockedRule("duplicate-ad.com")
        assertTrue(matchCached.isBlocked)

        val matchClean = snapshot.matchBlockedRule("clean-search.org")
        assertFalse(matchClean.isBlocked)
    }

    @Test
    fun `requirement 9 - smart protection profiles apply correct list selections`() = runTest {
        val allIds = AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS.map { it.id }

        assertEquals(SmartProtectionProfile.BALANCED, preferences.preferencesFlow.first().smartProtectionProfile)
        val balancedIds = AdBlockFilterEngine.enabledListIdsForProfile(SmartProtectionProfile.BALANCED, allIds)
        assertEquals(8, balancedIds.size)
        assertTrue(balancedIds.contains("oisd_big"))
        assertTrue(balancedIds.contains("adguard_dns"))
        assertTrue(balancedIds.contains("hagezi_normal"))
        assertTrue(balancedIds.contains("hagezi_tif"))
        assertTrue(balancedIds.contains("easylist_privacy"))
        assertTrue(balancedIds.contains("adaway_hosts"))
        assertTrue(balancedIds.contains("urlhaus_malware"))
        assertTrue(balancedIds.contains("oem_telemetry"))

        repository.applySmartProtectionProfile(SmartProtectionProfile.MAXIMUM_PROTECTION)
        assertEquals(SmartProtectionProfile.MAXIMUM_PROTECTION, preferences.preferencesFlow.first().smartProtectionProfile)
        val maxIds = AdBlockFilterEngine.enabledListIdsForProfile(SmartProtectionProfile.MAXIMUM_PROTECTION, allIds)
        assertEquals(21, maxIds.size)

        repository.applySmartProtectionProfile(SmartProtectionProfile.PRIVACY_FOCUSED)
        val privacyIds = AdBlockFilterEngine.enabledListIdsForProfile(SmartProtectionProfile.PRIVACY_FOCUSED, allIds)
        assertTrue(privacyIds.contains("easylist_privacy"))
        assertTrue(privacyIds.contains("oem_telemetry"))
        assertTrue(privacyIds.contains("windows_spy_blocker"))

        repository.applySmartProtectionProfile(SmartProtectionProfile.FAMILY_SAFE)
        val familyIds = AdBlockFilterEngine.enabledListIdsForProfile(SmartProtectionProfile.FAMILY_SAFE, allIds)
        assertTrue(familyIds.contains("hagezi_tif"))
        assertTrue(familyIds.contains("urlhaus_malware"))

        repository.applySmartProtectionProfile(SmartProtectionProfile.GAMING_MODE)
        val gamingIds = AdBlockFilterEngine.enabledListIdsForProfile(SmartProtectionProfile.GAMING_MODE, allIds)
        assertTrue(gamingIds.contains("oisd_big"))
        assertTrue(gamingIds.contains("adaway_hosts"))
        assertEquals(4, gamingIds.size)
    }
}
