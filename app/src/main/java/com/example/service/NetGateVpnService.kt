package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AdFilterListEntity
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.ActiveNetworkType
import com.example.data.model.DnsQueryLogEntry
import com.example.data.model.DnsQueryStatus
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.SniProxyPreset
import com.example.data.model.UpstreamDnsServer
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.YouTubeRestrictedMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class VpnRuntimeState(
    val isServiceRunning: Boolean = false,
    val isTunnelActive: Boolean = false,
    val activeNetworkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val blockedActivePackagesCount: Int = 0,
    val websiteFilterMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
    val isAdBlockerEnabled: Boolean = false,
    val isHttpsProxyActive: Boolean = false,
    val httpsProxyPort: Int = 8899,
    val blockedProxyRequestsCount: Long = 0L,
    val allowedProxyRequestsCount: Long = 0L,
    val activeUpstreamDnsTitle: String = UpstreamDnsServer.CLOUDFLARE_STANDARD.title,
    val blockedDnsQueriesCount: Long = 0L,
    val allowedDnsQueriesCount: Long = 0L,
    val blockedAdsCount: Long = 0L,
    val blockedPacketsCount: Long = 0L,
    val loadedAdDomainsCount: Int = 0,
    val isSafeSearchActive: Boolean = false,
    val youtubeRestrictedMode: YouTubeRestrictedMode = YouTubeRestrictedMode.OFF,
    val isYoutubeAdBlockActive: Boolean = false,
    val activeSniProxyPreset: SniProxyPreset = SniProxyPreset.STANDARD_DIRECT,
    val safeRewriteCount: Long = 0L,
    val recentBlockedDomains: List<String> = emptyList(),
    val recentDnsLogs: List<DnsQueryLogEntry> = emptyList()
)

class NetGateVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tunnelMutex = Mutex()
    private val tunWriteLock = Any()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var rulesJob: Job? = null
    private var timerJob: Job? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private val wifiNetworks = mutableSetOf<Network>()
    private val cellularNetworks = mutableSetOf<Network>()

    @Volatile
    private var latestRules: List<AppNetworkRuleEntity> = emptyList()

    @Volatile
    private var latestWebsiteRules: List<WebsiteRuleEntity> = emptyList()

    @Volatile
    private var latestWebsiteFilterMode: WebsiteFilterMode = WebsiteFilterMode.OFF

    @Volatile
    private var latestIsAdBlockerEnabled: Boolean = false

    @Volatile
    private var latestIsEssentialPassthroughEnabled: Boolean = true

    @Volatile
    private var latestUseDohUpstream: Boolean = true

    @Volatile
    private var latestUpstreamDnsTitle: String = UpstreamDnsServer.CLOUDFLARE_STANDARD.title

    @Volatile
    private var latestDohUrl: String = UpstreamDnsServer.CLOUDFLARE_STANDARD.dohUrl

    @Volatile
    private var latestUdpDnsServers: List<String> = UPSTREAM_DNS_SERVERS

    @Volatile
    private var latestIsHttpsProxyEnabled: Boolean = false

    @Volatile
    private var latestHttpsProxyPort: Int = 8899

    @Volatile
    private var latestStripTrackingParams: Boolean = true

    @Volatile
    private var latestIsSafeSearchEnabled: Boolean = false

    @Volatile
    private var latestYoutubeRestrictedMode: YouTubeRestrictedMode = YouTubeRestrictedMode.OFF

    @Volatile
    private var latestIsYoutubeAdBlockEnabled: Boolean = false

    @Volatile
    private var latestSniProxyPreset: SniProxyPreset = SniProxyPreset.STANDARD_DIRECT

    @Volatile
    private var latestCustomSniProxyHost: String = ""

    @Volatile
    private var latestCustomSniProxyPort: Int = 8080

    @Volatile
    private var latestIsDeepMonitorEnabled: Boolean = true

    @Volatile
    private var latestAdMatcherSnapshot: AdBlockMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY

    @Volatile
    private var latestLoadedAdDomainsCount: Int = 0

    @Volatile
    private var latestBypassPackages: Set<String> = emptySet()

    @Volatile
    private var latestBlockedUids: Set<Int> = emptySet()

    @Volatile
    private var currentNetworkType: ActiveNetworkType = ActiveNetworkType.NONE

    private var activeTunnelSignature: TunnelConfigSignature? = null
    private var lastAdMatcherSignature: String? = null
    private var proxyServer: LocalFilteringProxyServer? = null
    private var activeProxySignature: ProxyConfigSignature? = null

    private val blockedDnsCount = AtomicLong(0L)
    private val allowedDnsCount = AtomicLong(0L)
    private val blockedAdsCount = AtomicLong(0L)
    private val blockedPacketsCount = AtomicLong(0L)
    private val blockedProxyCount = AtomicLong(0L)
    private val allowedProxyCount = AtomicLong(0L)
    private val safeRewriteCount = AtomicLong(0L)
    private val pendingLifetimeAdsDelta = AtomicLong(0L)
    private val isFlushingLifetimeAds = AtomicBoolean(false)
    private val dnsLogSeq = AtomicLong(1L)

    private val recentBlockedLock = Any()
    private val recentBlockedQueue = ArrayDeque<String>()
    private val packetLogThrottleMap = HashMap<String, Long>()

    private lateinit var preferences: FirewallPreferences

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
            updateNetworkSet(network, caps, available = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateNetworkSet(network, networkCapabilities, available = true)
        }

        override fun onLost(network: Network) {
            synchronized(this@NetGateVpnService) {
                wifiNetworks.remove(network)
                cellularNetworks.remove(network)
            }
            recomputeNetworkAndRebuild()
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = FirewallPreferences(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        createNotificationChannel()
        registerPhysicalNetworkCallback()
        observeRules()
        observeTimers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    preferences.setFirewallEnabled(false)
                    stopFirewall()
                }
                return START_NOT_STICKY
            }
            ACTION_RELOAD_RULES -> {
                startForegroundCompat(
                    buildNotification(0, currentNetworkType, latestWebsiteFilterMode)
                )
                serviceScope.launch { rebuildTunnel(forceRebuild = true) }
                return START_STICKY
            }
            else -> {
                startForegroundCompat(
                    buildNotification(0, currentNetworkType, latestWebsiteFilterMode)
                )
                serviceScope.launch {
                    preferences.setFirewallEnabled(true)
                    recomputeNetworkAndRebuild()
                }
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        serviceScope.launch {
            preferences.setFirewallEnabled(false)
            stopFirewall()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterPhysicalNetworkCallback()
        rulesJob?.cancel()
        timerJob?.cancel()
        drainJob?.cancel()
        stopProxyServerLocked()
        closeVpnInterface()
        _runtimeState.value = VpnRuntimeState()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeTimers() {
        val db = NetGateDatabase.getInstance(applicationContext)
        timerJob = serviceScope.launch {
            AppTimerScheduler.processDueTimers(applicationContext)
            db.appTimerDao().observeAllTimers().collectLatest { timers ->
                if (timers.isEmpty()) return@collectLatest
                val nextExpiry = timers.minOf { it.expiresAtMillis }
                val waitMs = (nextExpiry - System.currentTimeMillis()).coerceAtLeast(25L)
                delay(waitMs)
                AppTimerScheduler.processDueTimers(applicationContext)
            }
        }
    }

    private fun observeRules() {
        val db = NetGateDatabase.getInstance(applicationContext)
        rulesJob = serviceScope.launch {
            withContext(Dispatchers.IO) {
                db.adFilterListDao().insertMissingFilterLists(AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS)
            }
            combine(
                db.appNetworkRuleDao().observeAllRules(),
                db.websiteRuleDao().observeAllRules(),
                db.adFilterListDao().observeAllFilterLists(),
                preferences.preferencesFlow
            ) { appRules, websiteRules, adFilterLists, prefs ->
                RulesSnapshot(appRules, websiteRules, adFilterLists, prefs)
            }.collectLatest { snapshot ->
                val prefs = snapshot.prefs
                latestRules = snapshot.appRules
                latestWebsiteRules = snapshot.websiteRules
                latestWebsiteFilterMode = prefs.websiteFilterMode
                latestBypassPackages = prefs.websiteBypassPackages
                latestIsAdBlockerEnabled = prefs.isAdBlockerEnabled
                latestIsEssentialPassthroughEnabled = prefs.isEssentialPassthroughEnabled
                latestUseDohUpstream = prefs.useDohUpstream
                latestUpstreamDnsTitle = prefs.selectedUpstreamDns.title
                latestDohUrl = prefs.resolvedDohUrl()
                latestUdpDnsServers = prefs.resolvedUdpDnsServers()
                latestIsHttpsProxyEnabled = prefs.isHttpsProxyEnabled
                latestHttpsProxyPort = prefs.httpsProxyPort.coerceIn(1024, 65535)
                latestStripTrackingParams = prefs.isProxyStripTrackingParams
                latestIsSafeSearchEnabled = prefs.isSafeSearchEnabled
                latestYoutubeRestrictedMode = prefs.youtubeRestrictedMode
                latestIsYoutubeAdBlockEnabled = prefs.isYoutubeAdBlockEnabled
                latestSniProxyPreset = prefs.sniProxyPreset
                latestCustomSniProxyHost = prefs.customSniProxyHost.trim()
                latestCustomSniProxyPort = prefs.customSniProxyPort.coerceIn(1, 65535)
                latestIsDeepMonitorEnabled = prefs.isDeepMonitorEnabled

                val desiredAdSig = if (!prefs.isAdBlockerEnabled && !prefs.isYoutubeAdBlockEnabled) {
                    "OFF"
                } else {
                    snapshot.adFilterLists
                        .filter { it.isEnabled }
                        .sortedBy { it.id }
                        .joinToString("|") { "${it.id}:${it.lastSyncedAt}:${it.domainCount}" }
                }
                if (desiredAdSig != lastAdMatcherSignature) {
                    lastAdMatcherSignature = desiredAdSig
                    if (!prefs.isAdBlockerEnabled) {
                        latestAdMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY
                        latestLoadedAdDomainsCount = 0
                    } else {
                        val built = withContext(Dispatchers.IO) {
                            AdBlockFilterEngine.buildMatcherSnapshot(
                                applicationContext,
                                snapshot.adFilterLists
                            )
                        }
                        latestAdMatcherSnapshot = built
                        latestLoadedAdDomainsCount = built.totalBlockedCount
                    }
                }

                rebuildTunnel(forceRebuild = false)
            }
        }
    }

    private data class RulesSnapshot(
        val appRules: List<AppNetworkRuleEntity>,
        val websiteRules: List<WebsiteRuleEntity>,
        val adFilterLists: List<AdFilterListEntity>,
        val prefs: FirewallPreferencesState
    )

    private fun registerPhysicalNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }
        seedInitialNetworks(cm)
    }

    private fun unregisterPhysicalNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        networkCallbackRegistered = false
    }

    private fun seedInitialNetworks(cm: ConnectivityManager) {
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeNetwork != null && caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        ) {
            updateNetworkSet(activeNetwork, caps, available = true)
        } else {
            currentNetworkType = detectPhysicalNetworkFallback(cm)
            _runtimeState.value = _runtimeState.value.copy(
                isServiceRunning = true,
                activeNetworkType = currentNetworkType
            )
        }
    }

    private fun detectPhysicalNetworkFallback(cm: ConnectivityManager): ActiveNetworkType {
        @Suppress("DEPRECATION")
        val all = cm.allNetworks
        var hasWifi = false
        var hasCell = false
        for (net in all) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) hasWifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) hasCell = true
        }
        return when {
            hasWifi -> ActiveNetworkType.WIFI
            hasCell -> ActiveNetworkType.MOBILE_DATA
            else -> ActiveNetworkType.NONE
        }
    }

    private fun updateNetworkSet(
        network: Network,
        caps: NetworkCapabilities,
        available: Boolean
    ) {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
        synchronized(this) {
            if (available && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiNetworks.add(network)
            } else {
                wifiNetworks.remove(network)
            }
            if (available && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                cellularNetworks.add(network)
            } else {
                cellularNetworks.remove(network)
            }
        }
        recomputeNetworkAndRebuild()
    }

    private fun recomputeNetworkAndRebuild() {
        val nextType = synchronized(this) {
            when {
                wifiNetworks.isNotEmpty() -> ActiveNetworkType.WIFI
                cellularNetworks.isNotEmpty() -> ActiveNetworkType.MOBILE_DATA
                else -> ActiveNetworkType.NONE
            }
        }
        currentNetworkType = nextType
        syncUnderlyingNetworks(nextType)
        serviceScope.launch {
            rebuildTunnel(forceRebuild = false)
        }
    }

    private fun syncUnderlyingNetworks(networkType: ActiveNetworkType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        val underlying: Array<Network>? = synchronized(this) {
            when (networkType) {
                ActiveNetworkType.WIFI -> wifiNetworks.toTypedArray().takeIf { it.isNotEmpty() }
                ActiveNetworkType.MOBILE_DATA -> cellularNetworks.toTypedArray().takeIf { it.isNotEmpty() }
                ActiveNetworkType.OTHER -> (wifiNetworks + cellularNetworks).toTypedArray().takeIf { it.isNotEmpty() }
                ActiveNetworkType.NONE -> emptyArray()
            }
        }
        runCatching { setUnderlyingNetworks(underlying) }
    }

    private fun resolvePackageUid(pkg: String): Int? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L)).uid
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(pkg, 0).uid
        }
    }.getOrNull()

    private fun isPackageInstalled(pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg, 0)
        }
        true
    }.getOrDefault(false)

    private suspend fun rebuildTunnel(forceRebuild: Boolean) = tunnelMutex.withLock {
        val netType = currentNetworkType
        val websiteMode = latestWebsiteFilterMode
        val adBlockEnabled = latestIsAdBlockerEnabled
        val httpsProxyEnabled = latestIsHttpsProxyEnabled
        val proxyPort = latestHttpsProxyPort.coerceIn(1024, 65535)
        val stripTracking = latestStripTrackingParams
        val safeSearchEnabled = latestIsSafeSearchEnabled
        val youtubeRestrictedMode = latestYoutubeRestrictedMode
        val youtubeAdBlockEnabled = latestIsYoutubeAdBlockEnabled
        val targetBlockedPackages = when (netType) {
            ActiveNetworkType.WIFI -> latestRules.filter { it.blockWifi }.map { it.packageName }
            ActiveNetworkType.MOBILE_DATA -> latestRules.filter { it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.OTHER -> latestRules.filter { it.blockWifi && it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.NONE -> emptyList()
        }.filter { it != packageName }.distinct().sorted()

        val needsDnsProxy = websiteMode != WebsiteFilterMode.OFF ||
            adBlockEnabled ||
            httpsProxyEnabled ||
            safeSearchEnabled ||
            youtubeRestrictedMode != YouTubeRestrictedMode.OFF ||
            youtubeAdBlockEnabled ||
            (latestIsDeepMonitorEnabled && targetBlockedPackages.isEmpty())

        latestBlockedUids = targetBlockedPackages.mapNotNull { resolvePackageUid(it) }.toSet()

        val installedBlocked = targetBlockedPackages.filter { isPackageInstalled(it) }
        val effectiveBypass = if (!needsDnsProxy) {
            emptySet()
        } else {
            latestBypassPackages
                .filter { it != packageName && it !in targetBlockedPackages && isPackageInstalled(it) }
                .toSet()
        }

        val desiredSignature = TunnelConfigSignature(
            networkType = netType,
            websiteMode = websiteMode,
            adBlockerEnabled = adBlockEnabled,
            httpsProxyEnabled = httpsProxyEnabled,
            httpsProxyPort = proxyPort,
            stripTrackingParams = stripTracking,
            safeSearchEnabled = safeSearchEnabled,
            youtubeRestrictedMode = youtubeRestrictedMode,
            youtubeAdBlockEnabled = youtubeAdBlockEnabled,
            sniProxyPreset = latestSniProxyPreset,
            blockedPackages = if (!needsDnsProxy) installedBlocked else emptyList(),
            bypassPackages = effectiveBypass
        )

        // If TUN configuration hasn't changed (e.g. only rule lists or upstream DNS changed), keep existing TUN running.
        if (!forceRebuild && vpnInterface != null && activeTunnelSignature == desiredSignature) {
            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = installedBlocked.size,
                websiteMode = websiteMode,
                adBlockEnabled = adBlockEnabled
            )
            updateNotification(installedBlocked.size, netType, websiteMode, adBlockEnabled)
            return
        }

        drainJob?.cancel()
        drainJob = null
        closeVpnInterface()
        activeTunnelSignature = null

        val shouldRunTunnel = netType != ActiveNetworkType.NONE &&
            (needsDnsProxy || installedBlocked.isNotEmpty())

        if (!shouldRunTunnel) {
            stopProxyServerLocked()
            publishRuntimeState(
                isTunnelActive = false,
                netType = netType,
                blockedCount = 0,
                websiteMode = websiteMode,
                adBlockEnabled = adBlockEnabled
            )
            updateNotification(0, netType, websiteMode, adBlockEnabled)
            return
        }

        if (!needsDnsProxy) {
            stopProxyServerLocked()
            // Pure per-app sinkhole mode: route all traffic of blocked apps into local TUN and drop/reject packets.
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.1.10.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                .addRoute("::", 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(netType == ActiveNetworkType.MOBILE_DATA)
            }

            var addedCount = 0
            for (pkg in installedBlocked) {
                if (runCatching { builder.addAllowedApplication(pkg) }.isSuccess) {
                    addedCount++
                }
            }

            // CRITICAL: Never call establish() with an empty allowed-list, as Android routes ALL apps when empty.
            if (addedCount == 0) {
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(0, netType, websiteMode, adBlockEnabled)
                return
            }

            val established = runCatching { builder.establish() }.getOrNull()
            if (established == null) {
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(0, netType, websiteMode, adBlockEnabled)
                return
            }

            vpnInterface = established
            activeTunnelSignature = desiredSignature
            syncUnderlyingNetworks(netType)
            drainJob = serviceScope.launch(Dispatchers.IO) {
                drainPackets(established, installedBlocked)
            }

            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = addedCount,
                websiteMode = websiteMode,
                adBlockEnabled = adBlockEnabled
            )
            updateNotification(addedCount, netType, websiteMode, adBlockEnabled)
        } else {
            // Start local HTTP/CONNECT & TLS SNI proxy before binding VPN HTTP Proxy if enabled
            val proxyActiveNow = if (httpsProxyEnabled) {
                ensureProxyServerStartedLocked(proxyPort, stripTracking)
            } else {
                stopProxyServerLocked()
                false
            }

            // DNS Proxy Mode (Website Blocker, Ad Blocker, and/or HTTPS Filtering Proxy):
            // Dual-stack IPv4 + IPv6 DNS interception plus DoH/DoT resolver routes so Chrome/Firefox cannot bypass.
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.1.10.1", 32)
                .addDnsServer(VIRTUAL_DNS_SERVER)
                .addRoute(VIRTUAL_DNS_SERVER, 32)
                .addAddress(VIRTUAL_IPV6_ADDR, 128)
                .addDnsServer(VIRTUAL_IPV6_DNS_SERVER)
                .addRoute(VIRTUAL_IPV6_DNS_SERVER, 128)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(netType == ActiveNetworkType.MOBILE_DATA)
                if (proxyActiveNow) {
                    runCatching {
                        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", proxyPort))
                    }
                }
            }

            for ((cidrIp, prefixLen) in DOH_SINKHOLE_ROUTES) {
                runCatching { builder.addRoute(cidrIp, prefixLen) }
            }

            runCatching { builder.addDisallowedApplication(packageName) }
            for (bypassPkg in effectiveBypass) {
                runCatching { builder.addDisallowedApplication(bypassPkg) }
            }

            val established = runCatching { builder.establish() }.getOrNull()
            if (established == null) {
                stopProxyServerLocked()
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(0, netType, websiteMode, adBlockEnabled)
                return
            }

            vpnInterface = established
            activeTunnelSignature = desiredSignature
            syncUnderlyingNetworks(netType)
            drainJob = serviceScope.launch(Dispatchers.IO) {
                runDnsProxyLoop(established)
            }

            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = installedBlocked.size,
                websiteMode = websiteMode,
                adBlockEnabled = adBlockEnabled
            )
            updateNotification(installedBlocked.size, netType, websiteMode, adBlockEnabled)
        }
    }

    private fun ensureProxyServerStartedLocked(port: Int, stripTracking: Boolean): Boolean {
        val desired = ProxyConfigSignature(port, stripTracking)
        val existing = proxyServer
        if (existing != null && existing.isRunning && activeProxySignature == desired) {
            return true
        }
        stopProxyServerLocked()
        val server = LocalFilteringProxyServer(
            port = port,
            stripTrackingParams = stripTracking,
            protectSocket = { socket -> protect(socket) },
            isClientBlocked = ::isProxyClientSocketBlocked,
            evaluateRequest = ::evaluateProxyRequest,
            resolveTargetHost = ::resolveProxyTargetHost,
            upstreamProxyProvider = ::provideUpstreamSniProxy,
            onRequestLogged = ::handleProxyLogged,
            onTrafficRelayed = { host, targetPort, protocol, remoteIp, bytesSent, bytesReceived, decision ->
                if (latestIsDeepMonitorEnabled) {
                    ConnectionMonitorHub.recordConnection(
                        context = applicationContext,
                        domain = host,
                        remoteIp = remoteIp.orEmpty(),
                        remotePort = targetPort,
                        protocol = protocol,
                        bytesSent = bytesSent,
                        bytesReceived = bytesReceived,
                        actionStatus = if (decision.allow) "ALLOWED" else "BLOCKED",
                        isAdBlocked = decision.isAdBlock
                    )
                }
            }
        )
        return if (server.start()) {
            proxyServer = server
            activeProxySignature = desired
            true
        } else {
            false
        }
    }

    private fun resolveProxyTargetHost(host: String): String {
        val rewrite = DnsPacketFilter.resolveSafeSearchAlias(
            domain = host,
            safeSearchEnabled = latestIsSafeSearchEnabled,
            youtubeRestrictedMode = latestYoutubeRestrictedMode
        )
        return rewrite?.aliasDomain ?: host
    }

    private fun provideUpstreamSniProxy(): Pair<String, Int>? {
        if (latestSniProxyPreset != SniProxyPreset.CUSTOM_UPSTREAM_SNI) return null
        val host = latestCustomSniProxyHost.trim()
        if (host.isEmpty()) return null
        return host to latestCustomSniProxyPort.coerceIn(1, 65535)
    }

    private fun stopProxyServerLocked() {
        runCatching { proxyServer?.stop() }
        proxyServer = null
        activeProxySignature = null
    }

    private fun evaluateProxyRequest(host: String, pathAndQuery: String?): ProxyFilterDecision {
        val domain = host.trim().trimEnd('.').lowercase()
        val websiteRules = latestWebsiteRules
        val websiteMode = latestWebsiteFilterMode
        val isExplicitlyWhitelisted = websiteRules.any {
            it.isEnabled && it.ruleType == WebsiteRuleType.ALLOW &&
                DnsPacketFilter.matchesDomainRule(domain, it.domain)
        }

        if (!isExplicitlyWhitelisted && websiteMode != WebsiteFilterMode.OFF) {
            val blockedBySiteRule = DnsPacketFilter.shouldBlockDomain(
                domain = domain,
                mode = websiteMode,
                rules = websiteRules
            )
            if (blockedBySiteRule) {
                val reason = if (websiteMode == WebsiteFilterMode.WHITELIST_ONLY) {
                    "Whitelist Lockdown"
                } else {
                    "Website Blocklist"
                }
                return ProxyFilterDecision(allow = false, reason = reason, isAdBlock = false)
            }
        }

        val adMatcher = latestAdMatcherSnapshot
        val isPassthrough = latestIsEssentialPassthroughEnabled &&
            adMatcher.isEssentialPassthrough(domain)
        if (isPassthrough) {
            return ProxyFilterDecision(allow = true, reason = "Essential Passthrough")
        }

        if (!isExplicitlyWhitelisted && latestIsYoutubeAdBlockEnabled) {
            if (AdBlockFilterEngine.isYoutubeAdDomain(domain) ||
                AdBlockFilterEngine.isYoutubeAdUrlPath(pathAndQuery)
            ) {
                return ProxyFilterDecision(allow = false, reason = "YouTube Ad Blocker", isAdBlock = true)
            }
        }

        if (!isExplicitlyWhitelisted && (latestIsAdBlockerEnabled || latestIsHttpsProxyEnabled)) {
            if (adMatcher.isBlockedByAdFilter(domain)) {
                return ProxyFilterDecision(allow = false, reason = "HTTPS Ad/Tracker Domain", isAdBlock = true)
            }
            if (AdBlockFilterEngine.isAdOrTrackerUrlPath(pathAndQuery)) {
                return ProxyFilterDecision(allow = false, reason = "HTTPS Ad/Tracker Path", isAdBlock = true)
            }
        }

        return ProxyFilterDecision(allow = true, reason = "SNI Proxy (${latestSniProxyPreset.title})")
    }

    private fun handleProxyLogged(host: String, decision: ProxyFilterDecision) {
        if (!decision.allow) {
            blockedProxyCount.incrementAndGet()
            if (decision.isAdBlock) {
                blockedAdsCount.incrementAndGet()
                scheduleFlushLifetimeAds()
            }
            recordBlockedDomain(host)
            recordDnsLog(host, DnsQueryStatus.BLOCKED_HTTPS, decision.reason)
        } else {
            allowedProxyCount.incrementAndGet()
        }
        publishDnsStatsOnly()
    }

    private fun drainPackets(
        pfd: ParcelFileDescriptor,
        blockedPackages: List<String>
    ) {
        val buffer = ByteArray(32767)
        val output = FileOutputStream(pfd.fileDescriptor)
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    if (bytesRead == 0) continue

                    val packetCopy = buffer.copyOf(bytesRead)
                    val parsed = TunPacketInspector.parseIpPacket(packetCopy, bytesRead)
                    blockedPacketsCount.incrementAndGet()

                    if (parsed != null) {
                        if (parsed.protocol == TunPacketInspector.IPPROTO_UDP && parsed.dstPort == 53) {
                            val dnsQuery = DnsPacketFilter.parseUdpDnsQuery(packetCopy, bytesRead)
                            if (dnsQuery != null) {
                                sendNxDomain(dnsQuery, output)
                                recordInterceptedAppPacket(
                                    parsed = parsed,
                                    domainOrHost = dnsQuery.domain,
                                    blockedPackages = blockedPackages
                                )
                                continue
                            }
                        } else if (parsed.protocol == TunPacketInspector.IPPROTO_TCP && parsed.isTcpSyn) {
                            val rstPacket = DnsPacketFilter.buildTcpResetPacket(packetCopy, bytesRead)
                            if (rstPacket != null) {
                                synchronized(tunWriteLock) {
                                    runCatching { output.write(rstPacket) }
                                }
                            }
                        }

                        recordInterceptedAppPacket(
                            parsed = parsed,
                            domainOrHost = parsed.dstIpText,
                            blockedPackages = blockedPackages
                        )
                    }
                }
            }
        } catch (_: IOException) {
            // Expected when interface is closed during rebuild or shutdown
        } finally {
            runCatching { output.close() }
        }
    }

    private fun recordInterceptedAppPacket(
        parsed: ParsedIpPacket,
        domainOrHost: String,
        blockedPackages: List<String>
    ) {
        val target = domainOrHost.ifBlank { parsed.dstIpText }.ifBlank { return }
        val ownerPkg = resolveInterceptedPacketPackage(parsed, blockedPackages)
        val throttleKey = "$ownerPkg|${parsed.dstIpText}|${parsed.dstPort}"
        val now = System.currentTimeMillis()
        val shouldRecord = synchronized(packetLogThrottleMap) {
            val prev = packetLogThrottleMap[throttleKey] ?: 0L
            if (now - prev >= 2500L) {
                if (packetLogThrottleMap.size > 256) {
                    packetLogThrottleMap.clear()
                }
                packetLogThrottleMap[throttleKey] = now
                true
            } else {
                false
            }
        }
        if (!shouldRecord) return

        recordBlockedDomain(target)
        recordDnsLog(target, DnsQueryStatus.BLOCKED_APP, "App Firewall (${parsed.protocolName})")
        if (latestIsDeepMonitorEnabled) {
            ConnectionMonitorHub.recordConnection(
                context = applicationContext,
                domain = target,
                remoteIp = parsed.dstIpText,
                remotePort = parsed.dstPort,
                protocol = parsed.protocolName,
                appPackage = ownerPkg,
                bytesSent = parsed.totalLength.toLong(),
                bytesReceived = 0L,
                actionStatus = "BLOCKED"
            )
        }
        publishDnsStatsOnly()
    }

    private fun resolveInterceptedPacketPackage(
        parsed: ParsedIpPacket,
        blockedPackages: List<String>
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (parsed.protocol == TunPacketInspector.IPPROTO_TCP || parsed.protocol == TunPacketInspector.IPPROTO_UDP) &&
            parsed.srcPort > 0 && parsed.dstPort > 0
        ) {
            val cm = connectivityManager
            val srcAddr = DnsPacketFilter.ipBytesToInetAddress(parsed.srcIp)
            val dstAddr = DnsPacketFilter.ipBytesToInetAddress(parsed.dstIp)
            if (cm != null && srcAddr != null && dstAddr != null) {
                val resolved = runCatching {
                    val uid = cm.getConnectionOwnerUid(
                        parsed.protocol,
                        InetSocketAddress(srcAddr, parsed.srcPort),
                        InetSocketAddress(dstAddr, parsed.dstPort)
                    )
                    if (uid > 0) packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty() else ""
                }.getOrDefault("")
                if (resolved.isNotBlank()) return resolved
            }
        }
        return if (blockedPackages.size == 1) blockedPackages.first() else ""
    }

    private fun isProxyClientSocketBlocked(client: java.net.Socket): Boolean {
        val blockedUids = latestBlockedUids
        if (blockedUids.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val cm = connectivityManager ?: return false
        val clientLocal = client.remoteSocketAddress as? InetSocketAddress ?: return false
        val proxyRemote = client.localSocketAddress as? InetSocketAddress ?: return false
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                TunPacketInspector.IPPROTO_TCP,
                clientLocal,
                proxyRemote
            )
            uid in blockedUids
        }.getOrDefault(false)
    }

    private fun runDnsProxyLoop(pfd: ParcelFileDescriptor) {
        val buffer = ByteArray(32767)
        val output = FileOutputStream(pfd.fileDescriptor)
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    if (bytesRead == 0) continue

                    val packetCopy = buffer.copyOf(bytesRead)
                    val query = DnsPacketFilter.parseUdpDnsQuery(packetCopy, bytesRead)
                    if (query != null) {
                        serviceScope.launch(Dispatchers.IO) {
                            handleDnsQuery(query, output)
                        }
                    } else {
                        // Send immediate TCP RST to DoH (443) / DoT (853) connections on routed resolver IPs
                        // so Chrome & Firefox instantly fall back to NetGate's filtered DNS.
                        val tcpReset = DnsPacketFilter.buildTcpResetPacket(packetCopy, bytesRead)
                        if (tcpReset != null) {
                            synchronized(tunWriteLock) {
                                runCatching { output.write(tcpReset) }
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // Expected when interface is closed during rebuild or shutdown
        } finally {
            runCatching { output.close() }
        }
    }

    private fun handleDnsQuery(query: ParsedDnsQuery, output: FileOutputStream) {
        val isCanary = DnsPacketFilter.isInternalBypassCanary(query.domain)
        val ownerPackage = if (!isCanary) resolveQueryOwnerPackage(query) else ""
        val isAppBlockedByUid = isQueryFromBlockedUid(query)
        if (isAppBlockedByUid) {
            sendNxDomain(query, output)
            if (!isCanary) {
                blockedDnsCount.incrementAndGet()
                recordBlockedDomain(query.domain)
                recordDnsLog(query.domain, DnsQueryStatus.BLOCKED_APP, "App Firewall")
                if (latestIsDeepMonitorEnabled) {
                    ConnectionMonitorHub.recordConnection(
                        context = applicationContext,
                        domain = query.domain,
                        remoteIp = "",
                        remotePort = 53,
                        protocol = "DNS",
                        appPackage = ownerPackage,
                        bytesSent = query.dnsPayload.size.toLong(),
                        bytesReceived = 0L,
                        actionStatus = "BLOCKED"
                    )
                }
                publishDnsStatsOnly()
            }
            return
        }

        val websiteRules = latestWebsiteRules
        val websiteMode = latestWebsiteFilterMode
        val isExplicitlyWhitelisted = websiteRules.any {
            it.isEnabled && it.ruleType == WebsiteRuleType.ALLOW &&
                DnsPacketFilter.matchesDomainRule(query.domain, it.domain)
        }

        if (!isExplicitlyWhitelisted && websiteMode != WebsiteFilterMode.OFF) {
            val blockedBySiteRule = DnsPacketFilter.shouldBlockDomain(
                domain = query.domain,
                mode = websiteMode,
                rules = websiteRules
            )
            if (blockedBySiteRule) {
                sendNxDomain(query, output)
                if (!isCanary) {
                    blockedDnsCount.incrementAndGet()
                    recordBlockedDomain(query.domain)
                    val reason = if (websiteMode == WebsiteFilterMode.WHITELIST_ONLY) {
                        "Whitelist Lockdown"
                    } else {
                        "Website Blocklist"
                    }
                    recordDnsLog(query.domain, DnsQueryStatus.BLOCKED_WEBSITE, reason)
                    if (latestIsDeepMonitorEnabled) {
                        ConnectionMonitorHub.recordConnection(
                            context = applicationContext,
                            domain = query.domain,
                            remoteIp = "",
                            remotePort = 53,
                            protocol = "DNS",
                            appPackage = ownerPackage,
                            bytesSent = query.dnsPayload.size.toLong(),
                            bytesReceived = 0L,
                            actionStatus = "BLOCKED"
                        )
                    }
                    publishDnsStatsOnly()
                }
                return
            }
        }

        // Disable Chrome/Firefox auto-DoH probing whenever DNS Proxy is active (Website or Ad Blocker)
        if (!isExplicitlyWhitelisted && isCanary) {
            sendNxDomain(query, output)
            return
        }

        val adMatcher = latestAdMatcherSnapshot
        val isPassthrough = latestIsEssentialPassthroughEnabled &&
            adMatcher.isEssentialPassthrough(query.domain)

        if (!isExplicitlyWhitelisted && !isPassthrough && latestIsYoutubeAdBlockEnabled) {
            if (AdBlockFilterEngine.isYoutubeAdDomain(query.domain)) {
                sendNxDomain(query, output)
                blockedDnsCount.incrementAndGet()
                blockedAdsCount.incrementAndGet()
                scheduleFlushLifetimeAds()
                recordBlockedDomain(query.domain)
                recordDnsLog(query.domain, DnsQueryStatus.BLOCKED_AD, "YouTube Ad Blocker")
                if (latestIsDeepMonitorEnabled) {
                    ConnectionMonitorHub.recordConnection(
                        context = applicationContext,
                        domain = query.domain,
                        remoteIp = "",
                        remotePort = 53,
                        protocol = "DNS",
                        appPackage = ownerPackage,
                        bytesSent = query.dnsPayload.size.toLong(),
                        bytesReceived = 0L,
                        actionStatus = "BLOCKED",
                        isAdBlocked = true
                    )
                }
                publishDnsStatsOnly()
                return
            }
        }

        if (!isExplicitlyWhitelisted && !isPassthrough && latestIsAdBlockerEnabled) {
            if (adMatcher.isBlockedByAdFilter(query.domain)) {
                sendNxDomain(query, output)
                blockedDnsCount.incrementAndGet()
                blockedAdsCount.incrementAndGet()
                scheduleFlushLifetimeAds()
                recordBlockedDomain(query.domain)
                recordDnsLog(query.domain, DnsQueryStatus.BLOCKED_AD, "Ad & Tracker Filter")
                if (latestIsDeepMonitorEnabled) {
                    ConnectionMonitorHub.recordConnection(
                        context = applicationContext,
                        domain = query.domain,
                        remoteIp = "",
                        remotePort = 53,
                        protocol = "DNS",
                        appPackage = ownerPackage,
                        bytesSent = query.dnsPayload.size.toLong(),
                        bytesReceived = 0L,
                        actionStatus = "BLOCKED",
                        isAdBlocked = true
                    )
                }
                publishDnsStatsOnly()
                return
            }
        }

        val safeRewrite = if (!isExplicitlyWhitelisted) {
            DnsPacketFilter.resolveSafeSearchAlias(
                domain = query.domain,
                safeSearchEnabled = latestIsSafeSearchEnabled,
                youtubeRestrictedMode = latestYoutubeRestrictedMode
            )
        } else {
            null
        }

        if (safeRewrite != null) {
            val rewrittenPayload = DnsPacketFilter.buildRewrittenQueryPayload(query, safeRewrite.aliasDomain)
            if (rewrittenPayload != null) {
                val aliasResponse = resolveDnsUpstream(rewrittenPayload).first
                if (aliasResponse != null) {
                    val rewrittenAnswer = DnsPacketFilter.buildRewrittenAnswerPayload(query, aliasResponse)
                    if (rewrittenAnswer != null) {
                        val responsePacket = DnsPacketFilter.buildDnsResponsePacket(query, rewrittenAnswer)
                        synchronized(tunWriteLock) {
                            runCatching { output.write(responsePacket) }
                        }
                        safeRewriteCount.incrementAndGet()
                        allowedDnsCount.incrementAndGet()
                        recordDnsLog(
                            query.domain,
                            DnsQueryStatus.SAFE_REWRITE,
                            "${safeRewrite.label} (${safeRewrite.aliasDomain})"
                        )
                        publishDnsStatsOnly()
                        return
                    }
                }
            }
        }

        val (upstreamDnsResponse, upstreamLabel) = resolveDnsUpstream(query.dnsPayload)

        if (upstreamDnsResponse != null) {
            val responsePacket = DnsPacketFilter.buildDnsResponsePacket(query, upstreamDnsResponse)
            synchronized(tunWriteLock) {
                runCatching { output.write(responsePacket) }
            }
            allowedDnsCount.incrementAndGet()
            val status = if (isPassthrough) DnsQueryStatus.PASSTHROUGH else DnsQueryStatus.ALLOWED
            val reason = if (isPassthrough) "Essential Passthrough ($upstreamLabel)" else upstreamLabel
            recordDnsLog(query.domain, status, reason)
            if (latestIsDeepMonitorEnabled && !isCanary) {
                val resolvedIps = DnsPacketFilter.extractResolvedIpsFromDnsResponse(upstreamDnsResponse)
                val primaryIp = resolvedIps.firstOrNull().orEmpty()
                ConnectionMonitorHub.recordConnection(
                    context = applicationContext,
                    domain = query.domain,
                    remoteIp = primaryIp,
                    remotePort = if (latestIsHttpsProxyEnabled) latestHttpsProxyPort else 443,
                    protocol = "DNS",
                    appPackage = ownerPackage,
                    bytesSent = query.dnsPayload.size.toLong(),
                    bytesReceived = upstreamDnsResponse.size.toLong(),
                    actionStatus = "ALLOWED"
                )
            }
            publishDnsStatsOnly()
        }
    }

    private fun resolveDnsUpstream(dnsPayload: ByteArray): Pair<ByteArray?, String> {
        return if (latestUseDohUpstream) {
            val dohResp = AdBlockFilterEngine.forwardDnsOverHttps(dnsPayload, latestDohUrl)
            if (dohResp != null) {
                dohResp to "${latestUpstreamDnsTitle} DoH"
            } else {
                forwardDnsQuery(dnsPayload) to "${latestUpstreamDnsTitle} UDP"
            }
        } else {
            forwardDnsQuery(dnsPayload) to "${latestUpstreamDnsTitle} UDP"
        }
    }

    private fun sendNxDomain(query: ParsedDnsQuery, output: FileOutputStream) {
        val blockedPacket = DnsPacketFilter.buildBlockedDnsResponsePacket(query)
        synchronized(tunWriteLock) {
            runCatching { output.write(blockedPacket) }
        }
    }

    private fun scheduleFlushLifetimeAds() {
        pendingLifetimeAdsDelta.incrementAndGet()
        if (isFlushingLifetimeAds.compareAndSet(false, true)) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    delay(1500L)
                    val delta = pendingLifetimeAdsDelta.getAndSet(0L)
                    if (delta > 0L) {
                        preferences.incrementLifetimeAdsBlocked(delta)
                    }
                } finally {
                    isFlushingLifetimeAds.set(false)
                    if (pendingLifetimeAdsDelta.get() > 0L) {
                        scheduleFlushLifetimeAds()
                    }
                }
            }
        }
    }

    private fun isQueryFromBlockedUid(query: ParsedDnsQuery): Boolean {
        val blockedUids = latestBlockedUids
        if (blockedUids.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val cm = connectivityManager ?: return false
        val srcAddr = DnsPacketFilter.ipBytesToInetAddress(query.srcIp) ?: return false
        val dstAddr = DnsPacketFilter.ipBytesToInetAddress(query.dstIp) ?: return false
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                17, // IPPROTO_UDP
                InetSocketAddress(srcAddr, query.srcPort),
                InetSocketAddress(dstAddr, query.dstPort)
            )
            uid in blockedUids
        }.getOrDefault(false)
    }

    private fun resolveQueryOwnerPackage(query: ParsedDnsQuery): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        val cm = connectivityManager ?: return ""
        val srcAddr = DnsPacketFilter.ipBytesToInetAddress(query.srcIp) ?: return ""
        val dstAddr = DnsPacketFilter.ipBytesToInetAddress(query.dstIp) ?: return ""
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                17,
                InetSocketAddress(srcAddr, query.srcPort),
                InetSocketAddress(dstAddr, query.dstPort)
            )
            if (uid > 0) packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty() else ""
        }.getOrDefault("")
    }

    private fun forwardDnsQuery(dnsPayload: ByteArray): ByteArray? {
        val candidates = latestUdpDnsServers.ifEmpty { UPSTREAM_DNS_SERVERS }
        for (upstreamIp in candidates) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                if (!protect(socket)) {
                    socket.close()
                    continue
                }
                socket.soTimeout = 2500
                val upstreamAddr = InetAddress.getByName(upstreamIp)
                val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddr, 53)
                socket.send(outPacket)

                val recvBuf = ByteArray(4096)
                val inPacket = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(inPacket)
                return recvBuf.copyOf(inPacket.length)
            } catch (_: Exception) {
                // Try fallback upstream DNS server
            } finally {
                runCatching { socket?.close() }
            }
        }
        return null
    }

    private fun recordBlockedDomain(domain: String) {
        synchronized(recentBlockedLock) {
            recentBlockedQueue.remove(domain)
            recentBlockedQueue.addFirst(domain)
            while (recentBlockedQueue.size > MAX_RECENT_BLOCKED_DOMAINS) {
                recentBlockedQueue.removeLast()
            }
        }
    }

    private fun recordDnsLog(domain: String, status: DnsQueryStatus, reason: String) {
        val entry = DnsQueryLogEntry(
            id = dnsLogSeq.getAndIncrement(),
            domain = domain,
            status = status,
            reason = reason,
            timestampMs = System.currentTimeMillis()
        )
        synchronized(dnsLogLock) {
            dnsLogQueue.addFirst(entry)
            while (dnsLogQueue.size > MAX_DNS_LOGS) {
                dnsLogQueue.removeLast()
            }
        }
    }

    private fun publishDnsStatsOnly() {
        val current = _runtimeState.value
        val recentSnapshot = synchronized(recentBlockedLock) { recentBlockedQueue.toList() }
        val logSnapshot = synchronized(dnsLogLock) { dnsLogQueue.toList() }
        _runtimeState.value = current.copy(
            isAdBlockerEnabled = latestIsAdBlockerEnabled,
            isHttpsProxyActive = proxyServer?.isRunning == true,
            httpsProxyPort = latestHttpsProxyPort,
            blockedProxyRequestsCount = blockedProxyCount.get(),
            allowedProxyRequestsCount = allowedProxyCount.get(),
            activeUpstreamDnsTitle = latestUpstreamDnsTitle,
            blockedDnsQueriesCount = blockedDnsCount.get(),
            allowedDnsQueriesCount = allowedDnsCount.get(),
            blockedAdsCount = blockedAdsCount.get(),
            blockedPacketsCount = blockedPacketsCount.get(),
            loadedAdDomainsCount = latestLoadedAdDomainsCount,
            isSafeSearchActive = latestIsSafeSearchEnabled,
            youtubeRestrictedMode = latestYoutubeRestrictedMode,
            isYoutubeAdBlockActive = latestIsYoutubeAdBlockEnabled,
            activeSniProxyPreset = latestSniProxyPreset,
            safeRewriteCount = safeRewriteCount.get(),
            recentBlockedDomains = recentSnapshot,
            recentDnsLogs = logSnapshot
        )
    }

    private fun publishRuntimeState(
        isTunnelActive: Boolean,
        netType: ActiveNetworkType,
        blockedCount: Int,
        websiteMode: WebsiteFilterMode,
        adBlockEnabled: Boolean
    ) {
        val recentSnapshot = synchronized(recentBlockedLock) { recentBlockedQueue.toList() }
        val logSnapshot = synchronized(dnsLogLock) { dnsLogQueue.toList() }
        _runtimeState.value = VpnRuntimeState(
            isServiceRunning = true,
            isTunnelActive = isTunnelActive,
            activeNetworkType = netType,
            blockedActivePackagesCount = blockedCount,
            websiteFilterMode = websiteMode,
            isAdBlockerEnabled = adBlockEnabled,
            isHttpsProxyActive = proxyServer?.isRunning == true,
            httpsProxyPort = latestHttpsProxyPort,
            blockedProxyRequestsCount = blockedProxyCount.get(),
            allowedProxyRequestsCount = allowedProxyCount.get(),
            activeUpstreamDnsTitle = latestUpstreamDnsTitle,
            blockedDnsQueriesCount = blockedDnsCount.get(),
            allowedDnsQueriesCount = allowedDnsCount.get(),
            blockedAdsCount = blockedAdsCount.get(),
            blockedPacketsCount = blockedPacketsCount.get(),
            loadedAdDomainsCount = latestLoadedAdDomainsCount,
            isSafeSearchActive = latestIsSafeSearchEnabled,
            youtubeRestrictedMode = latestYoutubeRestrictedMode,
            isYoutubeAdBlockActive = latestIsYoutubeAdBlockEnabled,
            activeSniProxyPreset = latestSniProxyPreset,
            safeRewriteCount = safeRewriteCount.get(),
            recentBlockedDomains = recentSnapshot,
            recentDnsLogs = logSnapshot
        )
    }

    private fun closeVpnInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private suspend fun stopFirewall() {
        tunnelMutex.withLock {
            drainJob?.cancel()
            drainJob = null
            stopProxyServerLocked()
            closeVpnInterface()
            activeTunnelSignature = null
        }
        _runtimeState.value = VpnRuntimeState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.firewall_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.firewall_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        blockedCount: Int,
        networkType: ActiveNetworkType,
        websiteMode: WebsiteFilterMode,
        adBlockEnabled: Boolean
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(blockedCount, networkType, websiteMode, adBlockEnabled))
    }

    private fun buildNotification(
        blockedCount: Int,
        networkType: ActiveNetworkType,
        websiteMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
        adBlockEnabled: Boolean = latestIsAdBlockerEnabled
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, NetGateVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val netLabel = when (networkType) {
            ActiveNetworkType.WIFI -> "Wi-Fi"
            ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
            ActiveNetworkType.OTHER -> "Network"
            ActiveNetworkType.NONE -> "Offline"
        }

        val proxyActive = proxyServer?.isRunning == true
        val adSuffix = buildString {
            if (adBlockEnabled) append(" + Ad Blocker")
            if (latestIsYoutubeAdBlockEnabled) append(" + YT Ads")
            if (latestIsSafeSearchEnabled) append(" + SafeSearch")
            if (latestYoutubeRestrictedMode != YouTubeRestrictedMode.OFF) append(" + YT Kids")
            if (proxyActive) append(" + SNI Proxy")
        }
        val contentText = when (websiteMode) {
            WebsiteFilterMode.WHITELIST_ONLY ->
                "$netLabel • Strict Whitelist Lockdown$adSuffix"
            WebsiteFilterMode.BLOCKLIST ->
                "$netLabel • Website Blocklist$adSuffix${if (blockedCount > 0) " + $blockedCount blocked app(s)" else ""}"
            WebsiteFilterMode.OFF -> when {
                proxyActive && blockedCount > 0 ->
                    "$netLabel • SNI Proxy$adSuffix + $blockedCount blocked app${if (blockedCount == 1) "" else "s"}"
                proxyActive ->
                    "$netLabel • SNI Proxy (${latestSniProxyPreset.title}) on 127.0.0.1:$latestHttpsProxyPort"
                (adBlockEnabled || latestIsSafeSearchEnabled || latestYoutubeRestrictedMode != YouTubeRestrictedMode.OFF) && blockedCount > 0 ->
                    "$netLabel • Filter active$adSuffix + $blockedCount blocked app${if (blockedCount == 1) "" else "s"}"
                adBlockEnabled || latestIsSafeSearchEnabled || latestYoutubeRestrictedMode != YouTubeRestrictedMode.OFF ->
                    "$netLabel • NetGate Guard$adSuffix"
                blockedCount > 0 ->
                    "$netLabel active • Blocking $blockedCount app${if (blockedCount == 1) "" else "s"}"
                else ->
                    "$netLabel active • Standby (0 apps blocked)"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(getString(R.string.firewall_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.firewall_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private data class ProxyConfigSignature(
        val port: Int,
        val stripTrackingParams: Boolean
    )

    private data class TunnelConfigSignature(
        val networkType: ActiveNetworkType,
        val websiteMode: WebsiteFilterMode,
        val adBlockerEnabled: Boolean,
        val httpsProxyEnabled: Boolean,
        val httpsProxyPort: Int,
        val stripTrackingParams: Boolean,
        val safeSearchEnabled: Boolean,
        val youtubeRestrictedMode: YouTubeRestrictedMode,
        val youtubeAdBlockEnabled: Boolean,
        val sniProxyPreset: SniProxyPreset,
        val blockedPackages: List<String>,
        val bypassPackages: Set<String>
    )

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_RELOAD_RULES = "com.example.service.ACTION_RELOAD_RULES"

        private const val VIRTUAL_DNS_SERVER = "10.1.10.2"
        private const val VIRTUAL_IPV6_ADDR = "fd00:1:fd00:1:fd00:1:fd00:1"
        private const val VIRTUAL_IPV6_DNS_SERVER = "fd00:1:fd00:1:fd00:1:fd00:2"
        private val UPSTREAM_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

        // Route major public DNS / DoH / DoT resolver IPs through local TUN in Website Filter mode.
        // TCP 443/853 packets hitting these IPs receive an immediate TCP RST so Chrome/Firefox fall back to UDP/53.
        private val DOH_SINKHOLE_ROUTES: List<Pair<String, Int>> = listOf(
            // Cloudflare & Mozilla DoH
            "1.1.1.1" to 32,
            "1.0.0.1" to 32,
            "1.1.1.2" to 32,
            "1.0.0.2" to 32,
            "1.1.1.3" to 32,
            "1.0.0.3" to 32,
            "104.16.248.249" to 32,
            "104.16.249.249" to 32,
            "104.16.132.229" to 32,
            "104.16.133.229" to 32,
            // Google Public DNS / DoH
            "8.8.8.8" to 32,
            "8.8.4.4" to 32,
            // Quad9
            "9.9.9.9" to 32,
            "9.9.9.10" to 32,
            "9.9.9.11" to 32,
            "149.112.112.112" to 32,
            // OpenDNS
            "208.67.222.222" to 32,
            "208.67.220.220" to 32,
            // AdGuard DNS
            "94.140.14.14" to 32,
            "94.140.15.15" to 32,
            // NextDNS
            "45.90.28.0" to 24,
            "45.90.30.0" to 24,
            // CleanBrowsing
            "185.228.168.0" to 24,
            "185.228.169.0" to 24,
            // IPv6 equivalents
            "2606:4700:4700::1111" to 128,
            "2606:4700:4700::1001" to 128,
            "2606:4700:4700::64" to 128,
            "2001:4860:4860::8888" to 128,
            "2001:4860:4860::8844" to 128,
            "2620:fe::fe" to 128,
            "2620:fe::9" to 128,
            "2a10:50c0::ad1:ff" to 128,
            "2a10:50c0::ad2:ff" to 128
        )
        private const val MAX_RECENT_BLOCKED_DOMAINS = 8
        private const val MAX_DNS_LOGS = 120

        private const val CHANNEL_ID = "netgate_firewall_channel"
        private const val NOTIFICATION_ID = 4041

        private val dnsLogLock = Any()
        private val dnsLogQueue = ArrayDeque<DnsQueryLogEntry>()

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        fun clearRecentDnsLogs() {
            synchronized(dnsLogLock) {
                dnsLogQueue.clear()
            }
            _runtimeState.value = _runtimeState.value.copy(recentDnsLogs = emptyList())
        }

        fun startOrReload(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
