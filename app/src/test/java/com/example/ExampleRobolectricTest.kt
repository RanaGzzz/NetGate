package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.ActiveNetworkType
import com.example.data.model.BatchAction
import com.example.data.model.ManagedApp
import com.example.data.model.WebsiteFilterMode
import com.example.data.repository.FirewallRepository
import com.example.service.DnsPacketFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            websiteRuleDao = database.websiteRuleDao(),
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

    @Test
    fun `website blocker normalizes domains and enforces blocklist and whitelist lockdown`() {
        assertEquals("youtube.com", DnsPacketFilter.normalizeDomainInput("https://www.YouTube.com/watch?v=123"))
        assertEquals("example.org", DnsPacketFilter.normalizeDomainInput("*.example.org"))
        assertNull(DnsPacketFilter.normalizeDomainInput("not_a_domain"))

        val rules = listOf(
            WebsiteRuleEntity(domain = "youtube.com", ruleType = WebsiteRuleType.BLOCK, isEnabled = true, createdAt = 1L),
            WebsiteRuleEntity(domain = "music.youtube.com", ruleType = WebsiteRuleType.ALLOW, isEnabled = true, createdAt = 2L),
            WebsiteRuleEntity(domain = "wikipedia.org", ruleType = WebsiteRuleType.ALLOW, isEnabled = true, createdAt = 3L)
        )

        // BLOCKLIST mode: blocks youtube.com & m.youtube.com, allows music.youtube.com (whitelisted) & unrelated sites
        assertTrue(DnsPacketFilter.shouldBlockDomain("youtube.com", WebsiteFilterMode.BLOCKLIST, rules))
        assertTrue(DnsPacketFilter.shouldBlockDomain("m.youtube.com", WebsiteFilterMode.BLOCKLIST, rules))
        assertFalse(DnsPacketFilter.shouldBlockDomain("music.youtube.com", WebsiteFilterMode.BLOCKLIST, rules))
        assertFalse(DnsPacketFilter.shouldBlockDomain("github.com", WebsiteFilterMode.BLOCKLIST, rules))

        // WHITELIST_ONLY lockdown mode: blocks everything except whitelisted domains & their subdomains
        assertTrue(DnsPacketFilter.shouldBlockDomain("github.com", WebsiteFilterMode.WHITELIST_ONLY, rules))
        assertTrue(DnsPacketFilter.shouldBlockDomain("youtube.com", WebsiteFilterMode.WHITELIST_ONLY, rules))
        assertFalse(DnsPacketFilter.shouldBlockDomain("en.wikipedia.org", WebsiteFilterMode.WHITELIST_ONLY, rules))
        assertFalse(DnsPacketFilter.shouldBlockDomain("music.youtube.com", WebsiteFilterMode.WHITELIST_ONLY, rules))
    }

    @Test
    fun `dns packet filter parses ipv4 udp query and builds nxdomain response`() {
        val dnsQuery = byteArrayOf(
            0x12, 0x34, // Tx ID
            0x01, 0x00, // Standard query with RD=1
            0x00, 0x01, // QDCOUNT=1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // QNAME: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01, // Type A
            0x00, 0x01  // Class IN
        )
        val udpLen = 8 + dnsQuery.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)
        packet[0] = 0x45.toByte()
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 17 // UDP
        // Src IP 10.1.10.1, Dst IP 10.1.10.2
        packet[12] = 10; packet[13] = 1; packet[14] = 10; packet[15] = 1
        packet[16] = 10; packet[17] = 1; packet[18] = 10; packet[19] = 2
        // Src port 54321, Dst port 53
        packet[20] = (54321 ushr 8).toByte(); packet[21] = (54321 and 0xFF).toByte()
        packet[22] = 0; packet[23] = 53
        packet[24] = (udpLen ushr 8).toByte(); packet[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsQuery, 0, packet, 28, dnsQuery.size)

        val parsed = DnsPacketFilter.parseIpv4UdpDnsQuery(packet, packet.size)
        assertNotNull(parsed)
        assertEquals("example.com", parsed!!.domain)
        assertEquals(54321, parsed.srcPort)

        val blockedResp = DnsPacketFilter.buildBlockedDnsResponsePacket(parsed)
        assertEquals(totalLen, blockedResp.size)
        // Swapped src/dst ports: src=53, dst=54321
        assertEquals(53, ((blockedResp[20].toInt() and 0xFF) shl 8) or (blockedResp[21].toInt() and 0xFF))
        assertEquals(54321, ((blockedResp[22].toInt() and 0xFF) shl 8) or (blockedResp[23].toInt() and 0xFF))
        // DNS QR=1 (0x81) and RCODE=3 NXDOMAIN (0x83)
        assertEquals(0x81.toByte(), blockedResp[28 + 2])
        assertEquals(0x83.toByte(), blockedResp[28 + 3])
    }

    @Test
    fun `website rules persist in room database through repository`() = runTest {
        val addedBlock = repository.addWebsiteRule("https://www.tiktok.com/explore", isWhitelist = false)
        assertTrue(addedBlock.isSuccess)
        assertEquals("tiktok.com", addedBlock.getOrNull())

        val addedAllow = repository.addWebsiteRule("khanacademy.org", isWhitelist = true)
        assertTrue(addedAllow.isSuccess)

        var list = repository.websiteRulesFlow.first()
        assertEquals(2, list.size)

        repository.setWebsiteRuleEnabled("tiktok.com", enabled = false)
        list = repository.websiteRulesFlow.first()
        assertFalse(list.first { it.domain == "tiktok.com" }.isEnabled)

        repository.deleteWebsiteRule("tiktok.com")
        list = repository.websiteRulesFlow.first()
        assertEquals(1, list.size)
        assertEquals("khanacademy.org", list.first().domain)

        repository.clearWebsiteRules(isWhitelist = true)
        list = repository.websiteRulesFlow.first()
        assertTrue(list.isEmpty())
    }
}
