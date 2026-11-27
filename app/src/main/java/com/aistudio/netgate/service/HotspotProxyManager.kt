package com.aistudio.netgate.service

import com.aistudio.netgate.data.network.LocalNetworkPermission

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.aistudio.netgate.data.local.AdvancedPreferences
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.HotspotClientInfo
import com.aistudio.netgate.data.model.HotspotFilterProfile
import com.aistudio.netgate.data.model.HotspotKidsGuard
import com.aistudio.netgate.data.model.HotspotProxyRuntimeState
import com.aistudio.netgate.service.proxy.ProxyConfiguration
import com.aistudio.netgate.service.proxy.ProxyCoordinator
import com.aistudio.netgate.service.proxy.ProxyRole
import com.aistudio.netgate.service.proxy.ProxyStartResult
import com.aistudio.netgate.service.hotspot.HotspotPacFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        return com.aistudio.netgate.service.policy.NetworkPolicyEngine.evaluateHotspotClientBlock(
            clientIp = clientIp,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            policy = com.aistudio.netgate.service.policy.NetworkPolicy(
                hotspotBlockedClientIps = blockedIps,
                hotspotClientCaps = clientCaps
            )
        )
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

    private data class HotspotPolicyBundle(
        val prefs: FirewallPreferencesState = FirewallPreferencesState(),
        val websiteRules: List<WebsiteRuleEntity> = emptyList(),
        val adMatcher: AdBlockMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY,
        val policy: com.aistudio.netgate.service.policy.NetworkPolicy = com.aistudio.netgate.service.policy.NetworkPolicy.EMPTY
    )

    private val lock = Any()
    private val policyLock = Any()

    private val clientStatsMap = ConcurrentHashMap<String, MutableClientStats>()
    private val totalSentCounter = AtomicLong(0L)
    private val totalRecvCounter = AtomicLong(0L)

    private val policyBundle = java.util.concurrent.atomic.AtomicReference(HotspotPolicyBundle())

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertedPendingIps = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    private var advancedState: AdvancedPreferencesState = AdvancedPreferencesState()

    fun updateAdvancedPreferences(state: AdvancedPreferencesState) {
        advancedState = state
        publishSnapshot()
    }

    private val _runtimeState = MutableStateFlow(HotspotProxyRuntimeState())
    val runtimeState: StateFlow<HotspotProxyRuntimeState> = _runtimeState.asStateFlow()

    fun updateFilterPolicies(
        prefs: FirewallPreferencesState,
        websiteRules: List<WebsiteRuleEntity>,
        adMatcher: AdBlockMatcherSnapshot
    ) {
        synchronized(policyLock) {
            policyBundle.set(
                HotspotPolicyBundle(
                    prefs = prefs,
                    websiteRules = websiteRules,
                    adMatcher = adMatcher,
                    policy = com.aistudio.netgate.service.policy.NetworkPolicy.fromPreferencesAndRules(
                        prefs = prefs,
                        websiteRules = websiteRules,
                        adMatcher = adMatcher
                    )
                )
            )
        }
        publishSnapshot()
    }

    fun updatePreferencesOnly(prefs: FirewallPreferencesState) {
        synchronized(policyLock) {
            val prev = policyBundle.get()
            policyBundle.set(
                HotspotPolicyBundle(
                    prefs = prefs,
                    websiteRules = prev.websiteRules,
                    adMatcher = prev.adMatcher,
                    policy = com.aistudio.netgate.service.policy.NetworkPolicy.fromPreferencesAndRules(
                        prefs = prefs,
                        websiteRules = prev.websiteRules,
                        adMatcher = prev.adMatcher
                    )
                )
            )
        }
        publishSnapshot()
    }

    fun startOrUpdate(context: Context, prefs: FirewallPreferencesState): Boolean {
        // Android 17+ needs an explicit grant before anything may reach LAN clients.
        if (!LocalNetworkPermission.isGranted(context)) {
            _runtimeState.value = _runtimeState.value.copy(
                isProxyRunning = false,
                errorMessage = LocalNetworkPermission.DENIED_MESSAGE
            )
            updatePreferencesOnly(prefs)
            return false
        }
        updatePreferencesOnly(prefs)
        val configuration = ProxyConfiguration.hotspot(
            port = prefs.hotspotProxyPort,
            stripTrackingParams = prefs.isProxyStripTrackingParams
        )
        synchronized(lock) {
            val result = ProxyCoordinator.instance.start(configuration) { bindPort, config ->
                LocalFilteringProxyServer(
                    port = bindPort,
                    bindHost = config.bindHost,
                    stripTrackingParams = config.stripTrackingParams,
                    protectSocket = { true },
                    clientBlockReason = { clientIp -> evaluateClientBlock(clientIp, context) },
                    evaluateRequest = { host, path -> evaluateRequestForHotspot("", host, path) },
                    evaluateRequestForClient = { clientIp, host, path ->
                        evaluateRequestForHotspot(clientIp, host, path)
                    },
                    clientThrottleBytesPerSec = { clientIp ->
                        advancedState.hotspotClientSpeedLimits[clientIp.trim()] ?: 0L
                    },
                    pacFileProvider = { localIp ->
                        val port = ProxyCoordinator.instance.boundPort(ProxyRole.HOTSPOT)
                            ?: prefs.hotspotProxyPort
                        HotspotPacFile.build(localIp, port)
                    },
                    resolveTargetHost = ::resolveTargetHostForHotspot,
                    upstreamProxyProvider = ::provideUpstreamProxy,
                    onRequestLogged = { _, _ -> },
                    onClientTraffic = { clientIp, host, bytesSent, bytesReceived, allowed ->
                        recordClientActivity(clientIp, host, bytesSent, bytesReceived, allowed)
                    }
                )
            }
            if (!result.isRunning) {
                val message = when (result) {
                    is ProxyStartResult.PortConflict ->
                        "Port ${result.port} is already used by the ${result.owner.label} proxy. " +
                            "Try another port (e.g. 8890 or 8080)."
                    else ->
                        "Could not bind Hotspot Proxy on port ${configuration.validatedPort}. " +
                            "Try another port (e.g. 8890 or 8080)."
                }
                _runtimeState.value = _runtimeState.value.copy(
                    isProxyRunning = false,
                    errorMessage = message
                )
                return false
            }
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
        ProxyCoordinator.instance.stop(ProxyRole.HOTSPOT)
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
        val isRunning = ProxyCoordinator.instance.isActive(ProxyRole.HOTSPOT)

        val snapshot = policyBundle.get()
        val clients = buildClientsList(snapshot.prefs)
        _runtimeState.value = HotspotProxyRuntimeState(
            isProxyRunning = isRunning,
            isHotspotInterfaceActive = detected != null,
            detectedHotspotIp = detected?.second,
            detectedHotspotInterface = detected?.first,
            isExternalVpnActive = isExternalVpn,
            isNetGateVpnActive = isNetGateVpn,
            activePort = if (isRunning) {
                ProxyCoordinator.instance.boundPort(ProxyRole.HOTSPOT) ?: snapshot.prefs.hotspotProxyPort
            } else {
                snapshot.prefs.hotspotProxyPort
            },
            totalRelayedSentBytes = totalSentCounter.get(),
            totalRelayedRecvBytes = totalRecvCounter.get(),
            osTetheringBytes = osTetheringBytes,
            shareTunnelThroughNetGate = advancedState.hotspotShareActiveTunnel,
            requireClientApproval = advancedState.hotspotRequireApproval,
            clients = clients,
            errorMessage = if (isRunning) null else _runtimeState.value.errorMessage
        )
    }

    private fun evaluateClientBlock(clientIp: String, context: Context? = null): String? {
        val cleanIp = clientIp.trim()
        if (cleanIp.isEmpty()) return null

        if (advancedState.hotspotRequireApproval &&
            cleanIp !in advancedState.hotspotApprovedClientIps
        ) {
            if (context != null && alertedPendingIps.add(cleanIp)) {
                val lastHost = clientStatsMap[cleanIp]?.lastHost.orEmpty()
                HotspotApprovalNotifier.notifyNewClient(context, cleanIp, lastHost)
                alertScope.launch {
                    runCatching { AdvancedPreferences(context).markHotspotClientPending(cleanIp) }
                }
            }
            return "Waiting for approval on NetGate"
        }

        val stats = clientStatsMap[cleanIp]
        val sent = stats?.bytesSent?.get() ?: 0L
        val recv = stats?.bytesReceived?.get() ?: 0L
        return com.aistudio.netgate.service.policy.NetworkPolicyEngine.evaluateHotspotClientBlock(
            clientIp = cleanIp,
            bytesSent = sent,
            bytesReceived = recv,
            policy = policyBundle.get().policy
        )
    }

    private fun evaluateRequestForHotspot(
        clientIp: String,
        host: String,
        pathAndQuery: String?
    ): ProxyFilterDecision {
        val bundle = policyBundle.get()
        val context = com.aistudio.netgate.service.policy.PolicyContext.forHotspot(
            clientIp = clientIp,
            pathAndQuery = pathAndQuery
        )
        val base = com.aistudio.netgate.service.policy.NetworkPolicyEngine.evaluate(
            rawDomain = host,
            context = context,
            policy = bundle.policy
        ).toProxyFilterDecision()

        val profile = advancedState.hotspotClientProfiles[clientIp.trim()]
            ?: HotspotFilterProfile.STANDARD
        return when (profile) {
            HotspotFilterProfile.STANDARD -> base
            HotspotFilterProfile.KIDS ->
                if (base.allow && HotspotKidsGuard.isBlocked(host)) {
                    ProxyFilterDecision(
                        allow = false,
                        reason = "Kids profile: adult, gambling or tracker domain blocked",
                        isAdBlock = true
                    )
                } else {
                    base
                }
            HotspotFilterProfile.STRICT ->
                if (base.allow && !isAllowlisted(host, bundle.websiteRules)) {
                    ProxyFilterDecision(
                        allow = false,
                        reason = "Strict profile: domain is not on the allowlist"
                    )
                } else {
                    base
                }
        }
    }

    private fun isAllowlisted(host: String, rules: List<WebsiteRuleEntity>): Boolean {
        val clean = host.trim().trimEnd('.').lowercase()
        if (clean.isEmpty()) return false
        return rules.any { rule ->
            rule.isEnabled &&
                rule.ruleType == WebsiteRuleType.ALLOW &&
                (clean == rule.domain || clean.endsWith(".${rule.domain}"))
        }
    }

    private fun resolveTargetHostForHotspot(host: String): String {
        val context = com.aistudio.netgate.service.policy.PolicyContext.forHotspot(clientIp = "")
        return com.aistudio.netgate.service.policy.NetworkPolicyEngine.resolveTargetHost(
            rawHost = host,
            context = context,
            policy = policyBundle.get().policy
        )
    }

    private fun provideUpstreamProxy(): Pair<String, Int>? {
        val prefs = policyBundle.get().prefs
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

    private fun buildClientsList(
        prefs: FirewallPreferencesState = policyBundle.get().prefs
    ): List<HotspotClientInfo> {
        val allIps = (clientStatsMap.keys +
            prefs.hotspotBlockedClientIps +
            prefs.hotspotClientCaps.keys +
            prefs.hotspotClientLabels.keys).distinct()

        return allIps.map { ip ->
            val stats = clientStatsMap[ip]
            var reqCount = 0
            var blkCount = 0
            var host = ""
            var firstSeen = 0L
            var lastSeen = 0L
            if (stats != null) {
                synchronized(stats) {
                    reqCount = stats.requestCount
                    blkCount = stats.blockedCount
                    host = stats.lastHost
                    firstSeen = stats.firstSeenAt
                    lastSeen = stats.lastSeenAt
                }
            }
            HotspotClientInfo(
                clientIp = ip,
                label = prefs.hotspotClientLabels[ip].orEmpty(),
                isManuallyBlocked = ip in prefs.hotspotBlockedClientIps,
                dataCapBytes = prefs.hotspotClientCaps[ip] ?: 0L,
                bytesSent = stats?.bytesSent?.get() ?: 0L,
                bytesReceived = stats?.bytesReceived?.get() ?: 0L,
                requestCount = reqCount,
                blockedCount = blkCount,
                lastHost = host,
                firstSeenAt = firstSeen,
                lastSeenAt = lastSeen,
                profile = advancedState.hotspotClientProfiles[ip]
                    ?: HotspotFilterProfile.STANDARD,
                speedLimitBytesPerSec = advancedState.hotspotClientSpeedLimits[ip] ?: 0L,
                isPendingApproval = advancedState.hotspotRequireApproval &&
                    ip !in advancedState.hotspotApprovedClientIps
            )
        }.sortedWith(
            compareByDescending<HotspotClientInfo> { it.totalBytes }
                .thenByDescending { it.lastSeenAt }
                .thenBy { it.clientIp }
        )
    }

    private fun publishSnapshot(errorMessage: String? = _runtimeState.value.errorMessage) {
        val snapshot = policyBundle.get()
        val current = _runtimeState.value
        val isRunning = ProxyCoordinator.instance.isActive(ProxyRole.HOTSPOT)
        _runtimeState.value = current.copy(
            isProxyRunning = isRunning,
            activePort = if (isRunning) {
                ProxyCoordinator.instance.boundPort(ProxyRole.HOTSPOT) ?: snapshot.prefs.hotspotProxyPort
            } else {
                snapshot.prefs.hotspotProxyPort
            },
            totalRelayedSentBytes = totalSentCounter.get(),
            totalRelayedRecvBytes = totalRecvCounter.get(),
            clients = buildClientsList(snapshot.prefs),
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
