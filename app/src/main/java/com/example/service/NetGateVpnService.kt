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
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.local.WebsiteRuleEntity
import com.example.data.model.ActiveNetworkType
import com.example.data.model.WebsiteFilterMode
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

data class VpnRuntimeState(
    val isServiceRunning: Boolean = false,
    val isTunnelActive: Boolean = false,
    val activeNetworkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val blockedActivePackagesCount: Int = 0,
    val websiteFilterMode: WebsiteFilterMode = WebsiteFilterMode.OFF,
    val blockedDnsQueriesCount: Long = 0L,
    val allowedDnsQueriesCount: Long = 0L,
    val recentBlockedDomains: List<String> = emptyList()
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
    private var latestBypassPackages: Set<String> = emptySet()

    @Volatile
    private var latestBlockedUids: Set<Int> = emptySet()

    @Volatile
    private var currentNetworkType: ActiveNetworkType = ActiveNetworkType.NONE

    private var activeTunnelSignature: TunnelConfigSignature? = null
    private val blockedDnsCount = AtomicLong(0L)
    private val allowedDnsCount = AtomicLong(0L)
    private val recentBlockedLock = Any()
    private val recentBlockedQueue = ArrayDeque<String>()

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
            combine(
                db.appNetworkRuleDao().observeAllRules(),
                db.websiteRuleDao().observeAllRules(),
                preferences.preferencesFlow
            ) { appRules, websiteRules, prefs ->
                Triple(appRules, websiteRules, prefs)
            }.collectLatest { (appRules, websiteRules, prefs) ->
                latestRules = appRules
                latestWebsiteRules = websiteRules
                latestWebsiteFilterMode = prefs.websiteFilterMode
                latestBypassPackages = prefs.websiteBypassPackages
                rebuildTunnel(forceRebuild = false)
            }
        }
    }

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
        serviceScope.launch {
            rebuildTunnel(forceRebuild = false)
        }
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
        val targetBlockedPackages = when (netType) {
            ActiveNetworkType.WIFI -> latestRules.filter { it.blockWifi }.map { it.packageName }
            ActiveNetworkType.MOBILE_DATA -> latestRules.filter { it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.OTHER -> latestRules.filter { it.blockWifi && it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.NONE -> emptyList()
        }.filter { it != packageName }.distinct().sorted()

        latestBlockedUids = targetBlockedPackages.mapNotNull { resolvePackageUid(it) }.toSet()

        val installedBlocked = targetBlockedPackages.filter { isPackageInstalled(it) }
        val effectiveBypass = if (websiteMode == WebsiteFilterMode.OFF) {
            emptySet()
        } else {
            latestBypassPackages
                .filter { it != packageName && it !in targetBlockedPackages && isPackageInstalled(it) }
                .toSet()
        }

        val desiredSignature = TunnelConfigSignature(
            networkType = netType,
            websiteMode = websiteMode,
            blockedPackages = if (websiteMode == WebsiteFilterMode.OFF) installedBlocked else emptyList(),
            bypassPackages = effectiveBypass
        )

        // If TUN configuration hasn't changed (e.g. only website rule list changed), keep existing TUN running.
        if (!forceRebuild && vpnInterface != null && activeTunnelSignature == desiredSignature) {
            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = installedBlocked.size,
                websiteMode = websiteMode
            )
            updateNotification(installedBlocked.size, netType, websiteMode)
            return
        }

        drainJob?.cancel()
        drainJob = null
        closeVpnInterface()
        activeTunnelSignature = null

        val shouldRunTunnel = netType != ActiveNetworkType.NONE &&
            (websiteMode != WebsiteFilterMode.OFF || installedBlocked.isNotEmpty())

        if (!shouldRunTunnel) {
            publishRuntimeState(
                isTunnelActive = false,
                netType = netType,
                blockedCount = 0,
                websiteMode = websiteMode
            )
            updateNotification(0, netType, websiteMode)
            return
        }

        if (websiteMode == WebsiteFilterMode.OFF) {
            // Pure per-app sinkhole mode: route all traffic of blocked apps into local TUN and drop packets.
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.1.10.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                .addRoute("::", 0)

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
                    websiteMode = websiteMode
                )
                updateNotification(0, netType, websiteMode)
                return
            }

            val established = runCatching { builder.establish() }.getOrNull()
            if (established == null) {
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode
                )
                updateNotification(0, netType, websiteMode)
                return
            }

            vpnInterface = established
            activeTunnelSignature = desiredSignature
            drainJob = serviceScope.launch(Dispatchers.IO) {
                drainPackets(established)
            }

            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = addedCount,
                websiteMode = websiteMode
            )
            updateNotification(addedCount, netType, websiteMode)
        } else {
            // Website Blocklist / Whitelist Lockdown mode:
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

            for ((cidrIp, prefixLen) in DOH_SINKHOLE_ROUTES) {
                runCatching { builder.addRoute(cidrIp, prefixLen) }
            }

            runCatching { builder.addDisallowedApplication(packageName) }
            for (bypassPkg in effectiveBypass) {
                runCatching { builder.addDisallowedApplication(bypassPkg) }
            }

            val established = runCatching { builder.establish() }.getOrNull()
            if (established == null) {
                publishRuntimeState(
                    isTunnelActive = false,
                    netType = netType,
                    blockedCount = 0,
                    websiteMode = websiteMode
                )
                updateNotification(0, netType, websiteMode)
                return
            }

            vpnInterface = established
            activeTunnelSignature = desiredSignature
            drainJob = serviceScope.launch(Dispatchers.IO) {
                runDnsProxyLoop(established)
            }

            publishRuntimeState(
                isTunnelActive = true,
                netType = netType,
                blockedCount = installedBlocked.size,
                websiteMode = websiteMode
            )
            updateNotification(installedBlocked.size, netType, websiteMode)
        }
    }

    private fun drainPackets(pfd: ParcelFileDescriptor) {
        val buffer = ByteArray(32767)
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    // Packets from blocked apps are intentionally sinkholed here.
                }
            }
        } catch (_: IOException) {
            // Expected when interface is closed during rebuild or shutdown
        }
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
        val isAppBlockedByUid = isQueryFromBlockedUid(query)
        val shouldBlockDomain = isAppBlockedByUid || DnsPacketFilter.shouldBlockDomain(
            domain = query.domain,
            mode = latestWebsiteFilterMode,
            rules = latestWebsiteRules
        )

        if (shouldBlockDomain) {
            val blockedPacket = DnsPacketFilter.buildBlockedDnsResponsePacket(query)
            synchronized(tunWriteLock) {
                runCatching { output.write(blockedPacket) }
            }
            if (!DnsPacketFilter.isInternalBypassCanary(query.domain)) {
                blockedDnsCount.incrementAndGet()
                recordBlockedDomain(query.domain)
                publishDnsStatsOnly()
            }
            return
        }

        val upstreamDnsResponse = forwardDnsQuery(query.dnsPayload)
        if (upstreamDnsResponse != null) {
            val responsePacket = DnsPacketFilter.buildDnsResponsePacket(query, upstreamDnsResponse)
            synchronized(tunWriteLock) {
                runCatching { output.write(responsePacket) }
            }
            allowedDnsCount.incrementAndGet()
            publishDnsStatsOnly()
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

    private fun forwardDnsQuery(dnsPayload: ByteArray): ByteArray? {
        for (upstreamIp in UPSTREAM_DNS_SERVERS) {
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

    private fun publishDnsStatsOnly() {
        val current = _runtimeState.value
        val recentSnapshot = synchronized(recentBlockedLock) { recentBlockedQueue.toList() }
        _runtimeState.value = current.copy(
            blockedDnsQueriesCount = blockedDnsCount.get(),
            allowedDnsQueriesCount = allowedDnsCount.get(),
            recentBlockedDomains = recentSnapshot
        )
    }

    private fun publishRuntimeState(
        isTunnelActive: Boolean,
        netType: ActiveNetworkType,
        blockedCount: Int,
        websiteMode: WebsiteFilterMode
    ) {
        val recentSnapshot = synchronized(recentBlockedLock) { recentBlockedQueue.toList() }
        _runtimeState.value = VpnRuntimeState(
            isServiceRunning = true,
            isTunnelActive = isTunnelActive,
            activeNetworkType = netType,
            blockedActivePackagesCount = blockedCount,
            websiteFilterMode = websiteMode,
            blockedDnsQueriesCount = blockedDnsCount.get(),
            allowedDnsQueriesCount = allowedDnsCount.get(),
            recentBlockedDomains = recentSnapshot
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
        websiteMode: WebsiteFilterMode
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(blockedCount, networkType, websiteMode))
    }

    private fun buildNotification(
        blockedCount: Int,
        networkType: ActiveNetworkType,
        websiteMode: WebsiteFilterMode = WebsiteFilterMode.OFF
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

        val contentText = when (websiteMode) {
            WebsiteFilterMode.WHITELIST_ONLY ->
                "$netLabel • Strict Whitelist Lockdown active"
            WebsiteFilterMode.BLOCKLIST ->
                "$netLabel • Website Blocklist${if (blockedCount > 0) " + $blockedCount blocked app(s)" else ""}"
            WebsiteFilterMode.OFF -> if (blockedCount > 0) {
                "$netLabel active • Blocking $blockedCount app${if (blockedCount == 1) "" else "s"}"
            } else {
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

    private data class TunnelConfigSignature(
        val networkType: ActiveNetworkType,
        val websiteMode: WebsiteFilterMode,
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

        private const val CHANNEL_ID = "netgate_firewall_channel"
        private const val NOTIFICATION_ID = 4041

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

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
