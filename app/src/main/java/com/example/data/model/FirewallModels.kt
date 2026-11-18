package com.example.data.model

enum class ActiveNetworkType {
    WIFI,
    MOBILE_DATA,
    OTHER,
    NONE
}

enum class AppFilterMode(val label: String) {
    ALL("All Apps"),
    BLOCKED("Restricted"),
    WIFI_BLOCKED("Wi-Fi Off"),
    DATA_BLOCKED("Data Off"),
    ALLOWED("Allowed"),
    TIMERS("Timers")
}

enum class BatchAction {
    BLOCK_WIFI,
    BLOCK_DATA,
    BLOCK_BOTH,
    ALLOW_ALL
}

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Day"),
    DARK("Night")
}

enum class DnsProviderPreset(
    val title: String,
    val subtitle: String,
    val hostname: String
) {
    CLOUDFLARE_FAST(
        title = "Cloudflare 1.1.1.1",
        subtitle = "Fastest encrypted DNS to bypass ISP DNS filtering",
        hostname = "one.one.one.one"
    ),
    CLOUDFLARE_SECURITY(
        title = "Cloudflare Malware Block",
        subtitle = "1.1.1.2 — Blocks malware & phishing domains",
        hostname = "security.cloudflare-dns.com"
    ),
    CLOUDFLARE_FAMILY(
        title = "Cloudflare Family",
        subtitle = "1.1.1.3 — Blocks malware & adult content",
        hostname = "family.cloudflare-dns.com"
    ),
    GOOGLE_DNS(
        title = "Google Public DNS",
        subtitle = "8.8.8.8 — Encrypted DNS-over-TLS",
        hostname = "dns.google"
    ),
    QUAD9_DNS(
        title = "Quad9 Security",
        subtitle = "9.9.9.9 — Threat-blocking encrypted DNS",
        hostname = "dns.quad9.net"
    )
}

enum class UpstreamDnsServer(
    val title: String,
    val subtitle: String,
    val dohUrl: String,
    val primaryUdpIp: String,
    val secondaryUdpIp: String,
    val dotHostname: String
) {
    CLOUDFLARE_STANDARD(
        title = "Cloudflare 1.1.1.1",
        subtitle = "Fastest global encrypted DoH & UDP resolver",
        dohUrl = "https://1.1.1.1/dns-query",
        primaryUdpIp = "1.1.1.1",
        secondaryUdpIp = "1.0.0.1",
        dotHostname = "one.one.one.one"
    ),
    CLOUDFLARE_SECURITY(
        title = "Cloudflare Security (1.1.1.2)",
        subtitle = "Blocks malware & phishing domains upstream",
        dohUrl = "https://security.cloudflare-dns.com/dns-query",
        primaryUdpIp = "1.1.1.2",
        secondaryUdpIp = "1.0.0.2",
        dotHostname = "security.cloudflare-dns.com"
    ),
    CLOUDFLARE_FAMILY(
        title = "Cloudflare Family (1.1.1.3)",
        subtitle = "Blocks malware & adult domains upstream",
        dohUrl = "https://family.cloudflare-dns.com/dns-query",
        primaryUdpIp = "1.1.1.3",
        secondaryUdpIp = "1.0.0.3",
        dotHostname = "family.cloudflare-dns.com"
    ),
    ADGUARD_DNS(
        title = "AdGuard DNS",
        subtitle = "Upstream ad, tracker & phishing filtering",
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        primaryUdpIp = "94.140.14.14",
        secondaryUdpIp = "94.140.15.15",
        dotHostname = "dns.adguard-dns.com"
    ),
    QUAD9_SECURITY(
        title = "Quad9 Threat Shield (9.9.9.9)",
        subtitle = "Security-focused DNSSEC threat blocking",
        dohUrl = "https://dns.quad9.net/dns-query",
        primaryUdpIp = "9.9.9.9",
        secondaryUdpIp = "149.112.112.112",
        dotHostname = "dns.quad9.net"
    ),
    GOOGLE_DNS(
        title = "Google Public DNS (8.8.8.8)",
        subtitle = "Anycast global DoH & UDP resolver",
        dohUrl = "https://dns.google/dns-query",
        primaryUdpIp = "8.8.8.8",
        secondaryUdpIp = "8.8.4.4",
        dotHostname = "dns.google"
    ),
    CUSTOM_DNS(
        title = "Custom Upstream DNS",
        subtitle = "Your own DoH URL or custom UDP resolver IP",
        dohUrl = "",
        primaryUdpIp = "1.1.1.1",
        secondaryUdpIp = "8.8.8.8",
        dotHostname = ""
    )
}

