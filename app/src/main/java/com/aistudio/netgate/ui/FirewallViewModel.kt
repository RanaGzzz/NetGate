package com.aistudio.netgate.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AdFilterListItem
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.AppFilterMode
import com.aistudio.netgate.data.model.AppIconStyle
import com.aistudio.netgate.data.model.AppUsageBreakdownItem
import com.aistudio.netgate.data.model.BackgroundAnimationStyle
import com.aistudio.netgate.data.model.BatchAction
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.ConnectionRiskLevel
import com.aistudio.netgate.data.model.CountryTrafficSummary
import com.aistudio.netgate.data.model.DailyTrafficHistoryPoint
import com.aistudio.netgate.data.model.DataUsageSubTab
import com.aistudio.netgate.data.model.DataUsageSummary
import com.aistudio.netgate.data.model.DeviceSecurityAuditReport
import com.aistudio.netgate.data.model.DnsCheckState
import com.aistudio.netgate.data.model.DnsLeakTestState
import com.aistudio.netgate.data.model.DnsProbeState
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.HistoricalUsageTotals
import com.aistudio.netgate.data.model.HotspotProxyRuntimeState
import com.aistudio.netgate.data.model.InstalledAppInfo
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.SiteConnectionRecord
import com.aistudio.netgate.data.model.SiteTrafficFilter
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.data.model.TimerTargetNetwork
import com.aistudio.netgate.data.model.UpstreamDnsServer
import com.aistudio.netgate.data.model.UsageChartMode
import com.aistudio.netgate.data.model.UsageFilterCategory
import com.aistudio.netgate.data.model.UsageTimeRange
import com.aistudio.netgate.data.model.VpnGateServer
import com.aistudio.netgate.data.model.VpnGateSortMode
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardProviderType
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.data.model.VpnGuardSubTab
import com.aistudio.netgate.data.model.VpnGuardUiState
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.WebsiteRuleItem
import com.aistudio.netgate.data.model.WifiDiagnosticsUiState
import com.aistudio.netgate.data.model.WifiIdleStatus
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.data.network.DnsConnectionChecker
import com.aistudio.netgate.data.network.SpeedTestPhase
import com.aistudio.netgate.data.network.SpeedTestRunner
import com.aistudio.netgate.data.network.SpeedTestState
import com.aistudio.netgate.data.repository.FirewallRepository
import com.aistudio.netgate.service.AdBlockFilterEngine
import com.aistudio.netgate.service.ConnectionMonitorHub
import com.aistudio.netgate.service.HotspotProxyManager
import com.aistudio.netgate.service.HotspotProxyService
import com.aistudio.netgate.service.NetGateVpnService
import com.aistudio.netgate.service.NetworkMonitorService
import com.aistudio.netgate.service.SystemNetworkMonitor
import com.aistudio.netgate.service.VpnGateClient
import com.aistudio.netgate.service.VpnGuardManager
import com.aistudio.netgate.service.VpnRuntimeState
import com.aistudio.netgate.service.WarpConfigGenerator
import com.aistudio.netgate.service.WifiAutomationManager
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.data.model.AppUpdateStatus
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.VpnDpiLogCategory
import com.aistudio.netgate.data.model.VpnDpiLogEntry
import com.aistudio.netgate.data.network.GitHubReleaseUpdateChecker
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.service.WireGuardConfigHelper
import com.aistudio.netgate.ui.AppIconManager
import com.aistudio.netgate.widget.NetGateWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DataUsageUiState(
    val selectedSubTab: DataUsageSubTab = DataUsageSubTab.OVERVIEW,
    val hasUsageAccess: Boolean = false,
    val isSyncing: Boolean = false,
    val timeRange: UsageTimeRange = UsageTimeRange.LAST_7D,
    val chartMode: UsageChartMode = UsageChartMode.TOTAL,
    val filterCategory: UsageFilterCategory = UsageFilterCategory.ALL,
    val summary: DataUsageSummary = DataUsageSummary(),
    val appItems: List<AppUsageBreakdownItem> = emptyList(),
    val dailyHistory: List<DailyTrafficHistoryPoint> = emptyList(),
    val historicalTotals: HistoricalUsageTotals = HistoricalUsageTotals(),
    val errorMessage: String? = null,
    val liveConnections: List<SiteConnectionRecord> = emptyList(),
    val historicalConnections: List<SiteConnectionRecord> = emptyList(),
    val displayedConnections: List<SiteConnectionRecord> = emptyList(),
    val countrySummaries: List<CountryTrafficSummary> = emptyList(),
    val siteFilter: SiteTrafficFilter = SiteTrafficFilter.ALL,
    val siteSearchQuery: String = "",
    val selectedCountryFilter: String? = null,
    val showLiveSitesFeed: Boolean = true,
    val securityAuditReport: DeviceSecurityAuditReport = DeviceSecurityAuditReport(),
    val wifiDiagnostics: WifiDiagnosticsUiState = WifiDiagnosticsUiState()
) {
    val activeFeedTotalCount: Int
        get() = if (showLiveSitesFeed) liveConnections.size else historicalConnections.size

    val flaggedSitesCount: Int
        get() = (liveConnections + historicalConnections)
            .distinctBy { "${it.domain}|${it.remoteIp}|${it.actionStatus}" }
            .count {
                it.riskLevel == ConnectionRiskLevel.SUSPICIOUS ||
                    it.riskLevel == ConnectionRiskLevel.CRITICAL
            }
}

data class WebsiteBlockerUiState(
    val filterMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
    val isAdBlockerEnabled: Boolean = false,
    val isEssentialPassthroughEnabled: Boolean = true,
    val useDohUpstream: Boolean = true,
    val selectedUpstreamDns: UpstreamDnsServer = UpstreamDnsServer.CLOUDFLARE_STANDARD,
    val isHttpsProxyEnabled: Boolean = false,
    val isHttpsProxyActive: Boolean = false,
    val httpsProxyPort: Int = 8899,
    val isProxyStripTrackingParams: Boolean = true,
    val blockedProxyRequestsCount: Long = 0L,
    val allowedProxyRequestsCount: Long = 0L,
    val adFilterLists: List<AdFilterListItem> = emptyList(),
    val loadedAdDomainsCount: Int = AdBlockFilterEngine.BUILTIN_AD_CORE_DOMAINS.size,
    val isSyncingAdLists: Boolean = false,
    val blockedAdsCount: Long = 0L,
    val lifetimeAdsBlockedCount: Long = 0L,
    val dnsQueryLogs: List<DnsQueryLogEntry> = emptyList(),
    val blocklistRules: List<WebsiteRuleItem> = emptyList(),
    val whitelistRules: List<WebsiteRuleItem> = emptyList(),
    val bypassPackages: Set<String> = emptySet(),
    val blockedDnsCount: Long = 0L,
    val allowedDnsCount: Long = 0L,
    val recentBlockedDomains: List<String> = emptyList(),
    val isSafeSearchEnabled: Boolean = false,
    val youtubeRestrictedMode: YouTubeRestrictedMode = YouTubeRestrictedMode.OFF,
    val isYoutubeAdBlockEnabled: Boolean = false,
    val sniProxyPreset: SniProxyPreset = SniProxyPreset.STANDARD_DIRECT,
    val customSniProxyHost: String = "",
    val customSniProxyPort: Int = 8080,
    val safeRewriteCount: Long = 0L,
    val dpiBypassMode: DpiBypassMode = DpiBypassMode.OFF,
    val dpiFragmentChunkSize: Int = 4,
    val dpiIdleKeepAliveEnabled: Boolean = true,
    val blockQuicForDpiBypass: Boolean = true,
    val antiDnsPoisonEnabled: Boolean = true,
    val dnsTransportMode: DnsTransportMode = DnsTransportMode.DOH_WITH_FALLBACK,
    val dpiBypassedConnectionsCount: Long = 0L,
    val dpiAutopilotLearnedCount: Int = 0,
    val antiDnsPoisonBlockedCount: Long = 0L,
    val quicStrippedCount: Long = 0L
)

data class FirewallUiState(
    val currentDestination: AppDestination = AppDestination.FIREWALL,
    val isLoading: Boolean = true,
    val allManagedApps: List<ManagedApp> = emptyList(),
    val filteredApps: List<ManagedApp> = emptyList(),
    val searchQuery: String = "",
    val filterMode: AppFilterMode = AppFilterMode.ALL,
    val preferences: FirewallPreferencesState = FirewallPreferencesState(),
    val vpnRuntimeState: VpnRuntimeState = VpnRuntimeState(),
    val connectionDetails: ConnectionDetails = ConnectionDetails(),
    val isSelectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val inspectedAppPackage: String? = null,
    val isSettingsOpen: Boolean = false,
    val isConnectionInfoOpen: Boolean = false,
    val isDnsProtectionOpen: Boolean = false,
    val dnsProbeState: DnsProbeState = DnsProbeState(),
    val dnsLeakTestState: DnsLeakTestState = DnsLeakTestState(),
    val dnsCheckState: DnsCheckState = DnsCheckState(),
    val isSpeedTestOpen: Boolean = false,
    val speedTestState: SpeedTestState = SpeedTestState(),
    val dataUsageState: DataUsageUiState = DataUsageUiState(),
    val websiteBlockerState: WebsiteBlockerUiState = WebsiteBlockerUiState(),
    val liveTelemetry: LiveNetworkTelemetry = LiveNetworkTelemetry(),
    val hotspotProxyState: HotspotProxyRuntimeState = HotspotProxyRuntimeState(),
    val wifiIdleStatus: WifiIdleStatus = WifiIdleStatus(),
    val vpnGuardState: VpnGuardUiState = VpnGuardUiState(),
    val appUpdateState: AppUpdateUiState = AppUpdateUiState(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE
    ),
    val isChangelogOpen: Boolean = false,
    val vpnDpiLogs: List<VpnDpiLogEntry> = emptyList(),
    val isVpnDpiLogsOpen: Boolean = false,
    val vpnDpiLogsInitialCategory: VpnDpiLogCategory = VpnDpiLogCategory.ALL,
    val nowMillis: Long = System.currentTimeMillis()
) {
    val effectiveNetworkType: ActiveNetworkType
        get() = if (vpnRuntimeState.isServiceRunning && vpnRuntimeState.activeNetworkType != ActiveNetworkType.NONE) {
            vpnRuntimeState.activeNetworkType
        } else {
            connectionDetails.activeNetworkType
        }

    val wifiBlockedCount: Int
        get() = allManagedApps.count { it.blockWifi }

    val dataBlockedCount: Int
        get() = allManagedApps.count { it.blockMobileData }

    val anyRestrictedCount: Int
        get() = allManagedApps.count { it.isAnyBlocked }

    val activeTimersCount: Int
        get() = allManagedApps.count { it.activeTimer != null }

    val inspectedApp: ManagedApp?
        get() = inspectedAppPackage?.let { pkg -> allManagedApps.find { it.packageName == pkg } }
}

class FirewallViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NetGateDatabase.getInstance(application)
    private val preferences = FirewallPreferences(application)
    private val repository = FirewallRepository(
        context = application,
        ruleDao = database.appNetworkRuleDao(),
        trafficUsageDao = database.appTrafficUsageDao(),
        websiteRuleDao = database.websiteRuleDao(),
        appTimerDao = database.appTimerDao(),
        adFilterListDao = database.adFilterListDao(),
        connectionHistoryDao = database.connectionHistoryDao(),
        preferences = preferences
    )

    private val nowMillisFlow = MutableStateFlow(System.currentTimeMillis())

    private val currentDestinationFlow = MutableStateFlow(AppDestination.FIREWALL)
    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingAppsFlow = MutableStateFlow(true)
    private val searchQueryFlow = MutableStateFlow("")
    private val filterModeFlow = MutableStateFlow(AppFilterMode.ALL)
    private val isSelectionModeFlow = MutableStateFlow(false)
    private val selectedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val inspectedPackageFlow = MutableStateFlow<String?>(null)
    private val isSettingsOpenFlow = MutableStateFlow(false)
    private val isConnectionInfoOpenFlow = MutableStateFlow(false)
    private val isDnsProtectionOpenFlow = MutableStateFlow(false)
    private val dnsProbeStateFlow = MutableStateFlow(DnsProbeState())
    private val dnsLeakTestStateFlow = MutableStateFlow(DnsLeakTestState())
    private val dnsCheckStateFlow = MutableStateFlow(DnsCheckState())
    private val isSpeedTestOpenFlow = MutableStateFlow(false)
    private val speedTestRunner = SpeedTestRunner()
    private val speedTestStateFlow = MutableStateFlow(SpeedTestState())
    private var speedTestJob: Job? = null
    private val connectionDetailsFlow = MutableStateFlow(repository.detectConnectionDetails())

    private val dataUsageSubTabFlow = MutableStateFlow(DataUsageSubTab.OVERVIEW)
    private val hasUsageAccessFlow = MutableStateFlow(repository.hasUsageStatsPermission())
    private val isSyncingUsageFlow = MutableStateFlow(false)
    private val usageTimeRangeFlow = MutableStateFlow(UsageTimeRange.LAST_7D)
    private val usageChartModeFlow = MutableStateFlow(UsageChartMode.TOTAL)
    private val usageFilterFlow = MutableStateFlow(UsageFilterCategory.ALL)
    private val usageErrorFlow = MutableStateFlow<String?>(null)
    private val siteFilterFlow = MutableStateFlow(SiteTrafficFilter.ALL)
    private val siteSearchQueryFlow = MutableStateFlow("")
    private val selectedCountryFilterFlow = MutableStateFlow<String?>(null)
    private val showLiveSitesFeedFlow = MutableStateFlow(true)
    private val securityAuditReportFlow = MutableStateFlow(DeviceSecurityAuditReport())
    private val wifiDiagnosticsStateFlow = MutableStateFlow(WifiDiagnosticsUiState())
    private val isSyncingAdListsFlow = MutableStateFlow(false)

    private val vpnGuardSubTabFlow = MutableStateFlow(VpnGuardSubTab.PROFILES)
    private val selectedVpnGuardProfileIdFlow = MutableStateFlow<String?>(null)
    private val isGeneratingWarpFlow = MutableStateFlow(false)
    private val vpnGateServersFlow = MutableStateFlow<List<VpnGateServer>>(emptyList())
    private val isLoadingVpnGateFlow = MutableStateFlow(false)
    private val vpnGateErrorFlow = MutableStateFlow<String?>(null)
    private val vpnGateLastFetchedAtFlow = MutableStateFlow(0L)
    private val vpnGateSearchQueryFlow = MutableStateFlow("")
    private val vpnGateSortModeFlow = MutableStateFlow(VpnGateSortMode.SCORE)

    private val appUpdateStateFlow = MutableStateFlow(
        AppUpdateUiState(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE
        )
    )
    private val isChangelogOpenFlow = MutableStateFlow(false)
    private val isVpnDpiLogsOpenFlow = MutableStateFlow(false)
    private val vpnDpiLogsInitialCategoryFlow = MutableStateFlow(VpnDpiLogCategory.ALL)
    private val vpnDpiLogMonitor = VpnDpiLogMonitor()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val iconCache = LruCache<String, ImageBitmap>(120)

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectionDetails()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            refreshConnectionDetails()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            refreshConnectionDetails()
        override fun onLost(network: Network) = refreshConnectionDetails()
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshConnectionDetails()
        }
    }
    private var wifiReceiverRegistered = false

    init {
        viewModelScope.launch {
            repository.ensureDefaultAdFilterListsSeeded()
        }
        refreshInstalledApps()
        registerNetworkMonitor()
        observeActiveTimerTicker()
        observeLiveNetworkTelemetry()
        initHotspotAndWifiObservers()
        vpnDpiLogMonitor.startObserving(
            scope = viewModelScope,
            vpnRuntimeFlow = NetGateVpnService.runtimeState,
            vpnGuardRuntimeFlow = VpnGuardManager.runtimeState,
            preferencesFlow = repository.preferencesFlow
        )
        observeWidgetSync()
    }

    private fun observeWidgetSync() {
        viewModelScope.launch {
            combine(
                repository.preferencesFlow.map { prefs ->
                    listOf(
                        prefs.isFirewallEnabled,
                        prefs.dpiBypassMode,
                        prefs.isAdBlockerEnabled,
                        prefs.hotspotProxyEnabled
                    )
                }.distinctUntilChanged(),
                NetGateVpnService.runtimeState.map { it.isServiceRunning }.distinctUntilChanged(),
                HotspotProxyManager.runtimeState.map { it.isProxyRunning }.distinctUntilChanged()
            ) { _, _, _ -> Unit }.collectLatest {
                NetGateWidgetProvider.requestUpdate(getApplication())
            }
        }
    }

    private fun initHotspotAndWifiObservers() {
        HotspotProxyManager.refreshEnvironment(getApplication())
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { prefs ->
                HotspotProxyManager.updatePreferencesOnly(prefs)
                if (prefs.globalWifiTimerExpiresAtMillis > 0L) {
                    while (isActive) {
                        val now = System.currentTimeMillis()
                        nowMillisFlow.value = now
                        if (now >= prefs.globalWifiTimerExpiresAtMillis) {
                            val triggered = WifiAutomationManager.processDueWifiTimer(getApplication(), now)
                            if (triggered) {
                                _snackbarMessages.tryEmit("Wi-Fi Timer expired — Wi-Fi traffic locked")
                            }
                            break
                        }
                        delay(1000L)
                    }
                }
            }
        }
    }

    private fun observeLiveNetworkTelemetry() {
        viewModelScope.launch(Dispatchers.Default) {
            SystemNetworkMonitor.sampleNow(getApplication())
            while (isActive) {
                delay(1000L)
                if (!SystemNetworkMonitor.isServiceActive) {
                    val telemetry = SystemNetworkMonitor.sampleNow(getApplication())
                    val prefs = uiState.value.preferences
                    if (prefs.autoBlockWifiOnIdleEnabled) {
                        WifiAutomationManager.evaluateIdleSample(
                            context = getApplication(),
                            activeNetworkType = telemetry.activeNetworkType,
                            totalBytesPerSec = telemetry.totalBytesPerSec,
                            prefs = prefs
                        )
                    }
                }
            }
        }
    }

    private fun observeActiveTimerTicker() {
        viewModelScope.launch {
            repository.processDueTimers()
            repository.appTimersFlow.collectLatest { timers ->
                if (timers.isEmpty()) return@collectLatest
                while (isActive) {
                    val now = System.currentTimeMillis()
                    nowMillisFlow.value = now
                    if (timers.any { it.expiresAtMillis <= now }) {
                        val expired = repository.processDueTimers(now)
                        if (expired.isNotEmpty()) {
                            val count = expired.size
                            _snackbarMessages.tryEmit(
                                "Timer ended — auto-disabled network for $count app${if (count == 1) "" else "s"}"
                            )
                            break
                        }
                    }
                    delay(1000L)
                }
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = connectivityManager
        if (cm != null) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            runCatching {
                cm.registerNetworkCallback(request, networkCallback)
            }
        }
        runCatching {
            val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            getApplication<Application>().registerReceiver(wifiStateReceiver, filter)
            wifiReceiverRegistered = true
        }
    }

    override fun onCleared() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        if (wifiReceiverRegistered) {
            runCatching {
                getApplication<Application>().unregisterReceiver(wifiStateReceiver)
            }
            wifiReceiverRegistered = false
        }
        super.onCleared()
    }

    private val computedAppsFlow = combine(
        installedAppsFlow,
        repository.rulesFlow,
        repository.appTimersFlow,
        repository.preferencesFlow.map { it.showOfflineApps to it.showSystemApps }.distinctUntilChanged(),
        combine(searchQueryFlow, filterModeFlow) { query, filter -> query to filter }
    ) { installed, rules, timers, (showOffline, showSystem), (query, filter) ->
        val ruleMap = rules.associateBy { it.packageName }
        val timerMap = timers.associateBy { it.packageName }

        val visibleInstalled = installed.filter { app ->
            val passInternet = showOffline || app.hasInternetPermission
            val passSystem = showSystem || !app.isSystemApp
            passInternet && passSystem
        }

        val managedApps = visibleInstalled.map { app ->
            val rule = ruleMap[app.packageName]
            ManagedApp(
                packageName = app.packageName,
                appName = app.appName,
                isSystemApp = app.isSystemApp,
                hasInternetPermission = app.hasInternetPermission,
                blockWifi = rule?.blockWifi ?: false,
                blockMobileData = rule?.blockMobileData ?: false,
                activeTimer = timerMap[app.packageName]
            )
        }

        val queryTrimmed = query.trim().lowercase()
        val filtered = managedApps.filter { app ->
            val matchesQuery = queryTrimmed.isEmpty() ||
                app.appName.lowercase().contains(queryTrimmed) ||
                app.packageName.lowercase().contains(queryTrimmed)

            val matchesFilter = when (filter) {
                AppFilterMode.ALL -> true
                AppFilterMode.BLOCKED -> app.isAnyBlocked
                AppFilterMode.WIFI_BLOCKED -> app.blockWifi
                AppFilterMode.DATA_BLOCKED -> app.blockMobileData
                AppFilterMode.ALLOWED -> !app.isAnyBlocked
                AppFilterMode.TIMERS -> app.activeTimer != null
            }
            matchesQuery && matchesFilter
        }
        managedApps to filtered
    }.flowOn(Dispatchers.Default)

    private val baseDataFlow = combine(
        computedAppsFlow,
        isLoadingAppsFlow,
        nowMillisFlow,
        repository.preferencesFlow,
        NetGateVpnService.runtimeState
    ) { (managedApps, filteredApps), loading, nowMs, prefs, vpnState ->
        BaseSnapshot(managedApps, filteredApps, loading, nowMs, prefs, vpnState)
    }

    private val uiInteractionFlow = combine(
        searchQueryFlow,
        filterModeFlow,
        isSelectionModeFlow,
        selectedPackagesFlow,
        inspectedPackageFlow
    ) { query, filter, selectionMode, selected, inspected ->
        InteractionSnapshot(query, filter, selectionMode, selected, inspected)
    }

    private val dnsUiFlow = combine(
        isDnsProtectionOpenFlow,
        dnsProbeStateFlow,
        dnsLeakTestStateFlow,
        dnsCheckStateFlow
    ) { dnsOpen, dnsProbe, dnsLeak, dnsCheck ->
        DnsUiSnapshot(dnsOpen, dnsProbe, dnsLeak, dnsCheck)
    }

    private val diagnosticsOverlayFlow = combine(
        appUpdateStateFlow,
        isChangelogOpenFlow,
        vpnDpiLogMonitor.logs,
        isVpnDpiLogsOpenFlow,
        vpnDpiLogsInitialCategoryFlow
    ) { appUpdate, changelogOpen, logs, logsOpen, logsCategory ->
        DiagnosticsOverlaySnapshot(
            appUpdateState = appUpdate,
            isChangelogOpen = changelogOpen,
            vpnDpiLogs = logs,
            isVpnDpiLogsOpen = logsOpen,
            vpnDpiLogsInitialCategory = logsCategory
        )
    }

    private val sheetsFlow = combine(
        combine(isSettingsOpenFlow, isConnectionInfoOpenFlow) { s, c -> s to c },
        isSpeedTestOpenFlow,
        speedTestStateFlow,
        dnsUiFlow,
        diagnosticsOverlayFlow
    ) { (settingsOpen, connInfoOpen), speedTestOpen, speedTest, dnsUi, diag ->
        SheetsSnapshot(
            settingsOpen = settingsOpen,
            connInfoOpen = connInfoOpen,
            dnsOpen = dnsUi.dnsOpen,
            dnsProbeState = dnsUi.dnsProbeState,
            dnsLeakTestState = dnsUi.dnsLeakTestState,
            dnsCheckState = dnsUi.dnsCheckState,
            speedTestOpen = speedTestOpen,
            speedTestState = speedTest,
            diagnostics = diag
        )
    }

    private val usageControlsFlow = combine(
        hasUsageAccessFlow,
        isSyncingUsageFlow,
        usageTimeRangeFlow,
        usageChartModeFlow,
        usageFilterFlow
    ) { hasAccess, syncing, range, chartMode, filterCat ->
        UsageControlsSnapshot(hasAccess, syncing, range, chartMode, filterCat)
    }

    private val usageStatsRepoFlow = combine(
        repository.trafficSummaryFlow,
        repository.trafficUsageWithRulesFlow,
        repository.dailyTrafficHistoryFlow,
        repository.historicalTotalsFlow
    ) { summary, rawRows, dailyHistory, historicalTotals ->
        UsageRepoSnapshot(summary, rawRows, dailyHistory, historicalTotals)
    }

    private val siteControlsFlow = combine(
        dataUsageSubTabFlow,
        siteFilterFlow,
        siteSearchQueryFlow,
        selectedCountryFilterFlow,
        showLiveSitesFeedFlow
    ) { subTab, siteFilter, siteQuery, countryFilter, showLive ->
        SiteControlsSnapshot(subTab, siteFilter, siteQuery, countryFilter, showLive)
    }

    private val siteMonitorFlow = combine(
        ConnectionMonitorHub.liveConnectionsFlow,
        repository.connectionHistoryFlow,
        siteControlsFlow,
        securityAuditReportFlow,
        wifiDiagnosticsStateFlow
    ) { live, history, controls, auditReport, wifiDiagnostics ->
        SiteMonitorSnapshot(live, history, controls, auditReport, wifiDiagnostics)
    }

    private val usageStateFlow = combine(
        usageControlsFlow,
        usageStatsRepoFlow,
        installedAppsFlow,
        usageErrorFlow,
        siteMonitorFlow
    ) { controls, repoStats, installedApps, errMsg, siteMonitor ->
        val appMetaMap = installedApps.associateBy { it.packageName }
        val allItems = repoStats.rawRows.map { row ->
            val meta = appMetaMap[row.packageName]
            AppUsageBreakdownItem(
                packageName = row.packageName,
                appName = meta?.appName ?: row.packageName,
                isSystemApp = meta?.isSystemApp ?: false,
                wifiBytes = row.wifiBytes,
                mobileBytes = row.mobileBytes,
                wifiRxBytes = row.wifiRxBytes,
                wifiTxBytes = row.wifiTxBytes,
                mobileRxBytes = row.mobileRxBytes,
                mobileTxBytes = row.mobileTxBytes,
                blockWifi = row.blockWifi,
                blockMobileData = row.blockMobileData
            )
        }
        val filteredItems = when (controls.filterCategory) {
            UsageFilterCategory.ALL -> allItems
            UsageFilterCategory.BLOCKED -> allItems.filter { it.isBlocked }
            UsageFilterCategory.ALLOWED -> allItems.filter { !it.isBlocked }
        }

        val siteCtrls = siteMonitor.controls
        val activeSource = if (siteCtrls.showLive) siteMonitor.live else siteMonitor.history
        val countrySummaries = activeSource
            .groupBy { it.countryCode }
            .map { (code, list) ->
                val first = list.first()
                CountryTrafficSummary(
                    countryCode = code,
                    countryName = first.countryName.ifBlank { code },
                    regionSample = list.firstOrNull { it.regionName.isNotBlank() }?.regionName.orEmpty(),
                    bytesSent = list.sumOf { it.bytesSent },
                    bytesReceived = list.sumOf { it.bytesReceived },
                    sitesCount = list.distinctBy { it.domain }.size,
                    hitsCount = list.sumOf { it.hitCount },
                    suspiciousCount = list.count {
                        it.riskLevel == ConnectionRiskLevel.SUSPICIOUS ||
                            it.riskLevel == ConnectionRiskLevel.CRITICAL
                    }
                )
            }
            .sortedWith(
                compareByDescending<CountryTrafficSummary> { it.totalBytes }
                    .thenByDescending { it.hitsCount }
            )

        val q = siteCtrls.siteQuery.trim().lowercase()
        val filteredSites = activeSource.filter { item ->
            val matchesCountry = siteCtrls.countryFilter == null || item.countryCode == siteCtrls.countryFilter
            val matchesFilter = when (siteCtrls.siteFilter) {
                SiteTrafficFilter.ALL -> true
                SiteTrafficFilter.SENDING -> item.bytesSent > 0L
                SiteTrafficFilter.RECEIVING -> item.bytesReceived > 0L
                SiteTrafficFilter.BLOCKED -> item.actionStatus == "BLOCKED"
                SiteTrafficFilter.FLAGGED -> item.riskLevel == ConnectionRiskLevel.SUSPICIOUS ||
                    item.riskLevel == ConnectionRiskLevel.CRITICAL
            }
            val matchesQuery = q.isEmpty() ||
                item.domain.lowercase().contains(q) ||
                item.remoteIp.lowercase().contains(q) ||
                item.countryName.lowercase().contains(q) ||
                item.regionName.lowercase().contains(q) ||
                item.appName.lowercase().contains(q) ||
                item.ispOrOrg.lowercase().contains(q)
            matchesCountry && matchesFilter && matchesQuery
        }

        DataUsageUiState(
            selectedSubTab = siteCtrls.subTab,
            hasUsageAccess = controls.hasAccess,
            isSyncing = controls.syncing,
            timeRange = controls.timeRange,
            chartMode = controls.chartMode,
            filterCategory = controls.filterCategory,
            summary = repoStats.summary,
            appItems = filteredItems,
            dailyHistory = repoStats.dailyHistory,
            historicalTotals = repoStats.historicalTotals,
            errorMessage = errMsg,
            liveConnections = siteMonitor.live,
            historicalConnections = siteMonitor.history,
            displayedConnections = filteredSites,
            countrySummaries = countrySummaries,
            siteFilter = siteCtrls.siteFilter,
            siteSearchQuery = siteCtrls.siteQuery,
            selectedCountryFilter = siteCtrls.countryFilter,
            showLiveSitesFeed = siteCtrls.showLive,
            securityAuditReport = siteMonitor.auditReport,
            wifiDiagnostics = siteMonitor.wifiDiagnostics
        )
    }

    private val websiteAndAdRepoFlow = combine(
        repository.websiteRulesFlow,
        repository.adFilterListsFlow,
        isSyncingAdListsFlow
    ) { websiteRules, adLists, isSyncingAds ->
        Triple(websiteRules, adLists, isSyncingAds)
    }

    private val hotspotAndIdleFlow = combine(
        HotspotProxyManager.runtimeState,
        WifiAutomationManager.idleStatus
    ) { hotspot, idle ->
        hotspot to idle
    }

    private val vpnGateControlFlow = combine(
        vpnGateServersFlow,
        isLoadingVpnGateFlow,
        vpnGateErrorFlow,
        vpnGateLastFetchedAtFlow,
        combine(vpnGateSearchQueryFlow, vpnGateSortModeFlow) { q, s -> q to s }
    ) { servers, loading, err, fetchedAt, (query, sort) ->
        VpnGateSnapshot(servers, loading, err, fetchedAt, query, sort)
    }

    private val vpnGuardUiFlow = combine(
        vpnGuardSubTabFlow,
        repository.vpnGuardProfilesFlow,
        selectedVpnGuardProfileIdFlow,
        isGeneratingWarpFlow,
        combine(vpnGateControlFlow, VpnGuardManager.runtimeState) { gate, runtime -> gate to runtime }
    ) { subTab, profiles, selectedId, generatingWarp, (gate, runtime) ->
        val resolvedSelectedId = selectedId?.takeIf { id -> profiles.any { it.id == id } }
            ?: runtime.activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id

        val q = gate.searchQuery.trim().lowercase()
        val filteredServers = gate.servers
            .filter { server ->
                q.isEmpty() ||
                    server.countryLong.lowercase().contains(q) ||
                    server.countryShort.lowercase().contains(q) ||
                    server.hostName.lowercase().contains(q) ||
                    server.ip.lowercase().contains(q) ||
                    server.operatorName.lowercase().contains(q)
            }
            .let { list ->
                when (gate.sortMode) {
                    VpnGateSortMode.SCORE -> list.sortedByDescending { it.score }
                    VpnGateSortMode.SPEED -> list.sortedByDescending { it.speedBps }
                    VpnGateSortMode.PING -> list.sortedBy { if (it.pingMs <= 0) Int.MAX_VALUE else it.pingMs }
                    VpnGateSortMode.SESSIONS -> list.sortedBy { it.numVpnSessions }
                }
            }

        VpnGuardUiState(
            selectedSubTab = subTab,
            profiles = profiles,
            selectedProfileId = resolvedSelectedId,
            isGeneratingWarp = generatingWarp,
            vpnGateServers = filteredServers,
            isLoadingVpnGate = gate.isLoading,
            vpnGateError = gate.errorMessage,
            vpnGateLastFetchedAt = gate.lastFetchedAt,
            vpnGateSearchQuery = gate.searchQuery,
            vpnGateSortMode = gate.sortMode,
            runtimeState = runtime
        )
    }

    val uiState: StateFlow<FirewallUiState> = combine(
        baseDataFlow,
        uiInteractionFlow,
        connectionDetailsFlow,
        sheetsFlow,
        combine(
            currentDestinationFlow,
            usageStateFlow,
            websiteAndAdRepoFlow,
            SystemNetworkMonitor.telemetryFlow,
            combine(hotspotAndIdleFlow, vpnGuardUiFlow) { hi, vg -> hi to vg }
        ) { dest, usage, webAndAd, liveTelemetry, (hi, vpnGuard) ->
            TailSnapshot(dest, usage, webAndAd, liveTelemetry, hi.first, hi.second, vpnGuard)
        }
    ) { base, interaction, connDetails, sheets, tail ->
        val destination = tail.destination
        val dataUsage = tail.dataUsage
        val (websiteRules, adLists, isSyncingAds) = tail.webAndAd
        val liveTelemetry = tail.liveTelemetry
        val hotspotState = tail.hotspotProxyState
        val idleStatus = tail.wifiIdleStatus
        val vpnGuardState = tail.vpnGuardState
        val managedApps = base.managedApps
        val filtered = base.filteredApps

        val loadedDomainsCount = if (base.vpnState.loadedAdDomainsCount > 0) {
            base.vpnState.loadedAdDomainsCount
        } else {
            AdBlockFilterEngine.BUILTIN_AD_CORE_DOMAINS.size +
                adLists.filter { it.isEnabled }.sumOf { it.domainCount }
        }

        val websiteState = WebsiteBlockerUiState(
            filterMode = base.prefs.websiteFilterMode,
            isAdBlockerEnabled = base.prefs.isAdBlockerEnabled,
            isEssentialPassthroughEnabled = base.prefs.isEssentialPassthroughEnabled,
            useDohUpstream = base.prefs.useDohUpstream,
            selectedUpstreamDns = base.prefs.selectedUpstreamDns,
            isHttpsProxyEnabled = base.prefs.isHttpsProxyEnabled,
            isHttpsProxyActive = base.vpnState.isHttpsProxyActive,
            httpsProxyPort = base.prefs.httpsProxyPort,
            isProxyStripTrackingParams = base.prefs.isProxyStripTrackingParams,
            blockedProxyRequestsCount = base.vpnState.blockedProxyRequestsCount,
            allowedProxyRequestsCount = base.vpnState.allowedProxyRequestsCount,
            adFilterLists = adLists,
            loadedAdDomainsCount = loadedDomainsCount,
            isSyncingAdLists = isSyncingAds,
            blockedAdsCount = base.vpnState.blockedAdsCount,
            lifetimeAdsBlockedCount = base.prefs.lifetimeAdsBlockedCount,
            dnsQueryLogs = base.vpnState.recentDnsLogs,
            blocklistRules = websiteRules.filter { !it.isWhitelist },
            whitelistRules = websiteRules.filter { it.isWhitelist },
            bypassPackages = base.prefs.websiteBypassPackages,
            blockedDnsCount = base.vpnState.blockedDnsQueriesCount,
            allowedDnsCount = base.vpnState.allowedDnsQueriesCount,
            recentBlockedDomains = base.vpnState.recentBlockedDomains,
            isSafeSearchEnabled = base.prefs.isSafeSearchEnabled,
            youtubeRestrictedMode = base.prefs.youtubeRestrictedMode,
            isYoutubeAdBlockEnabled = base.prefs.isYoutubeAdBlockEnabled,
            sniProxyPreset = base.prefs.sniProxyPreset,
            customSniProxyHost = base.prefs.customSniProxyHost,
            customSniProxyPort = base.prefs.customSniProxyPort,
            safeRewriteCount = base.vpnState.safeRewriteCount,
            dpiBypassMode = base.prefs.dpiBypassMode,
            dpiFragmentChunkSize = base.prefs.dpiFragmentChunkSize,
            dpiIdleKeepAliveEnabled = base.prefs.dpiIdleKeepAliveEnabled,
            blockQuicForDpiBypass = base.prefs.blockQuicForDpiBypass,
            antiDnsPoisonEnabled = base.prefs.antiDnsPoisonEnabled,
            dnsTransportMode = base.prefs.dnsTransportMode,
            dpiBypassedConnectionsCount = base.vpnState.dpiBypassedConnectionsCount,
            dpiAutopilotLearnedCount = base.vpnState.dpiAutopilotLearnedCount,
            antiDnsPoisonBlockedCount = base.vpnState.antiDnsPoisonBlockedCount,
            quicStrippedCount = base.vpnState.quicStrippedCount
        )

        val effectiveDnsCheck = if (sheets.dnsCheckState.checkedAtMs == 0L && !sheets.dnsCheckState.isChecking) {
            DnsConnectionChecker.buildInstantSnapshot(
                prefs = base.prefs,
                vpnState = base.vpnState,
                connectionDetails = connDetails
            )
        } else {
            sheets.dnsCheckState
        }

        FirewallUiState(
            currentDestination = destination,
            isLoading = base.loading,
            allManagedApps = managedApps,
            filteredApps = filtered,
            searchQuery = interaction.query,
            filterMode = interaction.filter,
            preferences = base.prefs,
            vpnRuntimeState = base.vpnState,
            connectionDetails = connDetails,
            isSelectionMode = interaction.selectionMode,
            selectedPackages = interaction.selected,
            inspectedAppPackage = interaction.inspected,
            isSettingsOpen = sheets.settingsOpen,
            isConnectionInfoOpen = sheets.connInfoOpen,
            isDnsProtectionOpen = sheets.dnsOpen,
            dnsProbeState = sheets.dnsProbeState,
            dnsLeakTestState = sheets.dnsLeakTestState,
            dnsCheckState = effectiveDnsCheck,
            isSpeedTestOpen = sheets.speedTestOpen,
            speedTestState = sheets.speedTestState,
            dataUsageState = dataUsage,
            websiteBlockerState = websiteState,
            liveTelemetry = liveTelemetry,
            hotspotProxyState = hotspotState,
            wifiIdleStatus = idleStatus,
            vpnGuardState = vpnGuardState,
            appUpdateState = sheets.diagnostics.appUpdateState,
            isChangelogOpen = sheets.diagnostics.isChangelogOpen,
            vpnDpiLogs = sheets.diagnostics.vpnDpiLogs,
            isVpnDpiLogsOpen = sheets.diagnostics.isVpnDpiLogsOpen,
            vpnDpiLogsInitialCategory = sheets.diagnostics.vpnDpiLogsInitialCategory,
            nowMillis = base.nowMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FirewallUiState()
    )

    fun refreshConnectionDetails() {
        connectionDetailsFlow.value = repository.detectConnectionDetails()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isLoadingAppsFlow.value = true
            val apps = repository.getInstalledApps()
            installedAppsFlow.value = apps
            isLoadingAppsFlow.value = false
            refreshConnectionDetails()
            if (repository.hasUsageStatsPermission()) {
                hasUsageAccessFlow.value = true
                syncDataUsageInternal(apps)
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        currentDestinationFlow.value = destination
        if (destination != AppDestination.FIREWALL && isSelectionModeFlow.value) {
            exitSelectionMode()
        }
        when (destination) {
            AppDestination.DATA_USAGE -> {
                refreshDataUsage()
                if (dataUsageSubTabFlow.value == DataUsageSubTab.SECURITY_AUDIT &&
                    securityAuditReportFlow.value.lastScannedAt == 0L
                ) {
                    runDeviceSecurityAudit()
                } else if (dataUsageSubTabFlow.value == DataUsageSubTab.SPEED_TEST) {
                    refreshConnectionDetails()
                } else if (dataUsageSubTabFlow.value == DataUsageSubTab.WIFI) {
                    refreshWifiDiagnostics()
                }
            }
            AppDestination.DNS -> {
                refreshConnectionDetails()
                if (dnsCheckStateFlow.value.checkedAtMs == 0L) {
                    runDnsCheck()
                }
            }
            AppDestination.VPN_GUARD -> {
                if (vpnGuardSubTabFlow.value == VpnGuardSubTab.VPNGATE && vpnGateServersFlow.value.isEmpty()) {
                    refreshVpnGateServers()
                }
            }
            else -> Unit
        }
    }

    fun selectDataUsageSubTab(tab: DataUsageSubTab) {
        dataUsageSubTabFlow.value = tab
        if (tab == DataUsageSubTab.SECURITY_AUDIT && securityAuditReportFlow.value.lastScannedAt == 0L) {
            runDeviceSecurityAudit()
        }
        if (tab == DataUsageSubTab.SPEED_TEST) {
            refreshConnectionDetails()
        }
        if (tab == DataUsageSubTab.WIFI) {
            refreshWifiDiagnostics()
        }
    }

    fun refreshWifiDiagnostics() {
        if (wifiDiagnosticsStateFlow.value.isRefreshing) return
        viewModelScope.launch {
            wifiDiagnosticsStateFlow.value = wifiDiagnosticsStateFlow.value.copy(isRefreshing = true)
            val observed = runCatching { preferences.observedWifiFlow.first() }.getOrDefault(emptyList())
            val state = repository.inspectWifiDiagnostics(observed)
            wifiDiagnosticsStateFlow.value = state
            connectionDetailsFlow.value = repository.detectConnectionDetails()
        }
    }

    fun clearObservedWifiHistory() {
        viewModelScope.launch {
            preferences.clearObservedWifi()
            refreshWifiDiagnostics()
            _snackbarMessages.tryEmit("Cleared observed device Wi-Fi history")
        }
    }

    fun setSiteTrafficFilter(filter: SiteTrafficFilter) {
        siteFilterFlow.value = filter
    }

    fun setSiteSearchQuery(query: String) {
        siteSearchQueryFlow.value = query
    }

    fun toggleCountryFilter(countryCode: String) {
        selectedCountryFilterFlow.value = if (selectedCountryFilterFlow.value == countryCode) {
            null
        } else {
            countryCode
        }
    }

    fun setShowLiveSitesFeed(showLive: Boolean) {
        showLiveSitesFeedFlow.value = showLive
    }

    fun clearConnectionHistory() {
        viewModelScope.launch {
            repository.clearConnectionHistory()
            _snackbarMessages.tryEmit("Cleared site & region connection history")
        }
    }

    fun runDeviceSecurityAudit() {
        if (securityAuditReportFlow.value.isScanning) return
        viewModelScope.launch {
            securityAuditReportFlow.value = securityAuditReportFlow.value.copy(isScanning = true)
            val recent = (ConnectionMonitorHub.liveConnectionsFlow.value + uiState.value.dataUsageState.historicalConnections)
                .distinctBy { "${it.domain}|${it.remoteIp}|${it.actionStatus}" }
            val report = repository.runDeviceSecurityAudit(recent)
            securityAuditReportFlow.value = report
        }
    }

    fun openFlaggedSitesFromAudit() {
        val current = uiState.value.dataUsageState
        val liveHasFlagged = current.liveConnections.any {
            it.riskLevel == ConnectionRiskLevel.SUSPICIOUS || it.riskLevel == ConnectionRiskLevel.CRITICAL
        }
        showLiveSitesFeedFlow.value = liveHasFlagged || current.historicalConnections.isEmpty()
        siteFilterFlow.value = SiteTrafficFilter.FLAGGED
        selectedCountryFilterFlow.value = null
        dataUsageSubTabFlow.value = DataUsageSubTab.SITES_REGIONS
    }

    fun quickBlockSiteFromMonitor(domain: String) {
        viewModelScope.launch {
            val result = repository.addWebsiteRule(domain, isWhitelist = false)
            result.onSuccess { normalized ->
                if (uiState.value.preferences.websiteFilterMode == WebsiteFilterMode.OFF) {
                    preferences.setWebsiteFilterMode(WebsiteFilterMode.BLOCKLIST)
                }
                val firewallOn = uiState.value.preferences.isFirewallEnabled
                _snackbarMessages.tryEmit(
                    if (firewallOn) {
                        "Blocked $normalized in Website Blocklist"
                    } else {
                        "Added $normalized to Blocklist — enable Firewall to enforce"
                    }
                )
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Unable to block $domain")
            }
        }
    }

    fun quickAllowSiteFromMonitor(domain: String) {
        viewModelScope.launch {
            val result = repository.addWebsiteRule(domain, isWhitelist = true)
            result.onSuccess { normalized ->
                _snackbarMessages.tryEmit("Added $normalized to Website Whitelist")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Unable to whitelist $domain")
            }
        }
    }

    fun setDeepMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDeepMonitorEnabled(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Deep Site & Region Monitor enabled" else "Deep Site & Region Monitor paused"
            )
        }
    }

    fun refreshDataUsage() {
        val granted = repository.hasUsageStatsPermission()
        hasUsageAccessFlow.value = granted
        if (!granted) {
            usageErrorFlow.value = null
            return
        }
        viewModelScope.launch {
            val apps = installedAppsFlow.value.ifEmpty {
                repository.getInstalledApps().also { installedAppsFlow.value = it }
            }
            syncDataUsageInternal(apps)
        }
    }

    fun setUsageTimeRange(range: UsageTimeRange) {
        if (usageTimeRangeFlow.value == range) return
        usageTimeRangeFlow.value = range
        refreshDataUsage()
    }

    fun setUsageChartMode(mode: UsageChartMode) {
        usageChartModeFlow.value = mode
    }

    fun setUsageFilterCategory(category: UsageFilterCategory) {
        usageFilterFlow.value = category
    }

    fun clearTrafficHistory() {
        viewModelScope.launch {
            repository.clearTrafficHistory()
            _snackbarMessages.tryEmit("Cleared Room historical traffic records")
        }
    }

    private suspend fun syncDataUsageInternal(apps: List<InstalledAppInfo>) {
        if (isSyncingUsageFlow.value) return
        isSyncingUsageFlow.value = true
        usageErrorFlow.value = null
        val result = repository.syncNetworkTrafficUsageToRoom(
            installedApps = apps,
            periodDays = usageTimeRangeFlow.value.days
        )
        result.onFailure { err ->
            usageErrorFlow.value = err.message ?: "Unable to query Android network stats"
        }
        isSyncingUsageFlow.value = false
    }

    fun getCachedAppIconBitmap(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun getAppIconBitmap(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val drawable = repository.loadAppIcon(packageName) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            drawable.toSafeBitmap(96).asImageBitmap()
        }
        iconCache.put(packageName, bitmap)
        return bitmap
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun onFilterModeChange(mode: AppFilterMode) {
        filterModeFlow.value = mode
    }

    fun toggleWifiBlock(app: ManagedApp) {
        viewModelScope.launch {
            repository.setAppRule(
                packageName = app.packageName,
                blockWifi = !app.blockWifi,
                blockMobileData = app.blockMobileData
            )
        }
    }

    fun toggleDataBlock(app: ManagedApp) {
        viewModelScope.launch {
            repository.setAppRule(
                packageName = app.packageName,
                blockWifi = app.blockWifi,
                blockMobileData = !app.blockMobileData
            )
        }
    }

    fun setAppRules(packageName: String, blockWifi: Boolean, blockMobileData: Boolean) {
        viewModelScope.launch {
            repository.setAppRule(
                packageName = packageName,
                blockWifi = blockWifi,
                blockMobileData = blockMobileData
            )
        }
    }

    fun scheduleAppTimer(
        packageName: String,
        durationMinutes: Int,
        target: TimerTargetNetwork
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            nowMillisFlow.value = now
            repository.scheduleAppTimer(
                packageName = packageName,
                durationMinutes = durationMinutes,
                blockWifiOnExpire = target.blockWifi,
                blockMobileDataOnExpire = target.blockMobileData,
                nowMillis = now
            )
            _snackbarMessages.tryEmit(
                "Timer set (${durationMinutes}m) — ${target.label} will disable automatically"
            )
        }
    }

    fun cancelAppTimer(packageName: String) {
        viewModelScope.launch {
            repository.cancelAppTimer(packageName)
            _snackbarMessages.tryEmit("Countdown timer cancelled")
        }
    }

    fun enterSelectionMode(initialPackage: String? = null) {
        isSelectionModeFlow.value = true
        if (initialPackage != null) {
            selectedPackagesFlow.value = selectedPackagesFlow.value + initialPackage
        }
    }

    fun exitSelectionMode() {
        isSelectionModeFlow.value = false
        selectedPackagesFlow.value = emptySet()
    }

    fun togglePackageSelected(packageName: String) {
        val current = selectedPackagesFlow.value
        selectedPackagesFlow.value = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
    }

    fun selectAllVisible() {
        val visible = uiState.value.filteredApps.map { it.packageName }.toSet()
        selectedPackagesFlow.value = visible
        if (visible.isNotEmpty()) {
            isSelectionModeFlow.value = true
        }
    }

    fun clearSelection() {
        selectedPackagesFlow.value = emptySet()
    }

    fun applyBatchAction(action: BatchAction) {
        val selected = selectedPackagesFlow.value
        if (selected.isEmpty()) return
        val count = selected.size
        viewModelScope.launch {
            repository.applyBatchAction(selected, action)
            val actionLabel = when (action) {
                BatchAction.BLOCK_WIFI -> "Blocked Wi-Fi for $count app${if (count == 1) "" else "s"}"
                BatchAction.BLOCK_DATA -> "Blocked Mobile Data for $count app${if (count == 1) "" else "s"}"
                BatchAction.BLOCK_BOTH -> "Blocked Wi-Fi & Data for $count app${if (count == 1) "" else "s"}"
                BatchAction.ALLOW_ALL -> "Restored full access for $count app${if (count == 1) "" else "s"}"
            }
            _snackbarMessages.tryEmit(actionLabel)
            exitSelectionMode()
        }
    }

    fun onFirewallPermissionGranted() {
        viewModelScope.launch {
            if (VpnGuardManager.runtimeState.value.isConnectedOrConnecting) {
                VpnGuardManager.disconnect(getApplication(), restoreFirewallIfPaused = false)
            }
            preferences.setFirewallEnabled(true)
            NetGateVpnService.startOrReload(getApplication())
            _snackbarMessages.tryEmit("NetGate Firewall active")
        }
    }

    fun disableFirewall() {
        viewModelScope.launch {
            preferences.setFirewallEnabled(false)
            NetGateVpnService.stop(getApplication())
            _snackbarMessages.tryEmit("NetGate Firewall paused")
        }
    }

    fun onVpnPermissionDenied() {
        viewModelScope.launch {
            preferences.setFirewallEnabled(false)
            _snackbarMessages.tryEmit("VPN permission is required to filter apps without root")
        }
    }

    fun openAppDetails(packageName: String) {
        inspectedPackageFlow.value = packageName
    }

    fun closeAppDetails() {
        inspectedPackageFlow.value = null
    }

    fun openSettings() {
        isSettingsOpenFlow.value = true
    }

    fun closeSettings() {
        isSettingsOpenFlow.value = false
    }

    fun checkForGitHubUpdates() {
        if (appUpdateStateFlow.value.status == AppUpdateStatus.CHECKING) return
        viewModelScope.launch {
            appUpdateStateFlow.value = appUpdateStateFlow.value.copy(
                status = AppUpdateStatus.CHECKING,
                errorMessage = null
            )
            val result = GitHubReleaseUpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
            val now = System.currentTimeMillis()
            result.onSuccess { checkResult ->
                appUpdateStateFlow.value = appUpdateStateFlow.value.copy(
                    status = if (checkResult.isUpdateAvailable) {
                        AppUpdateStatus.UPDATE_AVAILABLE
                    } else {
                        AppUpdateStatus.UP_TO_DATE
                    },
                    latestRelease = checkResult.latestRelease,
                    recentReleases = checkResult.recentReleases,
                    lastCheckedAtMs = now,
                    errorMessage = null
                )
                val msg = if (checkResult.isUpdateAvailable) {
                    "Update available: ${checkResult.latestRelease.tagName}"
                } else {
                    "NetGate v${BuildConfig.VERSION_NAME} is up to date (${checkResult.latestRelease.tagName})"
                }
                _snackbarMessages.tryEmit(msg)
            }.onFailure { err ->
                val msg = err.message ?: "Unable to check GitHub Releases"
                appUpdateStateFlow.value = appUpdateStateFlow.value.copy(
                    status = AppUpdateStatus.ERROR,
                    lastCheckedAtMs = now,
                    errorMessage = msg
                )
                _snackbarMessages.tryEmit(msg)
            }
        }
    }

    fun openChangelog() {
        isChangelogOpenFlow.value = true
    }

    fun closeChangelog() {
        isChangelogOpenFlow.value = false
    }

    fun openVpnDpiLogs(category: VpnDpiLogCategory = VpnDpiLogCategory.ALL) {
        vpnDpiLogsInitialCategoryFlow.value = category
        isVpnDpiLogsOpenFlow.value = true
    }

    fun closeVpnDpiLogs() {
        isVpnDpiLogsOpenFlow.value = false
    }

    fun clearVpnDpiLogs() {
        vpnDpiLogMonitor.clear()
        _snackbarMessages.tryEmit("Cleared VPN & DPI Bypass logs")
    }

    fun openConnectionInfo() {
        refreshConnectionDetails()
        isConnectionInfoOpenFlow.value = true
    }

    fun closeConnectionInfo() {
        isConnectionInfoOpenFlow.value = false
    }

    fun openDnsProtection() {
        selectDestination(AppDestination.DNS)
    }

    fun closeDnsProtection() {
        isDnsProtectionOpenFlow.value = false
        if (currentDestinationFlow.value == AppDestination.DNS) {
            selectDestination(AppDestination.FIREWALL)
        }
    }

    fun testCloudflareDoh() {
        if (dnsProbeStateFlow.value.isTesting) return
        val prefs = uiState.value.preferences
        val dohUrl = prefs.resolvedDohUrl()
        val label = prefs.selectedUpstreamDns.title
        viewModelScope.launch {
            dnsProbeStateFlow.value = DnsProbeState(isTesting = true)
            val result = repository.testCloudflareDohLatency(dohUrl)
            dnsProbeStateFlow.value = result.fold(
                onSuccess = { ms -> DnsProbeState(isTesting = false, latencyMs = ms) },
                onFailure = { err ->
                    DnsProbeState(
                        isTesting = false,
                        errorMessage = err.message ?: "$label DoH unreachable on current network"
                    )
                }
            )
        }
    }

    fun selectUpstreamDns(server: UpstreamDnsServer) {
        viewModelScope.launch {
            preferences.setSelectedUpstreamDns(server)
            _snackbarMessages.tryEmit("VPN upstream DNS set to ${server.title}")
        }
    }

    fun saveCustomDnsEndpoints(dohUrl: String, udpIp: String) {
        viewModelScope.launch {
            preferences.setCustomDnsEndpoints(dohUrl, udpIp)
            preferences.setSelectedUpstreamDns(UpstreamDnsServer.CUSTOM_DNS)
            _snackbarMessages.tryEmit("Saved custom upstream DNS settings")
        }
    }

    fun runDnsLeakTest() {
        if (dnsLeakTestStateFlow.value.isRunning) return
        refreshConnectionDetails()
        viewModelScope.launch {
            dnsLeakTestStateFlow.value = dnsLeakTestStateFlow.value.copy(
                isRunning = true,
                errorMessage = null
            )
            val snapshot = uiState.value
            val result = repository.runDnsLeakTest(
                prefs = snapshot.preferences,
                vpnState = snapshot.vpnRuntimeState,
                connectionDetails = snapshot.connectionDetails
            )
            dnsLeakTestStateFlow.value = result
        }
    }

    fun runDnsCheck() {
        if (dnsCheckStateFlow.value.isChecking) return
        refreshConnectionDetails()
        viewModelScope.launch {
            val currentSnapshot = uiState.value
            val instant = DnsConnectionChecker.buildInstantSnapshot(
                prefs = currentSnapshot.preferences,
                vpnState = currentSnapshot.vpnRuntimeState,
                connectionDetails = currentSnapshot.connectionDetails
            )
            dnsCheckStateFlow.value = instant.copy(isChecking = true)
            val result = repository.runLiveDnsCheck(
                prefs = currentSnapshot.preferences,
                vpnState = currentSnapshot.vpnRuntimeState,
                connectionDetails = currentSnapshot.connectionDetails
            )
            dnsCheckStateFlow.value = result
        }
    }

    fun notifyCopiedDnsHostname(hostname: String) {
        _snackbarMessages.tryEmit("Copied $hostname to clipboard")
    }

    fun openSpeedTest() {
        selectDestination(AppDestination.DATA_USAGE)
        selectDataUsageSubTab(DataUsageSubTab.SPEED_TEST)
    }

    fun closeSpeedTest() {
        stopSpeedTest()
        isSpeedTestOpenFlow.value = false
        if (dataUsageSubTabFlow.value == DataUsageSubTab.SPEED_TEST) {
            selectDataUsageSubTab(DataUsageSubTab.OVERVIEW)
        }
    }

    fun startSpeedTest() {
        refreshConnectionDetails()
        val netType = connectionDetailsFlow.value.activeNetworkType
        val netLabel = when (netType) {
            ActiveNetworkType.WIFI -> "Wi-Fi"
            ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
            ActiveNetworkType.OTHER -> "Ethernet / Other"
            ActiveNetworkType.NONE -> "Offline"
        }

        if (netType == ActiveNetworkType.NONE) {
            speedTestStateFlow.value = SpeedTestState(
                phase = SpeedTestPhase.ERROR,
                testedNetworkLabel = netLabel,
                errorMessage = "No active internet connection. Turn on Wi-Fi or Mobile Data first."
            )
            return
        }

        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            speedTestRunner.runTest(netLabel).collect { state ->
                speedTestStateFlow.value = state
            }
        }
    }

    fun startVideoPlaybackTest() {
        refreshConnectionDetails()
        val netType = connectionDetailsFlow.value.activeNetworkType
        val netLabel = when (netType) {
            ActiveNetworkType.WIFI -> "Wi-Fi"
            ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
            ActiveNetworkType.OTHER -> "Ethernet / Other"
            ActiveNetworkType.NONE -> "Offline"
        }

        if (netType == ActiveNetworkType.NONE) {
            speedTestStateFlow.value = speedTestStateFlow.value.copy(
                phase = SpeedTestPhase.ERROR,
                testedNetworkLabel = netLabel,
                errorMessage = "No active internet connection. Turn on Wi-Fi or Mobile Data first."
            )
            return
        }

        val snapshot = speedTestStateFlow.value
        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            speedTestRunner.runVideoPlaybackTest(netLabel, snapshot).collect { state ->
                speedTestStateFlow.value = state
            }
        }
    }

    fun stopSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        val current = speedTestStateFlow.value
        if (current.isRunning) {
            speedTestStateFlow.value = current.copy(
                phase = SpeedTestPhase.IDLE,
                progress = 0f,
                liveSpeedMbps = 0.0
            )
        }
    }

    fun setShowOfflineApps(show: Boolean) {
        viewModelScope.launch { preferences.setShowOfflineApps(show) }
    }

    fun setShowSystemApps(show: Boolean) {
        viewModelScope.launch { preferences.setShowSystemApps(show) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setThemeStyle(style: NetGateThemeStyle) {
        viewModelScope.launch {
            preferences.setThemeStyle(style)
            _snackbarMessages.tryEmit("Theme style: ${style.title}")
        }
    }

    fun cycleThemeMode() {
        val current = uiState.value.preferences.themeMode
        val next = when (current) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
        _snackbarMessages.tryEmit("Theme: ${next.label}")
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDynamicColor(enabled)
            NetGateWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun setBlurEngineStyle(style: BlurEngineStyle) {
        viewModelScope.launch {
            preferences.setBlurEngineStyle(style)
            _snackbarMessages.tryEmit("Blur Engine effect: ${style.title}")
        }
    }

    fun setBlurAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBlurAnimationEnabled(enabled)
        }
    }

    fun setBackgroundAnimation(style: BackgroundAnimationStyle) {
        viewModelScope.launch {
            preferences.setBackgroundAnimation(style)
            _snackbarMessages.tryEmit("Background animation: ${style.title}")
        }
    }

    fun setBackgroundParallaxEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackgroundParallaxEnabled(enabled) }
    }

    fun setBackgroundSeasonalEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackgroundSeasonalEnabled(enabled) }
    }

    fun setAppIconStyle(style: AppIconStyle) {
        viewModelScope.launch {
            preferences.setAppIconStyle(style)
            AppIconManager.apply(getApplication(), style)
            _snackbarMessages.tryEmit("App icon: ${style.title}")
        }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            repository.clearAllRules()
            _snackbarMessages.tryEmit("Cleared all per-app network rules")
        }
    }

    fun setWebsiteFilterMode(mode: WebsiteFilterMode) {
        viewModelScope.launch {
            preferences.setWebsiteFilterMode(mode)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                mode == WebsiteFilterMode.OFF -> "Website blocker turned off"
                firewallOn -> "Website filter set to ${mode.title}"
                else -> "Website ${mode.title} selected — enable NetGate Firewall to apply"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun addWebsiteRule(rawDomain: String, isWhitelist: Boolean) {
        viewModelScope.launch {
            val result = repository.addWebsiteRule(rawDomain, isWhitelist)
            result.onSuccess { domain ->
                val listName = if (isWhitelist) "Whitelist" else "Blocklist"
                _snackbarMessages.tryEmit("Added $domain to $listName")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Enter a valid domain like example.com")
            }
        }
    }

    fun toggleWebsiteRuleEnabled(domain: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setWebsiteRuleEnabled(domain, enabled)
        }
    }

    fun deleteWebsiteRule(domain: String) {
        viewModelScope.launch {
            repository.deleteWebsiteRule(domain)
            _snackbarMessages.tryEmit("Removed $domain")
        }
    }

    fun clearWebsiteRules(isWhitelist: Boolean) {
        viewModelScope.launch {
            repository.clearWebsiteRules(isWhitelist)
            val listName = if (isWhitelist) "Whitelist" else "Blocklist"
            _snackbarMessages.tryEmit("Cleared all $listName domains")
        }
    }

    fun toggleWebsiteBypassApp(packageName: String) {
        viewModelScope.launch {
            preferences.toggleWebsiteBypassPackage(packageName)
        }
    }

    fun clearWebsiteBypassApps() {
        viewModelScope.launch {
            preferences.clearWebsiteBypassPackages()
            _snackbarMessages.tryEmit("Cleared selected allowed apps")
        }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAdBlockerEnabled(enabled)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                !enabled -> "System-wide Ad Blocker turned off"
                firewallOn -> "System-wide Ad & Tracker Blocker active"
                else -> "Ad Blocker enabled — activate Firewall to filter ads"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun toggleAdFilterList(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAdFilterList(id, enabled)
        }
    }

    fun syncAdFilterLists() {
        if (isSyncingAdListsFlow.value) return
        viewModelScope.launch {
            isSyncingAdListsFlow.value = true
            val result = repository.syncEnabledAdFilterLists()
            isSyncingAdListsFlow.value = false
            result.onSuccess { totalDomains ->
                _snackbarMessages.tryEmit("Synced ad filter lists (${formatCompactCount(totalDomains)} domains active)")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Unable to download ad filter lists")
            }
        }
    }

    fun setEssentialPassthroughEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEssentialPassthroughEnabled(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Essential OS & push passthrough enabled" else "Essential passthrough disabled"
            )
        }
    }

    fun setUseDohUpstream(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDohUpstream(enabled)
            val dnsName = uiState.value.preferences.selectedUpstreamDns.title
            _snackbarMessages.tryEmit(
                if (enabled) "Upstream DNS set to $dnsName DoH" else "Upstream DNS set to UDP/53"
            )
        }
    }

    fun setHttpsProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHttpsProxyEnabled(enabled)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val port = uiState.value.preferences.httpsProxyPort
            val msg = when {
                !enabled -> "HTTPS Filtering Proxy turned off"
                firewallOn -> "HTTPS Filtering Proxy active on 127.0.0.1:$port"
                else -> "HTTPS Proxy enabled — activate NetGate Firewall to start listening"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun setHttpsProxyPort(port: Int) {
        val valid = port.coerceIn(1024, 65535)
        viewModelScope.launch {
            preferences.setHttpsProxyPort(valid)
            _snackbarMessages.tryEmit("HTTPS proxy port updated to $valid")
        }
    }

    fun setProxyStripTrackingParams(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setProxyStripTrackingParams(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "URL tracking parameter stripping enabled" else "URL tracking parameter stripping disabled"
            )
        }
    }

    fun setSafeSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSafeSearchEnabled(enabled)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                !enabled -> "SafeSearch enforcement turned off"
                firewallOn -> "SafeSearch active for Google, Bing, DuckDuckGo & Brave"
                else -> "SafeSearch enabled — activate NetGate Firewall to enforce"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun setYoutubeRestrictedMode(mode: YouTubeRestrictedMode) {
        viewModelScope.launch {
            preferences.setYoutubeRestrictedMode(mode)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                mode == YouTubeRestrictedMode.OFF -> "YouTube Restricted Mode turned off"
                firewallOn -> "YouTube Kids Safety set to ${mode.title}"
                else -> "YouTube ${mode.title} selected — activate NetGate Firewall to apply"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun setYoutubeAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setYoutubeAdBlockEnabled(enabled)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                !enabled -> "YouTube Ad Blocker turned off"
                firewallOn -> "YouTube Ad Blocker active"
                else -> "YouTube Ad Blocker ready — activate NetGate Firewall to filter"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun selectSniProxyPreset(preset: SniProxyPreset) {
        viewModelScope.launch {
            preferences.applySniProxyPreset(preset)
            _snackbarMessages.tryEmit("SNI Proxy preset: ${preset.title}")
        }
    }

    fun saveCustomSniProxy(host: String, port: Int) {
        val cleanedHost = host.trim()
        if (cleanedHost.isEmpty()) {
            _snackbarMessages.tryEmit("Enter a valid upstream proxy host or IP")
            return
        }
        val validPort = port.coerceIn(1, 65535)
        viewModelScope.launch {
            preferences.setCustomSniProxy(cleanedHost, validPort)
            _snackbarMessages.tryEmit("Saved custom upstream SNI proxy $cleanedHost:$validPort")
        }
    }

    fun setDpiBypassMode(mode: DpiBypassMode) {
        viewModelScope.launch {
            preferences.setDpiBypassMode(mode)
            val firewallOn = uiState.value.preferences.isFirewallEnabled
            val msg = when {
                mode == DpiBypassMode.OFF -> "DPI Bypass Shield turned off"
                firewallOn -> "DPI Bypass Shield active (${mode.title})"
                else -> "DPI ${mode.title} enabled — activate Firewall to apply"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun setDpiFragmentChunkSize(size: Int) {
        val valid = size.coerceIn(1, 32)
        viewModelScope.launch {
            preferences.setDpiFragmentChunkSize(valid)
            _snackbarMessages.tryEmit("TLS fragment chunk size set to ${valid}B")
        }
    }

    fun setDpiIdleKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDpiIdleKeepAliveEnabled(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Messaging idle keep-alive enabled (SO_KEEPALIVE)"
                else "Messaging idle keep-alive disabled"
            )
        }
    }

    fun setBlockQuicForDpiBypass(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBlockQuicForDpiBypass(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "QUIC HTTP/3 downgrade active — forcing TCP TLS for DPI bypass"
                else "QUIC HTTP/3 downgrade disabled"
            )
        }
    }

    fun setAntiDnsPoisonEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAntiDnsPoisonEnabled(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Anti-DNS Poisoning Guard active"
                else "Anti-DNS Poisoning Guard disabled"
            )
        }
    }

    fun setDnsTransportMode(mode: DnsTransportMode) {
        viewModelScope.launch {
            preferences.setDnsTransportMode(mode)
            _snackbarMessages.tryEmit("DNS transport set to ${mode.title}")
        }
    }

    fun flushDnsAndAutopilotCache() {
        NetGateVpnService.flushDnsAndAutopilotCache(getApplication())
        _snackbarMessages.tryEmit("Flushed DNS responses & reset Autopilot strategy cache")
    }

    fun addCustomAdFilterList(title: String, sourceUrl: String, category: String) {
        viewModelScope.launch {
            val res = repository.addCustomAdFilterList(title, sourceUrl, category)
            res.onSuccess { item ->
                _snackbarMessages.tryEmit("Added filter list \"${item.title}\" — tap Sync to download")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Unable to add custom filter list")
            }
        }
    }

    fun deleteCustomAdFilterList(id: String) {
        viewModelScope.launch {
            repository.deleteCustomAdFilterList(id)
            _snackbarMessages.tryEmit("Removed custom filter list")
        }
    }

    fun clearDnsLogs() {
        NetGateVpnService.clearRecentDnsLogs()
        _snackbarMessages.tryEmit("Cleared DNS query log")
    }

    fun setStatusBarSpeedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStatusBarSpeedEnabled(enabled)
            if (enabled) {
                NetworkMonitorService.start(getApplication())
                _snackbarMessages.tryEmit("Status bar live speed icon enabled")
            } else {
                NetworkMonitorService.stop(getApplication())
                _snackbarMessages.tryEmit("Status bar live speed icon turned off")
            }
        }
    }

    fun onStatusBarSpeedPermissionDenied() {
        viewModelScope.launch {
            preferences.setStatusBarSpeedEnabled(false)
            _snackbarMessages.tryEmit("Notification permission is required to show speed in the status bar")
        }
    }

    fun setStatusBarSpeedStyle(style: StatusBarSpeedStyle) {
        viewModelScope.launch {
            preferences.setStatusBarSpeedStyle(style)
            _snackbarMessages.tryEmit("Status bar style: ${style.title}")
        }
    }

    fun setShowStatusBarDailyUsage(show: Boolean) {
        viewModelScope.launch {
            preferences.setShowStatusBarDailyUsage(show)
        }
    }

    fun resetLiveSessionStats() {
        SystemNetworkMonitor.resetSession(getApplication())
        _snackbarMessages.tryEmit("Reset live session speed & byte counters")
    }

    fun setExcludeFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setExcludeFromRecents(enabled)
            BackgroundPowerHelper.applyExcludeFromRecents(getApplication(), enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "NetGate hidden from Recent Apps"
                else "NetGate visible in Recent Apps"
            )
        }
    }

    fun setHotspotProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHotspotProxyEnabled(enabled)
            if (enabled) {
                HotspotProxyService.start(getApplication())
                val prefs = uiState.value.preferences.copy(hotspotProxyEnabled = true)
                HotspotProxyManager.startOrUpdate(getApplication(), prefs)
                _snackbarMessages.tryEmit(
                    "Hotspot Proxy started on port ${prefs.hotspotProxyPort}"
                )
            } else {
                HotspotProxyService.stop(getApplication())
                HotspotProxyManager.stop(getApplication())
                _snackbarMessages.tryEmit("Hotspot Proxy stopped")
            }
        }
    }

    fun setHotspotProxyPort(port: Int) {
        val valid = port.coerceIn(1024, 65535)
        if (valid == uiState.value.preferences.httpsProxyPort && uiState.value.preferences.isHttpsProxyEnabled) {
            _snackbarMessages.tryEmit("Port $valid is used by local SNI proxy — pick another (e.g. 8890)")
            return
        }
        viewModelScope.launch {
            preferences.setHotspotProxyPort(valid)
            _snackbarMessages.tryEmit("Hotspot proxy port set to $valid")
        }
    }

    fun setHotspotApplyNetGateFilters(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHotspotApplyNetGateFilters(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Hotspot clients will share NetGate ad & site filters"
                else "Hotspot Proxy switched to Direct Relay"
            )
        }
    }

    fun toggleHotspotClientBlocked(clientIp: String, blocked: Boolean) {
        viewModelScope.launch {
            preferences.setHotspotClientBlocked(clientIp, blocked)
            _snackbarMessages.tryEmit(
                if (blocked) "Blocked Hotspot client $clientIp"
                else "Allowed Hotspot client $clientIp"
            )
        }
    }

    fun setHotspotClientDataCap(clientIp: String, capBytes: Long) {
        viewModelScope.launch {
            preferences.setHotspotClientDataCap(clientIp, capBytes)
            _snackbarMessages.tryEmit(
                if (capBytes > 0L) {
                    "Set cap on $clientIp to ${com.aistudio.netgate.data.model.formatDataBytes(capBytes)}"
                } else {
                    "Removed data cap on $clientIp"
                }
            )
        }
    }

    fun setHotspotClientLabel(clientIp: String, label: String) {
        viewModelScope.launch {
            preferences.setHotspotClientLabel(clientIp, label)
        }
    }

    fun resetHotspotClientStats(clientIp: String) {
        HotspotProxyManager.resetClientStats(clientIp)
        _snackbarMessages.tryEmit("Cleared usage counter for $clientIp")
    }

    fun clearAllHotspotStats() {
        HotspotProxyManager.clearAllClientStats()
        _snackbarMessages.tryEmit("Cleared Hotspot client session stats")
    }

    fun refreshHotspotEnvironment() {
        viewModelScope.launch(Dispatchers.IO) {
            HotspotProxyManager.refreshEnvironment(getApplication())
        }
    }

    fun notifyCopiedHotspotEndpoint(endpoint: String) {
        _snackbarMessages.tryEmit("Copied Hotspot proxy $endpoint")
    }

    fun setGlobalWifiBlocked(blocked: Boolean) {
        viewModelScope.launch {
            WifiAutomationManager.setGlobalWifiLocked(
                context = getApplication(),
                locked = blocked,
                triggerReasonTitle = if (blocked) "Global Wi-Fi Lock Active" else null,
                triggerReasonBody = if (blocked) "All app Wi-Fi traffic is paused by NetGate." else null
            )
            _snackbarMessages.tryEmit(
                if (blocked) "Wi-Fi traffic locked across all apps"
                else "Wi-Fi traffic restored"
            )
        }
    }

    fun scheduleGlobalWifiTimer(minutes: Int) {
        viewModelScope.launch {
            val validMin = minutes.coerceIn(1, 720)
            WifiAutomationManager.scheduleWifiTimer(getApplication(), validMin)
            _snackbarMessages.tryEmit("Wi-Fi traffic will auto-lock in $validMin min")
        }
    }

    fun cancelGlobalWifiTimer() {
        viewModelScope.launch {
            WifiAutomationManager.cancelWifiTimer(getApplication())
            _snackbarMessages.tryEmit("Cancelled Wi-Fi countdown timer")
        }
    }

    fun setAutoBlockWifiOnIdle(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoBlockWifiOnIdle(enabled)
            if (!enabled) {
                WifiAutomationManager.resetIdleProgress()
            }
            _snackbarMessages.tryEmit(
                if (enabled) "Auto-lock Wi-Fi when idle enabled"
                else "Auto-lock Wi-Fi when idle turned off"
            )
        }
    }

    fun setAutoBlockWifiIdleMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setAutoBlockWifiIdleMinutes(minutes)
        }
    }

    fun setAutoBlockWifiOpenPanelNotify(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoBlockWifiOpenPanelNotify(enabled)
        }
    }

    fun selectVpnGuardSubTab(tab: VpnGuardSubTab) {
        vpnGuardSubTabFlow.value = tab
        if (tab == VpnGuardSubTab.VPNGATE && vpnGateServersFlow.value.isEmpty() && !isLoadingVpnGateFlow.value) {
            refreshVpnGateServers()
        }
    }

    fun selectVpnGuardProfile(profileId: String) {
        selectedVpnGuardProfileIdFlow.value = profileId
    }

    fun importWireGuardConfig(
        rawText: String,
        nameHint: String = "",
        routingMode: VpnGuardRoutingMode = VpnGuardRoutingMode.WHOLE_SYSTEM,
        targetPackages: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            val res = WireGuardConfigHelper.parseToProfile(
                rawConfig = rawText,
                nameHint = nameHint,
                routingModeOverride = if (routingMode != VpnGuardRoutingMode.WHOLE_SYSTEM || targetPackages.isNotEmpty()) {
                    routingMode
                } else {
                    null
                },
                targetPackagesOverride = if (targetPackages.isNotEmpty()) targetPackages else null
            )
            res.onSuccess { profile ->
                repository.saveVpnGuardProfile(profile)
                selectedVpnGuardProfileIdFlow.value = profile.id
                _snackbarMessages.tryEmit("Imported ${profile.name} (${profile.routingMode.title})")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Invalid WireGuard .conf file")
            }
        }
    }

    fun generateWarpProfile(
        profileName: String = "Cloudflare WARP",
        routingMode: VpnGuardRoutingMode = VpnGuardRoutingMode.WHOLE_SYSTEM,
        targetPackages: Set<String> = emptySet(),
        autoConnect: Boolean = false
    ) {
        if (isGeneratingWarpFlow.value) return
        viewModelScope.launch {
            isGeneratingWarpFlow.value = true
            val result = WarpConfigGenerator.generateLiveWarpProfile(
                profileName = profileName.ifBlank { "Cloudflare WARP" },
                routingMode = routingMode,
                targetPackages = targetPackages
            )
            isGeneratingWarpFlow.value = false
            result.onSuccess { profile ->
                repository.saveVpnGuardProfile(profile)
                selectedVpnGuardProfileIdFlow.value = profile.id
                if (autoConnect) {
                    connectVpnGuardProfile(profile)
                } else {
                    _snackbarMessages.tryEmit("Generated Cloudflare WARP WireGuard .conf — ready to Connect or Download")
                }
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Unable to generate WARP config right now")
            }
        }
    }

    fun generateWireGuardKeypairTemplate(profileName: String = "Custom WireGuard") {
        viewModelScope.launch {
            val (confText, pubKey) = WireGuardConfigHelper.generateTemplateConfig()
            val parsed = WireGuardConfigHelper.parseToProfile(
                rawConfig = confText,
                nameHint = profileName,
                providerHint = VpnGuardProviderType.CUSTOM_WIREGUARD
            )
            parsed.onSuccess { profile ->
                repository.saveVpnGuardProfile(profile)
                selectedVpnGuardProfileIdFlow.value = profile.id
                _snackbarMessages.tryEmit("Created new keypair profile (PubKey: ${pubKey.take(10)}…)")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Failed to create WireGuard template")
            }
        }
    }

    fun updateVpnGuardProfileRouting(
        profileId: String,
        routingMode: VpnGuardRoutingMode,
        targetPackages: Set<String>
    ) {
        viewModelScope.launch {
            if (routingMode != VpnGuardRoutingMode.WHOLE_SYSTEM && targetPackages.isEmpty()) {
                _snackbarMessages.tryEmit("Pick at least one app for ${routingMode.title}")
                return@launch
            }
            repository.updateVpnGuardRouting(profileId, routingMode, targetPackages)
            val updated = uiState.value.vpnGuardState.profiles.firstOrNull { it.id == profileId }
                ?.copy(routingMode = routingMode, targetPackages = targetPackages)
            val runtime = VpnGuardManager.runtimeState.value
            if (updated != null && runtime.activeProfileId == profileId && runtime.isConnectedOrConnecting) {
                VpnGuardManager.connect(getApplication(), updated)
                _snackbarMessages.tryEmit("Reconnected VPN Guard with ${routingMode.title}")
            } else {
                _snackbarMessages.tryEmit("Routing updated to ${routingMode.title}")
            }
        }
    }

    fun deleteVpnGuardProfile(profileId: String) {
        viewModelScope.launch {
            val runtime = VpnGuardManager.runtimeState.value
            if (runtime.activeProfileId == profileId && runtime.isConnectedOrConnecting) {
                VpnGuardManager.disconnect(getApplication(), restoreFirewallIfPaused = false)
            }
            repository.deleteVpnGuardProfile(profileId)
            _snackbarMessages.tryEmit("Removed VPN Guard profile")
        }
    }

    fun connectVpnGuardProfile(profile: VpnGuardProfile) {
        viewModelScope.launch {
            if (profile.routingMode != VpnGuardRoutingMode.WHOLE_SYSTEM && profile.targetPackages.isEmpty()) {
                _snackbarMessages.tryEmit("Select at least 1 app before connecting in ${profile.routingMode.title} mode")
                return@launch
            }
            val wasFirewallEnabled = uiState.value.preferences.isFirewallEnabled
            if (wasFirewallEnabled) {
                preferences.setFirewallEnabled(false)
            }
            val result = VpnGuardManager.connect(getApplication(), profile)
            result.onSuccess {
                repository.markVpnGuardConnected(profile.id)
                val scopeMsg = when (profile.routingMode) {
                    VpnGuardRoutingMode.WHOLE_SYSTEM -> "Whole System"
                    VpnGuardRoutingMode.SELECTED_APPS -> "${profile.targetPackages.size} selected app(s)"
                    VpnGuardRoutingMode.EXCLUDE_APPS -> "All except ${profile.targetPackages.size} app(s)"
                }
                _snackbarMessages.tryEmit("VPN Guard connected (${profile.name} • $scopeMsg)")
            }.onFailure { err ->
                if (wasFirewallEnabled && android.net.VpnService.prepare(getApplication()) == null) {
                    preferences.setFirewallEnabled(true)
                    NetGateVpnService.startOrReload(getApplication())
                }
                _snackbarMessages.tryEmit(err.message ?: "VPN Guard connection failed")
            }
        }
    }

    fun disconnectVpnGuard(restoreFirewall: Boolean = false) {
        viewModelScope.launch {
            VpnGuardManager.disconnect(getApplication(), restoreFirewallIfPaused = restoreFirewall)
            if (restoreFirewall) {
                preferences.setFirewallEnabled(true)
                _snackbarMessages.tryEmit("VPN Guard disconnected — NetGate Firewall restored")
            } else {
                _snackbarMessages.tryEmit("VPN Guard disconnected")
            }
        }
    }

    fun refreshVpnGateServers(force: Boolean = false) {
        if (isLoadingVpnGateFlow.value) return
        if (!force && vpnGateServersFlow.value.isNotEmpty() &&
            System.currentTimeMillis() - vpnGateLastFetchedAtFlow.value < 60_000L
        ) {
            return
        }
        viewModelScope.launch {
            isLoadingVpnGateFlow.value = true
            vpnGateErrorFlow.value = null
            val res = VpnGateClient.fetchLiveServers()
            isLoadingVpnGateFlow.value = false
            res.onSuccess { servers ->
                vpnGateServersFlow.value = servers
                vpnGateLastFetchedAtFlow.value = System.currentTimeMillis()
                _snackbarMessages.tryEmit("Loaded ${servers.size} live VPNGate servers")
            }.onFailure { err ->
                vpnGateErrorFlow.value = err.message ?: "Failed to fetch live VPNGate servers"
            }
        }
    }

    fun setVpnGateSearchQuery(query: String) {
        vpnGateSearchQueryFlow.value = query
    }

    fun setVpnGateSortMode(mode: VpnGateSortMode) {
        vpnGateSortModeFlow.value = mode
    }

    fun notifyVpnGuardMessage(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    private fun formatCompactCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private data class BaseSnapshot(
        val managedApps: List<ManagedApp>,
        val filteredApps: List<ManagedApp>,
        val loading: Boolean,
        val nowMillis: Long,
        val prefs: FirewallPreferencesState,
        val vpnState: VpnRuntimeState
    )

    private data class InteractionSnapshot(
        val query: String,
        val filter: AppFilterMode,
        val selectionMode: Boolean,
        val selected: Set<String>,
        val inspected: String?
    )

    private data class DnsUiSnapshot(
        val dnsOpen: Boolean,
        val dnsProbeState: DnsProbeState,
        val dnsLeakTestState: DnsLeakTestState,
        val dnsCheckState: DnsCheckState
    )

    private data class DiagnosticsOverlaySnapshot(
        val appUpdateState: AppUpdateUiState,
        val isChangelogOpen: Boolean,
        val vpnDpiLogs: List<VpnDpiLogEntry>,
        val isVpnDpiLogsOpen: Boolean,
        val vpnDpiLogsInitialCategory: VpnDpiLogCategory
    )

    private data class SheetsSnapshot(
        val settingsOpen: Boolean,
        val connInfoOpen: Boolean,
        val dnsOpen: Boolean,
        val dnsProbeState: DnsProbeState,
        val dnsLeakTestState: DnsLeakTestState,
        val dnsCheckState: DnsCheckState,
        val speedTestOpen: Boolean,
        val speedTestState: SpeedTestState,
        val diagnostics: DiagnosticsOverlaySnapshot
    )

    private data class UsageControlsSnapshot(
        val hasAccess: Boolean,
        val syncing: Boolean,
        val timeRange: UsageTimeRange,
        val chartMode: UsageChartMode,
        val filterCategory: UsageFilterCategory
    )

    private data class UsageRepoSnapshot(
        val summary: DataUsageSummary,
        val rawRows: List<com.aistudio.netgate.data.local.AppTrafficWithRuleTuple>,
        val dailyHistory: List<DailyTrafficHistoryPoint>,
        val historicalTotals: HistoricalUsageTotals
    )

    private data class VpnGateSnapshot(
        val servers: List<VpnGateServer>,
        val isLoading: Boolean,
        val errorMessage: String?,
        val lastFetchedAt: Long,
        val searchQuery: String,
        val sortMode: VpnGateSortMode
    )

    private data class TailSnapshot(
        val destination: AppDestination,
        val dataUsage: DataUsageUiState,
        val webAndAd: Triple<List<WebsiteRuleItem>, List<AdFilterListItem>, Boolean>,
        val liveTelemetry: LiveNetworkTelemetry,
        val hotspotProxyState: HotspotProxyRuntimeState,
        val wifiIdleStatus: WifiIdleStatus,
        val vpnGuardState: VpnGuardUiState
    )

    private data class SiteControlsSnapshot(
        val subTab: DataUsageSubTab,
        val siteFilter: SiteTrafficFilter,
        val siteQuery: String,
        val countryFilter: String?,
        val showLive: Boolean
    )

    private data class SiteMonitorSnapshot(
        val live: List<SiteConnectionRecord>,
        val history: List<SiteConnectionRecord>,
        val controls: SiteControlsSnapshot,
        val auditReport: DeviceSecurityAuditReport,
        val wifiDiagnostics: WifiDiagnosticsUiState
    )
}

private fun Drawable.toSafeBitmap(targetSizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return if (bitmap.width == targetSizePx && bitmap.height == targetSizePx) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetSizePx, targetSizePx, true)
        }
    }
    val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
