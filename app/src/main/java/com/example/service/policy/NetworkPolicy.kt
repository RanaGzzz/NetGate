package com.example.service.policy

import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.YouTubeRestrictedMode
import com.example.service.AdBlockMatcherSnapshot

/**
 * Immutable snapshot of all active filtering rules, preferences, and lists.
 * Allows packet processing loops and proxy servers to perform lock-free, zero-allocation,
 * zero-database evaluations.
 */
data class NetworkPolicy(
    val websiteFilterMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
    val whitelistedDomains: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val rawWebsiteRules: List<WebsiteRuleEntity> = emptyList(),
    val adMatcherSnapshot: AdBlockMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY,
    val isAdBlockerEnabled: Boolean = false,
    val isEssentialPassthroughEnabled: Boolean = true,
    val isYoutubeAdBlockEnabled: Boolean = false,
    val isSafeSearchEnabled: Boolean = false,
    val youtubeRestrictedMode: YouTubeRestrictedMode = YouTubeRestrictedMode.OFF,
    val isHttpsProxyEnabled: Boolean = false,
    val sniProxyPresetTitle: String = "Direct",
    val hotspotApplyNetGateFilters: Boolean = true,
    val hotspotBlockedClientIps: Set<String> = emptySet(),
    val hotspotClientCaps: Map<String, Long> = emptyMap(),
    val blockedUids: Set<Int> = emptySet(),
    val blockedPackages: Set<String> = emptySet()
) {

    /**
     * Checks if [domain] is explicitly whitelisted in website rules.
     * An explicit whitelist rule overrides blocklists, ad filters, and YouTube blocks.
     */
    fun isExplicitlyWhitelisted(domain: String): Boolean {
        if (whitelistedDomains.isEmpty()) return false
        val clean = DomainNormalizer.normalizeHost(domain)
        return DomainNormalizer.matchesAny(clean, whitelistedDomains)
    }

    /**
     * Checks if [domain] is in the custom website blocklist.
     */
    fun isBlockedByWebsiteRule(domain: String): Boolean {
        if (blockedDomains.isEmpty()) return false
        val clean = DomainNormalizer.normalizeHost(domain)
        return DomainNormalizer.matchesAny(clean, blockedDomains)
    }

    companion object {
        val EMPTY = NetworkPolicy()

        /**
         * Builds an immutable [NetworkPolicy] from current preferences, Room rules, and AdBlock snapshot.
         */
        fun fromPreferencesAndRules(
            prefs: FirewallPreferencesState,
            websiteRules: List<WebsiteRuleEntity>,
            adMatcher: AdBlockMatcherSnapshot,
            blockedUids: Set<Int> = emptySet(),
            blockedPackages: Set<String> = emptySet()
        ): NetworkPolicy {
            val whitelist = HashSet<String>()
            val blocklist = HashSet<String>()

            for (rule in websiteRules) {
                if (!rule.isEnabled) continue
                val clean = DomainNormalizer.normalize(rule.domain) ?: DomainNormalizer.normalizeHost(rule.domain)
                if (clean.isEmpty()) continue
                if (rule.ruleType == WebsiteRuleType.ALLOW) {
                    whitelist.add(clean)
                } else if (rule.ruleType == WebsiteRuleType.BLOCK) {
                    blocklist.add(clean)
                }
            }

            return NetworkPolicy(
                websiteFilterMode = prefs.websiteFilterMode,
                whitelistedDomains = whitelist,
                blockedDomains = blocklist,
                rawWebsiteRules = websiteRules,
                adMatcherSnapshot = adMatcher,
                isAdBlockerEnabled = prefs.isAdBlockerEnabled,
                isEssentialPassthroughEnabled = prefs.isEssentialPassthroughEnabled,
                isYoutubeAdBlockEnabled = prefs.isYoutubeAdBlockEnabled,
                isSafeSearchEnabled = prefs.isSafeSearchEnabled,
                youtubeRestrictedMode = prefs.youtubeRestrictedMode,
                isHttpsProxyEnabled = prefs.isHttpsProxyEnabled,
                sniProxyPresetTitle = prefs.sniProxyPreset.title,
                hotspotApplyNetGateFilters = prefs.hotspotApplyNetGateFilters,
                hotspotBlockedClientIps = prefs.hotspotBlockedClientIps,
                hotspotClientCaps = prefs.hotspotClientCaps,
                blockedUids = blockedUids,
                blockedPackages = blockedPackages
            )
        }
    }
}