enum class DnsLeakVerdict(val title: String) {
    IDLE("Ready to Test"),
    NO_LEAK("No DNS Leaks Detected"),
    PARTIAL_RISK("Partial Leak / Bypass Risk"),
    LEAK_DETECTED("DNS Leak Risk Detected")
}

enum class DnsLeakCheckStatus {
    PASS,
    WARN,
    FAIL
}

data class DnsLeakCheckItem(
    val title: String,
    val detail: String,
    val status: DnsLeakCheckStatus
)

data class DnsLeakTestState(
    val isRunning: Boolean = false,
    val verdict: DnsLeakVerdict = DnsLeakVerdict.IDLE,
    val summary: String = "Run a live diagnostic to verify VPN DNS routing, IPv6 leak guard, DoH encryption, and egress POP.",
    val dohLatencyMs: Int? = null,
    val egressIp: String? = null,
    val egressColo: String? = null,
    val activeSystemDns: List<String> = emptyList(),
    val checks: List<DnsLeakCheckItem> = emptyList(),
    val lastTestedAtMs: Long = 0L,
    val errorMessage: String? = null
)

data class DnsProbeState(
    val isTesting: Boolean = false,
    val latencyMs: Int? = null,
    val errorMessage: String? = null
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val hasInternetPermission: Boolean
)

enum class TimerTargetNetwork(
    val label: String,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
) {
    BOTH("Wi-Fi & Data", true, true),
    WIFI_ONLY("Wi-Fi Only", true, false),
    DATA_ONLY("Data Only", false, true)
}

data class AppCountdownTimer(
    val packageName: String,
    val blockWifiOnExpire: Boolean,
    val blockMobileDataOnExpire: Boolean,
    val durationMillis: Long,
    val expiresAtMillis: Long,
    val createdAtMillis: Long
) {
    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    fun targetLabel(): String = when {
        blockWifiOnExpire && blockMobileDataOnExpire -> "Wi-Fi & Data"
        blockWifiOnExpire -> "Wi-Fi"
        else -> "Data"
    }
}

data class ManagedApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val hasInternetPermission: Boolean,
    val blockWifi: Boolean,
    val blockMobileData: Boolean,
    val activeTimer: AppCountdownTimer? = null
) {
    val isAnyBlocked: Boolean get() = blockWifi || blockMobileData
    val isFullyBlocked: Boolean get() = blockWifi && blockMobileData

    fun isBlockedOn(networkType: ActiveNetworkType): Boolean = when (networkType) {
        ActiveNetworkType.WIFI -> blockWifi
        ActiveNetworkType.MOBILE_DATA -> blockMobileData
        ActiveNetworkType.OTHER -> blockWifi && blockMobileData
        ActiveNetworkType.NONE -> false
    }
}

