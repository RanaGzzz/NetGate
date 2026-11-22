package com.example.service.policy

import com.example.data.model.WebsiteFilterMode
import com.example.service.AdBlockFilterEngine
import com.example.service.DnsPacketFilter

/**
 * Centralized, thread-safe, pure-logic evaluation engine for network traffic policies.
 * Shared by both [com.example.service.NetGateVpnService] and [com.example.service.HotspotProxyManager].
 *
 * Implements strict rule precedence, zero-allocation domain checks against immutable [NetworkPolicy]
 * snapshots, and explicit differentiation between device-local firewall and hotspot tethered traffic.
 */
object NetworkPolicyEngine {

    /**
     * Evaluates a domain (and optional URL path/query) against an immutable [NetworkPolicy]
     * within the given [PolicyContext].
     *
     * Precedence Order:
     * 1. Hotspot filter bypass check (if Hotspot filtering disabled -> Allow)
     * 2. Hotspot client IP manual block and data cap evaluation
     * 3. Local firewall UID / package blocking
     * 4. Explicit website Whitelist check (ALLOW rules override blocklist, YouTube, and AdBlock)
     * 5. Custom Website Rules (BLOCKLIST or WHITELIST_ONLY lockdown)
     * 6. Anti-DoH bypass canary checks (forces browsers to use NetGate DNS)
     * 7. Essential OS & service passthrough (FCM, Payment Gateways, Captive Portals)
     * 8. YouTube Ad Blocker (domain + URL path)
     * 9. Ad & Tracker Blocker (hosts/adblock list domains + tracking URL paths)
     * 10. Default Allow (with context-specific reason)
     */
    fun evaluate(
        rawDomain: String,
        context: PolicyContext,
        policy: NetworkPolicy
    ): PolicyDecision {
        val domain = DomainNormalizer.normalizeHost(rawDomain)
        if (domain.isEmpty()) {
            return PolicyDecision.Allow(reason = "Empty Host")
        }

        // 1. Hotspot direct relay override
        if (context.isHotspot && !policy.hotspotApplyNetGateFilters) {
            return PolicyDecision.Allow(reason = "Hotspot Direct Relay")
        }

        // 2. Hotspot Client IP & Quota Evaluation
        if (context.isHotspot && !context.clientIp.isNullOrBlank()) {
            val clientBlockReason = evaluateHotspotClientBlock(
                clientIp = context.clientIp,
                bytesSent = context.clientBytesSent,
                bytesReceived = context.clientBytesReceived,
                policy = policy
            )
            if (clientBlockReason != null) {
                val category = if (clientBlockReason.contains("Data Cap", ignoreCase = true)) {
                    PolicyDecision.BlockCategory.HOTSPOT_DATA_CAP
                } else {
                    PolicyDecision.BlockCategory.HOTSPOT_CLIENT_BANNED
                }
                return PolicyDecision.Block(
                    reason = clientBlockReason,
                    isAdBlock = false,
                    category = category
                )
            }
        }

        // 3. Local Firewall UID / Package Blocking
        if (context.isFirewall) {
            val isUidBlocked = context.appUid != null && context.appUid in policy.blockedUids
            val isPkgBlocked = context.appPackage != null && context.appPackage in policy.blockedPackages
            if (isUidBlocked || isPkgBlocked) {
                return PolicyDecision.Block(
                    reason = "App Firewall",
                    isAdBlock = false,
                    category = PolicyDecision.BlockCategory.APP_FIREWALL
                )
            }
        }

        // 4. Explicit Whitelist Check
        val isWhitelisted = policy.isExplicitlyWhitelisted(domain)

        // 5. Website Filter Mode & Blocklist Check
        if (!isWhitelisted && policy.websiteFilterMode != WebsiteFilterMode.OFF) {
            val isBlockedByMode = when (policy.websiteFilterMode) {
                WebsiteFilterMode.OFF -> false
                WebsiteFilterMode.BLOCKLIST -> policy.isBlockedByWebsiteRule(domain)
                WebsiteFilterMode.WHITELIST_ONLY -> true // Whitelist was false above
            }

            if (isBlockedByMode) {
                val reason = if (policy.websiteFilterMode == WebsiteFilterMode.WHITELIST_ONLY) {
                    if (context.isHotspot) "Hotspot Whitelist Lockdown" else "Whitelist Lockdown"
                } else {
                    if (context.isHotspot) "Hotspot Website Blocklist" else "Website Blocklist"
                }
                val category = if (policy.websiteFilterMode == WebsiteFilterMode.WHITELIST_ONLY) {
                    PolicyDecision.BlockCategory.WHITELIST_LOCKDOWN
                } else {
                    PolicyDecision.BlockCategory.WEBSITE_BLOCKLIST
                }
                return PolicyDecision.Block(
                    reason = reason,
                    isAdBlock = false,
                    category = category
                )
            }
        }

        // 6. Anti-DoH Canary Checks (for DNS / Firewall)
        if (!isWhitelisted && DnsPacketFilter.isInternalBypassCanary(domain)) {
            val reason = if (context.isHotspot) "Hotspot DoH Canary" else "DoH Bypass Canary"
            return PolicyDecision.Block(
                reason = reason,
                isAdBlock = false,
                category = PolicyDecision.BlockCategory.DOH_BYPASS_CANARY
            )
        }

        // 7. Essential Passthrough (connectivity checks, payment gateways, push)
        val isPassthrough = policy.isEssentialPassthroughEnabled &&
            policy.adMatcherSnapshot.isEssentialPassthrough(domain)
        if (isPassthrough) {
            val reason = if (context.isHotspot) "Hotspot Passthrough" else "Essential Passthrough"
            return PolicyDecision.Allow(
                reason = reason,
                isWhitelisted = isWhitelisted,
                isPassthrough = true
            )
        }

        // 8. YouTube Ad Blocker
        if (!isWhitelisted && policy.isYoutubeAdBlockEnabled) {
            val isYtDomain = AdBlockFilterEngine.isYoutubeAdDomain(domain)
            val isYtPath = AdBlockFilterEngine.isYoutubeAdUrlPath(context.pathAndQuery)
            if (isYtDomain || isYtPath) {
                val reason = if (context.isHotspot) "Hotspot YouTube Ad Block" else "YouTube Ad Blocker"
                return PolicyDecision.Block(
                    reason = reason,
                    isAdBlock = true,
                    category = PolicyDecision.BlockCategory.YOUTUBE_AD
                )
            }
        }

        // 9. Ad & Tracker Blocker
        val isAdBlockEvaluated = if (context.isHotspot) {
            policy.isAdBlockerEnabled
        } else {
            policy.isAdBlockerEnabled || policy.isHttpsProxyEnabled
        }

        if (!isWhitelisted && isAdBlockEvaluated) {
            val isAdDomain = policy.adMatcherSnapshot.isBlockedByAdFilter(domain)
            val isAdPath = AdBlockFilterEngine.isAdOrTrackerUrlPath(context.pathAndQuery)
            if (isAdDomain || isAdPath) {
                val reason = when {
                    context.isHotspot -> "Hotspot Ad Blocker"
                    isAdPath -> "HTTPS Ad/Tracker Path"
                    context.protocol == "DNS" -> "Ad & Tracker Filter"
                    else -> "HTTPS Ad/Tracker Domain"
                }
                return PolicyDecision.Block(
                    reason = reason,
                    isAdBlock = true,
                    category = PolicyDecision.BlockCategory.AD_TRACKER
                )
            }
        }

        // 10. Default Allow
        val defaultReason = if (context.isHotspot) {
            "Hotspot Proxy Allowed"
        } else {
            "SNI Proxy (${policy.sniProxyPresetTitle})"
        }

        return PolicyDecision.Allow(
            reason = defaultReason,
            isWhitelisted = isWhitelisted,
            isPassthrough = false
        )
    }

