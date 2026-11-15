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

data class FirewallPreferencesState(
    val isFirewallEnabled: Boolean = false,
    val showOfflineApps: Boolean = false,
    val showSystemApps: Boolean = true,
    val defaultBlockWifiForNewRules: Boolean = false,
    val defaultBlockDataForNewRules: Boolean = false
)
