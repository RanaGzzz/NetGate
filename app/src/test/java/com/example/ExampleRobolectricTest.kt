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
import com.example.service.AdBlockFilterEngine
import com.example.service.AdBlockMatcherSnapshot
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
            appTimerDao = database.appTimerDao(),
            adFilterListDao = database.adFilterListDao(),
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

    @Test
    fun `browser doh canary domains are automatically blocked when filter active`() {
        val rules = emptyList<WebsiteRuleEntity>()
        assertTrue(DnsPacketFilter.shouldBlockDomain("use-application-dns.net", WebsiteFilterMode.BLOCKLIST, rules))
        assertTrue(DnsPacketFilter.shouldBlockDomain("_dns.resolver.arpa", WebsiteFilterMode.BLOCKLIST, rules))
        assertTrue(DnsPacketFilter.shouldBlockDomain("dns.google", WebsiteFilterMode.BLOCKLIST, rules))
        assertTrue(DnsPacketFilter.shouldBlockDomain("mozilla.cloudflare-dns.com", WebsiteFilterMode.BLOCKLIST, rules))
        assertFalse(DnsPacketFilter.shouldBlockDomain("use-application-dns.net", WebsiteFilterMode.OFF, rules))
    }

    @Test
    fun `dns packet filter parses ipv6 udp query and builds ipv6 nxdomain response`() {
        val dnsQuery = byteArrayOf(
            0x56, 0x78, // Tx ID
            0x01, 0x00, // RD=1
            0x00, 0x01, // QDCOUNT=1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x1C, // Type AAAA
            0x00, 0x01  // Class IN
        )
        val udpLen = 8 + dnsQuery.size
        val packet = ByteArray(40 + udpLen)
        packet[0] = 0x60.toByte() // IPv6
        packet[4] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[5] = (udpLen and 0xFF).toByte()
        packet[6] = 17 // Next Header UDP
        packet[7] = 64
        // Src IPv6 ::1, Dst IPv6 ::2
        packet[23] = 1
        packet[39] = 2
        // Src port 45000, Dst port 53
        packet[40] = (45000 ushr 8).toByte(); packet[41] = (45000 and 0xFF).toByte()
        packet[42] = 0; packet[43] = 53
        packet[44] = (udpLen ushr 8).toByte(); packet[45] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsQuery, 0, packet, 48, dnsQuery.size)

        val parsed = DnsPacketFilter.parseUdpDnsQuery(packet, packet.size)
        assertNotNull(parsed)
        assertTrue(parsed!!.isIpv6)
        assertEquals("google.com", parsed.domain)

        val blockedResp = DnsPacketFilter.buildBlockedDnsResponsePacket(parsed)
        assertEquals(packet.size, blockedResp.size)
        assertEquals(6, (blockedResp[0].toInt() ushr 4) and 0x0F)
        assertEquals(53, ((blockedResp[40].toInt() and 0xFF) shl 8) or (blockedResp[41].toInt() and 0xFF))
        assertEquals(45000, ((blockedResp[42].toInt() and 0xFF) shl 8) or (blockedResp[43].toInt() and 0xFF))
        assertEquals(0x83.toByte(), blockedResp[48 + 3])
    }

    @Test
    fun `tcp reset generator builds immediate rst ack for doh syn attempts`() {
        val totalLen = 40
        val synPacket = ByteArray(totalLen)
        synPacket[0] = 0x45.toByte()
        synPacket[2] = 0
        synPacket[3] = totalLen.toByte()
        synPacket[8] = 64
        synPacket[9] = 6 // TCP
        // Src 10.1.10.1 -> Dst 1.1.1.1
        synPacket[12] = 10; synPacket[13] = 1; synPacket[14] = 10; synPacket[15] = 1
        synPacket[16] = 1; synPacket[17] = 1; synPacket[18] = 1; synPacket[19] = 1
        // Src port 51234, Dst port 443
        synPacket[20] = (51234 ushr 8).toByte(); synPacket[21] = (51234 and 0xFF).toByte()
        synPacket[22] = (443 ushr 8).toByte(); synPacket[23] = (443 and 0xFF).toByte()
        // Seq = 100
        synPacket[27] = 100
        synPacket[32] = 0x50.toByte() // data offset 20 bytes
        synPacket[33] = 0x02 // SYN

        val rstPacket = DnsPacketFilter.buildTcpResetPacket(synPacket, totalLen)
        assertNotNull(rstPacket)
        assertEquals(40, rstPacket!!.size)
        assertEquals(443, ((rstPacket[20].toInt() and 0xFF) shl 8) or (rstPacket[21].toInt() and 0xFF))
        assertEquals(51234, ((rstPacket[22].toInt() and 0xFF) shl 8) or (rstPacket[23].toInt() and 0xFF))
        // RST + ACK flags = 0x14
        assertEquals(0x14.toByte(), rstPacket[33])
        // ACK number = 101 (seq + 1 for SYN)
        assertEquals(101, rstPacket[31].toInt() and 0xFF)
    }

    @Test
    fun `room historical traffic usage persists rx and tx bytes across days`() = runTest {
        val usageDao = database.appTrafficUsageDao()
        usageDao.upsertHistoryEntries(
            listOf(
                com.example.data.local.AppTrafficHistoryEntity(
                    dateEpochDay = 20_000L,
                    packageName = "com.android.chrome",
                    wifiRxBytes = 50_000_000L,
                    wifiTxBytes = 10_000_000L,
                    mobileRxBytes = 15_000_000L,
                    mobileTxBytes = 5_000_000L,
                    recordedAt = 1_700_000_000_000L
                ),
                com.example.data.local.AppTrafficHistoryEntity(
                    dateEpochDay = 20_001L,
                    packageName = "com.android.chrome",
                    wifiRxBytes = 30_000_000L,
                    wifiTxBytes = 5_000_000L,
                    mobileRxBytes = 0L,
                    mobileTxBytes = 0L,
                    recordedAt = 1_700_086_400_000L
                )
            )
        )

        val history = repository.dailyTrafficHistoryFlow.first()
        assertEquals(2, history.size)
        assertEquals(80_000_000L, history[0].totalBytes)
        assertEquals(65_000_000L, history[0].rxBytes)
        assertEquals(15_000_000L, history[0].txBytes)

        val totals = repository.historicalTotalsFlow.first()
        assertEquals(2, totals.daysTrackedCount)
        assertEquals(80_000_000L, totals.totalWifiRxBytes)
        assertEquals(15_000_000L, totals.totalWifiTxBytes)
        assertEquals(15_000_000L, totals.totalMobileRxBytes)
        assertEquals(5_000_000L, totals.totalMobileTxBytes)
        assertEquals(115_000_000L, totals.grandTotalBytes)
    }

    @Test
    fun `app countdown timer persists and automatically disables wifi and data on expiration`() = runTest {
        val baseTime = 1_700_000_000_000L

        // Existing rule: Wi-Fi already blocked for com.video.app; timer will disable Mobile Data after 10 mins
        repository.setAppRule("com.video.app", blockWifi = true, blockMobileData = false)
        repository.scheduleAppTimer(
            packageName = "com.video.app",
            durationMinutes = 10,
            blockWifiOnExpire = false,
            blockMobileDataOnExpire = true,
            nowMillis = baseTime
        )
        // Second app: 30m timer to disable both Wi-Fi & Data
        repository.scheduleAppTimer(
            packageName = "com.social.app",
            durationMinutes = 30,
            blockWifiOnExpire = true,
            blockMobileDataOnExpire = true,
            nowMillis = baseTime
        )

        var activeTimers = repository.appTimersFlow.first()
        assertEquals(2, activeTimers.size)

        // Before 10 minutes: nothing fires
        val expiredEarly = repository.processDueTimers(nowMillis = baseTime + 5 * 60_000L)
        assertTrue(expiredEarly.isEmpty())

        // At 10 minutes: com.video.app expires and merges Mobile Data block with existing Wi-Fi block
        val expiredAt10m = repository.processDueTimers(nowMillis = baseTime + 10 * 60_000L)
        assertEquals(listOf("com.video.app"), expiredAt10m)

        val rulesAfter10m = repository.getAllRulesOnce().associateBy { it.packageName }
        assertTrue(rulesAfter10m["com.video.app"]!!.blockWifi)
        assertTrue(rulesAfter10m["com.video.app"]!!.blockMobileData)
        assertFalse(rulesAfter10m.containsKey("com.social.app"))

        // Remaining timer is com.social.app; test cancelling it before expiry
        activeTimers = repository.appTimersFlow.first()
        assertEquals(1, activeTimers.size)
        assertEquals("com.social.app", activeTimers.first().packageName)

        repository.cancelAppTimer("com.social.app")
        assertTrue(repository.appTimersFlow.first().isEmpty())
    }

    @Test
    fun `countdown formatter displays mm ss and h mm ss cleanly`() {
        assertEquals("00:00", com.example.data.model.formatCountdownTimer(0L))
        assertEquals("05:00", com.example.data.model.formatCountdownTimer(300_000L))
        assertEquals("14:59", com.example.data.model.formatCountdownTimer(899_000L))
        assertEquals("1:05:00", com.example.data.model.formatCountdownTimer(3_900_000L))
    }

    @Test
    fun `ad block filter engine parses hosts and adblock syntax cleanly`() {
        assertNull(AdBlockFilterEngine.parseFilterLine("# comment line"))
        assertNull(AdBlockFilterEngine.parseFilterLine("! Adblock comment"))
        assertNull(AdBlockFilterEngine.parseFilterLine("0.0.0.0 localhost"))
        assertNull(AdBlockFilterEngine.parseFilterLine("example.com##.ad-banner"))

        val hostsZero = AdBlockFilterEngine.parseFilterLine("0.0.0.0 ads.tracker.example.com # inline")
        assertNotNull(hostsZero)
        assertEquals("ads.tracker.example.com", hostsZero!!.domain)
        assertFalse(hostsZero.isException)

        val hostsLoopback = AdBlockFilterEngine.parseFilterLine("127.0.0.1 telemetry.sdk.io")
        assertNotNull(hostsLoopback)
        assertEquals("telemetry.sdk.io", hostsLoopback!!.domain)
        assertFalse(hostsLoopback.isException)

        val adguardBlock = AdBlockFilterEngine.parseFilterLine("||doubleclick.net^\$third-party")
        assertNotNull(adguardBlock)
        assertEquals("doubleclick.net", adguardBlock!!.domain)
        assertFalse(adguardBlock.isException)

        val exceptionRule = AdBlockFilterEngine.parseFilterLine("@@||analytics.trusted.org^")
        assertNotNull(exceptionRule)
        assertEquals("analytics.trusted.org", exceptionRule!!.domain)
        assertTrue(exceptionRule.isException)
    }

    @Test
    fun `ad block matcher matches subdomains respects exceptions and essential passthrough`() {
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = setOf("doubleclick.net", "trusted.org", "adservice.google.com"),
            exceptionDomains = setOf("analytics.trusted.org")
        )

        // Exact & parent-domain match
        assertTrue(snapshot.isBlockedByAdFilter("doubleclick.net"))
        assertTrue(snapshot.isBlockedByAdFilter("cm.g.doubleclick.net."))
        assertTrue(snapshot.isBlockedByAdFilter("adservice.google.com"))

        // Exception overrides blocked parent domain
        assertTrue(snapshot.isBlockedByAdFilter("ads.trusted.org"))
        assertFalse(snapshot.isBlockedByAdFilter("analytics.trusted.org"))
        assertFalse(snapshot.isBlockedByAdFilter("sub.analytics.trusted.org"))

        // Essential passthrough detection for OS connectivity & push
        assertTrue(snapshot.isEssentialPassthrough("connectivitycheck.gstatic.com"))
        assertTrue(snapshot.isEssentialPassthrough("mtalk.google.com"))
        assertFalse(snapshot.isEssentialPassthrough("cm.g.doubleclick.net"))
    }

    @Test
    fun `room ad filter lists seed 10 defaults and support custom list crud`() = runTest {
        repository.ensureDefaultAdFilterListsSeeded()
        val seeded = repository.adFilterListsFlow.first()
        assertEquals(10, seeded.size)
        assertTrue(seeded.first { it.id == "stevenblack" }.isEnabled)
        assertTrue(seeded.first { it.id == "adguard_dns" }.isEnabled)
        assertTrue(seeded.first { it.id == "oisd_big" }.isEnabled)
        assertTrue(seeded.first { it.id == "hagezi_tif" }.isEnabled)
        assertFalse(seeded.first { it.id == "peter_lowe" }.isEnabled)

        repository.toggleAdFilterList("peter_lowe", enabled = true)
        repository.toggleAdFilterList("stevenblack", enabled = false)

        val customRes = repository.addCustomAdFilterList(
            rawTitle = "My Blocklist",
            rawUrl = "https://example.org/hosts.txt",
            rawCategory = "Custom"
        )
        assertTrue(customRes.isSuccess)
        val customId = customRes.getOrThrow().id
        assertTrue(customId.startsWith("custom_"))

        val updated = repository.adFilterListsFlow.first().associateBy { it.id }
        assertEquals(11, updated.size)
        assertTrue(updated["peter_lowe"]!!.isEnabled)
        assertFalse(updated["stevenblack"]!!.isEnabled)

        repository.deleteCustomAdFilterList(customId)
        assertEquals(10, repository.adFilterListsFlow.first().size)
    }

    @Test
    fun `ad block engine parses wildcard domains strips tracking params and flags ad paths`() {
        val wildcard = AdBlockFilterEngine.parseFilterLine("*.ads.network.io")
        assertNotNull(wildcard)
        assertEquals("ads.network.io", wildcard!!.domain)
        assertFalse(wildcard.isException)

        val ipv6Host = AdBlockFilterEngine.parseFilterLine("::0 tracker.v6.example")
        assertNotNull(ipv6Host)
        assertEquals("tracker.v6.example", ipv6Host!!.domain)

        assertTrue(AdBlockFilterEngine.isAdOrTrackerUrlPath("/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockFilterEngine.isAdOrTrackerUrlPath("/gtm.js?id=GTM-123"))
        assertFalse(AdBlockFilterEngine.isAdOrTrackerUrlPath("/articles/kotlin-compose"))

        val cleaned = AdBlockFilterEngine.stripTrackingQueryParams(
            "/article?id=42&utm_source=newsletter&fbclid=xyz123&page=2&gclid=abc"
        )
        assertEquals("/article?id=42&page=2", cleaned)
        assertEquals(
            "/home",
            AdBlockFilterEngine.stripTrackingQueryParams("/home?utm_campaign=launch&igshid=99")
        )
    }

    @Test
    fun `local filtering proxy parses authority and extracts tls sni hostname`() {
        val (host, port) = com.example.service.LocalFilteringProxyServer.parseAuthority("ads.example.com:443", 443)
        assertEquals("ads.example.com", host)
        assertEquals(443, port)

        val (v6Host, v6Port) = com.example.service.LocalFilteringProxyServer.parseAuthority("[2001:db8::1]:8443", 443)
        assertEquals("2001:db8::1", v6Host)
        assertEquals(8443, v6Port)

        // Build a synthetic TLS 1.2 ClientHello with SNI = "doubleclick.net"
        val sniHost = "doubleclick.net".toByteArray(Charsets.US_ASCII)
        val sniEntryLen = 1 + 2 + sniHost.size
        val sniListLen = sniEntryLen
        val extDataLen = 2 + sniListLen
        val extensionsLen = 4 + extDataLen

        val clientHelloBody = java.io.ByteArrayOutputStream()
        clientHelloBody.write(byteArrayOf(0x03, 0x03)) // TLS 1.2
        clientHelloBody.write(ByteArray(32)) // Random
        clientHelloBody.write(0x00) // Session ID len = 0
        clientHelloBody.write(byteArrayOf(0x00, 0x02, 0x13, 0x01)) // 1 cipher suite
        clientHelloBody.write(byteArrayOf(0x01, 0x00)) // Compression methods len=1 (null)
        clientHelloBody.write(byteArrayOf(((extensionsLen ushr 8) and 0xFF).toByte(), (extensionsLen and 0xFF).toByte()))
        // Extension 0x0000 (server_name)
        clientHelloBody.write(byteArrayOf(0x00, 0x00))
        clientHelloBody.write(byteArrayOf(((extDataLen ushr 8) and 0xFF).toByte(), (extDataLen and 0xFF).toByte()))
        clientHelloBody.write(byteArrayOf(((sniListLen ushr 8) and 0xFF).toByte(), (sniListLen and 0xFF).toByte()))
        clientHelloBody.write(0x00) // host_name
        clientHelloBody.write(byteArrayOf(((sniHost.size ushr 8) and 0xFF).toByte(), (sniHost.size and 0xFF).toByte()))
        clientHelloBody.write(sniHost)

        val bodyBytes = clientHelloBody.toByteArray()
        val hsLen = bodyBytes.size
        val recordPayloadLen = 4 + hsLen

        val tlsRecord = java.io.ByteArrayOutputStream()
        tlsRecord.write(byteArrayOf(0x16, 0x03, 0x01)) // Handshake record
        tlsRecord.write(byteArrayOf(((recordPayloadLen ushr 8) and 0xFF).toByte(), (recordPayloadLen and 0xFF).toByte()))
        tlsRecord.write(0x01) // ClientHello
        tlsRecord.write(byteArrayOf(((hsLen ushr 16) and 0xFF).toByte(), ((hsLen ushr 8) and 0xFF).toByte(), (hsLen and 0xFF).toByte()))
        tlsRecord.write(bodyBytes)

        val rawTls = tlsRecord.toByteArray()
        assertEquals(
            "doubleclick.net",
            com.example.service.LocalFilteringProxyServer.extractTlsSniHostname(rawTls, rawTls.size)
        )
    }

    @Test
    fun `dns leak evaluator distinguishes protected doh vpn tunnel from unguarded isp dns`() {
        val protectedReport = com.example.data.network.DnsLeakTester.evaluateLeakReport(
            prefs = com.example.data.model.FirewallPreferencesState(
                isFirewallEnabled = true,
                isAdBlockerEnabled = true,
                useDohUpstream = true,
                isHttpsProxyEnabled = true,
                httpsProxyPort = 8899
            ),
            vpnState = com.example.service.VpnRuntimeState(
                isServiceRunning = true,
                isTunnelActive = true,
                isHttpsProxyActive = true
            ),
            connectionDetails = com.example.data.model.ConnectionDetails(
                activeNetworkType = ActiveNetworkType.WIFI,
                dnsServers = listOf("192.168.1.1")
            ),
            vpnDnsServers = listOf("10.1.10.2", "fd00:1:fd00:1:fd00:1:fd00:2"),
            physicalDnsServers = listOf("192.168.1.1"),
            hasVpnIpv6Dns = true,
            hasVpnHttpProxy = true,
            dohLatencyMs = 24,
            dohValid = true,
            egressIp = "203.0.113.10",
            egressColo = "SJC",
            testedAtMs = 1_700_000_000_000L
        )
        assertEquals(com.example.data.model.DnsLeakVerdict.NO_LEAK, protectedReport.verdict)

        val exposedReport = com.example.data.network.DnsLeakTester.evaluateLeakReport(
            prefs = com.example.data.model.FirewallPreferencesState(
                isFirewallEnabled = false,
                useDohUpstream = false
            ),
            vpnState = com.example.service.VpnRuntimeState(isServiceRunning = false, isTunnelActive = false),
            connectionDetails = com.example.data.model.ConnectionDetails(
                activeNetworkType = ActiveNetworkType.WIFI,
                dnsServers = listOf("192.168.1.1", "fe80::1")
            ),
            vpnDnsServers = emptyList(),
            physicalDnsServers = listOf("192.168.1.1", "fe80::1"),
            hasVpnIpv6Dns = false,
            hasVpnHttpProxy = false,
            dohLatencyMs = null,
            dohValid = false,
            egressIp = null,
            egressColo = null,
            testedAtMs = 1_700_000_000_000L
        )
        assertEquals(com.example.data.model.DnsLeakVerdict.LEAK_DETECTED, exposedReport.verdict)
    }

    @Test
    fun `safesearch and youtube restricted mode resolve official safe vip aliases`() {
        val disabled = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "www.google.com",
            safeSearchEnabled = false,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.OFF
        )
        assertNull(disabled)

        val googleSafe = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "www.google.co.uk",
            safeSearchEnabled = true,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.OFF
        )
        assertNotNull(googleSafe)
        assertEquals("forcesafesearch.google.com", googleSafe!!.aliasDomain)

        val bingSafe = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "www.bing.com",
            safeSearchEnabled = true,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.OFF
        )
        assertEquals("strict.bing.com", bingSafe!!.aliasDomain)

        val ddgSafe = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "duckduckgo.com",
            safeSearchEnabled = true,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.OFF
        )
        assertEquals("safe.duckduckgo.com", ddgSafe!!.aliasDomain)

        val braveSafe = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "search.brave.com",
            safeSearchEnabled = true,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.OFF
        )
        assertEquals("safesearch.brave.com", braveSafe!!.aliasDomain)

        assertNull(
            DnsPacketFilter.resolveSafeSearchAlias(
                domain = "wikipedia.org",
                safeSearchEnabled = true,
                youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.STRICT
            )
        )

        val ytModerate = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "m.youtube.com",
            safeSearchEnabled = false,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.MODERATE
        )
        assertEquals("restrictmoderate.youtube.com", ytModerate!!.aliasDomain)

        val ytStrict = DnsPacketFilter.resolveSafeSearchAlias(
            domain = "www.youtube.com",
            safeSearchEnabled = false,
            youtubeRestrictedMode = com.example.data.model.YouTubeRestrictedMode.STRICT
        )
        assertEquals("restrict.youtube.com", ytStrict!!.aliasDomain)
    }

    @Test
    fun `dns packet filter rewrites safesearch query and binds upstream vip answer to original domain`() {
        val rawDnsQuery = byteArrayOf(
            0x22, 0x33, // Tx ID
            0x01, 0x00, // RD=1
            0x00, 0x01, // QDCOUNT=1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01, // A
            0x00, 0x01  // IN
        )
        val udpLen = 8 + rawDnsQuery.size
        val totalLen = 20 + udpLen
        val ipPacket = ByteArray(totalLen)
        ipPacket[0] = 0x45.toByte()
        ipPacket[2] = ((totalLen ushr 8) and 0xFF).toByte()
        ipPacket[3] = (totalLen and 0xFF).toByte()
        ipPacket[9] = 17
        ipPacket[12] = 10; ipPacket[13] = 1; ipPacket[14] = 10; ipPacket[15] = 1
        ipPacket[16] = 10; ipPacket[17] = 1; ipPacket[18] = 10; ipPacket[19] = 2
        ipPacket[20] = 0x30; ipPacket[21] = 0x39
        ipPacket[22] = 0x00; ipPacket[23] = 53
        ipPacket[24] = (udpLen ushr 8).toByte(); ipPacket[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(rawDnsQuery, 0, ipPacket, 28, rawDnsQuery.size)

        val parsed = DnsPacketFilter.parseUdpDnsQuery(ipPacket, ipPacket.size)!!
        val rewrittenQuery = DnsPacketFilter.buildRewrittenQueryPayload(parsed, "forcesafesearch.google.com")
        assertNotNull(rewrittenQuery)

        // Build synthetic upstream response for forcesafesearch.google.com -> A 216.239.38.120
        val upstreamResp = java.io.ByteArrayOutputStream()
        upstreamResp.write(rewrittenQuery!!, 0, 2) // Tx ID
        upstreamResp.write(byteArrayOf(0x81.toByte(), 0x80.toByte())) // QR=1, RD=1, RA=1, NOERROR
        upstreamResp.write(byteArrayOf(0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)) // QD=1, AN=1
        upstreamResp.write(rewrittenQuery, 12, rewrittenQuery.size - 12) // Question
        // Answer RR: pointer 0xC00C, Type A, Class IN, TTL 300, RDLEN 4, 216.239.38.120
        upstreamResp.write(byteArrayOf(0xC0.toByte(), 0x0C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x01, 0x2C, 0x00, 0x04))
        upstreamResp.write(byteArrayOf(216.toByte(), 239.toByte(), 38, 120))

        val boundAnswer = DnsPacketFilter.buildRewrittenAnswerPayload(parsed, upstreamResp.toByteArray())
        assertNotNull(boundAnswer)
        val respBytes = boundAnswer!!
        assertEquals(0x22.toByte(), respBytes[0])
        assertEquals(0x33.toByte(), respBytes[1])
        assertEquals(1, ((respBytes[6].toInt() and 0xFF) shl 8) or (respBytes[7].toInt() and 0xFF))
        // Ends with VIP IPv4 216.239.38.120
        val n = respBytes.size
        assertEquals(216.toByte(), respBytes[n - 4])
        assertEquals(239.toByte(), respBytes[n - 3])
        assertEquals(38.toByte(), respBytes[n - 2])
        assertEquals(120.toByte(), respBytes[n - 1])
    }

    @Test
    fun `youtube ad blocker targets ad endpoints and paths without breaking video streams`() {
        assertTrue(AdBlockFilterEngine.isYoutubeAdDomain("ads.youtube.com"))
        assertTrue(AdBlockFilterEngine.isYoutubeAdDomain("s.youtube.com"))
        assertTrue(AdBlockFilterEngine.isYoutubeAdDomain("googleads.g.doubleclick.net"))
        assertTrue(AdBlockFilterEngine.isYoutubeAdDomain("pagead2.googlesyndication.com"))

        // Normal video streaming & thumbnails must remain intact
        assertFalse(AdBlockFilterEngine.isYoutubeAdDomain("www.youtube.com"))
        assertFalse(AdBlockFilterEngine.isYoutubeAdDomain("i.ytimg.com"))
        assertFalse(AdBlockFilterEngine.isYoutubeAdDomain("rr2---sn-4g5e6nze.googlevideo.com"))

        assertTrue(AdBlockFilterEngine.isYoutubeAdUrlPath("/youtubei/v1/player/ad_break?key=abc"))
        assertTrue(AdBlockFilterEngine.isYoutubeAdUrlPath("/api/stats/ads?ns=yt"))
        assertFalse(AdBlockFilterEngine.isYoutubeAdUrlPath("/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `sni proxy presets and custom upstream proxy persist cleanly in preferences`() = runTest {
        val prefs = FirewallPreferences(context)
        prefs.applySniProxyPreset(com.example.data.model.SniProxyPreset.FAMILY_SAFE)
        var state = prefs.preferencesFlow.first()
        assertEquals(com.example.data.model.SniProxyPreset.FAMILY_SAFE, state.sniProxyPreset)
        assertTrue(state.isHttpsProxyEnabled)
        assertTrue(state.isAdBlockerEnabled)
        assertTrue(state.isYoutubeAdBlockEnabled)
        assertTrue(state.isSafeSearchEnabled)
        assertEquals(com.example.data.model.YouTubeRestrictedMode.STRICT, state.youtubeRestrictedMode)

        prefs.setCustomSniProxy("proxy.corp.local", 3128)
        state = prefs.preferencesFlow.first()
        assertEquals(com.example.data.model.SniProxyPreset.CUSTOM_UPSTREAM_SNI, state.sniProxyPreset)
        assertEquals("proxy.corp.local" to 3128, state.resolvedUpstreamSniProxy())
    }
}
