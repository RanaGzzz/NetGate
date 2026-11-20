package com.example.service

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.data.local.AdFilterListEntity
import com.example.data.local.WebsiteRuleEntity
import com.example.data.local.WebsiteRuleType
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.HotspotClientInfo
import com.example.data.model.HotspotProxyRuntimeState
import com.example.data.model.WebsiteFilterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object HotspotPolicyEvaluator {

    fun evaluateClientBlockReason(
        clientIp: String,
        blockedIps: Set<String>,
        clientCaps: Map<String, Long>,
        bytesSent: Long,
        bytesReceived: Long
    ): String? {
        if (clientIp in blockedIps) {
            return "Blocked by NetGate Hotspot Firewall"
        }
        val cap = clientCaps[clientIp] ?: 0L
        if (cap > 0L && (bytesSent + bytesReceived) >= cap) {
            return "Hotspot Data Cap Reached"
        }
        return null
    }

    fun isHotspotInterfaceName(rawName: String?): Boolean {
        val name = rawName?.trim()?.lowercase().orEmpty()
        if (name.isEmpty()) return false
        return name.startsWith("ap") ||
            name.startsWith("softap") ||
            name.startsWith("swlan") ||
            name.startsWith("tether") ||
            name.startsWith("rndis") ||
            name.startsWith("usb") ||
            name == "wlan1" ||
            name == "wlan2"
    }
}

object HotspotProxyManager {

