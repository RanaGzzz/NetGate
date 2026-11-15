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
    ALLOWED("Allowed")
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

data class ManagedApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val hasInternetPermission: Boolean,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
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

data class FirewallPreferencesState(
    val isFirewallEnabled: Boolean = false,
    val showOfflineApps: Boolean = false,
    val showSystemApps: Boolean = true,
    val defaultBlockWifiForNewRules: Boolean = false,
    val defaultBlockDataForNewRules: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true
)

enum class AppDestination(val label: String) {
    FIREWALL("Firewall"),
    DATA_USAGE("Data Usage")
}

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
    val blockedAppsCount: Int = 0,
    val allowedAppsCount: Int = 0,
    val lastSyncedAt: Long = 0L
) {
    val grandTotalBytes: Long get() = blockedTotalBytes + allowedTotalBytes
}

data class AppUsageBreakdownItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
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
