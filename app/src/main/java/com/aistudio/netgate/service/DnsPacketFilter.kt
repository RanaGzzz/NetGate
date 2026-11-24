package com.aistudio.netgate.service

import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.service.dns.DnsPacketParser
import com.aistudio.netgate.service.dns.DnsResponseBuilder
import com.aistudio.netgate.service.policy.DomainNormalizer
import java.net.InetAddress

// The DNS models live in `com.aistudio.netgate.service.dns.DnsModels.kt`. These aliases keep the
// established `com.aistudio.netgate.service.ParsedDnsQuery` / `SafeRewriteResult` names working for
// the VPN service, the policy engine and the existing test suite.
typealias ParsedDnsQuery = com.aistudio.netgate.service.dns.ParsedDnsQuery
typealias SafeRewriteResult = com.aistudio.netgate.service.dns.SafeRewriteResult

/**
 * DNS policy facade.
 *
 * Packet parsing/building and upstream transport now live in the `dns` package
 * ([DnsPacketParser], [DnsResponseBuilder], [DnsTransportManager], [DnsEngine]). This object keeps
 * the domain-level DNS policy that the rest of the app already depends on — SafeSearch alias
 * mapping, browser DoH-bypass canaries and website-rule evaluation — and re-exports the packet
 * helpers for backward source compatibility.
 *
 * There is intentionally **no second filtering implementation** here: the runtime pipeline
 * evaluates rules through [com.aistudio.netgate.service.policy.NetworkPolicyEngine].
 */
object DnsPacketFilter {

