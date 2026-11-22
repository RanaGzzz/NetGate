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
import com.example.data.local.AdFilterListDao
import com.example.data.local.AdFilterListEntity
import com.example.data.local.AppNetworkRuleDao
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.AppTimerDao
import com.example.data.local.AppTimerEntity
import com.example.data.local.AppTrafficHistoryEntity
import com.example.data.local.AppTrafficUsageDao
import com.example.data.local.AppTrafficUsageEntity
import com.example.data.local.AppTrafficWithRuleTuple
import com.example.data.local.ConnectionHistoryDao
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.local.VpnGuardProfileDao
import com.example.data.local.VpnGuardProfileEntity
import com.example.data.local.WebsiteRuleDao
import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AdFilterListItem
import com.example.data.model.AppCountdownTimer
import com.example.data.model.BatchAction
import com.example.data.model.ConnectionDetails
import com.example.data.model.DailyTrafficHistoryPoint
import com.example.data.model.DataUsageSummary
import com.example.data.model.DeviceSecurityAuditReport
import com.example.data.model.DnsCheckState
import com.example.data.model.DnsLeakTestState
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.HistoricalUsageTotals
import com.example.data.model.InstalledAppInfo
import com.example.data.model.SiteConnectionRecord
import com.example.data.model.VpnGuardProfile
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.WebsiteRuleItem
import com.example.data.network.DnsConnectionChecker
import com.example.data.network.DnsLeakTester
import com.example.service.AdBlockFilterEngine
import com.example.service.AppTimerScheduler
import com.example.service.ConnectionMonitorHub
import com.example.service.DnsPacketFilter
import com.example.service.SecurityAuditEngine
import com.example.service.VpnRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.URL
import java.util.Calendar