data class ConnectionDetails(
    val activeNetworkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val isWifiRadioEnabled: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isMobileDataEnabled: Boolean = false,
    val isMobileDataConnected: Boolean = false,
    val interfaceName: String? = null,
    val ipv4Address: String? = null,
    val ipv6Address: String? = null,
    val gatewayAddress: String? = null,
    val dnsServers: List<String> = emptyList(),
    val isMetered: Boolean = false,
    val isValidated: Boolean = false,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    // Private DNS status
    val isPrivateDnsActive: Boolean = false,
    val privateDnsHostname: String? = null,
    val privateDnsMode: String? = null,
    // Wi-Fi specific telemetry
    val wifiRssiDbm: Int? = null,
    val wifiSignalLabel: String? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val wifiRxLinkSpeedMbps: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiBandLabel: String? = null,
    val wifiStandardLabel: String? = null
) {
    val isCloudflarePrivateDns: Boolean
        get() {
            val host = privateDnsHostname?.lowercase().orEmpty()
            return isPrivateDnsActive && (
                host.contains("one.one.one.one") ||
                    host.contains("cloudflare-dns.com")
                )
        }
}

enum class YouTubeRestrictedMode(
    val title: String,
    val subtitle: String,
    val targetHost: String?
) {
    OFF(
        title = "Off",
        subtitle = "Standard YouTube content without restriction",
        targetHost = null
    ),
    MODERATE(
        title = "Moderate",
        subtitle = "Filters mature videos via restrictmoderate.youtube.com",
        targetHost = "restrictmoderate.youtube.com"
    ),
    STRICT(
        title = "Strict (Kids Safe)",
        subtitle = "Strict family-safe filtering via restrict.youtube.com",
        targetHost = "restrict.youtube.com"
    )
}

enum class SniProxyPreset(
    val title: String,
    val subtitle: String,
    val defaultAdBlocker: Boolean,
    val defaultYoutubeAdBlock: Boolean,
    val defaultSafeSearch: Boolean,
    val defaultYoutubeMode: YouTubeRestrictedMode?,
    val defaultStripTracking: Boolean
) {
    STANDARD_DIRECT(
        title = "Standard Direct",
        subtitle = "Local CONNECT & TLS SNI filter on 127.0.0.1 with tracking cleanup",
        defaultAdBlocker = false,
        defaultYoutubeAdBlock = false,
        defaultSafeSearch = false,
        defaultYoutubeMode = null,
        defaultStripTracking = true
    ),
    AD_TRACKER_SHIELD(
        title = "Ad & YouTube SNI",
        subtitle = "Blocks ad networks, YouTube ad endpoints & strips tracking params",
        defaultAdBlocker = true,
        defaultYoutubeAdBlock = true,
        defaultSafeSearch = false,
        defaultYoutubeMode = null,
        defaultStripTracking = true
    ),
    FAMILY_SAFE(
        title = "Family & Kids Safe",
        subtitle = "Enforces SafeSearch, YouTube Strict Kids Mode & YouTube Ad Block",
        defaultAdBlocker = true,
        defaultYoutubeAdBlock = true,
        defaultSafeSearch = true,
        defaultYoutubeMode = YouTubeRestrictedMode.STRICT,
        defaultStripTracking = true
    ),
    STRICT_PRIVACY(
        title = "Strict Privacy Guard",
        subtitle = "Combines SNI ad/tracker blocking, SafeSearch & URL param stripping",
        defaultAdBlocker = true,
        defaultYoutubeAdBlock = true,
        defaultSafeSearch = true,
        defaultYoutubeMode = null,
        defaultStripTracking = true
    ),
    CUSTOM_UPSTREAM_SNI(
        title = "Custom Upstream SNI",
        subtitle = "Filters SNI locally then forwards CONNECT tunnels to a custom proxy",
        defaultAdBlocker = false,
        defaultYoutubeAdBlock = false,
        defaultSafeSearch = false,
        defaultYoutubeMode = null,
        defaultStripTracking = true
    )
}

enum class StatusBarSpeedStyle(
    val title: String,
    val subtitle: String
) {
    DOWNLOAD_UPLOAD(
        title = "↓ DL + ↑ UL",
        subtitle = "Stacked download & upload speed in status bar"
    ),
    COMBINED(
        title = "Total Speed",
        subtitle = "Combined incoming + outgoing speed"
    ),
    DOWNLOAD_ONLY(
        title = "↓ Download",
        subtitle = "Incoming download speed only"
    )
}

