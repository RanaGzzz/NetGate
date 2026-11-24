package com.aistudio.netgate.service

import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode

/**
 * Core per-app firewall routing mode applied to Android [android.net.VpnService.Builder].
 */
enum class FirewallTunnelMode {
    /** Offline or no active rules/filters; TUN interface stays torn down. */
    DISABLED,

    /** Pauses all non-NetGate device traffic while connected to Wi-Fi. */
    GLOBAL_WIFI_LOCK,

    /**
     * Kernel-enforced per-app sinkhole: uses VpnService.Builder.addAllowedApplication(pkg)
     * strictly for blocked packages and routes 0.0.0.0/0 + ::/0 into TUN, leaving all
     * permitted apps completely outside the VPN path at native kernel speed.
     */
    PER_APP_SINKHOLE,

    /**
     * Hybrid DNS/SNI interception + UID-attributed app blocking: intercepts DNS (UDP/53)
     * and DoH/DoT fallback probes while enforcing per-app UID/package blocks across DNS
     * and the local filtering HTTP/CONNECT proxy.
     */
    HYBRID_DNS_AND_APP_FILTER
}

/**
 * Disposition action for a packet trapped by a per-app sinkhole TUN interface.
 */
enum class BlockedPacketAction {
    REJECT_DNS_NXDOMAIN,
    REJECT_TCP_RST,
    REJECT_ICMP_UNREACHABLE,
    DROP_SILENTLY
}

/**
 * Immutable signature capturing only OS-level TUN configuration attributes that require
 * tearing down and re-establishing the [android.os.ParcelFileDescriptor] interface.
 */
data class TunnelConfigSignature(
    val mode: FirewallTunnelMode,
    val networkType: ActiveNetworkType,
    val isHttpsProxyEnabled: Boolean,
    val httpsProxyPort: Int,
    val stripTrackingParams: Boolean,
    val sniProxyPreset: SniProxyPreset,
    val sinkholePackages: List<String>,
    val bypassPackages: Set<String>
)

/**
 * Complete deterministic plan evaluated by [PerAppFirewallEngine] from current network
 * connectivity, Room app rules, and user preferences.
 */
data class TunnelPlan(
    val mode: FirewallTunnelMode,
    val networkType: ActiveNetworkType,
    val blockedPackages: List<String>,
    val blockedUids: Set<Int>,
    val bypassPackages: Set<String>,
    val isHttpsProxyEnabled: Boolean,
    val httpsProxyPort: Int,
    val stripTrackingParams: Boolean,
    val signature: TunnelConfigSignature
) {
    val shouldRunTunnel: Boolean
        get() = mode != FirewallTunnelMode.DISABLED
}

/**
 * Pure-Kotlin core decision engine for NetGate's [android.net.VpnService]-based per-app firewall.
 */
object PerAppFirewallEngine {

