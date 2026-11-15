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
    // Wi-Fi specific telemetry
    val wifiRssiDbm: Int? = null,
    val wifiSignalLabel: String? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val wifiRxLinkSpeedMbps: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiBandLabel: String? = null,
    val wifiStandardLabel: String? = null
)

data class FirewallPreferencesState(
    val isFirewallEnabled: Boolean = false,
    val showOfflineApps: Boolean = false,
    val showSystemApps: Boolean = true,
    val defaultBlockWifiForNewRules: Boolean = false,
    val defaultBlockDataForNewRules: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true
)