data class ThroughputSample(
    val timestampMs: Long,
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long
)

data class LiveNetworkTelemetry(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val peakDownloadBytesPerSec: Long = 0L,
    val peakUploadBytesPerSec: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val todayWifiRxBytes: Long = 0L,
    val todayWifiTxBytes: Long = 0L,
    val todayMobileRxBytes: Long = 0L,
    val todayMobileTxBytes: Long = 0L,
    val activeNetworkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val activeInterfaceName: String? = null,
    val historySamples: List<ThroughputSample> = emptyList(),
    val isMonitorServiceRunning: Boolean = false,
    val isTrafficStatsSupported: Boolean = true
) {
    val totalBytesPerSec: Long get() = downloadBytesPerSec + uploadBytesPerSec
    val sessionTotalBytes: Long get() = sessionRxBytes + sessionTxBytes
    val todayTotalRxBytes: Long get() = todayWifiRxBytes + todayMobileRxBytes
    val todayTotalTxBytes: Long get() = todayWifiTxBytes + todayMobileTxBytes
    val todayWifiBytes: Long get() = todayWifiRxBytes + todayWifiTxBytes
    val todayMobileBytes: Long get() = todayMobileRxBytes + todayMobileTxBytes
    val todayTotalBytes: Long get() = todayTotalRxBytes + todayTotalTxBytes
}

data class CompactSpeedBadge(
    val valueText: String,
    val unitText: String
)

data class FirewallPreferencesState(
    val isFirewallEnabled: Boolean = false,
    val showOfflineApps: Boolean = false,
    val showSystemApps: Boolean = true,
    val defaultBlockWifiForNewRules: Boolean = false,
    val defaultBlockDataForNewRules: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val websiteFilterMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
    val websiteBypassPackages: Set<String> = emptySet(),
    val isAdBlockerEnabled: Boolean = false,
    val isEssentialPassthroughEnabled: Boolean = true,
    val useDohUpstream: Boolean = true,
    val lifetimeAdsBlockedCount: Long = 0L,
    val selectedUpstreamDns: UpstreamDnsServer = UpstreamDnsServer.CLOUDFLARE_STANDARD,
    val customDohUrl: String = "",
    val customUdpDnsIp: String = "",
    val isHttpsProxyEnabled: Boolean = false,
    val httpsProxyPort: Int = 8899,
    val isProxyStripTrackingParams: Boolean = true,
    val isSafeSearchEnabled: Boolean = false,
    val youtubeRestrictedMode: YouTubeRestrictedMode = YouTubeRestrictedMode.OFF,
    val isYoutubeAdBlockEnabled: Boolean = false,
    val sniProxyPreset: SniProxyPreset = SniProxyPreset.STANDARD_DIRECT,
    val customSniProxyHost: String = "",
    val customSniProxyPort: Int = 8080,
    val isStatusBarSpeedEnabled: Boolean = false,
    val statusBarSpeedStyle: StatusBarSpeedStyle = StatusBarSpeedStyle.DOWNLOAD_UPLOAD,
    val showStatusBarDailyUsage: Boolean = true,
    val isDeepMonitorEnabled: Boolean = true
) {
    fun resolvedDohUrl(): String {
        return if (selectedUpstreamDns == UpstreamDnsServer.CUSTOM_DNS) {
            customDohUrl.trim().takeIf { it.startsWith("https://") }
                ?: UpstreamDnsServer.CLOUDFLARE_STANDARD.dohUrl
        } else {
            selectedUpstreamDns.dohUrl
        }
    }

    fun resolvedUdpDnsServers(): List<String> {
        return if (selectedUpstreamDns == UpstreamDnsServer.CUSTOM_DNS) {
            val customIp = customUdpDnsIp.trim()
            if (customIp.isNotEmpty()) {
                listOf(customIp, "1.1.1.1", "8.8.8.8")
            } else {
                listOf("1.1.1.1", "8.8.8.8")
            }
        } else {
            listOf(selectedUpstreamDns.primaryUdpIp, selectedUpstreamDns.secondaryUdpIp, "1.1.1.1").distinct()
        }
    }

    fun resolvedUpstreamSniProxy(): Pair<String, Int>? {
        if (sniProxyPreset != SniProxyPreset.CUSTOM_UPSTREAM_SNI) return null
        val host = customSniProxyHost.trim()
        if (host.isEmpty()) return null
        return host to customSniProxyPort.coerceIn(1, 65535)
    }
}