    private val GOOGLE_SEARCH_DOMAINS_REGEX = Regex(
        "^(?:www\\.)?google\\.(?:com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})$"
    )
    private val BING_SEARCH_DOMAINS = setOf("bing.com", "www.bing.com")
    private val DUCKDUCKGO_DOMAINS = setOf(
        "duckduckgo.com",
        "www.duckduckgo.com",
        "start.duckduckgo.com",
        "html.duckduckgo.com"
    )
    private val BRAVE_SEARCH_DOMAINS = setOf("search.brave.com")
    private val YANDEX_SEARCH_DOMAINS = setOf("yandex.com", "www.yandex.com", "yandex.ru", "www.yandex.ru")
    private val PIXABAY_DOMAINS = setOf("pixabay.com", "www.pixabay.com")
    private val YOUTUBE_RESTRICT_DOMAINS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtubei.googleapis.com",
        "youtube.googleapis.com",
        "www.youtube-nocookie.com"
    )

    // Canary & DoH bootstrap domains that browsers (Chrome, Firefox, Safari/WebKit) query
    // to enable or auto-upgrade DNS-over-HTTPS bypassing local VPN DNS filtering.
    private val DOH_BYPASS_DOMAINS = setOf(
        "use-application-dns.net", // Firefox official DoH disable canary (requires NXDOMAIN)
        "_dns.resolver.arpa",      // RFC 9462 DDR auto-DoH upgrade discovery
        "dns.google",
        "dns.google.com",
        "8888.google",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "doh.opendns.com",
        "dns.nextdns.io",
        "dns.adguard-dns.com",
        "mask.icloud.com",
        "mask-h2.icloud.com"
    )

    // ---- Policy: SafeSearch / YouTube Restricted Mode aliases -------------------------------

    fun resolveSafeSearchAlias(
        domain: String,
        safeSearchEnabled: Boolean,
        youtubeRestrictedMode: YouTubeRestrictedMode
    ): SafeRewriteResult? {
        val normalized = domain.trim().trimEnd('.').lowercase()
        if (normalized.isEmpty()) return null

        val ytTarget = youtubeRestrictedMode.targetHost
        if (ytTarget != null && normalized in YOUTUBE_RESTRICT_DOMAINS) {
            return SafeRewriteResult(
                aliasDomain = ytTarget,
                label = "YouTube ${youtubeRestrictedMode.title}"
            )
        }

        if (!safeSearchEnabled) return null

        return when {
            normalized == "forcesafesearch.google.com" -> null
            GOOGLE_SEARCH_DOMAINS_REGEX.matches(normalized) -> SafeRewriteResult(
                aliasDomain = "forcesafesearch.google.com",
                label = "SafeSearch Google"
            )
            normalized in BING_SEARCH_DOMAINS -> SafeRewriteResult(
                aliasDomain = "strict.bing.com",
                label = "SafeSearch Bing"
            )
            normalized in DUCKDUCKGO_DOMAINS -> SafeRewriteResult(
                aliasDomain = "safe.duckduckgo.com",
                label = "SafeSearch DuckDuckGo"
            )
            normalized in BRAVE_SEARCH_DOMAINS -> SafeRewriteResult(
                aliasDomain = "safesearch.brave.com",
                label = "SafeSearch Brave"
            )
            normalized in PIXABAY_DOMAINS -> SafeRewriteResult(
                aliasDomain = "safesearch.pixabay.com",
                label = "SafeSearch Pixabay"
            )
            normalized in YANDEX_SEARCH_DOMAINS -> SafeRewriteResult(
                aliasDomain = "familysearch.yandex.com",
                label = "SafeSearch Yandex"
            )
            else -> null
        }
    }

    // ---- Policy: domain rules and DoH-bypass canaries ---------------------------------------

    fun normalizeDomainInput(raw: String): String? {
        return DomainNormalizer.normalize(raw)
    }

    fun matchesDomainRule(queriedDomain: String, ruleDomain: String): Boolean {
        return DomainNormalizer.matchesRule(queriedDomain, ruleDomain)
    }

    fun isInternalBypassCanary(domain: String): Boolean {
        val normalized = DomainNormalizer.normalizeHost(domain)
        if (normalized.isEmpty()) return false
        return DomainNormalizer.parentDomainSequence(normalized)
            .any { it in DOH_BYPASS_DOMAINS }
    }

    fun shouldBlockDomain(
        domain: String,
        mode: WebsiteFilterMode,
        rules: List<WebsiteRuleEntity>
    ): Boolean {
        if (mode == WebsiteFilterMode.OFF) return false

        // Always block DoH bypass canaries/endpoints before whitelist checks
        // so browsers fall back to NetGate's DNS filter.
        if (isInternalBypassCanary(domain)) {
            return true
        }

        val normalized = DomainNormalizer.normalizeHost(domain)
        if (normalized.isEmpty()) return false

        val whitelist = HashSet<String>()
        val blocklist = HashSet<String>()
        for (rule in rules) {
            if (!rule.isEnabled) continue
            val cleanRule = DomainNormalizer.normalize(rule.domain)
                ?: DomainNormalizer.normalizeHost(rule.domain)
            if (cleanRule.isEmpty()) continue
            when (rule.ruleType) {
                WebsiteRuleType.ALLOW -> whitelist.add(cleanRule)
                WebsiteRuleType.BLOCK -> blocklist.add(cleanRule)
            }
        }

        for (candidate in DomainNormalizer.parentDomainSequence(normalized)) {
            val isAllow = candidate in whitelist
            val isBlock = candidate in blocklist
            if (isAllow) return false
            if (isBlock) return true
        }

        return mode == WebsiteFilterMode.WHITELIST_ONLY
    }

    // ---- Backward-compatible packet API (delegates to the dns package) -----------------------

    fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        return DnsPacketParser.parseIpv4UdpDnsQuery(packet, length)
    }

    fun parseUdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        return DnsPacketParser.parseUdpDnsQuery(packet, length)
    }

    fun extractQueryType(query: ParsedDnsQuery): Int {
        return DnsPacketParser.extractQueryType(query)
    }

    fun extractResolvedIpsFromDnsResponse(dnsPayload: ByteArray): List<String> {
        return DnsPacketParser.extractResolvedIpsFromDnsResponse(dnsPayload)
    }

    fun ipBytesToInetAddress(ip: ByteArray): InetAddress? {
        return DnsPacketParser.ipBytesToInetAddress(ip)
    }

    fun buildRewrittenQueryPayload(originalQuery: ParsedDnsQuery, aliasDomain: String): ByteArray? {
        return DnsResponseBuilder.buildRewrittenQueryPayload(originalQuery, aliasDomain)
    }

    fun buildRewrittenAnswerPayload(
        originalQuery: ParsedDnsQuery,
        upstreamAliasResponse: ByteArray
    ): ByteArray? {
        return DnsResponseBuilder.buildRewrittenAnswerPayload(originalQuery, upstreamAliasResponse)
    }

    fun buildBlockedDnsResponsePacket(query: ParsedDnsQuery): ByteArray {
        return DnsResponseBuilder.buildBlockedDnsResponsePacket(query)
    }

    fun buildNoDataDnsResponsePacket(query: ParsedDnsQuery): ByteArray {
        return DnsResponseBuilder.buildNoDataDnsResponsePacket(query)
    }

    fun buildDnsResponsePacket(query: ParsedDnsQuery, dnsResponsePayload: ByteArray): ByteArray {
        return DnsResponseBuilder.buildDnsResponsePacket(query, dnsResponsePayload)
    }

    fun buildTcpResetPacket(packet: ByteArray, length: Int): ByteArray? {
        return DnsResponseBuilder.buildTcpResetPacket(packet, length)
    }

    fun buildIcmpUnreachablePacket(packet: ByteArray, length: Int): ByteArray? {
        return DnsResponseBuilder.buildIcmpUnreachablePacket(packet, length)
    }
}
