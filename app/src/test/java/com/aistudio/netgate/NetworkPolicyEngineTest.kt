package com.aistudio.netgate

import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.service.AdBlockMatcherSnapshot
import com.aistudio.netgate.service.policy.DomainNormalizer
import com.aistudio.netgate.service.policy.NetworkPolicy
import com.aistudio.netgate.service.policy.NetworkPolicyEngine
import com.aistudio.netgate.service.policy.PolicyContext
import com.aistudio.netgate.service.policy.PolicyDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyEngineTest {

    // =========================================================================
    // 1. DomainNormalizer Tests
    // =========================================================================

    @Test
    fun `DomainNormalizer strips schemes, paths, queries, and ports`() {
        assertEquals("example.com", DomainNormalizer.normalize("https://example.com/path/to/page?q=1#frag"))
        assertEquals("example.com", DomainNormalizer.normalize("http://example.com:8080/foo"))
        assertEquals("sub.domain.org", DomainNormalizer.normalize("http://www.sub.domain.org:443/"))
        assertEquals("facebook.com", DomainNormalizer.normalize("https://www.facebook.com"))
        assertEquals("google.com", DomainNormalizer.normalize("*.google.com"))
        assertEquals("sub.example.com", DomainNormalizer.normalize("*.www.sub.example.com"))
    }

    @Test
    fun `DomainNormalizer rejects invalid hostnames`() {
        assertNull(DomainNormalizer.normalize(null))
        assertNull(DomainNormalizer.normalize(""))
        assertNull(DomainNormalizer.normalize("   "))
        assertNull(DomainNormalizer.normalize("localhost")) // No dot / single label
        assertNull(DomainNormalizer.normalize("foo..bar.com")) // Empty label
        assertNull(DomainNormalizer.normalize("-badlabel.com")) // Leading hyphen
        assertNull(DomainNormalizer.normalize("badlabel-.com")) // Trailing hyphen
        assertNull(DomainNormalizer.normalize("inv@lid.com")) // Illegal character
        assertNull(DomainNormalizer.normalize("[2001:db8::1]")) // IPv6 literal
    }

    @Test
    fun `DomainNormalizer matchesRule correctly evaluates exact and subdomains`() {
        assertTrue(DomainNormalizer.matchesRule("example.com", "example.com"))
        assertTrue(DomainNormalizer.matchesRule("sub.example.com", "example.com"))
        assertTrue(DomainNormalizer.matchesRule("deep.nested.sub.example.com", "example.com"))
        assertFalse(DomainNormalizer.matchesRule("notexample.com", "example.com"))
        assertFalse(DomainNormalizer.matchesRule("example.org", "example.com"))
        assertFalse(DomainNormalizer.matchesRule("example.com", "sub.example.com"))
    }

    @Test
    fun `DomainNormalizer parentDomainSequence produces hierarchical parents`() {
        val parents = DomainNormalizer.parentDomainSequence("deep.sub.example.co.uk").toList()
        assertEquals(
            listOf("deep.sub.example.co.uk", "sub.example.co.uk", "example.co.uk", "co.uk", "uk"),
            parents
        )
    }

    @Test
    fun `DomainNormalizer normalizeHost handles schemes, ports, leading dots, wildcards, underscores, and IPv6 brackets`() {
        assertEquals("dns.google", DomainNormalizer.normalizeHost("dns.google:443"))
        assertEquals("mozilla.cloudflare-dns.com", DomainNormalizer.normalizeHost("https://Mozilla.Cloudflare-DNS.com/dns-query?name=test"))
        assertEquals("use-application-dns.net", DomainNormalizer.normalizeHost(" .USE-APPLICATION-DNS.NET. "))
        assertEquals("_dns.resolver.arpa", DomainNormalizer.normalizeHost("_DNS.RESOLVER.ARPA."))
        assertEquals("2001:4860:4860::8888", DomainNormalizer.normalizeHost("[2001:4860:4860::8888]:443"))
        assertNull(DomainNormalizer.normalize("[2001:4860:4860::8888]:443"))
        assertNull(DomainNormalizer.normalize("not_a_domain"))
    }

    // =========================================================================
    // 2. PolicyDecision & ProxyFilterDecision Adapter Tests
    // =========================================================================

    @Test
    fun `PolicyDecision adapter preserves compatibility with LocalFilteringProxyServer`() {
        val allowDecision = PolicyDecision.Allow(reason = "SNI Proxy (Direct)", isWhitelisted = true)
        val legacyAllow = allowDecision.toProxyFilterDecision()
        assertTrue(legacyAllow.allow)
        assertEquals("SNI Proxy (Direct)", legacyAllow.reason)
        assertFalse(legacyAllow.isAdBlock)

        val blockDecision = PolicyDecision.Block(
            reason = "YouTube Ad Blocker",
            isAdBlock = true,
            category = PolicyDecision.BlockCategory.YOUTUBE_AD
        )
        val legacyBlock = blockDecision.toProxyFilterDecision()
        assertFalse(legacyBlock.allow)
        assertEquals("YouTube Ad Blocker", legacyBlock.reason)
        assertTrue(legacyBlock.isAdBlock)
    }

    // =========================================================================
    // 3. Rule Precedence Tests (Whitelist overrides Blocklists and AdBlock)
    // =========================================================================

    @Test
    fun `explicit whitelist overrides website blocklist, adblock, and youtube filters`() {
        val testAdMatcher = AdBlockMatcherSnapshot(
            blockedDomains = setOf("adservice.google.com", "doubleclick.net"),
            exceptionDomains = emptySet()
        )

        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            whitelistedDomains = setOf("doubleclick.net", "whitelisted-tracker.com"),
            blockedDomains = setOf("doubleclick.net", "evil-site.com"),
            rawWebsiteRules = listOf(
                WebsiteRuleEntity(domain = "doubleclick.net", ruleType = WebsiteRuleType.ALLOW),
                WebsiteRuleEntity(domain = "doubleclick.net", ruleType = WebsiteRuleType.BLOCK)
            ),
            adMatcherSnapshot = testAdMatcher,
            isAdBlockerEnabled = true,
            isYoutubeAdBlockEnabled = true
        )

        val context = PolicyContext.forFirewall(protocol = "HTTPS")

        // Whitelisted domain must be allowed even though it is in blockedDomains and testAdMatcher
        val decision = NetworkPolicyEngine.evaluate("doubleclick.net", context, policy)
        assertTrue("Explicit whitelist must be allowed", decision.isAllowed)
        assertTrue(decision is PolicyDecision.Allow)
        assertTrue((decision as PolicyDecision.Allow).isWhitelisted)

        // Subdomain of whitelisted domain must also be allowed
        val subDecision = NetworkPolicyEngine.evaluate("ad.doubleclick.net", context, policy)
        assertTrue("Subdomain of explicit whitelist must be allowed", subDecision.isAllowed)

        // Unwhitelisted blocked site must be blocked
        val blockedDecision = NetworkPolicyEngine.evaluate("evil-site.com", context, policy)
        assertFalse("Unwhitelisted blocked domain must be blocked", blockedDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.WEBSITE_BLOCKLIST, (blockedDecision as PolicyDecision.Block).category)
    }

    @Test
    fun `whitelist lockdown mode blocks unwhitelisted domains but allows whitelisted`() {
        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.WHITELIST_ONLY,
            whitelistedDomains = setOf("school.edu"),
            blockedDomains = emptySet(),
            isAdBlockerEnabled = false
        )

        val firewallContext = PolicyContext.forFirewall()
        val allowedDecision = NetworkPolicyEngine.evaluate("school.edu", firewallContext, policy)
        assertTrue(allowedDecision.isAllowed)

        val subAllowedDecision = NetworkPolicyEngine.evaluate("portal.school.edu", firewallContext, policy)
        assertTrue(subAllowedDecision.isAllowed)

        val blockedDecision = NetworkPolicyEngine.evaluate("wikipedia.org", firewallContext, policy)
        assertFalse(blockedDecision.isAllowed)
        assertEquals("Whitelist Lockdown", blockedDecision.reason)
        assertEquals(PolicyDecision.BlockCategory.WHITELIST_LOCKDOWN, (blockedDecision as PolicyDecision.Block).category)
    }

    // =========================================================================
    // 4. Essential Passthrough Tests
    // =========================================================================

    @Test
    fun `essential passthrough domains bypass ad blocker`() {
        val testAdMatcher = AdBlockMatcherSnapshot(
            blockedDomains = setOf("connectivitycheck.gstatic.com", "checkout.stripe.com"),
            exceptionDomains = emptySet()
        )

        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.OFF,
            adMatcherSnapshot = testAdMatcher,
            isAdBlockerEnabled = true,
            isEssentialPassthroughEnabled = true
        )

        val context = PolicyContext.forFirewall()
        val decision = NetworkPolicyEngine.evaluate("connectivitycheck.gstatic.com", context, policy)
        assertTrue("Essential connectivity check must pass through", decision.isAllowed)
        assertEquals("Essential Passthrough", decision.reason)
        assertTrue((decision as PolicyDecision.Allow).isPassthrough)

        val paymentDecision = NetworkPolicyEngine.evaluate("checkout.stripe.com", context, policy)
        assertTrue("Payment gateway must pass through", paymentDecision.isAllowed)
        assertTrue((paymentDecision as PolicyDecision.Allow).isPassthrough)
    }

    // =========================================================================
    // 5. Anti-DoH Canary Tests
    // =========================================================================

    @Test
    fun `anti-doh canary domains normalize mixed case, ports, urls, and subdomains and cannot be bypassed by whitelist`() {
        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            whitelistedDomains = setOf("google.com", "cloudflare-dns.com", "use-application-dns.net"),
            isAdBlockerEnabled = true
        )

        val dnsContext = PolicyContext.forFirewall(protocol = "DNS")
        for (rawCanary in listOf(
            "use-application-dns.net",
            ".USE-APPLICATION-DNS.NET.",
            "_dns.resolver.arpa",
            "dns.google:443",
            "sub.dns.google.com.",
            "https://mozilla.cloudflare-dns.com/dns-query"
        )) {
            val canaryDecision = NetworkPolicyEngine.evaluate(rawCanary, dnsContext, policy)
            assertFalse("DoH canary $rawCanary must be blocked", canaryDecision.isAllowed)
            assertEquals(PolicyDecision.BlockCategory.DOH_BYPASS_CANARY, (canaryDecision as PolicyDecision.Block).category)
            assertTrue(com.aistudio.netgate.service.DnsPacketFilter.isInternalBypassCanary(rawCanary))
        }
    }

    @Test
    fun `specific subdomain block overrides broad parent whitelist while specific allow overrides parent block`() {
        val policy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            whitelistedDomains = setOf("example.com", "music.youtube.com"),
            blockedDomains = setOf("malware.example.com", "youtube.com"),
            isAdBlockerEnabled = true,
            isSafeSearchEnabled = true
        )
        val context = PolicyContext.forFirewall(protocol = "HTTPS")

        // Specific child BLOCK beats broad parent ALLOW
        val malwareDecision = NetworkPolicyEngine.evaluate("malware.example.com", context, policy)
        assertFalse("Specific child block must override parent allow", malwareDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.WEBSITE_BLOCKLIST, (malwareDecision as PolicyDecision.Block).category)

        val nestedMalwareDecision = NetworkPolicyEngine.evaluate("dl.malware.example.com", context, policy)
        assertFalse("Nested child under blocked subdomain must be blocked", nestedMalwareDecision.isAllowed)

        // Sibling under parent ALLOW remains allowed
        val cleanSubDecision = NetworkPolicyEngine.evaluate("docs.example.com", context, policy)
        assertTrue("Clean subdomain under parent allow remains allowed", cleanSubDecision.isAllowed)

        // Specific child ALLOW beats broad parent BLOCK
        val musicDecision = NetworkPolicyEngine.evaluate("music.youtube.com", context, policy)
        assertTrue("Specific child allow must override parent block", musicDecision.isAllowed)

        val otherYtDecision = NetworkPolicyEngine.evaluate("m.youtube.com", context, policy)
        assertFalse("Other child under blocked parent remains blocked", otherYtDecision.isAllowed)

        // Same specificity precedence inside WHITELIST_ONLY lockdown
        val lockdownPolicy = policy.copy(websiteFilterMode = WebsiteFilterMode.WHITELIST_ONLY)
        assertFalse(NetworkPolicyEngine.evaluate("malware.example.com", context, lockdownPolicy).isAllowed)
        assertTrue(NetworkPolicyEngine.evaluate("docs.example.com", context, lockdownPolicy).isAllowed)
    }

    // =========================================================================
    // 6. SafeSearch & YouTube Restricted Mode Alias Resolution Tests
    // =========================================================================

    @Test
    fun `resolveTargetHost rewrites google and youtube when enabled`() {
        val policy = NetworkPolicy(
            isSafeSearchEnabled = true,
            youtubeRestrictedMode = YouTubeRestrictedMode.STRICT,
            whitelistedDomains = emptySet()
        )

        val context = PolicyContext.forFirewall()
        val googleHost = NetworkPolicyEngine.resolveTargetHost("www.google.com", context, policy)
        assertEquals("forcesafesearch.google.com", googleHost)

        val ytHost = NetworkPolicyEngine.resolveTargetHost("www.youtube.com", context, policy)
        assertEquals("restrict.youtube.com", ytHost)

        // Non-rewritten host remains untouched
        val otherHost = NetworkPolicyEngine.resolveTargetHost("example.com", context, policy)
        assertEquals("example.com", otherHost)
    }

    @Test
    fun `resolveTargetHost respects explicit whitelist and bypasses rewrite`() {
        val policy = NetworkPolicy(
            isSafeSearchEnabled = true,
            youtubeRestrictedMode = YouTubeRestrictedMode.STRICT,
            whitelistedDomains = setOf("google.com")
        )

        val context = PolicyContext.forFirewall()
        val googleHost = NetworkPolicyEngine.resolveTargetHost("www.google.com", context, policy)
        assertEquals("www.google.com", googleHost)
    }

    // =========================================================================
    // 7. Firewall UID & Package Block Tests
    // =========================================================================

    @Test
    fun `firewall traffic blocks by UID or package`() {
        val policy = NetworkPolicy(
            blockedUids = setOf(10150),
            blockedPackages = setOf("com.blocked.app")
        )

        val uidContext = PolicyContext.forFirewall(appUid = 10150, appPackage = "com.allowed.app")
        val uidDecision = NetworkPolicyEngine.evaluate("example.com", uidContext, policy)
        assertFalse(uidDecision.isAllowed)
        assertEquals("App Firewall", uidDecision.reason)
        assertEquals(PolicyDecision.BlockCategory.APP_FIREWALL, (uidDecision as PolicyDecision.Block).category)

        val pkgContext = PolicyContext.forFirewall(appUid = 10151, appPackage = "com.blocked.app")
        val pkgDecision = NetworkPolicyEngine.evaluate("example.com", pkgContext, policy)
        assertFalse(pkgDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.APP_FIREWALL, (pkgDecision as PolicyDecision.Block).category)
    }

    // =========================================================================
    // 8. Hotspot Client & Quota Parity Tests
    // =========================================================================

    @Test
    fun `hotspot traffic honors client ban and data quota`() {
        val policy = NetworkPolicy(
            hotspotApplyNetGateFilters = true,
            hotspotBlockedClientIps = setOf("192.168.43.50"),
            hotspotClientCaps = mapOf("192.168.43.60" to 1_000_000L) // 1 MB cap
        )

        // 1. Banned client IP
        val bannedContext = PolicyContext.forHotspotClient(clientIp = "192.168.43.50")
        val bannedDecision = NetworkPolicyEngine.evaluate("example.com", bannedContext, policy)
        assertFalse("Banned client IP must be rejected", bannedDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.HOTSPOT_CLIENT_BANNED, (bannedDecision as PolicyDecision.Block).category)

        // 2. Client under quota
        val underQuotaContext = PolicyContext.forHotspotClient(
            clientIp = "192.168.43.60",
            clientBytesSent = 200_000L,
            clientBytesReceived = 300_000L
        )
        val underQuotaDecision = NetworkPolicyEngine.evaluate("example.com", underQuotaContext, policy)
        assertTrue("Client under quota must be allowed", underQuotaDecision.isAllowed)

        // 3. Client exceeding quota
        val overQuotaContext = PolicyContext.forHotspotClient(
            clientIp = "192.168.43.60",
            clientBytesSent = 600_000L,
            clientBytesReceived = 500_000L
        )
        val overQuotaDecision = NetworkPolicyEngine.evaluate("example.com", overQuotaContext, policy)
        assertFalse("Client exceeding quota must be blocked", overQuotaDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.HOTSPOT_DATA_CAP, (overQuotaDecision as PolicyDecision.Block).category)
    }

    @Test
    fun `hotspot bypasses content and DoH filters when hotspotApplyNetGateFilters is disabled while enforcing client bans and caps`() {
        val policy = NetworkPolicy(
            hotspotApplyNetGateFilters = false,
            hotspotBlockedClientIps = setOf("192.168.43.50"),
            hotspotClientCaps = mapOf("192.168.43.60" to 1_000L),
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            blockedDomains = setOf("evil.com"),
            isAdBlockerEnabled = true,
            isSafeSearchEnabled = true
        )

        // Unbanned client under quota bypasses website blocklist, DoH canaries, and SafeSearch rewrites
        val normalClient = PolicyContext.forHotspotClient(
            clientIp = "192.168.43.99",
            clientBytesSent = 100L,
            clientBytesReceived = 100L
        )
        val siteDecision = NetworkPolicyEngine.evaluate("evil.com", normalClient, policy)
        assertTrue("Hotspot direct relay allows site filtering bypass when filters disabled", siteDecision.isAllowed)
        assertEquals("Hotspot Direct Relay", siteDecision.reason)

        val dohDecision = NetworkPolicyEngine.evaluate("dns.google", normalClient, policy)
        assertTrue("Hotspot direct relay allows DoH endpoint when filters disabled", dohDecision.isAllowed)
        assertEquals("Hotspot Direct Relay", dohDecision.reason)

        assertEquals("www.google.com", NetworkPolicyEngine.resolveTargetHost("www.google.com", normalClient, policy))

        // Banned client and over-cap client remain blocked even when NetGate content filters are off
        val bannedClient = PolicyContext.forHotspotClient(clientIp = "192.168.43.50")
        val bannedDecision = NetworkPolicyEngine.evaluate("evil.com", bannedClient, policy)
        assertFalse("Banned client remains blocked when filters disabled", bannedDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.HOTSPOT_CLIENT_BANNED, (bannedDecision as PolicyDecision.Block).category)

        val overCapClient = PolicyContext.forHotspotClient(
            clientIp = "192.168.43.60",
            clientBytesSent = 600L,
            clientBytesReceived = 400L
        )
        val capDecision = NetworkPolicyEngine.evaluate("github.com", overCapClient, policy)
        assertFalse("Over-cap client remains blocked when filters disabled", capDecision.isAllowed)
        assertEquals(PolicyDecision.BlockCategory.HOTSPOT_DATA_CAP, (capDecision as PolicyDecision.Block).category)
    }

    @Test
    fun `hotspot evaluator delegates trimming and quota logic consistently and concurrent snapshot reads remain coherent`() {
        val reasonBanned = com.aistudio.netgate.service.HotspotPolicyEvaluator.evaluateClientBlockReason(
            clientIp = " 192.168.43.50 ",
            blockedIps = setOf("192.168.43.50"),
            clientCaps = emptyMap(),
            bytesSent = 0L,
            bytesReceived = 0L
        )
        assertEquals("Blocked by NetGate Hotspot Firewall", reasonBanned)

        val reasonQuota = com.aistudio.netgate.service.HotspotPolicyEvaluator.evaluateClientBlockReason(
            clientIp = " 192.168.43.60 ",
            blockedIps = emptySet(),
            clientCaps = mapOf("192.168.43.60" to 1024L),
            bytesSent = 512L,
            bytesReceived = 512L
        )
        assertEquals("Hotspot Data Cap Reached", reasonQuota)

        // Concurrent snapshot swap & read verification
        val ref = java.util.concurrent.atomic.AtomicReference(
            NetworkPolicy(
                websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
                whitelistedDomains = setOf("example.com"),
                blockedDomains = setOf("malware.example.com")
            )
        )
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val writer = Thread {
            repeat(500) { i ->
                val mode = if (i % 2 == 0) WebsiteFilterMode.BLOCKLIST else WebsiteFilterMode.WHITELIST_ONLY
                ref.set(
                    NetworkPolicy(
                        websiteFilterMode = mode,
                        whitelistedDomains = setOf("example.com"),
                        blockedDomains = setOf("malware.example.com")
                    )
                )
            }
        }
        val readers = List(4) {
            Thread {
                runCatching {
                    val ctx = PolicyContext.forFirewall()
                    repeat(500) {
                        val snap = ref.get()
                        assertTrue(NetworkPolicyEngine.evaluate("docs.example.com", ctx, snap).isAllowed)
                        assertFalse(NetworkPolicyEngine.evaluate("malware.example.com", ctx, snap).isAllowed)
                    }
                }.onFailure { errors.add(it) }
            }
        }
        writer.start()
        readers.forEach { it.start() }
        writer.join()
        readers.forEach { it.join() }
        assertTrue("Expected zero concurrency errors: $errors", errors.isEmpty())
    }

    @Test
    fun `firewall and hotspot evaluate parity on shared ad and block rules`() {
        val testAdMatcher = AdBlockMatcherSnapshot(
            blockedDomains = setOf("adtracker.com"),
            exceptionDomains = emptySet()
        )

        val policy = NetworkPolicy(
            hotspotApplyNetGateFilters = true,
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            blockedDomains = setOf("gambling.com"),
            adMatcherSnapshot = testAdMatcher,
            isAdBlockerEnabled = true,
            sniProxyPresetTitle = "Cloudflare Warp"
        )

        val fwContext = PolicyContext.forFirewall()
        val hsContext = PolicyContext.forHotspotClient(clientIp = "192.168.43.100")

        // 1. Ad block parity
        val fwAd = NetworkPolicyEngine.evaluate("adtracker.com", fwContext, policy)
        val hsAd = NetworkPolicyEngine.evaluate("adtracker.com", hsContext, policy)
        assertFalse(fwAd.isAllowed)
        assertFalse(hsAd.isAllowed)
        assertTrue(fwAd.isAdBlock)
        assertTrue(hsAd.isAdBlock)

        // 2. Custom blocklist parity
        val fwSite = NetworkPolicyEngine.evaluate("gambling.com", fwContext, policy)
        val hsSite = NetworkPolicyEngine.evaluate("gambling.com", hsContext, policy)
        assertFalse(fwSite.isAllowed)
        assertFalse(hsSite.isAllowed)

        // 3. Normal allowed site parity
        val fwGood = NetworkPolicyEngine.evaluate("github.com", fwContext, policy)
        val hsGood = NetworkPolicyEngine.evaluate("github.com", hsContext, policy)
        assertTrue(fwGood.isAllowed)
        assertTrue(hsGood.isAllowed)
        assertEquals("SNI Proxy (Cloudflare Warp)", fwGood.reason)
        assertEquals("Hotspot Proxy Allowed", hsGood.reason)
    }

    // =========================================================================
    // 9. Immutable Snapshot Construction from Preferences & Room Entities
    // =========================================================================

    @Test
    fun `NetworkPolicy fromPreferencesAndRules indexes whitelist and blocklist correctly`() {
        val prefs = FirewallPreferencesState(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            isAdBlockerEnabled = true,
            isEssentialPassthroughEnabled = true,
            isYoutubeAdBlockEnabled = true,
            isSafeSearchEnabled = true,
            youtubeRestrictedMode = YouTubeRestrictedMode.MODERATE,
            sniProxyPreset = SniProxyPreset.STANDARD_DIRECT,
            hotspotApplyNetGateFilters = true,
            hotspotBlockedClientIps = setOf("192.168.43.10"),
            hotspotClientCaps = mapOf("192.168.43.20" to 5000L)
        )

        val rules = listOf(
            WebsiteRuleEntity(domain = "https://www.allowed-site.com/index.html", ruleType = WebsiteRuleType.ALLOW),
            WebsiteRuleEntity(domain = "*.blocked-site.com", ruleType = WebsiteRuleType.BLOCK),
            WebsiteRuleEntity(domain = "disabled.com", ruleType = WebsiteRuleType.BLOCK, isEnabled = false)
        )

        val policy = NetworkPolicy.fromPreferencesAndRules(
            prefs = prefs,
            websiteRules = rules,
            adMatcher = AdBlockMatcherSnapshot.EMPTY,
            blockedUids = setOf(1005),
            blockedPackages = setOf("com.bad.app")
        )

        assertTrue(policy.isExplicitlyWhitelisted("allowed-site.com"))
        assertTrue(policy.isExplicitlyWhitelisted("sub.allowed-site.com"))
        assertTrue(policy.isBlockedByWebsiteRule("blocked-site.com"))
        assertTrue(policy.isBlockedByWebsiteRule("sub.blocked-site.com"))
        assertFalse(policy.isBlockedByWebsiteRule("disabled.com")) // Disabled rule is skipped

        assertEquals(setOf(1005), policy.blockedUids)
        assertEquals(setOf("com.bad.app"), policy.blockedPackages)
        assertEquals("Standard Direct", policy.sniProxyPresetTitle)
        assertEquals(5000L, policy.hotspotClientCaps["192.168.43.20"])
    }
}