enum class AppDestination(val label: String) {
    FIREWALL("Firewall"),
    WEBSITES("Websites"),
    DATA_USAGE("Data Usage"),
    SPEED_TEST("Speed Test"),
    DNS("1.1.1.1 DNS")
}

enum class WebsiteFilterMode(val title: String, val subtitle: String) {
    OFF(
        title = "Off",
        subtitle = "Website filter disabled — only per-app rules run"
    ),
    BLOCKLIST(
        title = "Blocklist",
        subtitle = "Block websites in your Blocklist • Allow all other sites"
    ),
    WHITELIST_ONLY(
        title = "Whitelist Only",
        subtitle = "Block ALL internet websites • Only whitelisted sites & selected apps allowed"
    )
}

enum class WebsiteTabSection(val label: String) {
    AD_FILTERS("Ad Lists"),
    DNS_LOGS("DNS Log"),
    BLOCKLIST("Blocklist"),
    WHITELIST("Whitelist"),
    ALLOWED_APPS("Allowed Apps")
}

enum class DnsQueryStatus(val label: String) {
    ALLOWED("Allowed"),
    BLOCKED_AD("Ad Blocked"),
    BLOCKED_WEBSITE("Site Blocked"),
    BLOCKED_APP("App Blocked"),
    BLOCKED_HTTPS("HTTPS Proxy Blocked"),
    PASSTHROUGH("Passthrough"),
    SAFE_REWRITE("SafeSearch / Kids")
}

data class DnsQueryLogEntry(
    val id: Long,
    val domain: String,
    val status: DnsQueryStatus,
    val reason: String,
    val timestampMs: Long
)

data class AdFilterListItem(
    val id: String,
    val title: String,
    val description: String,
    val sourceUrl: String,
    val category: String,
    val isEnabled: Boolean,
    val domainCount: Int,
    val lastSyncedAt: Long
)

data class WebsiteRuleItem(
    val domain: String,
    val isWhitelist: Boolean,
    val isEnabled: Boolean,
    val createdAt: Long
)

enum class UsageTimeRange(val label: String, val days: Int) {
    LAST_24H("24h", 1),
    LAST_7D("7 Days", 7),
    LAST_30D("30 Days", 30)
}

enum class UsageChartMode(val label: String) {
    TOTAL("Total"),
    SPLIT("Wi-Fi vs Data")
}

