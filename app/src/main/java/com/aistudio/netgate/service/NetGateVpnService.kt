package com.aistudio.netgate.service

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
import com.aistudio.netgate.AppLog
import com.aistudio.netgate.MainActivity
import com.aistudio.netgate.R
import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.local.AdvancedPreferences
import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.UpstreamDnsServer
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.service.dns.DnsEngine
import com.aistudio.netgate.service.dns.DnsOutcome
import com.aistudio.netgate.service.dns.DnsResolutionContext
import com.aistudio.netgate.service.dns.DnsResponseCache
import com.aistudio.netgate.service.dns.DnsSocketPolicy
import com.aistudio.netgate.service.policy.NetworkPolicy
import com.aistudio.netgate.service.policy.NetworkPolicyEngine
import com.aistudio.netgate.service.policy.PolicyContext
import com.aistudio.netgate.service.policy.PolicyDecision
import com.aistudio.netgate.service.proxy.ProxyConfiguration
import com.aistudio.netgate.service.proxy.ProxyCoordinator
import com.aistudio.netgate.service.proxy.ProxyRole
import com.aistudio.netgate.service.session.VpnSessionCoordinator
import com.aistudio.netgate.service.session.VpnSlotOwner
import com.aistudio.netgate.service.vpn.TunnelWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    val isGlobalWifiBlocked: Boolean = false,
    val dpiBypassMode: DpiBypassMode = DpiBypassMode.OFF,
    val dpiBypassedConnectionsCount: Long = 0L,
    val dpiAutopilotLearnedCount: Int = 0,
    val dnsTransportMode: DnsTransportMode = DnsTransportMode.DOH_WITH_FALLBACK,
    val antiDnsPoisonBlockedCount: Long = 0L,
    val quicStrippedCount: Long = 0L,
    val recentBlockedDomains: List<String> = emptyList(),
    val recentDnsLogs: List<DnsQueryLogEntry> = emptyList(),
    /** Short, user-safe reason when a tunnel failed to establish; null once it is up. */
    val tunnelError: String? = null
)

/**
 * Bounded backoff for a tunnel that fails to establish: three retries after 1s, 3s and 10s. When
 * the budget is spent the failure stays visible in the firewall header until the next rebuild.
 */
internal object TunnelRetryPolicy {
    private val delaysMs = longArrayOf(1_000L, 3_000L, 10_000L)

    /** Delay before retry number [retriesSoFar] + 1, or null once every retry has been used. */
    fun delayBeforeRetry(retriesSoFar: Int): Long? = delaysMs.getOrNull(retriesSoFar)
}

class NetGateVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tunnelMutex = Mutex()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var rulesJob: Job? = null
    private var timerJob: Job? = null
    private var wifiAutomationJob: Job? = null

    /** Pending self-healing retry; cancelled by a new rebuild, ACTION_STOP and onDestroy. */
    private var tunnelRetryJob: Job? = null

    @Volatile
    private var tunnelEstablishAttempts: Int = 0

    @Volatile
    private var latestTunnelError: String? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private val wifiNetworks = mutableSetOf<Network>()
    private val cellularNetworks = mutableSetOf<Network>()
    private val otherNetworks = mutableSetOf<Network>()

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
    private var latestBlockedPackages: Set<String> = emptySet()

    @Volatile
    private var latestBlockedUids: Set<Int> = emptySet()

    @Volatile
    private var latestGlobalWifiBlocked: Boolean = false

    @Volatile
    private var latestFullPreferences: FirewallPreferencesState = FirewallPreferencesState()

    @Volatile
    private var latestDpiBypassMode: DpiBypassMode = DpiBypassMode.OFF

    @Volatile
    private var latestDpiFragmentChunkSize: Int = 4

    @Volatile
    private var latestDpiFragmentDelayMs: Int = 1

    @Volatile
    private var latestDpiIdleKeepAliveEnabled: Boolean = true

    @Volatile
    private var latestBlockQuicForDpiBypass: Boolean = true

    @Volatile
    private var latestAntiDnsPoisonEnabled: Boolean = true

    @Volatile
    private var latestDnsTransportMode: DnsTransportMode = DnsTransportMode.DOH_WITH_FALLBACK

    @Volatile
    private var latestDnsCacheEnabled: Boolean = false

    /** Fail-closed kill switch: while true, every packet on the TUN is dropped. */
    @Volatile
    private var killSwitchLockdown: Boolean = false

    private var advancedPrefsJob: Job? = null

    @Volatile
    private var currentNetworkType: ActiveNetworkType = ActiveNetworkType.NONE

    @Volatile
    private var isSelfDisallowedOnTunnel: Boolean = false

    private val policyLock = Any()
    private val policyRef = java.util.concurrent.atomic.AtomicReference(NetworkPolicy.EMPTY)
    private val dnsEngine = DnsEngine()

    private var activeTunnelSignature: TunnelConfigSignature? = null
    private var lastAdMatcherSignature: String? = null

    /** Inputs that can invalidate a remembered DNS answer; a change clears the cache. */
    private var lastDnsCacheSignature: String? = null

    private val blockedDnsCount = AtomicLong(0L)
    private val allowedDnsCount = AtomicLong(0L)
    private val blockedAdsCount = AtomicLong(0L)
    private val blockedPacketsCount = AtomicLong(0L)
    private val blockedProxyCount = AtomicLong(0L)
    private val allowedProxyCount = AtomicLong(0L)
    private val safeRewriteCount = AtomicLong(0L)
    private val dpiBypassedCount = AtomicLong(0L)
    private val antiDnsPoisonCount = AtomicLong(0L)
    private val quicStrippedCount = AtomicLong(0L)
    private val pendingLifetimeAdsDelta = AtomicLong(0L)
    private val isFlushingLifetimeAds = AtomicBoolean(false)
    private val dnsLogSeq = AtomicLong(1L)
    @Volatile private var stopping = false

    private val recentBlockedLock = Any()
    private val recentBlockedQueue = ArrayDeque<String>()
    private val packetLogThrottleMap = HashMap<String, Long>()
    private val appLabelCache = ConcurrentHashMap<String, String>(64)

    private lateinit var preferences: FirewallPreferences

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (stopping) return
            val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
            updateNetworkSet(network, caps, available = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (stopping) return
            updateNetworkSet(network, networkCapabilities, available = true)
        }

        override fun onLost(network: Network) {
            if (stopping) return
            synchronized(this@NetGateVpnService) {
                wifiNetworks.remove(network)
                cellularNetworks.remove(network)
                otherNetworks.remove(network)
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
        observeWifiAutomation()
        observeAdvancedSettings()
        BackgroundPowerHelper.acquireServiceWakeLock(applicationContext, "firewall-vpn")
    }

    private fun observeAdvancedSettings() {
        advancedPrefsJob?.cancel()
        advancedPrefsJob = serviceScope.launch {
            AdvancedPreferences(applicationContext).flow.collect { advanced ->
                killSwitchLockdown = advanced.killSwitchEngaged
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLogCollector.info("Service", "Firewall VPN service stopping", "Received ACTION_STOP command")
                serviceScope.launch {
                    preferences.setFirewallEnabled(false)
                    stopFirewall()
                }
                return START_NOT_STICKY
            }
            ACTION_RELOAD_RULES -> {
                // The service is started with startForegroundService(), so the foreground
                // notification must go up before any early return, otherwise Android 26+ kills the
                // app with ForegroundServiceDidNotStartInTimeException.
                startForegroundCompat(
                    buildNotification(0, currentNetworkType, latestWebsiteFilterMode)
                )
                if (prepare(this) != null) {
                    AppLogCollector.warn("Service", "Firewall reload aborted", "Missing VpnService consent")
                    serviceScope.launch {
                        preferences.setFirewallEnabled(false)
                        stopFirewall()
                    }
                    return START_NOT_STICKY
                }
                // Android allows a single VpnService per profile: if the VPN Guard tunnel owns the
                // slot (or a transition is in flight) this service must not establish a second one.
                if (!VpnSessionCoordinator.instance.claimLocalFirewall()) {
                    AppLogCollector.warn("Service", "Firewall reload skipped", "VPN Guard currently holds the system VPN slot")
                    serviceScope.launch {
                        preferences.setFirewallEnabled(false)
                        stopFirewall()
                    }
                    return START_NOT_STICKY
                }
                AppLogCollector.info("Service", "Reloading firewall rules", "Rebuilding NetGate VPN tunnel")
                serviceScope.launch { rebuildTunnel(forceRebuild = true) }
                return START_STICKY
            }
            else -> {
                // See ACTION_RELOAD_RULES: start foreground before the consent/claim early returns.
                startForegroundCompat(
                    buildNotification(0, currentNetworkType, latestWebsiteFilterMode)
                )
                if (prepare(this) != null) {
                    AppLogCollector.warn("Service", "Firewall start aborted", "Missing VpnService consent")
                    serviceScope.launch {
                        preferences.setFirewallEnabled(false)
                        stopFirewall()
                    }
                    return START_NOT_STICKY
                }
                if (!VpnSessionCoordinator.instance.claimLocalFirewall()) {
                    AppLogCollector.warn("Service", "Firewall start deferred", "VPN Guard holds the VPN slot")
                    serviceScope.launch {
                        preferences.setFirewallEnabled(false)
                        stopFirewall()
                    }
                    return START_NOT_STICKY
                }
                AppLogCollector.info("Service", "Firewall VPN service started", "Initializing tunnel and rule engine")
                serviceScope.launch {
                    preferences.setFirewallEnabled(true)
                    recomputeNetworkAndRebuild()
                }
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        AppLogCollector.warn("Service", "VPN permission revoked", "Android system or another VPN app revoked NetGate TUN")
        serviceScope.launch {
            preferences.setFirewallEnabled(false)
            stopFirewall()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        AppLogCollector.info("Service", "NetGateVpnService destroyed", "Released wakelock and closed TUN interface")
        unregisterPhysicalNetworkCallback()
        rulesJob?.cancel()
        timerJob?.cancel()
        wifiAutomationJob?.cancel()
        advancedPrefsJob?.cancel()
        drainJob?.cancel()
        tunnelRetryJob?.cancel()
        tunnelRetryJob = null
        BackgroundPowerHelper.releaseServiceWakeLock("firewall-vpn")
        serviceScope.cancel()
        stopProxyServerLocked()
        closeVpnInterface()
        _runtimeState.value = VpnRuntimeState()
        VpnSessionCoordinator.instance.releaseLocalFirewall()
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

    private fun observeWifiAutomation() {
        wifiAutomationJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                // Refresh the Doze-proof wake lock on every tick (no-op while it is still held).
                BackgroundPowerHelper.acquireServiceWakeLock(applicationContext, "firewall-vpn")
                val prefs = latestFullPreferences
                if (prefs.globalWifiTimerExpiresAtMillis > 0L &&
                    System.currentTimeMillis() >= prefs.globalWifiTimerExpiresAtMillis
                ) {
                    WifiAutomationManager.processDueWifiTimer(applicationContext)
                }
                if (prefs.autoBlockWifiOnIdleEnabled && !SystemNetworkMonitor.isServiceActive) {
                    val telemetry = SystemNetworkMonitor.sampleNow(applicationContext)
                    WifiAutomationManager.evaluateIdleSample(
                        context = applicationContext,
                        activeNetworkType = telemetry.activeNetworkType,
                        totalBytesPerSec = telemetry.totalBytesPerSec,
                        prefs = prefs
                    )
                }
                val nowMs = System.currentTimeMillis()
                if (latestWebsiteRules.any { it.isEnabled && it.expiresAtMillis in 1..nowMs }) {
                    runCatching {
                        NetGateDatabase.getInstance(applicationContext)
                            .websiteRuleDao()
                            .deleteExpiredRules(nowMs)
                    }
                }
            }
        }
    }

    private fun observeRules() {
        val db = NetGateDatabase.getInstance(applicationContext)
        rulesJob = serviceScope.launch {
            withContext(Dispatchers.IO) {
                db.websiteRuleDao().deleteExpiredRules(System.currentTimeMillis())
                val adDao = db.adFilterListDao()
                adDao.deleteFilterListsByIds(AdBlockFilterEngine.LEGACY_REMOVED_LIST_IDS.toList())
                adDao.insertMissingFilterLists(AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS)
                if (preferences.markBalancedDefaultsV2AppliedIfUnset()) {
                    adDao.getAllFilterListsOnce()
                        .filter { !it.id.startsWith("custom_") }
                        .forEach { entity ->
                            val shouldEnable = entity.id in AdBlockFilterEngine.BALANCED_DEFAULT_LIST_IDS
                            if (entity.isEnabled != shouldEnable) {
                                adDao.setListEnabled(entity.id, shouldEnable)
                            }
                        }
                }
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
                val desiredAdSig = if (!prefs.isAdBlockerEnabled && !prefs.isYoutubeAdBlockEnabled) {
                    "OFF"
                } else {
                    snapshot.adFilterLists
                        .filter { it.isEnabled }
                        .sortedBy { it.id }
                        .joinToString("|") { "${it.id}:${it.lastSyncedAt}:${it.domainCount}" }
                }

                var rebuiltAdMatcher: AdBlockMatcherSnapshot? = null
                var rebuiltAdCount = 0
                val adMatcherChanged = desiredAdSig != lastAdMatcherSignature
                if (adMatcherChanged) {
                    if (!prefs.isAdBlockerEnabled) {
                        rebuiltAdMatcher = AdBlockMatcherSnapshot.EMPTY
                        rebuiltAdCount = 0
                    } else {
                        val built = withContext(Dispatchers.IO) {
                            AdBlockFilterEngine.buildMatcherSnapshot(
                                applicationContext,
                                snapshot.adFilterLists
                            )
                        }
                        rebuiltAdMatcher = built
                        rebuiltAdCount = built.totalBlockedCount
                    }
                }

                synchronized(policyLock) {
                    latestFullPreferences = prefs
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
                    latestGlobalWifiBlocked = prefs.globalWifiBlocked
                    latestDpiBypassMode = prefs.dpiBypassMode
                    latestDpiFragmentChunkSize = prefs.dpiFragmentChunkSize
                    latestDpiFragmentDelayMs = prefs.dpiFragmentDelayMs
                    latestDpiIdleKeepAliveEnabled = prefs.dpiIdleKeepAliveEnabled
                    latestBlockQuicForDpiBypass = prefs.blockQuicForDpiBypass
                    latestAntiDnsPoisonEnabled = prefs.antiDnsPoisonEnabled
                    latestDnsTransportMode = prefs.dnsTransportMode
                    latestDnsCacheEnabled = prefs.dnsResponseCacheEnabled

                    // Rules, lists, upstreams and DPI/anti-poison settings all change what a
                    // remembered answer means, so any change drops the cache.
                    val dnsCacheSignature = listOf(
                        prefs.websiteFilterMode.name,
                        prefs.isAdBlockerEnabled.toString(),
                        prefs.selectedUpstreamDns.name,
                        prefs.customDohUrl,
                        prefs.customUdpDnsIp,
                        prefs.useDohUpstream.toString(),
                        prefs.dnsTransportMode.name,
                        prefs.dpiBypassMode.name,
                        prefs.blockQuicForDpiBypass.toString(),
                        prefs.antiDnsPoisonEnabled.toString(),
                        snapshot.appRules.size.toString(),
                        snapshot.websiteRules.joinToString(",") { "${it.domain}:${it.ruleType}:${it.isEnabled}" },
                        snapshot.adFilterLists.joinToString(",") { "${it.id}:${it.isEnabled}:${it.lastSyncedAt}" }
                    ).joinToString("|")
                    if (lastDnsCacheSignature != null && lastDnsCacheSignature != dnsCacheSignature) {
                        DnsResponseCache.clear()
                    }
                    lastDnsCacheSignature = dnsCacheSignature

                    if (adMatcherChanged && rebuiltAdMatcher != null) {
                        lastAdMatcherSignature = desiredAdSig
                        latestAdMatcherSnapshot = rebuiltAdMatcher
                        latestLoadedAdDomainsCount = rebuiltAdCount
                    }

                    policyRef.set(
                        NetworkPolicy.fromPreferencesAndRules(
                            prefs = prefs,
                            websiteRules = snapshot.websiteRules,
                            adMatcher = latestAdMatcherSnapshot,
                            blockedUids = latestBlockedUids,
                            blockedPackages = latestBlockedPackages,
                            nowMillis = System.currentTimeMillis()
                        )
                    )
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
        var hasOther = false
        for (net in all) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> hasWifi = true
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> hasCell = true
                else -> hasOther = true
            }
        }
        return when {
            hasWifi -> ActiveNetworkType.WIFI
            hasCell -> ActiveNetworkType.MOBILE_DATA
            hasOther -> ActiveNetworkType.OTHER
            else -> ActiveNetworkType.NONE
        }
    }

    private fun updateNetworkSet(
        network: Network,
        caps: NetworkCapabilities,
        available: Boolean
    ) {
        if (stopping) return
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        synchronized(this) {
            val isWifi = available && hasInternet && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = available && hasInternet && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isOther = available && hasInternet && !isWifi && !isCell

            if (isWifi) wifiNetworks.add(network) else wifiNetworks.remove(network)
            if (isCell) cellularNetworks.add(network) else cellularNetworks.remove(network)
            if (isOther) otherNetworks.add(network) else otherNetworks.remove(network)
        }
        recomputeNetworkAndRebuild()
    }

    private fun recomputeNetworkAndRebuild() {
        if (stopping) return
        val nextType = synchronized(this) {
            when {
                wifiNetworks.isNotEmpty() -> ActiveNetworkType.WIFI
                cellularNetworks.isNotEmpty() -> ActiveNetworkType.MOBILE_DATA
                otherNetworks.isNotEmpty() -> ActiveNetworkType.OTHER
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
                ActiveNetworkType.OTHER -> otherNetworks.toTypedArray()
                    .ifEmpty { (wifiNetworks + cellularNetworks).toTypedArray() }
                    .takeIf { it.isNotEmpty() }
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
        if (stopping) return
        // A new rebuild supersedes any retry that was waiting for its backoff to elapse.
        tunnelRetryJob?.cancel()
        tunnelRetryJob = null
        val prefs: FirewallPreferencesState
        val rulesSnapshot: List<AppNetworkRuleEntity>
        val websiteMode: WebsiteFilterMode
        val adBlockEnabled: Boolean
        synchronized(policyLock) {
            prefs = latestFullPreferences
            rulesSnapshot = latestRules
            websiteMode = latestWebsiteFilterMode
            adBlockEnabled = latestIsAdBlockerEnabled
        }
        val netType = currentNetworkType

        val plan = PerAppFirewallEngine.buildTunnelPlan(
            networkType = netType,
            rules = rulesSnapshot,
            preferences = prefs,
            selfPackageName = packageName,
            isPackageInstalled = ::isPackageInstalled,
            resolvePackageUid = ::resolvePackageUid
        )

        synchronized(policyLock) {
            latestBlockedPackages = plan.blockedPackages.toSet()
            latestBlockedUids = plan.blockedUids
            policyRef.set(
                policyRef.get().copy(
                    blockedUids = latestBlockedUids,
                    blockedPackages = latestBlockedPackages
                )
            )
        }

        // If TUN configuration hasn't changed (e.g. only upstream DNS, rule lists, or UID blocks in hybrid mode),
        // keep existing TUN running seamlessly and refresh state + notification.
        if (!forceRebuild && vpnInterface != null && activeTunnelSignature == plan.signature) {
            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = plan.blockedPackages.size,
                websiteMode = websiteMode,
                adBlockEnabled = adBlockEnabled
            )
            updateNotification(plan.blockedPackages.size, netType, websiteMode, adBlockEnabled)
            return
        }

        drainJob?.cancel()
        drainJob = null
        closeVpnInterface()
        activeTunnelSignature = null

        if (!plan.shouldRunTunnel) {
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

        when (plan.mode) {
            FirewallTunnelMode.DISABLED -> {
                stopProxyServerLocked()
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(0, netType, websiteMode, adBlockEnabled)
            }

            FirewallTunnelMode.GLOBAL_WIFI_LOCK -> {
                stopProxyServerLocked()
                val established = buildGlobalWifiLockInterface()
                if (established == null) {
                    AppLog.warn("Tunnel", "global Wi-Fi lock interface establish failed")
                    scheduleTunnelRetry("Global Wi-Fi lock tunnel could not start")
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
                activeTunnelSignature = plan.signature
                syncUnderlyingNetworks(netType)
                drainJob = serviceScope.launch(Dispatchers.IO) {
                    drainPackets(established, emptyList())
                }
                publishRuntimeState(
                    isTunnelActive = true,
                    netType = netType,
                    blockedCount = plan.blockedPackages.size,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(plan.blockedPackages.size, netType, websiteMode, adBlockEnabled)
            }

            FirewallTunnelMode.PER_APP_SINKHOLE -> {
                stopProxyServerLocked()
                val (established, addedCount) = buildPerAppSinkholeInterface(plan.blockedPackages, netType)
                if (established == null || addedCount == 0) {
                    AppLog.warn("Tunnel", "per-app sinkhole interface establish failed")
                    scheduleTunnelRetry("Per-app firewall tunnel could not start")
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
                activeTunnelSignature = plan.signature
                syncUnderlyingNetworks(netType)
                drainJob = serviceScope.launch(Dispatchers.IO) {
                    drainPackets(established, plan.blockedPackages)
                }
                publishRuntimeState(
                    isTunnelActive = true,
                    netType = netType,
                    blockedCount = addedCount,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(addedCount, netType, websiteMode, adBlockEnabled)
            }

            FirewallTunnelMode.HYBRID_DNS_AND_APP_FILTER -> {
                val proxyActiveNow = if (plan.isHttpsProxyEnabled) {
                    ensureProxyServerStartedLocked(plan.httpsProxyPort, plan.stripTrackingParams)
                } else {
                    stopProxyServerLocked()
                    false
                }
                val activeProxyPort = if (proxyActiveNow) {
                    ProxyCoordinator.instance.boundPort(ProxyRole.LOCAL_FIREWALL)
                        ?.takeIf { it in ProxyConfiguration.MIN_PORT..ProxyConfiguration.MAX_PORT }
                        ?: plan.httpsProxyPort
                } else {
                    null
                }
                val established = buildHybridDnsProxyInterface(
                    bypassPackages = plan.bypassPackages,
                    netType = netType,
                    proxyPort = activeProxyPort
                )
                if (established == null) {
                    AppLog.warn("Tunnel", "hybrid DNS proxy interface establish failed")
                    scheduleTunnelRetry("DNS proxy tunnel could not start")
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
                activeTunnelSignature = plan.signature
                syncUnderlyingNetworks(netType)
                drainJob = serviceScope.launch(Dispatchers.IO) {
                    runDnsProxyLoop(established)
                }
                publishRuntimeState(
                    isTunnelActive = true,
                    netType = netType,
                    blockedCount = plan.blockedPackages.size,
                    websiteMode = websiteMode,
                    adBlockEnabled = adBlockEnabled
                )
                updateNotification(plan.blockedPackages.size, netType, websiteMode, adBlockEnabled)
            }
        }
    }

    private fun buildGlobalWifiLockInterface(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.1.10.1", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress(VIRTUAL_IPV6_ADDR, 128)
            .addRoute("::", 0)
        runCatching { builder.addDisallowedApplication(packageName) }
        return runCatching { builder.establish() }.getOrNull()
    }

    private fun buildPerAppSinkholeInterface(
        blockedPackages: List<String>,
        netType: ActiveNetworkType
    ): Pair<ParcelFileDescriptor?, Int> {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.1.10.1", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress(VIRTUAL_IPV6_ADDR, 128)
            .addRoute("::", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(netType == ActiveNetworkType.MOBILE_DATA)
        }

        var addedCount = 0
        for (pkg in blockedPackages) {
            if (runCatching { builder.addAllowedApplication(pkg) }.isSuccess) {
                addedCount++
            }
        }
        // CRITICAL: Never call establish() with an empty allowed-list, as Android routes ALL apps when empty.
        if (addedCount == 0) return null to 0

        return runCatching { builder.establish() }.getOrNull() to addedCount
    }

    private fun buildHybridDnsProxyInterface(
        bypassPackages: Set<String>,
        netType: ActiveNetworkType,
        proxyPort: Int?
    ): ParcelFileDescriptor? {
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
            if (proxyPort != null) {
                runCatching {
                    builder.setHttpProxy(
                        ProxyInfo.buildDirectProxy("127.0.0.1", proxyPort, PROXY_BYPASS_HOSTS)
                    )
                }.onFailure {
                    runCatching {
                        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", proxyPort))
                    }
                }
            }
        }

        for ((cidrIp, prefixLen) in DOH_SINKHOLE_ROUTES) {
            runCatching { builder.addRoute(cidrIp, prefixLen) }
        }

        isSelfDisallowedOnTunnel = runCatching {
            builder.addDisallowedApplication(packageName)
            true
        }.getOrDefault(false)
        for (bypassPkg in bypassPackages) {
            runCatching { builder.addDisallowedApplication(bypassPkg) }
        }

        return runCatching { builder.establish() }.getOrNull()
    }

    private fun ensureProxyServerStartedLocked(port: Int, stripTracking: Boolean): Boolean {
        val configuration = ProxyConfiguration.localFirewall(
            port = port,
            stripTrackingParams = stripTracking
        )
        val result = ProxyCoordinator.instance.start(configuration) { bindPort, config ->
            LocalFilteringProxyServer(
                port = bindPort,
                bindHost = config.bindHost,
                stripTrackingParams = config.stripTrackingParams,
                protectSocket = { socket -> protect(socket) || isSelfDisallowedOnTunnel },
                isClientBlocked = ::isProxyClientSocketBlocked,
                evaluateRequest = ::evaluateProxyRequest,
                resolveTargetHost = ::resolveProxyTargetHost,
                resolveTargetAddresses = ::resolveProxyTargetAddresses,
                upstreamProxyProvider = ::provideUpstreamSniProxy,
                dpiBypassConfigProvider = {
                    DpiBypassConfig(
                        mode = latestDpiBypassMode,
                        chunkSize = latestDpiFragmentChunkSize,
                        fragmentDelayMs = latestDpiFragmentDelayMs,
                        idleKeepAliveEnabled = latestDpiIdleKeepAliveEnabled
                    )
                },
                onDpiBypassApplied = { _, _ ->
                    dpiBypassedCount.incrementAndGet()
                    publishDnsStatsOnly()
                },
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
                            isAdBlocked = decision.isAdBlock,
                            dpiBypassed = decision.allow && latestDpiBypassMode != DpiBypassMode.OFF
                        )
                    }
                },
                resolveClientApp = ::resolveProxyClientApp
            )
        }
        return result.isRunning
    }

    private fun resolveProxyTargetHost(host: String): String {
        return NetworkPolicyEngine.resolveTargetHost(
            rawHost = host,
            context = PolicyContext.forFirewall(protocol = "HTTPS"),
            policy = policyRef.get()
        )
    }

    private fun resolveProxyTargetAddresses(host: String): List<InetAddress> {
        val cleanHost = host.trim().trimEnd('.').removeSurrounding("[", "]")
        if (cleanHost.isEmpty()) return emptyList()

        // Numeric IP literals don't need DNS resolution
        if (isIpLiteral(cleanHost)) {
            return runCatching { listOf(InetAddress.getByName(cleanHost)) }.getOrDefault(emptyList())
        }

        val now = System.currentTimeMillis()
        proxyAddressCache[cleanHost.lowercase()]?.let { cached ->
            if (now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        val resolved = mutableListOf<InetAddress>()
        val antiPoison = latestAntiDnsPoisonEnabled

        // Query A (IPv4) first via NetGate's DoH / DoTCP / Anti-Poisoned DNS so blocked/poisoned
        // domains still resolve cleanly and never stall on unreachable IPv6 routes.
        val ipv4Query = buildRawDnsQuery(cleanHost, qType = 1)
        val aResp = dnsEngine.resolveRaw(ipv4Query, cleanHost, buildDnsResolutionContext()).payload
        if (aResp != null) {
            val ips = DnsPacketFilter.extractResolvedIpsFromDnsResponse(aResp)
            val safeIps = if (antiPoison && DpiBypassEngine.isLikelyForgedDnsResponse(cleanHost, ips)) {
                emptyList()
            } else {
                ips
            }
            for (ipStr in safeIps) {
                runCatching { InetAddress.getByName(ipStr) }.getOrNull()?.let(resolved::add)
            }
        }

        if (resolved.isEmpty()) {
            val ipv6Query = buildRawDnsQuery(cleanHost, qType = 28)
            val aaaaResp = dnsEngine.resolveRaw(ipv6Query, cleanHost, buildDnsResolutionContext()).payload
            if (aaaaResp != null) {
                val ips = DnsPacketFilter.extractResolvedIpsFromDnsResponse(aaaaResp)
                val safeIps = if (antiPoison && DpiBypassEngine.isLikelyForgedDnsResponse(cleanHost, ips)) {
                    emptyList()
                } else {
                    ips
                }
                for (ipStr in safeIps) {
                    runCatching { InetAddress.getByName(ipStr) }.getOrNull()?.let(resolved::add)
                }
            }
        }

        if (resolved.isEmpty()) {
            val osAddrs = LocalFilteringProxyServer.defaultResolveAddressesIpv4First(cleanHost)
            resolved.addAll(osAddrs)
        }

        val ordered = LocalFilteringProxyServer.sortIpv4First(resolved)
        if (ordered.isNotEmpty()) {
            if (proxyAddressCache.size > 512) {
                proxyAddressCache.clear()
            }
            proxyAddressCache[cleanHost.lowercase()] = CachedHostAddresses(
                addresses = ordered,
                expiresAtMs = now + PROXY_DNS_CACHE_TTL_MS
            )
        }
        return ordered
    }

    private fun isIpLiteral(host: String): Boolean {
        if (':' in host) return true
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            val n = part.toIntOrNull()
            n != null && n in 0..255
        }
    }

    private fun buildRawDnsQuery(domain: String, qType: Int): ByteArray {
        val out = ByteArrayOutputStream(64)
        // Random-ish TxID + standard recursive query flags (RD=1, QDCOUNT=1)
        val txId = (System.nanoTime() and 0xFFFFL).toInt()
        out.write((txId ushr 8) and 0xFF)
        out.write(txId and 0xFF)
        out.write(0x01); out.write(0x00)
        out.write(0x00); out.write(0x01)
        out.write(ByteArray(6))
        for (label in domain.split('.')) {
            if (label.isEmpty() || label.length > 63) continue
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write((qType ushr 8) and 0xFF)
        out.write(qType and 0xFF)
        out.write(0x00); out.write(0x01) // QCLASS = IN
        return out.toByteArray()
    }

    private fun provideUpstreamSniProxy(): Pair<String, Int>? {
        if (latestSniProxyPreset != SniProxyPreset.CUSTOM_UPSTREAM_SNI) return null
        val host = latestCustomSniProxyHost.trim()
        if (host.isEmpty()) return null
        return host to latestCustomSniProxyPort.coerceIn(1, 65535)
    }

    private fun stopProxyServerLocked() {
        ProxyCoordinator.instance.stop(ProxyRole.LOCAL_FIREWALL)
    }

    private fun evaluateProxyRequest(host: String, pathAndQuery: String?): ProxyFilterDecision {
        return NetworkPolicyEngine.evaluate(
            rawDomain = host,
            context = PolicyContext.forFirewall(pathAndQuery = pathAndQuery, protocol = "HTTPS"),
            policy = policyRef.get()
        ).toProxyFilterDecision()
    }

    private fun handleProxyLogged(host: String, decision: ProxyFilterDecision) {
        if (!decision.allow) {
            blockedProxyCount.incrementAndGet()
            if (decision.isAdBlock) {
                blockedAdsCount.incrementAndGet()
                scheduleFlushLifetimeAds()
            }
            recordBlockedDomain(host)
            recordDnsLog(
                domain = host,
                status = DnsQueryStatus.BLOCKED_HTTPS,
                reason = decision.reason,
                fullUrl = decision.fullUrl,
                appName = decision.appName,
                appPackage = decision.appPackage,
                filterListName = decision.filterListName.ifBlank { "HTTPS Proxy Filter" },
                blockReason = if (decision.blockReason != AdBlockReason.NONE) {
                    decision.blockReason
                } else if (decision.isAdBlock) {
                    AdBlockReason.ADS
                } else {
                    AdBlockReason.CUSTOM_RULE
                },
                latencyMicros = decision.latencyMicros
            )
        } else {
            allowedProxyCount.incrementAndGet()
            if (decision.fullUrl.isNotBlank()) {
                recordDnsLog(
                    domain = host,
                    status = DnsQueryStatus.ALLOWED,
                    reason = decision.reason,
                    fullUrl = decision.fullUrl,
                    appName = decision.appName,
                    appPackage = decision.appPackage,
                    filterListName = "HTTPS Proxy",
                    blockReason = AdBlockReason.NONE,
                    latencyMicros = decision.latencyMicros
                )
            }
        }
        publishDnsStatsOnly()
    }

    private fun drainPackets(
        pfd: ParcelFileDescriptor,
        blockedPackages: List<String>
    ) {
        val buffer = ByteArray(32767)
        val output = TunOutput(FileOutputStream(pfd.fileDescriptor))
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    if (bytesRead == 0) continue

                    blockedPacketsCount.incrementAndGet()
                    val parsed = TunPacketInspector.parseIpPacket(buffer, bytesRead) ?: continue

                    // Kill switch: the tunnel died, stay fail-closed until it comes back.
                    if (killSwitchLockdown) continue

                    when (PerAppFirewallEngine.classifySinkholePacket(parsed)) {
                        BlockedPacketAction.REJECT_DNS_NXDOMAIN -> {
                            val dnsQuery = DnsPacketFilter.parseUdpDnsQuery(buffer, bytesRead)
                            if (dnsQuery != null) {
                                output.write(dnsEngine.buildBlockedResponse(dnsQuery))
                                recordInterceptedAppPacket(
                                    parsed = parsed,
                                    domainOrHost = dnsQuery.domain,
                                    blockedPackages = blockedPackages
                                )
                                continue
                            }
                            val icmpPacket = DnsPacketFilter.buildIcmpUnreachablePacket(buffer, bytesRead)
                            if (icmpPacket != null) {
                                output.write(icmpPacket)
                            }
                        }

                        BlockedPacketAction.REJECT_TCP_RST -> {
                            // Reset both new SYN handshakes and already-open sockets immediately.
                            val rstPacket = DnsPacketFilter.buildTcpResetPacket(buffer, bytesRead)
                            if (rstPacket != null) {
                                output.write(rstPacket)
                            }
                        }

                        BlockedPacketAction.REJECT_ICMP_UNREACHABLE -> {
                            val icmpPacket = DnsPacketFilter.buildIcmpUnreachablePacket(buffer, bytesRead)
                            if (icmpPacket != null) {
                                output.write(icmpPacket)
                            }
                        }

                        BlockedPacketAction.DROP_SILENTLY -> Unit
                    }

                    recordInterceptedAppPacket(
                        parsed = parsed,
                        domainOrHost = parsed.dstIpText,
                        blockedPackages = blockedPackages
                    )
                }
            }
        } catch (_: IOException) {
            // Expected when interface is closed during rebuild or shutdown
        } finally {
            runCatching { output.close() }
        }
    }

    private val dnsQueryDispatcher = Dispatchers.IO.limitedParallelism(DNS_QUERY_CONCURRENCY)

    /**
     * Second, explicit ceiling on DNS work. A permit is acquired inside the launched coroutine, so
     * the TUN read loop keeps draining packets and never waits for a resolver slot.
     */
    private val dnsQuerySemaphore = Semaphore(MAX_IN_FLIGHT_DNS_QUERIES)
    private val dnsInFlight = AtomicInteger(0)
    private val droppedDnsQueries = AtomicLong(0L)

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
        recordDnsLog(
            domain = target,
            status = DnsQueryStatus.BLOCKED_APP,
            reason = "App Firewall (${parsed.protocolName})",
            appName = resolveAppLabel(ownerPkg),
            appPackage = ownerPkg,
            filterListName = "App Firewall",
            blockReason = AdBlockReason.CUSTOM_RULE
        )
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
        val policy = policyRef.get()
        val blockedUids = policy.blockedUids
        val blockedPkgs = policy.blockedPackages
        if ((blockedUids.isEmpty() && blockedPkgs.isEmpty()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val cm = connectivityManager ?: return false
        val clientLocal = client.remoteSocketAddress as? InetSocketAddress ?: return false
        val proxyRemote = client.localSocketAddress as? InetSocketAddress ?: return false
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                TunPacketInspector.IPPROTO_TCP,
                clientLocal,
                proxyRemote
            )
            if (uid <= 0) {
                false
            } else if (uid in blockedUids) {
                true
            } else {
                packageManager.getPackagesForUid(uid)?.any { it in blockedPkgs } == true
            }
        }.getOrDefault(false)
    }

    private fun runDnsProxyLoop(pfd: ParcelFileDescriptor) {
        val buffer = ByteArray(32767)
        val output = TunOutput(FileOutputStream(pfd.fileDescriptor))
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    if (bytesRead == 0) continue

                    val packetCopy = buffer.copyOf(bytesRead)
                    val query = DnsPacketFilter.parseUdpDnsQuery(packetCopy, bytesRead)
                    if (query != null) {
                        dispatchDnsQuery(query, output)
                    } else {
                        // Send immediate TCP RST to DoH (443) / DoT (853) connections on routed resolver IPs
                        // so Chrome & Firefox instantly fall back to NetGate's filtered DNS.
                        val tcpReset = DnsPacketFilter.buildTcpResetPacket(packetCopy, bytesRead)
                        if (tcpReset != null) {
                            output.write(tcpReset)
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

    /**
     * Bounds how many DNS lookups can be resolving at once. Every packet used to get its own
     * unbounded `Dispatchers.IO` coroutine, so a stalled upstream could spawn hundreds of them and
     * exhaust the shared IO pool used by the rest of the app.
     */
    private fun dispatchDnsQuery(query: ParsedDnsQuery, output: TunOutput) {
        if (dnsInFlight.get() >= MAX_IN_FLIGHT_DNS_QUERIES) {
            droppedDnsQueries.incrementAndGet()
            return
        }
        dnsInFlight.incrementAndGet()
        serviceScope.launch(dnsQueryDispatcher) {
            try {
                dnsQuerySemaphore.withPermit {
                    if (output.isUsable) handleDnsQuery(query, output)
                }
            } finally {
                dnsInFlight.decrementAndGet()
            }
        }
    }

    private fun handleDnsQuery(query: ParsedDnsQuery, output: TunOutput) {
        val policy = policyRef.get()
        val isCanary = DnsPacketFilter.isInternalBypassCanary(query.domain)
        val owner = if (!isCanary) resolveDnsQueryOwner(query) else null
        val ownerPackage = owner?.primaryPackage.orEmpty()
        val ownerAppName = resolveAppLabel(ownerPackage)
        val policyContext = PolicyContext.forFirewall(
            appPackage = ownerPackage.takeIf { it.isNotEmpty() },
            appUid = owner?.uid,
            protocol = "DNS"
        )

        val outcome = dnsEngine.process(
            query = query,
            policy = policy,
            policyContext = policyContext,
            resolutionContext = buildDnsResolutionContext(),
            quicDowngradeEnabled = latestDpiBypassMode != DpiBypassMode.OFF &&
                latestBlockQuicForDpiBypass,
            dnsCacheEnabled = latestDnsCacheEnabled
        )

        val poisonTrips = outcomeAntiPoisonTrips(outcome)
        if (poisonTrips > 0) {
            antiDnsPoisonCount.addAndGet(poisonTrips.toLong())
        }

        when (outcome) {
            is DnsOutcome.Blocked -> {
                writeToTun(outcome.responsePacket, output)
                when (outcome.category) {
                    PolicyDecision.BlockCategory.APP_FIREWALL -> {
                        if (!isCanary) {
                            blockedDnsCount.incrementAndGet()
                            recordBlockedDomain(query.domain)
                            recordDnsLog(
                                domain = query.domain,
                                status = DnsQueryStatus.BLOCKED_APP,
                                reason = outcome.reason,
                                appName = ownerAppName,
                                appPackage = ownerPackage,
                                filterListName = "App Firewall",
                                blockReason = AdBlockReason.CUSTOM_RULE,
                                latencyMicros = outcome.latencyMicros
                            )
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
                    }
                    PolicyDecision.BlockCategory.WEBSITE_BLOCKLIST,
                    PolicyDecision.BlockCategory.WHITELIST_LOCKDOWN -> {
                        if (!isCanary) {
                            blockedDnsCount.incrementAndGet()
                            recordBlockedDomain(query.domain)
                            recordDnsLog(
                                domain = query.domain,
                                status = DnsQueryStatus.BLOCKED_WEBSITE,
                                reason = outcome.reason,
                                appName = ownerAppName,
                                appPackage = ownerPackage,
                                filterListName = outcome.filterListName.ifBlank { "Custom Website Rule" },
                                blockReason = AdBlockReason.CUSTOM_RULE,
                                latencyMicros = outcome.latencyMicros
                            )
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
                    }
                    PolicyDecision.BlockCategory.DOH_BYPASS_CANARY -> {
                        // Internal bypass canary dropped silently
                    }
                    PolicyDecision.BlockCategory.YOUTUBE_AD,
                    PolicyDecision.BlockCategory.AD_TRACKER -> {
                        blockedDnsCount.incrementAndGet()
                        blockedAdsCount.incrementAndGet()
                        scheduleFlushLifetimeAds()
                        recordBlockedDomain(query.domain)
                        recordDnsLog(
                            domain = query.domain,
                            status = DnsQueryStatus.BLOCKED_AD,
                            reason = outcome.reason,
                            appName = ownerAppName,
                            appPackage = ownerPackage,
                            filterListName = outcome.filterListName.ifBlank { "NetGate Core Ad Shield" },
                            blockReason = if (outcome.blockReason != AdBlockReason.NONE) {
                                outcome.blockReason
                            } else {
                                AdBlockReason.ADS
                            },
                            latencyMicros = outcome.latencyMicros
                        )
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
                    }
                    else -> {
                        blockedDnsCount.incrementAndGet()
                        publishDnsStatsOnly()
                    }
                }
            }

            is DnsOutcome.QuicDowngraded -> {
                writeToTun(outcome.responsePacket, output)
                quicStrippedCount.incrementAndGet()
                recordDnsLog(
                    domain = query.domain,
                    status = DnsQueryStatus.PASSTHROUGH,
                    reason = "QUIC Downgrade (DPI Bypass)",
                    appName = ownerAppName,
                    appPackage = ownerPackage,
                    filterListName = "DPI Bypass Engine"
                )
                publishDnsStatsOnly()
            }

            is DnsOutcome.SafeRewrite -> {
                writeToTun(outcome.responsePacket, output)
                safeRewriteCount.incrementAndGet()
                allowedDnsCount.incrementAndGet()
                recordDnsLog(
                    domain = query.domain,
                    status = DnsQueryStatus.SAFE_REWRITE,
                    reason = "${outcome.label} (${outcome.aliasDomain})",
                    appName = ownerAppName,
                    appPackage = ownerPackage,
                    filterListName = "SafeSearch Rewrite",
                    latencyMicros = outcome.latencyMicros
                )
                publishDnsStatsOnly()
            }

            is DnsOutcome.Answered -> {
                writeToTun(outcome.responsePacket, output)
                allowedDnsCount.incrementAndGet()
                val status = if (outcome.isPassthrough) DnsQueryStatus.PASSTHROUGH else DnsQueryStatus.ALLOWED
                val reason = if (outcome.isPassthrough) {
                    "Essential Passthrough (${outcome.transportLabel})"
                } else {
                    outcome.transportLabel
                }
                recordDnsLog(
                    domain = query.domain,
                    status = status,
                    reason = reason,
                    appName = ownerAppName,
                    appPackage = ownerPackage,
                    filterListName = if (outcome.isPassthrough) "Essential Passthrough" else outcome.transportLabel,
                    latencyMicros = outcome.latencyMicros
                )
                if (latestIsDeepMonitorEnabled && !isCanary) {
                    val resolvedIps = DnsPacketFilter.extractResolvedIpsFromDnsResponse(outcome.upstreamPayload)
                    val primaryIp = resolvedIps.firstOrNull().orEmpty()
                    ConnectionMonitorHub.recordConnection(
                        context = applicationContext,
                        domain = query.domain,
                        remoteIp = primaryIp,
                        remotePort = if (latestIsHttpsProxyEnabled || latestDpiBypassMode != DpiBypassMode.OFF) {
                            latestHttpsProxyPort
                        } else {
                            443
                        },
                        protocol = "DNS",
                        appPackage = ownerPackage,
                        bytesSent = query.dnsPayload.size.toLong(),
                        bytesReceived = outcome.upstreamPayload.size.toLong(),
                        actionStatus = "ALLOWED"
                    )
                }
                publishDnsStatsOnly()
            }

            DnsOutcome.Dropped -> Unit
        }
    }

    private fun outcomeAntiPoisonTrips(outcome: DnsOutcome): Int {
        return when (outcome) {
            is DnsOutcome.SafeRewrite -> outcome.antiPoisonTrips
            is DnsOutcome.Answered -> outcome.antiPoisonTrips
            else -> 0
        }
    }

    private fun buildDnsResolutionContext(): DnsResolutionContext {
        return DnsResolutionContext(
            transportMode = latestDnsTransportMode,
            useDohUpstream = latestUseDohUpstream,
            dohUrl = latestDohUrl,
            upstreamTitle = latestUpstreamDnsTitle,
            udpDnsServers = latestUdpDnsServers,
            antiDnsPoisonEnabled = latestAntiDnsPoisonEnabled,
            socketPolicy = DnsSocketPolicy(
                protectSocket = { socket -> protect(socket) },
                protectDatagramSocket = { socket -> protect(socket) },
                selfDisallowedOnTunnel = isSelfDisallowedOnTunnel
            )
        )
    }

    private fun writeToTun(packet: ByteArray, output: TunOutput) {
        output.write(packet)
    }

    /**
     * Write side of the TUN descriptor with a closed latch.
     *
     * Interface rebuilds close the old [ParcelFileDescriptor] while DNS coroutines may still be
     * resolving, and every write used to rely on a swallowed exception instead of stopping. The
     * latch makes those writes no-ops and lets in-flight work bail out instead of logging forever.
     */
    private class TunOutput(private val stream: FileOutputStream) {
        @Volatile
        private var closed = false

        val isUsable: Boolean get() = !closed

        fun write(packet: ByteArray): Boolean {
            synchronized(this) {
                if (closed) return false
                return try {
                    stream.write(packet)
                    true
                } catch (_: IOException) {
                    false
                }
            }
        }

        fun close() {
            synchronized(this) {
                if (closed) return
                closed = true
                runCatching { stream.close() }
            }
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

    private data class PacketOwner(
        val uid: Int,
        val primaryPackage: String,
        val packages: Array<String>
    )

    private fun resolveDnsQueryOwner(query: ParsedDnsQuery): PacketOwner? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cm = connectivityManager ?: return null
        val srcAddr = DnsPacketFilter.ipBytesToInetAddress(query.srcIp) ?: return null
        val dstAddr = DnsPacketFilter.ipBytesToInetAddress(query.dstIp) ?: return null
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                TunPacketInspector.IPPROTO_UDP,
                InetSocketAddress(srcAddr, query.srcPort),
                InetSocketAddress(dstAddr, query.dstPort)
            )
            if (uid <= 0) return null
            val packages = packageManager.getPackagesForUid(uid) ?: emptyArray()
            PacketOwner(
                uid = uid,
                primaryPackage = packages.firstOrNull().orEmpty(),
                packages = packages
            )
        }.getOrNull()
    }

    private fun resolveAppLabel(pkg: String): String {
        if (pkg.isBlank()) return ""
        appLabelCache[pkg]?.let { return it }
        val resolved = runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString().ifBlank { pkg }
        }.getOrDefault(pkg)
        if (appLabelCache.size > 256) {
            appLabelCache.clear()
        }
        appLabelCache[pkg] = resolved
        return resolved
    }

    private fun resolveProxyClientApp(client: java.net.Socket): Pair<String, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cm = connectivityManager ?: return null
        val clientLocal = client.remoteSocketAddress as? InetSocketAddress ?: return null
        val proxyRemote = client.localSocketAddress as? InetSocketAddress ?: return null
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                TunPacketInspector.IPPROTO_TCP,
                clientLocal,
                proxyRemote
            )
            if (uid <= 0) return null
            val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
            if (pkg.isEmpty()) return null
            pkg to resolveAppLabel(pkg)
        }.getOrNull()
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

    private fun recordDnsLog(
        domain: String,
        status: DnsQueryStatus,
        reason: String,
        fullUrl: String = "",
        appName: String = "",
        appPackage: String = "",
        filterListName: String = "",
        blockReason: AdBlockReason = AdBlockReason.NONE,
        latencyMicros: Long = 0L
    ) {
        val hitCount = AdBlockAnalyticsHub.recordEvent(
            domain = domain,
            status = status,
            blockReason = blockReason,
            filterListName = filterListName,
            appPackage = appPackage,
            appName = appName
        )
        val entry = DnsQueryLogEntry(
            id = dnsLogSeq.getAndIncrement(),
            domain = domain,
            status = status,
            reason = reason,
            timestampMs = System.currentTimeMillis(),
            fullUrl = fullUrl,
            appName = appName,
            appPackage = appPackage,
            filterListName = filterListName,
            blockReason = blockReason,
            hitCount = hitCount,
            latencyMicros = latencyMicros
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
            isHttpsProxyActive = ProxyCoordinator.instance.isActive(ProxyRole.LOCAL_FIREWALL),
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
            dpiBypassMode = latestDpiBypassMode,
            dpiBypassedConnectionsCount = dpiBypassedCount.get(),
            dpiAutopilotLearnedCount = DpiBypassEngine.getAutopilotLearnedCount(),
            dnsTransportMode = latestDnsTransportMode,
            antiDnsPoisonBlockedCount = antiDnsPoisonCount.get(),
            quicStrippedCount = quicStrippedCount.get(),
            recentBlockedDomains = recentSnapshot,
            recentDnsLogs = logSnapshot
        )
    }

    /**
     * Records a short reason for a failed establish and schedules one bounded retry. Never retries
     * while VPN Guard owns the VPN slot, since establishing would fight that handshake.
     */
    private fun scheduleTunnelRetry(reason: String) {
        if (VpnSessionCoordinator.instance.state.value.owner == VpnSlotOwner.TUNNEL) {
            latestTunnelError = "$reason — VPN Guard holds the VPN slot"
            return
        }
        val delayMs = TunnelRetryPolicy.delayBeforeRetry(tunnelEstablishAttempts)
            ?: run {
                latestTunnelError = "$reason — retries exhausted"
                return
            }
        latestTunnelError = "$reason — retrying"
        tunnelEstablishAttempts += 1
        tunnelRetryJob?.cancel()
        tunnelRetryJob = serviceScope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            if (VpnSessionCoordinator.instance.state.value.owner == VpnSlotOwner.TUNNEL) return@launch
            // The retry now runs; drop the handle so the rebuild below cannot cancel itself.
            tunnelRetryJob = null
            rebuildTunnel(forceRebuild = true)
        }
    }

    private fun publishRuntimeState(
        isTunnelActive: Boolean,
        netType: ActiveNetworkType,
        blockedCount: Int,
        websiteMode: WebsiteFilterMode,
        adBlockEnabled: Boolean
    ) {
        if (stopping) return
        if (isTunnelActive) {
            // A live tunnel clears both the retry budget and the visible error.
            tunnelEstablishAttempts = 0
            latestTunnelError = null
        }
        val recentSnapshot = synchronized(recentBlockedLock) { recentBlockedQueue.toList() }
        val logSnapshot = synchronized(dnsLogLock) { dnsLogQueue.toList() }
        _runtimeState.value = VpnRuntimeState(
            isServiceRunning = true,
            isTunnelActive = isTunnelActive,
            activeNetworkType = netType,
            blockedActivePackagesCount = blockedCount,
            websiteFilterMode = websiteMode,
            isAdBlockerEnabled = adBlockEnabled,
            isHttpsProxyActive = ProxyCoordinator.instance.isActive(ProxyRole.LOCAL_FIREWALL),
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
            isGlobalWifiBlocked = (netType == ActiveNetworkType.WIFI && latestGlobalWifiBlocked),
            dpiBypassMode = latestDpiBypassMode,
            dpiBypassedConnectionsCount = dpiBypassedCount.get(),
            dpiAutopilotLearnedCount = DpiBypassEngine.getAutopilotLearnedCount(),
            dnsTransportMode = latestDnsTransportMode,
            antiDnsPoisonBlockedCount = antiDnsPoisonCount.get(),
            quicStrippedCount = quicStrippedCount.get(),
            recentBlockedDomains = recentSnapshot,
            recentDnsLogs = logSnapshot,
            tunnelError = latestTunnelError
        )
    }

    private fun closeVpnInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private suspend fun stopFirewall() {
        stopping = true
        AppLogCollector.info("Service", "Firewall tunnel stopped", "Torn down local VPN TUN and proxy")
        tunnelMutex.withLock {
            drainJob?.cancel()
            drainJob = null
            stopProxyServerLocked()
            closeVpnInterface()
            activeTunnelSignature = null
        }
        tunnelRetryJob?.cancel()
        tunnelRetryJob = null
        tunnelEstablishAttempts = 0
        latestTunnelError = null
        _runtimeState.value = VpnRuntimeState()
        killSwitchLockdown = false
        TunnelWatchdog.releaseKillSwitch(applicationContext)
        VpnSessionCoordinator.instance.releaseLocalFirewall()
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

        val proxyActive = ProxyCoordinator.instance.isActive(ProxyRole.LOCAL_FIREWALL)
        val adSuffix = buildString {
            if (adBlockEnabled) append(" + Ad Blocker")
            if (latestIsYoutubeAdBlockEnabled) append(" + YT Ads")
            if (latestIsSafeSearchEnabled) append(" + SafeSearch")
            if (latestYoutubeRestrictedMode != YouTubeRestrictedMode.OFF) append(" + YT Kids")
            if (latestDpiBypassMode != DpiBypassMode.OFF) append(" + DPI ${latestDpiBypassMode.title}")
            if (proxyActive) append(" + SNI Proxy")
        }
        val contentText = when {
            networkType == ActiveNetworkType.WIFI && latestGlobalWifiBlocked ->
                "Wi-Fi Global Lock Active • All app Wi-Fi traffic paused"
            websiteMode == WebsiteFilterMode.WHITELIST_ONLY ->
                "$netLabel • Strict Whitelist Lockdown$adSuffix"
            websiteMode == WebsiteFilterMode.BLOCKLIST ->
                "$netLabel • Website Blocklist$adSuffix${if (blockedCount > 0) " + $blockedCount blocked app(s)" else ""}"
            else -> when {
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

    companion object {
        const val ACTION_START = "com.aistudio.netgate.service.ACTION_START"
        const val ACTION_STOP = "com.aistudio.netgate.service.ACTION_STOP"
        const val ACTION_RELOAD_RULES = "com.aistudio.netgate.service.ACTION_RELOAD_RULES"

        private const val VIRTUAL_DNS_SERVER = "10.1.10.2"
        private const val VIRTUAL_IPV6_ADDR = "fd00:1:fd00:1:fd00:1:fd00:1"
        private const val VIRTUAL_IPV6_DNS_SERVER = "fd00:1:fd00:1:fd00:1:fd00:2"
        private const val PROXY_DNS_CACHE_TTL_MS = 45_000L
        private val UPSTREAM_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
        private val PROXY_BYPASS_HOSTS = listOf(
            "localhost",
            "127.0.0.1",
            "10.*",
            "192.168.*",
            "*.local"
        )

        private data class CachedHostAddresses(
            val addresses: List<InetAddress>,
            val expiresAtMs: Long
        )

        private val proxyAddressCache = ConcurrentHashMap<String, CachedHostAddresses>()

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
        private const val MAX_IN_FLIGHT_DNS_QUERIES = 64
        private const val DNS_QUERY_CONCURRENCY = 16

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
            AdBlockAnalyticsHub.clear()
            _runtimeState.value = _runtimeState.value.copy(recentDnsLogs = emptyList())
        }

        fun startOrReload(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_START
            }
            // On Android 12+ a background start can be refused with
            // ForegroundServiceStartNotAllowedException. Swallow it (and any other start failure)
            // without touching state: the caller stays alive and the user can retry from the UI.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // ForegroundServiceStartNotAllowedException (API 31+), IllegalStateException,
                // SecurityException: the service simply does not start this time.
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_STOP
            }
            val started = runCatching {
                context.startService(intent)
                true
            }.getOrDefault(false)
            if (!started) {
                runCatching {
                    context.stopService(Intent(context, NetGateVpnService::class.java))
                }
            }
        }

        fun flushDnsAndAutopilotCache(context: Context) {
            DpiBypassEngine.clearAutopilotCache()
            DnsResponseCache.clear()
            proxyAddressCache.clear()
            if (_runtimeState.value.isServiceRunning) {
                val intent = Intent(context, NetGateVpnService::class.java).apply {
                    action = ACTION_RELOAD_RULES
                }
                // A refused background start (ForegroundServiceStartNotAllowedException on API 31+)
                // must not propagate: runtime state stays exactly as it was before the request.
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Exception) {
                    // Reload skipped; the running service keeps its current rules.
                }
            } else {
                _runtimeState.value = _runtimeState.value.copy(dpiAutopilotLearnedCount = 0)
            }
        }
    }
}
