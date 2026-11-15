package com.example.data.repository

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
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
import android.os.Process
import android.provider.Settings
import com.example.data.local.AppNetworkRuleDao
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.AppTrafficUsageDao
import com.example.data.local.AppTrafficUsageEntity
import com.example.data.local.AppTrafficWithRuleTuple
import com.example.data.local.FirewallPreferences
import com.example.data.model.ActiveNetworkType
import com.example.data.model.BatchAction
import com.example.data.model.ConnectionDetails
import com.example.data.model.DataUsageSummary
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class FirewallRepository(
    private val context: Context,
    private val ruleDao: AppNetworkRuleDao,
    private val trafficUsageDao: AppTrafficUsageDao,
    val preferences: FirewallPreferences
) {
    val rulesFlow: Flow<List<AppNetworkRuleEntity>> = ruleDao.observeAllRules()
    val preferencesFlow: Flow<FirewallPreferencesState> = preferences.preferencesFlow
    val trafficSummaryFlow: Flow<DataUsageSummary> = trafficUsageDao.observeBlockedVsAllowedSummary()
        .map { tuple ->
            DataUsageSummary(
                blockedTotalBytes = tuple.blockedTotalBytes,
                blockedWifiBytes = tuple.blockedWifiBytes,
                blockedMobileBytes = tuple.blockedMobileBytes,
                allowedTotalBytes = tuple.allowedTotalBytes,
                allowedWifiBytes = tuple.allowedWifiBytes,
                allowedMobileBytes = tuple.allowedMobileBytes,
                blockedAppsCount = tuple.blockedAppsCount,
                allowedAppsCount = tuple.allowedAppsCount,
                lastSyncedAt = tuple.lastSyncedAt
            )
        }
    val trafficUsageWithRulesFlow: Flow<List<AppTrafficWithRuleTuple>> =
        trafficUsageDao.observeUsageJoinedWithRules()

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

        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.isPrivateDnsActive == true
        } else {
            false
        }
        val privateDnsServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.privateDnsServerName
        } else {
            null
        }
        val privateDnsMode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        val privateDnsSpecifier = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        }.getOrNull()
        val resolvedPrivateDnsHost = privateDnsServer
            ?: privateDnsSpecifier?.takeIf { privateDnsMode == "hostname" && it.isNotBlank() }

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
            isPrivateDnsActive = privateDnsActive || (privateDnsMode == "hostname" && !resolvedPrivateDnsHost.isNullOrBlank()),
            privateDnsHostname = resolvedPrivateDnsHost,
            privateDnsMode = privateDnsMode,
            wifiRssiDbm = rssi,
            wifiSignalLabel = signalLabel,
            wifiLinkSpeedMbps = linkSpeed,
            wifiRxLinkSpeedMbps = rxLinkSpeed,
            wifiFrequencyMhz = freq,
            wifiBandLabel = bandLabel,
            wifiStandardLabel = wifiStandardLabel
        )
    }

    suspend fun testCloudflareDohLatency(): Result<Int> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL("https://cloudflare-dns.com/dns-query?name=cloudflare.com&type=A")
            val startNs = System.nanoTime()
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("Connection", "close")
            }
            val code = connection.responseCode
            connection.inputStream.use { it.readBytes() }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            if (code in 200..299) {
                Result.success(elapsedMs)
            } else {
                Result.failure(IllegalStateException("HTTP $code from Cloudflare DoH"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    suspend fun isFirewallEnabledOnce(): Boolean = preferencesFlow.first().isFirewallEnabled

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    suspend fun syncNetworkTrafficUsageToRoom(
        installedApps: List<InstalledAppInfo>,
        periodDays: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) {
            return@withContext Result.failure(SecurityException("Usage Access permission required"))
        }
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return@withContext Result.failure(IllegalStateException("NetworkStatsManager unavailable"))

        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - periodDays.toLong() * 86_400_000L

            @Suppress("DEPRECATION")
            val wifiByUid = queryBytesByUid(nsm, ConnectivityManager.TYPE_WIFI, startTime, endTime)
            @Suppress("DEPRECATION")
            val mobileByUid = queryBytesByUid(nsm, ConnectivityManager.TYPE_MOBILE, startTime, endTime)

            val pm = context.packageManager
            val existingRules = ruleDao.getAllRulesOnce().associateBy { it.packageName }

            // Map installed apps to their Linux UID; attribute shared-UID traffic once to avoid double counting.
            val appsByUid = mutableMapOf<Int, MutableList<InstalledAppInfo>>()
            for (app in installedApps) {
                val uid = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(
                            app.packageName,
                            PackageManager.ApplicationInfoFlags.of(0L)
                        ).uid
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(app.packageName, 0).uid
                    }
                }.getOrNull() ?: continue
                appsByUid.getOrPut(uid) { mutableListOf() }.add(app)
            }

            val entities = mutableListOf<AppTrafficUsageEntity>()
            val syncedAt = System.currentTimeMillis()
            for ((uid, group) in appsByUid) {
                // Pick the package with an active rule first (or first alphabetically) as primary owner of the UID's bytes.
                val primaryPkg = group.firstOrNull { app ->
                    val rule = existingRules[app.packageName]
                    rule != null && (rule.blockWifi || rule.blockMobileData)
                }?.packageName ?: group.first().packageName

                val wifiBytes = wifiByUid[uid] ?: 0L
                val mobileBytes = mobileByUid[uid] ?: 0L

                for (app in group) {
                    val isPrimary = app.packageName == primaryPkg
                    entities.add(
                        AppTrafficUsageEntity(
                            packageName = app.packageName,
                            wifiBytes = if (isPrimary) wifiBytes else 0L,
                            mobileBytes = if (isPrimary) mobileBytes else 0L,
                            periodDays = periodDays,
                            syncedAt = syncedAt
                        )
                    )
                }
            }

            trafficUsageDao.replaceSnapshot(entities)
            Result.success(entities.size)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryBytesByUid(
        nsm: NetworkStatsManager,
        networkType: Int,
        startTime: Long,
        endTime: Long
    ): Map<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        var stats: NetworkStats? = null
        try {
            stats = nsm.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                if (stats.getNextBucket(bucket)) {
                    val uid = bucket.uid
                    val bytes = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                    if (bytes > 0L) {
                        result[uid] = (result[uid] ?: 0L) + bytes
                    }
                }
            }
        } catch (_: SecurityException) {
            throw SecurityException("Usage Access permission required")
        } catch (_: Exception) {
            // Best effort if one transport is unavailable on this device
        } finally {
            runCatching { stats?.close() }
        }
        return result
    }
}