    /**
     * Returns sorted, deduplicated package names blocked on [networkType], always excluding
     * [selfPackageName] so NetGate never deadlocks its own control/upstream sockets.
     */
    fun evaluateBlockedPackages(
        networkType: ActiveNetworkType,
        rules: List<AppNetworkRuleEntity>,
        selfPackageName: String
    ): List<String> {
        if (networkType == ActiveNetworkType.NONE || rules.isEmpty()) return emptyList()
        return rules.asSequence()
            .filter { rule ->
                when (networkType) {
                    ActiveNetworkType.WIFI -> rule.blockWifi
                    ActiveNetworkType.MOBILE_DATA -> rule.blockMobileData
                    ActiveNetworkType.OTHER -> rule.blockWifi && rule.blockMobileData
                    ActiveNetworkType.NONE -> false
                }
            }
            .map { it.packageName }
            .filter { it.isNotBlank() && it != selfPackageName }
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Builds the complete [TunnelPlan] determining TUN routing mode, allowed/disallowed apps,
     * UID lookup sets, and whether the OS TUN file descriptor must be rebuilt.
     */
    fun buildTunnelPlan(
        networkType: ActiveNetworkType,
        rules: List<AppNetworkRuleEntity>,
        preferences: FirewallPreferencesState,
        selfPackageName: String,
        isPackageInstalled: (String) -> Boolean,
        resolvePackageUid: (String) -> Int?
    ): TunnelPlan {
        val targetBlockedPackages = evaluateBlockedPackages(
            networkType = networkType,
            rules = rules,
            selfPackageName = selfPackageName
        )
        val installedBlocked = targetBlockedPackages.filter(isPackageInstalled)
        val blockedSet = installedBlocked.toSet()
        val blockedUids = installedBlocked.mapNotNull(resolvePackageUid).toSet()

        val globalWifiLockNow = (networkType == ActiveNetworkType.WIFI && preferences.globalWifiBlocked)
        val effectiveHttpsProxy = preferences.isHttpsProxyEnabled ||
            preferences.dpiBypassMode != com.aistudio.netgate.data.model.DpiBypassMode.OFF
        val needsDnsProxy = !globalWifiLockNow && (
            preferences.websiteFilterMode != WebsiteFilterMode.OFF ||
                preferences.isAdBlockerEnabled ||
                effectiveHttpsProxy ||
                preferences.isSafeSearchEnabled ||
                preferences.youtubeRestrictedMode != YouTubeRestrictedMode.OFF ||
                preferences.isYoutubeAdBlockEnabled ||
                (preferences.isDeepMonitorEnabled && installedBlocked.isEmpty())
            )

        val mode = when {
            networkType == ActiveNetworkType.NONE -> FirewallTunnelMode.DISABLED
            globalWifiLockNow -> FirewallTunnelMode.GLOBAL_WIFI_LOCK
            needsDnsProxy -> FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER
            installedBlocked.isNotEmpty() -> FirewallTunnelMode.PER_APP_SINKHOLE
            else -> FirewallTunnelMode.DISABLED
        }

        // Security invariant: a package marked blocked never receives website/DNS bypass.
        val effectiveBypass = if (mode == FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER) {
            preferences.websiteBypassPackages.asSequence()
                .filter { it.isNotBlank() && it != selfPackageName && it !in blockedSet }
                .filter(isPackageInstalled)
                .toSortedSet()
        } else {
            emptySet()
        }

        val proxyPort = preferences.httpsProxyPort.coerceIn(1024, 65535)
        val httpsProxyActive = mode == FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER &&
            effectiveHttpsProxy

        // In HYBRID_DNS_AND_APP_FILTER, per-app blocks are enforced via UID/package filtering
        // inside DNS + local proxy without tearing down allowed apps' TUN descriptor.
        // In PER_APP_SINKHOLE, the TUN descriptor itself binds exact allowedApplication entries.
        val signature = TunnelConfigSignature(
            mode = mode,
            networkType = networkType,
            isHttpsProxyEnabled = httpsProxyActive,
            httpsProxyPort = proxyPort,
            stripTrackingParams = preferences.isProxyStripTrackingParams,
            sniProxyPreset = preferences.sniProxyPreset,
            sinkholePackages = if (mode == FirewallTunnelMode.PER_APP_SINKHOLE) installedBlocked else emptyList(),
            bypassPackages = effectiveBypass
        )

        return TunnelPlan(
            mode = mode,
            networkType = networkType,
            blockedPackages = installedBlocked,
            blockedUids = blockedUids,
            bypassPackages = effectiveBypass,
            isHttpsProxyEnabled = httpsProxyActive,
            httpsProxyPort = proxyPort,
            stripTrackingParams = preferences.isProxyStripTrackingParams,
            signature = signature
        )
    }

    /**
     * Classifies an IP packet trapped inside a blocked-app or global-lock TUN sinkhole so the
     * app fails cleanly and immediately instead of hanging on OS retransmission timeouts.
     */
    fun classifySinkholePacket(parsed: ParsedIpPacket): BlockedPacketAction {
        return when (parsed.protocol) {
            TunPacketInspector.IPPROTO_UDP -> {
                if (parsed.dstPort == 53) {
                    BlockedPacketAction.REJECT_DNS_NXDOMAIN
                } else {
                    BlockedPacketAction.REJECT_ICMP_UNREACHABLE
                }
            }
            TunPacketInspector.IPPROTO_TCP -> BlockedPacketAction.REJECT_TCP_RST
            else -> BlockedPacketAction.DROP_SILENTLY
        }
    }
}