enum class UsageFilterCategory(val label: String) {
    ALL("All"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed")
}

data class DataUsageSummary(
    val blockedTotalBytes: Long = 0L,
    val blockedWifiBytes: Long = 0L,
    val blockedMobileBytes: Long = 0L,
    val allowedTotalBytes: Long = 0L,
    val allowedWifiBytes: Long = 0L,
    val allowedMobileBytes: Long = 0L,
    val totalWifiRxBytes: Long = 0L,
    val totalWifiTxBytes: Long = 0L,
    val totalMobileRxBytes: Long = 0L,
    val totalMobileTxBytes: Long = 0L,
    val blockedAppsCount: Int = 0,
    val allowedAppsCount: Int = 0,
    val lastSyncedAt: Long = 0L
) {
    val grandTotalBytes: Long get() = blockedTotalBytes + allowedTotalBytes
    val totalRxBytes: Long get() = totalWifiRxBytes + totalMobileRxBytes
    val totalTxBytes: Long get() = totalWifiTxBytes + totalMobileTxBytes
    val totalWifiBytes: Long get() = totalWifiRxBytes + totalWifiTxBytes
    val totalMobileBytes: Long get() = totalMobileRxBytes + totalMobileTxBytes
}

data class DailyTrafficHistoryPoint(
    val dateEpochDay: Long,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val appsCount: Int
) {
    val wifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileBytes: Long get() = mobileRxBytes + mobileTxBytes
    val rxBytes: Long get() = wifiRxBytes + mobileRxBytes
    val txBytes: Long get() = wifiTxBytes + mobileTxBytes
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

data class HistoricalUsageTotals(
    val totalWifiRxBytes: Long = 0L,
    val totalWifiTxBytes: Long = 0L,
    val totalMobileRxBytes: Long = 0L,
    val totalMobileTxBytes: Long = 0L,
    val daysTrackedCount: Int = 0
) {
    val totalRxBytes: Long get() = totalWifiRxBytes + totalMobileRxBytes
    val totalTxBytes: Long get() = totalWifiTxBytes + totalMobileTxBytes
    val totalWifiBytes: Long get() = totalWifiRxBytes + totalWifiTxBytes
    val totalMobileBytes: Long get() = totalMobileRxBytes + totalMobileTxBytes
    val grandTotalBytes: Long get() = totalWifiBytes + totalMobileBytes
}

data class AppUsageBreakdownItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
    val totalRxBytes: Long get() = wifiRxBytes + mobileRxBytes
    val totalTxBytes: Long get() = wifiTxBytes + mobileTxBytes
    val isBlocked: Boolean get() = blockWifi || blockMobileData
}

fun formatDataBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    return when {
        bytes >= tb -> String.format(java.util.Locale.US, "%.2f TB", bytes / tb)
        bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatCountdownTimer(remainingMillis: Long): String {
    val totalSeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatSpeedBytesPerSec(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 B/s"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytesPerSec >= gb -> String.format(java.util.Locale.US, "%.2f GB/s", bytesPerSec / gb)
        bytesPerSec >= 10.0 * mb -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / mb)
        bytesPerSec >= mb -> String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSec / mb)
        bytesPerSec >= 10.0 * kb -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / kb)
        bytesPerSec >= kb -> String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / kb)
        else -> "$bytesPerSec B/s"
    }
}

fun formatCompactStatusBarSpeed(bytesPerSec: Long): CompactSpeedBadge {
    if (bytesPerSec <= 0L) return CompactSpeedBadge("0", "B/s")
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytesPerSec >= gb -> CompactSpeedBadge(
            String.format(java.util.Locale.US, "%.1f", bytesPerSec / gb),
            "G/s"
        )
        bytesPerSec >= 100.0 * mb -> CompactSpeedBadge(
            String.format(java.util.Locale.US, "%.0f", bytesPerSec / mb),
            "M/s"
        )
        bytesPerSec >= mb -> CompactSpeedBadge(
            String.format(java.util.Locale.US, "%.1f", bytesPerSec / mb),
            "M/s"
        )
        bytesPerSec >= 10.0 * kb -> CompactSpeedBadge(
            String.format(java.util.Locale.US, "%.0f", bytesPerSec / kb),
            "K/s"
        )
        bytesPerSec >= kb -> CompactSpeedBadge(
            String.format(java.util.Locale.US, "%.1f", bytesPerSec / kb),
            "K/s"
        )
        else -> CompactSpeedBadge(bytesPerSec.toString(), "B/s")
    }
}

fun formatSingleLineCompactSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytesPerSec >= gb -> String.format(java.util.Locale.US, "%.1fG", bytesPerSec / gb)
        bytesPerSec >= 100.0 * mb -> String.format(java.util.Locale.US, "%.0fM", bytesPerSec / mb)
        bytesPerSec >= mb -> String.format(java.util.Locale.US, "%.1fM", bytesPerSec / mb)
        bytesPerSec >= 10.0 * kb -> String.format(java.util.Locale.US, "%.0fK", bytesPerSec / kb)
        bytesPerSec >= kb -> String.format(java.util.Locale.US, "%.1fK", bytesPerSec / kb)
        else -> "${bytesPerSec}B"
    }
}