    /**
     * Resolves SafeSearch or YouTube restricted mode alias for [rawHost] if configured.
     * Returns the [com.example.service.SafeRewriteResult] metadata or null if no alias applies or host is whitelisted.
     */
    fun resolveSafeSearchRewrite(
        rawHost: String,
        context: PolicyContext,
        policy: NetworkPolicy
    ): com.example.service.SafeRewriteResult? {
        if (context.isHotspot && !policy.hotspotApplyNetGateFilters) {
            return null
        }
        val clean = DomainNormalizer.normalizeHost(rawHost)
        if (clean.isEmpty() || policy.isExplicitlyWhitelisted(clean)) {
            return null
        }
        return DnsPacketFilter.resolveSafeSearchAlias(
            domain = clean,
            safeSearchEnabled = policy.isSafeSearchEnabled,
            youtubeRestrictedMode = policy.youtubeRestrictedMode
        )
    }

    /**
     * Resolves SafeSearch or YouTube restricted mode alias for [rawHost] if configured.
     * Returns rewritten hostname or original [rawHost] if no alias applies.
     */
    fun resolveTargetHost(
        rawHost: String,
        context: PolicyContext,
        policy: NetworkPolicy
    ): String {
        return resolveSafeSearchRewrite(rawHost, context, policy)?.aliasDomain ?: rawHost
    }

    /**
     * Evaluates hotspot client IP against manual blocklist and data cap quotas.
     */
    fun evaluateHotspotClientBlock(
        clientIp: String,
        bytesSent: Long,
        bytesReceived: Long,
        policy: NetworkPolicy
    ): String? {
        val cleanIp = clientIp.trim()
        if (cleanIp in policy.hotspotBlockedClientIps) {
            return "Blocked by NetGate Hotspot Firewall"
        }
        val cap = policy.hotspotClientCaps[cleanIp] ?: 0L
        if (cap > 0L && (bytesSent + bytesReceived) >= cap) {
            return "Hotspot Data Cap Reached"
        }
        return null
    }
}
