package com.aistudio.netgate.service.policy

import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.service.AdBlockMatcherSnapshot

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

    enum class ResolvedRuleAction {
        ALLOW,
        BLOCK
    }

    /**
     * Resolves the most-specific enabled website rule matching [domain] by walking from exact host
     * up through parent domains.
     *
     * Security precedence:
     * - A more-specific [WebsiteRuleType.BLOCK] (e.g. `malware.example.com`) overrides a broader
     *   parent [WebsiteRuleType.ALLOW] (`example.com`).
     * - A more-specific [WebsiteRuleType.ALLOW] (e.g. `music.youtube.com`) overrides a broader
     *   parent [WebsiteRuleType.BLOCK] (`youtube.com`).
     * - On an exact same-domain tie, [WebsiteRuleType.ALLOW] wins.
     */
    fun resolveWebsiteRuleAction(domain: String): ResolvedRuleAction? {
        if (whitelistedDomains.isEmpty() && blockedDomains.isEmpty()) return null
        val clean = DomainNormalizer.normalizeHost(domain)
        if (clean.isEmpty()) return null
        for (candidate in DomainNormalizer.parentDomainSequence(clean)) {
            val isAllow = candidate in whitelistedDomains
            val isBlock = candidate in blockedDomains
            if (isAllow) return ResolvedRuleAction.ALLOW
            if (isBlock) return ResolvedRuleAction.BLOCK
        }
        return null
    }

    /**
     * Checks if [domain] resolves to an explicit website whitelist rule without being
     * overridden by a more-specific block rule.
     */
    fun isExplicitlyWhitelisted(domain: String): Boolean {
        return resolveWebsiteRuleAction(domain) == ResolvedRuleAction.ALLOW
    }

    /**
     * Checks if [domain] resolves to a custom website block rule.
     */
    fun isBlockedByWebsiteRule(domain: String): Boolean {
        return resolveWebsiteRuleAction(domain) == ResolvedRuleAction.BLOCK
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
            blockedPackages: Set<String> = emptySet(),
            nowMillis: Long = System.currentTimeMillis()
        ): NetworkPolicy {
            val whitelist = HashSet<String>()
            val blocklist = HashSet<String>()

            for (rule in websiteRules) {
                if (!rule.isEnabled) continue
                if (rule.expiresAtMillis in 1..nowMillis) continue
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