enum class DataUsageSubTab(val label: String) {
    OVERVIEW("Live & Usage"),
    SITES_REGIONS("Sites & Regions"),
    SECURITY_AUDIT("Security Audit")
}

enum class SiteTrafficFilter(val label: String) {
    ALL("All Sites"),
    SENDING("Sending ↑"),
    RECEIVING("Receiving ↓"),
    BLOCKED("Blocked"),
    FLAGGED("Suspicious")
}

enum class ConnectionRiskLevel(val label: String) {
    SAFE("Verified"),
    NOTICE("Notice"),
    SUSPICIOUS("Suspicious"),
    CRITICAL("High Risk")
}

data class SiteConnectionRecord(
    val id: Long = 0L,
    val domain: String,
    val remoteIp: String,
    val remotePort: Int,
    val protocol: String,
    val appPackage: String,
    val appName: String,
    val countryCode: String,
    val countryName: String,
    val regionName: String,
    val ispOrOrg: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val actionStatus: String,
    val riskLevel: ConnectionRiskLevel,
    val riskReason: String,
    val hitCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long
) {
    val totalBytes: Long get() = bytesSent + bytesReceived
    val countryFlag: String get() = countryCodeToFlagEmoji(countryCode)
    val locationLabel: String
        get() = when {
            regionName.isNotBlank() && countryName.isNotBlank() -> "$regionName, $countryName"
            countryName.isNotBlank() -> countryName
            else -> "Resolving region…"
        }
}

data class CountryTrafficSummary(
    val countryCode: String,
    val countryName: String,
    val regionSample: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val sitesCount: Int,
    val hitsCount: Int,
    val suspiciousCount: Int
) {
    val totalBytes: Long get() = bytesSent + bytesReceived
    val countryFlag: String get() = countryCodeToFlagEmoji(countryCode)
}

enum class SecurityAuditStatus {
    SECURE,
    NOTICE,
    WARNING
}

enum class SecurityAuditAction {
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_DEVICE_ADMIN_SETTINGS,
    OPEN_DEVELOPER_SETTINGS,
    OPEN_USAGE_ACCESS,
    FILTER_FLAGGED_SITES
}

data class SecurityAuditCheckItem(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val status: SecurityAuditStatus,
    val actionLabel: String? = null,
    val actionType: SecurityAuditAction? = null
)

data class ActiveSocketInfo(
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val stateLabel: String,
    val uid: Int,
    val appPackage: String,
    val appName: String,
    val countryCode: String,
    val countryName: String,
    val isListening: Boolean,
    val isSuspicious: Boolean,
    val reason: String
) {
    val countryFlag: String get() = countryCodeToFlagEmoji(countryCode)
}

data class DeviceSecurityAuditReport(
    val isScanning: Boolean = false,
    val securityScore: Int = 100,
    val verdictLabel: String = "Ready to Scan",
    val overallStatus: SecurityAuditStatus = SecurityAuditStatus.SECURE,
    val summaryText: String = "Scan active accessibility hooks, device admins, listening ports, high-risk app permissions, and suspicious site endpoints.",
    val checks: List<SecurityAuditCheckItem> = emptyList(),
    val activeSockets: List<ActiveSocketInfo> = emptyList(),
    val flaggedConnectionsCount: Int = 0,
    val lastScannedAt: Long = 0L
)

fun countryCodeToFlagEmoji(code: String): String {
    val clean = code.trim().uppercase()
    if (clean == "LAN") return "\uD83C\uDFE0" // House emoji
    if (clean.length != 2 || !clean[0].isLetter() || !clean[1].isLetter()) {
        return "\uD83C\uDF10" // Globe emoji
    }
    val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}


