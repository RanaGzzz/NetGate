package com.example.ui

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
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppFilterMode
import com.example.data.model.BatchAction
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsProbeState
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ManagedApp
import com.example.data.model.ThemeMode
import com.example.data.network.SpeedTestPhase
import com.example.data.network.SpeedTestRunner
import com.example.data.network.SpeedTestState
import com.example.data.repository.FirewallRepository
import com.example.service.NetGateVpnService
import com.example.service.VpnRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FirewallUiState(
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
    val isSpeedTestOpen: Boolean = false,
    val speedTestState: SpeedTestState = SpeedTestState()
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

    val inspectedApp: ManagedApp?
        get() = inspectedAppPackage?.let { pkg -> allManagedApps.find { it.packageName == pkg } }
}

class FirewallViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NetGateDatabase.getInstance(application)
    private val preferences = FirewallPreferences(application)
    private val repository = FirewallRepository(
        context = application,
        ruleDao = database.appNetworkRuleDao(),
        preferences = preferences
    )

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
    private val isSpeedTestOpenFlow = MutableStateFlow(false)
    private val speedTestRunner = SpeedTestRunner()
    private val speedTestStateFlow = MutableStateFlow(SpeedTestState())
    private var speedTestJob: Job? = null
    private val connectionDetailsFlow = MutableStateFlow(repository.detectConnectionDetails())

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
        refreshInstalledApps()
        registerNetworkMonitor()
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

    private val baseDataFlow = combine(
        installedAppsFlow,
        isLoadingAppsFlow,
        repository.rulesFlow,
        repository.preferencesFlow,
        NetGateVpnService.runtimeState
    ) { installed, loading, rules, prefs, vpnState ->
        BaseSnapshot(installed, loading, rules, prefs, vpnState)
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
        dnsProbeStateFlow
    ) { dnsOpen, dnsProbe ->
        dnsOpen to dnsProbe
    }

    private val sheetsFlow = combine(
        isSettingsOpenFlow,
        isConnectionInfoOpenFlow,
        isSpeedTestOpenFlow,
        speedTestStateFlow,
        dnsUiFlow
    ) { settingsOpen, connInfoOpen, speedTestOpen, speedTest, (dnsOpen, dnsProbe) ->
        SheetsSnapshot(settingsOpen, connInfoOpen, dnsOpen, dnsProbe, speedTestOpen, speedTest)
    }

    val uiState: StateFlow<FirewallUiState> = combine(
        baseDataFlow,
        uiInteractionFlow,
        connectionDetailsFlow,
        sheetsFlow
    ) { base, interaction, connDetails, sheets ->
        val ruleMap = base.rules.associateBy { it.packageName }

        val visibleInstalled = base.installed.filter { app ->
            val passInternet = base.prefs.showOfflineApps || app.hasInternetPermission
            val passSystem = base.prefs.showSystemApps || !app.isSystemApp
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
                blockMobileData = rule?.blockMobileData ?: false
            )
        }

        val queryTrimmed = interaction.query.trim().lowercase()
        val filtered = managedApps.filter { app ->
            val matchesQuery = queryTrimmed.isEmpty() ||
                app.appName.lowercase().contains(queryTrimmed) ||
                app.packageName.lowercase().contains(queryTrimmed)

            val matchesFilter = when (interaction.filter) {
                AppFilterMode.ALL -> true
                AppFilterMode.BLOCKED -> app.isAnyBlocked
                AppFilterMode.WIFI_BLOCKED -> app.blockWifi
                AppFilterMode.DATA_BLOCKED -> app.blockMobileData
                AppFilterMode.ALLOWED -> !app.isAnyBlocked
            }
            matchesQuery && matchesFilter
        }

        FirewallUiState(
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
            isSpeedTestOpen = sheets.speedTestOpen,
            speedTestState = sheets.speedTestState
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
            installedAppsFlow.value = repository.getInstalledApps()
            isLoadingAppsFlow.value = false
            refreshConnectionDetails()
        }
    }

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

    fun openConnectionInfo() {
        refreshConnectionDetails()
        isConnectionInfoOpenFlow.value = true
    }

    fun closeConnectionInfo() {
        isConnectionInfoOpenFlow.value = false
    }

    fun openDnsProtection() {
        refreshConnectionDetails()
        isDnsProtectionOpenFlow.value = true
    }

    fun closeDnsProtection() {
        isDnsProtectionOpenFlow.value = false
    }

    fun testCloudflareDoh() {
        if (dnsProbeStateFlow.value.isTesting) return
        viewModelScope.launch {
            dnsProbeStateFlow.value = DnsProbeState(isTesting = true)
            val result = repository.testCloudflareDohLatency()
            dnsProbeStateFlow.value = result.fold(
                onSuccess = { ms -> DnsProbeState(isTesting = false, latencyMs = ms) },
                onFailure = { err ->
                    DnsProbeState(
                        isTesting = false,
                        errorMessage = err.message ?: "Cloudflare DoH unreachable on current network"
                    )
                }
            )
        }
    }

    fun notifyCopiedDnsHostname(hostname: String) {
        _snackbarMessages.tryEmit("Copied $hostname to clipboard")
    }

    fun openSpeedTest() {
        refreshConnectionDetails()
        isSpeedTestOpenFlow.value = true
    }

    fun closeSpeedTest() {
        stopSpeedTest()
        isSpeedTestOpenFlow.value = false
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
        viewModelScope.launch { preferences.setUseDynamicColor(enabled) }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            repository.clearAllRules()
            _snackbarMessages.tryEmit("Cleared all per-app network rules")
        }
    }

    private data class BaseSnapshot(
        val installed: List<InstalledAppInfo>,
        val loading: Boolean,
        val rules: List<com.example.data.local.AppNetworkRuleEntity>,
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

    private data class SheetsSnapshot(
        val settingsOpen: Boolean,
        val connInfoOpen: Boolean,
        val dnsOpen: Boolean,
        val dnsProbeState: DnsProbeState,
        val speedTestOpen: Boolean,
        val speedTestState: SpeedTestState
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
