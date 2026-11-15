package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.example.data.local.AppNetworkRuleDao
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.FirewallPreferences
import com.example.data.model.ActiveNetworkType
import com.example.data.model.BatchAction
import com.example.data.model.ConnectionDetails
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address

class FirewallRepository(
    private val context: Context,
    private val ruleDao: AppNetworkRuleDao,
    val preferences: FirewallPreferences
) {
    val rulesFlow: Flow<List<AppNetworkRuleEntity>> = ruleDao.observeAllRules()
    val preferencesFlow: Flow<FirewallPreferencesState> = preferences.preferencesFlow

    suspend fun getAllRulesOnce(): List<AppNetworkRuleEntity> = withContext(Dispatchers.IO) {
        ruleDao.getAllRulesOnce()
    }

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        resolveInfos
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo)?.toString()?.trim().orEmpty()
                    .ifEmpty { appInfo.packageName }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val hasInternet = hasInternetPermission(pm, appInfo.packageName)

                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    hasInternetPermission = hasInternet
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    suspend fun loadAppIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    private fun hasInternetPermission(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            pkgInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        }.getOrDefault(true)
    }

    suspend fun setAppRule(
        packageName: String,
        blockWifi: Boolean,
        blockMobileData: Boolean
    ) {
        if (!blockWifi && !blockMobileData) {
            ruleDao.deleteRule(packageName)
        } else {
            ruleDao.upsertRule(
                AppNetworkRuleEntity(
                    packageName = packageName,
                    blockWifi = blockWifi,
                    blockMobileData = blockMobileData,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun applyBatchAction(
        packageNames: Set<String>,
        action: BatchAction
    ) {
        if (packageNames.isEmpty()) return
        val existingMap = ruleDao.getAllRulesOnce().associateBy { it.packageName }
        val now = System.currentTimeMillis()

        when (action) {
            BatchAction.ALLOW_ALL -> {
                packageNames.forEach { ruleDao.deleteRule(it) }
            }
            BatchAction.BLOCK_BOTH -> {
                val updates = packageNames.map { pkg ->
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = true,
                        blockMobileData = true,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
            BatchAction.BLOCK_WIFI -> {
                val updates = packageNames.map { pkg ->
                    val current = existingMap[pkg]
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = true,
                        blockMobileData = current?.blockMobileData ?: false,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
            BatchAction.BLOCK_DATA -> {
                val updates = packageNames.map { pkg ->
                    val current = existingMap[pkg]
                    AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = current?.blockWifi ?: false,
                        blockMobileData = true,
                        updatedAt = now
                    )
                }
                ruleDao.upsertRules(updates)
            }
        }
    }

    suspend fun clearAllRules() {
        ruleDao.clearAllRules()
    }

    fun detectCurrentNetworkType(): ActiveNetworkType {
        return detectConnectionDetails().activeNetworkType
    }

    fun detectConnectionDetails(): ConnectionDetails {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val isWifiRadioOn = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val isMobileDataSettingOn = runCatching {
            Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
        }.getOrDefault(false)

        if (cm == null) {
            return ConnectionDetails(
                isWifiRadioEnabled = isWifiRadioOn,
                isMobileDataEnabled = isMobileDataSettingOn
            )
        }

        var wifiNetwork: Network? = null
        var wifiCaps: NetworkCapabilities? = null
        var cellNetwork: Network? = null
        var cellCaps: NetworkCapabilities? = null
        var otherNetwork: Network? = null
        var otherCaps: NetworkCapabilities? = null

        @Suppress("DEPRECATION")
        val allNetworks = runCatching { cm.allNetworks }.getOrDefault(emptyArray())
        for (net in allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    wifiNetwork = net
                    wifiCaps = caps
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    cellNetwork = net
                    cellCaps = caps
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    otherNetwork = net
                    otherCaps = caps
                }
            }
        }

        // Fallback to activeNetwork if it is not our VPN
        if (wifiNetwork == null && cellNetwork == null && otherNetwork == null) {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            if (active != null && caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        wifiNetwork = active
                        wifiCaps = caps
                    }
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        cellNetwork = active
                        cellCaps = caps
                    }
                    else -> {
                        otherNetwork = active
                        otherCaps = caps
                    }
                }
            }
        }

        val activeType = when {
            wifiNetwork != null -> ActiveNetworkType.WIFI
            cellNetwork != null -> ActiveNetworkType.MOBILE_DATA
            otherNetwork != null -> ActiveNetworkType.OTHER
            else -> ActiveNetworkType.NONE
        }

        val primaryNet = wifiNetwork ?: cellNetwork ?: otherNetwork
        val primaryCaps = wifiCaps ?: cellCaps ?: otherCaps
        val linkProps: LinkProperties? = primaryNet?.let { runCatching { cm.getLinkProperties(it) }.getOrNull() }

        val ipv4 = linkProps?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress
        val ipv6 = linkProps?.linkAddresses
            ?.firstOrNull { it.address is Inet6Address && !it.address.isLinkLocalAddress }
            ?.address?.hostAddress
            ?: linkProps?.linkAddresses?.firstOrNull { it.address is Inet6Address }?.address?.hostAddress

        val gateway = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress

        val dnsServers = linkProps?.dnsServers
            ?.mapNotNull { it.hostAddress }
            ?.distinct()
            .orEmpty()

        val wifiInfo: WifiInfo? = if (wifiNetwork != null) {
            val transportWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiCaps?.transportInfo as? WifiInfo
            } else {
                null
            }
            @Suppress("DEPRECATION")
            transportWifi ?: runCatching { wifiManager?.connectionInfo }.getOrNull()
        } else {
            null
        }

        val rssi = wifiInfo?.rssi?.takeIf { it in -120..-1 }
        val signalLabel = rssi?.let {
            when {
                it >= -55 -> "Excellent"
                it >= -67 -> "Good"
                it >= -75 -> "Fair"
                else -> "Weak"
            }
        }
        val freq = wifiInfo?.frequency?.takeIf { it > 0 }
        val bandLabel = freq?.let {
            when {
                it >= 5925 -> "6 GHz"
                it >= 4900 -> "5 GHz"
                it >= 2400 -> "2.4 GHz"
                else -> "$it MHz"
            }
        }
        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }
        val rxLinkSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo?.rxLinkSpeedMbps?.takeIf { it > 0 }
        } else {
            null
        }
        val wifiStandardLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiInfo != null) {
            when (wifiInfo.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy (802.11a/b/g)"
                else -> null
            }
        } else {
            null
        }

        return ConnectionDetails(
            activeNetworkType = activeType,
            isWifiRadioEnabled = isWifiRadioOn || wifiNetwork != null,
            isWifiConnected = wifiNetwork != null,
            isMobileDataEnabled = isMobileDataSettingOn || cellNetwork != null,
            isMobileDataConnected = cellNetwork != null,
            interfaceName = linkProps?.interfaceName,
            ipv4Address = ipv4,
            ipv6Address = ipv6,
            gatewayAddress = gateway,
            dnsServers = dnsServers,
            isMetered = primaryCaps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false,
            isValidated = primaryCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false,
            downstreamKbps = primaryCaps?.linkDownstreamBandwidthKbps ?: 0,
            upstreamKbps = primaryCaps?.linkUpstreamBandwidthKbps ?: 0,
            wifiRssiDbm = rssi,
            wifiSignalLabel = signalLabel,
            wifiLinkSpeedMbps = linkSpeed,
            wifiRxLinkSpeedMbps = rxLinkSpeed,
            wifiFrequencyMhz = freq,
            wifiBandLabel = bandLabel,
            wifiStandardLabel = wifiStandardLabel
        )
    }

    suspend fun isFirewallEnabledOnce(): Boolean = preferencesFlow.first().isFirewallEnabled
}