    private data class MutableClientStats(
        val clientIp: String,
        val bytesSent: AtomicLong = AtomicLong(0L),
        val bytesReceived: AtomicLong = AtomicLong(0L),
        var requestCount: Int = 0,
        var blockedCount: Int = 0,
        var lastHost: String = "",
        val firstSeenAt: Long = System.currentTimeMillis(),
        var lastSeenAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private var proxyServer: LocalFilteringProxyServer? = null
    private var runningPort: Int = 8890

    private val clientStatsMap = ConcurrentHashMap<String, MutableClientStats>()
    private val totalSentCounter = AtomicLong(0L)
    private val totalRecvCounter = AtomicLong(0L)

    @Volatile
    private var latestPrefs: FirewallPreferencesState = FirewallPreferencesState()

    @Volatile
    private var latestWebsiteRules: List<WebsiteRuleEntity> = emptyList()

    @Volatile
    private var latestAdMatcher: AdBlockMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY

    private val _runtimeState = MutableStateFlow(HotspotProxyRuntimeState())
    val runtimeState: StateFlow<HotspotProxyRuntimeState> = _runtimeState.asStateFlow()

    fun updateFilterPolicies(
        prefs: FirewallPreferencesState,
        websiteRules: List<WebsiteRuleEntity>,
        adMatcher: AdBlockMatcherSnapshot
    ) {
        latestPrefs = prefs
        latestWebsiteRules = websiteRules
        latestAdMatcher = adMatcher
        publishSnapshot()
    }

    fun updatePreferencesOnly(prefs: FirewallPreferencesState) {
        latestPrefs = prefs
        publishSnapshot()
    }

    fun startOrUpdate(context: Context, prefs: FirewallPreferencesState): Boolean {
        latestPrefs = prefs
        val port = prefs.hotspotProxyPort.coerceIn(1024, 65535)
        synchronized(lock) {
            val existing = proxyServer
            if (existing != null && existing.isRunning && runningPort == port) {
                refreshEnvironment(context)
                return true
            }
            stopLocked()

            val server = LocalFilteringProxyServer(
                port = port,
                bindHost = "0.0.0.0",
                stripTrackingParams = prefs.isProxyStripTrackingParams,
                protectSocket = { true },
                clientBlockReason = ::evaluateClientBlock,
                evaluateRequest = ::evaluateRequestForHotspot,
                resolveTargetHost = ::resolveTargetHostForHotspot,
                upstreamProxyProvider = ::provideUpstreamProxy,
                onRequestLogged = { _, _ -> },
                onClientTraffic = { clientIp, host, bytesSent, bytesReceived, allowed ->
                    recordClientActivity(clientIp, host, bytesSent, bytesReceived, allowed)
                }
            )
            if (!server.start()) {
                _runtimeState.value = _runtimeState.value.copy(
                    isProxyRunning = false,
                    errorMessage = "Could not bind Hotspot Proxy on port $port. Try another port (e.g. 8890 or 8080)."
                )
                return false
            }
            proxyServer = server
            runningPort = port
        }
        refreshEnvironment(context)
        return true
    }

    fun stop(context: Context? = null) {
        synchronized(lock) {
            stopLocked()
        }
        if (context != null) {
            refreshEnvironment(context)
        } else {
            publishSnapshot(errorMessage = null)
        }
    }

    private fun stopLocked() {
        runCatching { proxyServer?.stop() }
        proxyServer = null
    }

    fun resetClientStats(clientIp: String) {
        val clean = clientIp.trim()
        clientStatsMap.remove(clean)
        publishSnapshot()
    }

    fun clearAllClientStats() {
        clientStatsMap.clear()
        totalSentCounter.set(0L)
        totalRecvCounter.set(0L)
        publishSnapshot()
    }

    fun refreshEnvironment(context: Context) {
        val detected = detectHotspotInterface()
        val isExternalVpn = isExternalVpnConnected(context)
        val osTetheringBytes = queryOsTetheringBytesToday(context)
        val isNetGateVpn = NetGateVpnService.runtimeState.value.isServiceRunning
        val isRunning = proxyServer?.isRunning == true

        val clients = buildClientsList()
        _runtimeState.value = HotspotProxyRuntimeState(
            isProxyRunning = isRunning,
            isHotspotInterfaceActive = detected != null,
            detectedHotspotIp = detected?.second,
            detectedHotspotInterface = detected?.first,
            isExternalVpnActive = isExternalVpn,
            isNetGateVpnActive = isNetGateVpn,
            activePort = if (isRunning) runningPort else latestPrefs.hotspotProxyPort,
            totalRelayedSentBytes = totalSentCounter.get(),
            totalRelayedRecvBytes = totalRecvCounter.get(),
            osTetheringBytes = osTetheringBytes,
            clients = clients,
            errorMessage = if (isRunning) null else _runtimeState.value.errorMessage
        )
    }

    private fun evaluateClientBlock(clientIp: String): String? {
        val prefs = latestPrefs
        val stats = clientStatsMap[clientIp]
        val sent = stats?.bytesSent?.get() ?: 0L
        val recv = stats?.bytesReceived?.get() ?: 0L
        return HotspotPolicyEvaluator.evaluateClientBlockReason(
            clientIp = clientIp,
            blockedIps = prefs.hotspotBlockedClientIps,
            clientCaps = prefs.hotspotClientCaps,
            bytesSent = sent,
            bytesReceived = recv
        )
    }

    private fun evaluateRequestForHotspot(host: String, pathAndQuery: String?): ProxyFilterDecision {
        val prefs = latestPrefs
        if (!prefs.hotspotApplyNetGateFilters) {
            return ProxyFilterDecision(allow = true, reason = "Hotspot Direct Relay")
        }

        val domain = host.trim().trimEnd('.').lowercase()
        val rules = latestWebsiteRules
        val mode = prefs.websiteFilterMode
        val isWhitelisted = rules.any {
            it.isEnabled && it.ruleType == WebsiteRuleType.ALLOW &&
                DnsPacketFilter.matchesDomainRule(domain, it.domain)
        }

        if (!isWhitelisted && mode != WebsiteFilterMode.OFF) {
            if (DnsPacketFilter.shouldBlockDomain(domain, mode, rules)) {
                val reason = if (mode == WebsiteFilterMode.WHITELIST_ONLY) {
                    "Hotspot Whitelist Lockdown"
                } else {
                    "Hotspot Website Blocklist"
                }
                return ProxyFilterDecision(allow = false, reason = reason, isAdBlock = false)
            }
        }

        val adMatcher = latestAdMatcher
        if (prefs.isEssentialPassthroughEnabled && adMatcher.isEssentialPassthrough(domain)) {
            return ProxyFilterDecision(allow = true, reason = "Hotspot Passthrough")
        }

        if (!isWhitelisted && prefs.isYoutubeAdBlockEnabled) {
            if (AdBlockFilterEngine.isYoutubeAdDomain(domain) ||
                AdBlockFilterEngine.isYoutubeAdUrlPath(pathAndQuery)
            ) {
                return ProxyFilterDecision(allow = false, reason = "Hotspot YouTube Ad Block", isAdBlock = true)
            }
        }

        if (!isWhitelisted && prefs.isAdBlockerEnabled) {
            if (adMatcher.isBlockedByAdFilter(domain) ||
                AdBlockFilterEngine.isAdOrTrackerUrlPath(pathAndQuery)
            ) {
                return ProxyFilterDecision(allow = false, reason = "Hotspot Ad Blocker", isAdBlock = true)
            }
        }

        return ProxyFilterDecision(allow = true, reason = "Hotspot Proxy Allowed")
    }

    private fun resolveTargetHostForHotspot(host: String): String {
        val prefs = latestPrefs
        if (!prefs.hotspotApplyNetGateFilters) return host
        val rewrite = DnsPacketFilter.resolveSafeSearchAlias(
            domain = host,
            safeSearchEnabled = prefs.isSafeSearchEnabled,
            youtubeRestrictedMode = prefs.youtubeRestrictedMode
        )
        return rewrite?.aliasDomain ?: host
    }

    private fun provideUpstreamProxy(): Pair<String, Int>? {
        val prefs = latestPrefs
        if (!prefs.hotspotApplyNetGateFilters) return null
        return prefs.resolvedUpstreamSniProxy()
    }

    private fun recordClientActivity(
        clientIp: String,
        host: String,
        bytesSent: Long,
        bytesReceived: Long,
        allowed: Boolean
    ) {
        val cleanIp = clientIp.trim()
        if (cleanIp.isEmpty() || cleanIp == "127.0.0.1" || cleanIp == "::1") return
        val now = System.currentTimeMillis()
        val entry = clientStatsMap.computeIfAbsent(cleanIp) {
            MutableClientStats(clientIp = cleanIp, firstSeenAt = now, lastSeenAt = now)
        }
        synchronized(entry) {
            entry.lastSeenAt = now
            if (host.isNotBlank()) entry.lastHost = host
            if (allowed) {
                entry.requestCount += 1
            } else {
                entry.blockedCount += 1
            }
        }
        if (bytesSent > 0L) {
            entry.bytesSent.addAndGet(bytesSent)
            totalSentCounter.addAndGet(bytesSent)
        }
        if (bytesReceived > 0L) {
            entry.bytesReceived.addAndGet(bytesReceived)
            totalRecvCounter.addAndGet(bytesReceived)
        }
        publishSnapshot()
    }

    private fun buildClientsList(): List<HotspotClientInfo> {
        val prefs = latestPrefs
        val allIps = (clientStatsMap.keys +
            prefs.hotspotBlockedClientIps +
            prefs.hotspotClientCaps.keys +
            prefs.hotspotClientLabels.keys).distinct()

        return allIps.map { ip ->
            val stats = clientStatsMap[ip]
            HotspotClientInfo(
                clientIp = ip,
                label = prefs.hotspotClientLabels[ip].orEmpty(),
                isManuallyBlocked = ip in prefs.hotspotBlockedClientIps,
                dataCapBytes = prefs.hotspotClientCaps[ip] ?: 0L,
                bytesSent = stats?.bytesSent?.get() ?: 0L,
                bytesReceived = stats?.bytesReceived?.get() ?: 0L,
                requestCount = stats?.requestCount ?: 0,
                blockedCount = stats?.blockedCount ?: 0,
                lastHost = stats?.lastHost.orEmpty(),
                firstSeenAt = stats?.firstSeenAt ?: 0L,
                lastSeenAt = stats?.lastSeenAt ?: 0L
            )
        }.sortedWith(
            compareByDescending<HotspotClientInfo> { it.totalBytes }
                .thenByDescending { it.lastSeenAt }
                .thenBy { it.clientIp }
        )
    }

    private fun publishSnapshot(errorMessage: String? = _runtimeState.value.errorMessage) {
        val current = _runtimeState.value
        _runtimeState.value = current.copy(
            isProxyRunning = proxyServer?.isRunning == true,
            activePort = if (proxyServer?.isRunning == true) runningPort else latestPrefs.hotspotProxyPort,
            totalRelayedSentBytes = totalSentCounter.get(),
            totalRelayedRecvBytes = totalRecvCounter.get(),
            clients = buildClientsList(),
            errorMessage = errorMessage
        )
    }

    private fun detectHotspotInterface(): Pair<String, String>? {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()
        }.getOrNull() ?: return null

        var subnetFallback: Pair<String, String>? = null
        for (iface in interfaces) {
            val isUp = runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
            if (!isUp) continue
            val ipv4 = iface.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress ?: continue

            if (HotspotPolicyEvaluator.isHotspotInterfaceName(iface.name)) {
                return iface.name to ipv4
            }
            if (ipv4.startsWith("192.168.43.") || ipv4.startsWith("192.168.137.")) {
                subnetFallback = iface.name to ipv4
            }
        }
        return subnetFallback
    }

    @Suppress("DEPRECATION")
    private fun isExternalVpnConnected(context: Context): Boolean {
        if (NetGateVpnService.runtimeState.value.isServiceRunning) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return runCatching {
            cm.allNetworks.any { net ->
                val caps = cm.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }

    private fun queryOsTetheringBytesToday(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return -1L
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return -1L
        val endMs = System.currentTimeMillis()
        val startMs = endMs - 24L * 60L * 60L * 1000L
        return runCatching {
            var total = 0L
            for (transport in listOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)) {
                @Suppress("DEPRECATION")
                val stats = nsm.queryDetailsForUid(
                    transport,
                    null,
                    startMs,
                    endMs,
                    NetworkStats.Bucket.UID_TETHERING
                )
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    total += bucket.rxBytes + bucket.txBytes
                }
                stats.close()
            }
            total
        }.getOrDefault(-1L)
    }
}