class FirewallRepository(
    private val context: Context,
    private val ruleDao: AppNetworkRuleDao,
    private val trafficUsageDao: AppTrafficUsageDao,
    private val websiteRuleDao: WebsiteRuleDao,
    private val appTimerDao: AppTimerDao,
    private val adFilterListDao: AdFilterListDao,
    private val connectionHistoryDao: ConnectionHistoryDao,
    val preferences: FirewallPreferences,
    vpnGuardProfileDao: VpnGuardProfileDao? = null
) {
    private val guardProfileDao: VpnGuardProfileDao by lazy {
        vpnGuardProfileDao ?: NetGateDatabase.getInstance(context).vpnGuardProfileDao()
    }
    val rulesFlow: Flow<List<AppNetworkRuleEntity>> = ruleDao.observeAllRules()
    val appTimersFlow: Flow<List<AppCountdownTimer>> = appTimerDao.observeAllTimers()
        .map { entities ->
            entities.map { entity ->
                AppCountdownTimer(
                    packageName = entity.packageName,
                    blockWifiOnExpire = entity.blockWifiOnExpire,
                    blockMobileDataOnExpire = entity.blockMobileDataOnExpire,
                    durationMillis = entity.durationMillis,
                    expiresAtMillis = entity.expiresAtMillis,
                    createdAtMillis = entity.createdAtMillis
                )
            }
        }
    val preferencesFlow: Flow<FirewallPreferencesState> = preferences.preferencesFlow
    val websiteRulesFlow: Flow<List<WebsiteRuleItem>> = websiteRuleDao.observeAllRules()
        .map { entities ->
            entities.map { entity ->
                WebsiteRuleItem(
                    domain = entity.domain,
                    isWhitelist = entity.ruleType == WebsiteRuleType.ALLOW,
                    isEnabled = entity.isEnabled,
                    createdAt = entity.createdAt
                )
            }
        }
    val adFilterListsFlow: Flow<List<AdFilterListItem>> = adFilterListDao.observeAllFilterLists()
        .map { entities ->
            entities.map { entity ->
                AdFilterListItem(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    sourceUrl = entity.sourceUrl,
                    category = entity.category,
                    isEnabled = entity.isEnabled,
                    domainCount = entity.domainCount,
                    lastSyncedAt = entity.lastSyncedAt
                )
            }
        }

    val vpnGuardProfilesFlow: Flow<List<VpnGuardProfile>>
        get() = guardProfileDao.observeAllProfiles().map { list ->
            list.map { it.toModel() }
        }

    suspend fun saveVpnGuardProfile(profile: VpnGuardProfile) {
        guardProfileDao.upsertProfile(VpnGuardProfileEntity.fromModel(profile))
    }

    suspend fun updateVpnGuardRouting(
        profileId: String,
        routingMode: VpnGuardRoutingMode,
        targetPackages: Set<String>
    ) {
        guardProfileDao.updateRoutingScope(
            id = profileId,
            routingMode = routingMode.name,
            packagesCsv = targetPackages.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        )
    }

    suspend fun markVpnGuardConnected(profileId: String, timestampMs: Long = System.currentTimeMillis()) {
        guardProfileDao.markConnectedAt(profileId, timestampMs)
    }

    suspend fun deleteVpnGuardProfile(profileId: String) {
        guardProfileDao.deleteProfile(profileId)
    }
    val trafficSummaryFlow: Flow<DataUsageSummary> = trafficUsageDao.observeBlockedVsAllowedSummary()
        .map { tuple ->
            DataUsageSummary(
                blockedTotalBytes = tuple.blockedTotalBytes,
                blockedWifiBytes = tuple.blockedWifiBytes,
                blockedMobileBytes = tuple.blockedMobileBytes,
                allowedTotalBytes = tuple.allowedTotalBytes,
                allowedWifiBytes = tuple.allowedWifiBytes,
                allowedMobileBytes = tuple.allowedMobileBytes,
                totalWifiRxBytes = tuple.totalWifiRxBytes,
                totalWifiTxBytes = tuple.totalWifiTxBytes,
                totalMobileRxBytes = tuple.totalMobileRxBytes,
                totalMobileTxBytes = tuple.totalMobileTxBytes,
                blockedAppsCount = tuple.blockedAppsCount,
                allowedAppsCount = tuple.allowedAppsCount,
                lastSyncedAt = tuple.lastSyncedAt
            )
        }
    val trafficUsageWithRulesFlow: Flow<List<AppTrafficWithRuleTuple>> =
        trafficUsageDao.observeUsageJoinedWithRules()
    val dailyTrafficHistoryFlow: Flow<List<DailyTrafficHistoryPoint>> =
        trafficUsageDao.observeDailyTrafficHistory(limit = 14)
            .map { rows ->
                rows.map { row ->
                    DailyTrafficHistoryPoint(
                        dateEpochDay = row.dateEpochDay,
                        wifiRxBytes = row.wifiRxBytes,
                        wifiTxBytes = row.wifiTxBytes,
                        mobileRxBytes = row.mobileRxBytes,
                        mobileTxBytes = row.mobileTxBytes,
                        appsCount = row.appsCount
                    )
                }.sortedBy { it.dateEpochDay }
            }
    val historicalTotalsFlow: Flow<HistoricalUsageTotals> =
        trafficUsageDao.observeHistoricalTotals()
            .map { tuple ->
                HistoricalUsageTotals(
                    totalWifiRxBytes = tuple.totalWifiRxBytes,
                    totalWifiTxBytes = tuple.totalWifiTxBytes,
                    totalMobileRxBytes = tuple.totalMobileRxBytes,
                    totalMobileTxBytes = tuple.totalMobileTxBytes,
                    daysTrackedCount = tuple.daysTrackedCount
                )
            }
    val connectionHistoryFlow: Flow<List<SiteConnectionRecord>> =
        connectionHistoryDao.observeRecentConnections(limit = 250)
            .map { entities -> entities.map(ConnectionMonitorHub::entityToModel) }
    val flaggedConnectionsCountFlow: Flow<Int> =
        connectionHistoryDao.observeFlaggedConnectionsCount()

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

    suspend fun scheduleAppTimer(
        packageName: String,
        durationMinutes: Int,
        blockWifiOnExpire: Boolean,
        blockMobileDataOnExpire: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if ((!blockWifiOnExpire && !blockMobileDataOnExpire) || durationMinutes <= 0) return
        val durationMs = durationMinutes.toLong() * 60_000L
        appTimerDao.upsertTimer(
            AppTimerEntity(
                packageName = packageName,
                blockWifiOnExpire = blockWifiOnExpire,
                blockMobileDataOnExpire = blockMobileDataOnExpire,
                durationMillis = durationMs,
                expiresAtMillis = nowMillis + durationMs,
                createdAtMillis = nowMillis
            )
        )
        runCatching { AppTimerScheduler.scheduleNextAlarm(context) }
    }

    suspend fun cancelAppTimer(packageName: String) {
        appTimerDao.deleteTimer(packageName)
        runCatching { AppTimerScheduler.scheduleNextAlarm(context) }
    }

    suspend fun processDueTimers(nowMillis: Long = System.currentTimeMillis()): List<String> {
        val expired = AppTimerScheduler.processDueTimersWithDaos(
            ruleDao = ruleDao,
            timerDao = appTimerDao,
            nowMillis = nowMillis
        )
        if (expired.isNotEmpty()) {
            runCatching { AppTimerScheduler.scheduleNextAlarm(context) }
        }
        return expired
    }

    suspend fun addWebsiteRule(rawDomain: String, isWhitelist: Boolean): Result<String> {
        val normalized = DnsPacketFilter.normalizeDomainInput(rawDomain)
            ?: return Result.failure(IllegalArgumentException("Enter a valid domain like example.com"))
        websiteRuleDao.upsertRule(
            WebsiteRuleEntity(
                domain = normalized,
                ruleType = if (isWhitelist) WebsiteRuleType.ALLOW else WebsiteRuleType.BLOCK,
                isEnabled = true,
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(normalized)
    }

    suspend fun setWebsiteRuleEnabled(domain: String, enabled: Boolean) {
        websiteRuleDao.setRuleEnabled(domain, enabled)
    }

    suspend fun deleteWebsiteRule(domain: String) {
        websiteRuleDao.deleteRule(domain)
    }

    suspend fun clearWebsiteRules(isWhitelist: Boolean) {
        websiteRuleDao.clearRulesByType(
            if (isWhitelist) WebsiteRuleType.ALLOW else WebsiteRuleType.BLOCK
        )
    }

    suspend fun ensureDefaultAdFilterListsSeeded() = withContext(Dispatchers.IO) {
        adFilterListDao.insertMissingFilterLists(AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS)
    }

    suspend fun toggleAdFilterList(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        adFilterListDao.setListEnabled(id, enabled)
    }

    suspend fun addCustomAdFilterList(
        rawTitle: String,
        rawUrl: String,
        rawCategory: String
    ): Result<AdFilterListItem> = withContext(Dispatchers.IO) {
        val title = rawTitle.trim().ifEmpty { "Custom Filter List" }
        val url = rawUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalArgumentException("Filter URL must start with https://")
            )
        }
        val parsedHost = runCatching { URL(url).host }.getOrNull().orEmpty()
        if (parsedHost.isBlank() || '.' !in parsedHost) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a valid HTTPS hosts or Adblock list URL")
            )
        }
        val category = rawCategory.trim().ifEmpty { "Custom" }
        val id = "custom_${System.currentTimeMillis()}"
        val entity = AdFilterListEntity(
            id = id,
            title = title,
            description = "Custom filter ($parsedHost)",
            sourceUrl = url,
            category = category,
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        )
        adFilterListDao.upsertFilterLists(listOf(entity))
        Result.success(
            AdFilterListItem(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                sourceUrl = entity.sourceUrl,
                category = entity.category,
                isEnabled = entity.isEnabled,
                domainCount = entity.domainCount,
                lastSyncedAt = entity.lastSyncedAt
            )
        )
    }

    suspend fun deleteCustomAdFilterList(id: String) = withContext(Dispatchers.IO) {
        if (!id.startsWith("custom_")) return@withContext
        adFilterListDao.deleteFilterList(id)
        runCatching { AdBlockFilterEngine.getCacheFile(context, id).delete() }
    }

    suspend fun syncEnabledAdFilterLists(): Result<Int> = withContext(Dispatchers.IO) {
        ensureDefaultAdFilterListsSeeded()
        val allLists = adFilterListDao.getAllFilterListsOnce()
        val enabledLists = allLists.filter { it.isEnabled }
        if (enabledLists.isEmpty()) {
            val snapshot = AdBlockFilterEngine.buildMatcherSnapshot(context, emptyList())
            return@withContext Result.success(snapshot.totalBlockedCount)
        }

        var syncedCount = 0
        var lastError: Throwable? = null
        val now = System.currentTimeMillis()
        for (list in enabledLists) {
            val res = AdBlockFilterEngine.downloadAndCacheFilterList(context, list)
            res.onSuccess { domainCount ->
                adFilterListDao.updateSyncStats(list.id, domainCount, now)
                syncedCount++
            }.onFailure { err ->
                lastError = err
            }
        }

        val updatedLists = adFilterListDao.getAllFilterListsOnce()
        val snapshot = AdBlockFilterEngine.buildMatcherSnapshot(context, updatedLists)

        if (syncedCount == 0 && lastError != null) {
            Result.failure(lastError!!)
        } else {
            Result.success(snapshot.totalBlockedCount)
        }
    }

    suspend fun getLoadedAdDomainsCount(): Int = withContext(Dispatchers.IO) {
        val lists = adFilterListDao.getAllFilterListsOnce()
        AdBlockFilterEngine.getLastLoadedDomainsCount(context, lists)
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

    suspend fun runDnsLeakTest(
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails
    ): DnsLeakTestState = DnsLeakTester.runDiagnostic(
        context = context,
        prefs = prefs,
        vpnState = vpnState,
        connectionDetails = connectionDetails
    )

    suspend fun runLiveDnsCheck(
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails
    ): DnsCheckState = DnsConnectionChecker.runLiveDnsCheck(
        context = context,
        prefs = prefs,
        vpnState = vpnState,
        connectionDetails = connectionDetails
    )

    suspend fun testCloudflareDohLatency(
        dohEndpointUrl: String = "https://1.1.1.1/dns-query"
    ): Result<Int> = withContext(Dispatchers.IO) {
        val sampleQuery = byteArrayOf(
            0x19, 0x73,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0A, 'c'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(),
            'd'.code.toByte(), 'f'.code.toByte(), 'l'.code.toByte(), 'a'.code.toByte(),
            'r'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,
            0x00, 0x01
        )
        try {
            val startNs = System.nanoTime()
            val resp = AdBlockFilterEngine.forwardDnsOverHttps(sampleQuery, dohEndpointUrl)
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            if (resp != null && resp.size >= 12) {
                Result.success(elapsedMs)
            } else {
                Result.failure(IllegalStateException("DoH endpoint unreachable: $dohEndpointUrl"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun clearTrafficHistory() {
        trafficUsageDao.clearHistory()
    }

    suspend fun clearConnectionHistory() = withContext(Dispatchers.IO) {
        connectionHistoryDao.clearAll()
        ConnectionMonitorHub.clearLiveFeed()
    }

    suspend fun runDeviceSecurityAudit(
        recentConnections: List<SiteConnectionRecord>
    ): DeviceSecurityAuditReport = withContext(Dispatchers.IO) {
        SecurityAuditEngine.runDeviceSecurityAudit(context, recentConnections)
    }

    suspend fun setDeepMonitorEnabled(enabled: Boolean) {
        preferences.setDeepMonitorEnabled(enabled)
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
            val wifiByUid = queryRxTxByUid(nsm, ConnectivityManager.TYPE_WIFI, startTime, endTime)
            @Suppress("DEPRECATION")
            val mobileByUid = queryRxTxByUid(nsm, ConnectivityManager.TYPE_MOBILE, startTime, endTime)

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
            val primaryPkgByUid = mutableMapOf<Int, String>()
            val syncedAt = System.currentTimeMillis()
            for ((uid, group) in appsByUid) {
                val primaryPkg = group.firstOrNull { app ->
                    val rule = existingRules[app.packageName]
                    rule != null && (rule.blockWifi || rule.blockMobileData)
                }?.packageName ?: group.first().packageName
                primaryPkgByUid[uid] = primaryPkg

                val wifiStats = wifiByUid[uid] ?: RxTxBytes()
                val mobileStats = mobileByUid[uid] ?: RxTxBytes()

                for (app in group) {
                    val isPrimary = app.packageName == primaryPkg
                    val wRx = if (isPrimary) wifiStats.rxBytes else 0L
                    val wTx = if (isPrimary) wifiStats.txBytes else 0L
                    val mRx = if (isPrimary) mobileStats.rxBytes else 0L
                    val mTx = if (isPrimary) mobileStats.txBytes else 0L

                    entities.add(
                        AppTrafficUsageEntity(
                            packageName = app.packageName,
                            wifiBytes = wRx + wTx,
                            mobileBytes = mRx + mTx,
                            wifiRxBytes = wRx,
                            wifiTxBytes = wTx,
                            mobileRxBytes = mRx,
                            mobileTxBytes = mTx,
                            periodDays = periodDays,
                            syncedAt = syncedAt
                        )
                    )
                }
            }

            trafficUsageDao.replaceSnapshot(entities)

            // Also persist per-day historical Rx/Tx records in Room (preserved across syncs).
            val historyEntries = mutableListOf<AppTrafficHistoryEntity>()
            val daysToSyncHistory = minOf(periodDays, 7).coerceAtLeast(1)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayMidnightMs = cal.timeInMillis
            val tzOffsetMs = cal.timeZone.getOffset(syncedAt).toLong()

            for (offset in 0 until daysToSyncHistory) {
                val dayStart = todayMidnightMs - offset.toLong() * 86_400_000L
                val dayEnd = if (offset == 0) syncedAt else (dayStart + 86_400_000L - 1L)
                val epochDay = (dayStart + tzOffsetMs) / 86_400_000L

                @Suppress("DEPRECATION")
                val dayWifi = queryRxTxByUid(nsm, ConnectivityManager.TYPE_WIFI, dayStart, dayEnd)
                @Suppress("DEPRECATION")
                val dayMobile = queryRxTxByUid(nsm, ConnectivityManager.TYPE_MOBILE, dayStart, dayEnd)

                val activeUids = dayWifi.keys + dayMobile.keys
                for (uid in activeUids) {
                    val pkg = primaryPkgByUid[uid] ?: continue
                    val w = dayWifi[uid] ?: RxTxBytes()
                    val m = dayMobile[uid] ?: RxTxBytes()
                    if (w.total + m.total > 0L) {
                        historyEntries.add(
                            AppTrafficHistoryEntity(
                                dateEpochDay = epochDay,
                                packageName = pkg,
                                wifiRxBytes = w.rxBytes,
                                wifiTxBytes = w.txBytes,
                                mobileRxBytes = m.rxBytes,
                                mobileTxBytes = m.txBytes,
                                recordedAt = syncedAt
                            )
                        )
                    }
                }
            }

            if (historyEntries.isNotEmpty()) {
                trafficUsageDao.upsertHistoryEntries(historyEntries)
            }

            Result.success(entities.size)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class RxTxBytes(
        val rxBytes: Long = 0L,
        val txBytes: Long = 0L
    ) {
        val total: Long get() = rxBytes + txBytes
    }

    private fun queryRxTxByUid(
        nsm: NetworkStatsManager,
        networkType: Int,
        startTime: Long,
        endTime: Long
    ): Map<Int, RxTxBytes> {
        val result = mutableMapOf<Int, RxTxBytes>()
        var stats: NetworkStats? = null
        try {
            stats = nsm.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                if (stats.getNextBucket(bucket)) {
                    val uid = bucket.uid
                    val rx = bucket.rxBytes.coerceAtLeast(0L)
                    val tx = bucket.txBytes.coerceAtLeast(0L)
                    if (rx > 0L || tx > 0L) {
                        val prev = result[uid] ?: RxTxBytes()
                        result[uid] = RxTxBytes(
                            rxBytes = prev.rxBytes + rx,
                            txBytes = prev.txBytes + tx
                        )
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
