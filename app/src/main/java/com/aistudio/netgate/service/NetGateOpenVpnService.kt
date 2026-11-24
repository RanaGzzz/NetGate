package com.aistudio.netgate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.tim.openvpn.model.CIDRIP
import com.tim.openvpn.utils.NetworkSpace
import com.tim.openvpn.utils.NetworkUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.openvpn.ovpn3.ClientAPI_Config
import net.openvpn.ovpn3.ClientAPI_Event
import net.openvpn.ovpn3.ClientAPI_ExternalPKICertRequest
import net.openvpn.ovpn3.ClientAPI_ExternalPKISignRequest
import net.openvpn.ovpn3.ClientAPI_LogInfo
import net.openvpn.ovpn3.ClientAPI_OpenVPNClient
import net.openvpn.ovpn3.ClientAPI_ProvideCreds
import net.openvpn.ovpn3.ClientAPI_StringVec
import net.openvpn.ovpn3.DnsOptions
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * In-app OpenVPN3 VpnService powered by `libovpn3.so` (`net.openvpn.ovpn3.ClientAPI_OpenVPNClient`).
 * Supports standard `.ovpn` profiles, VPNGate relays, username/password auth, live Rx/Tx transport
 * stats, and NetGate's per-app split-tunneling modes (`Whole System`, `Selected Apps`, `Exclude Apps`).
 */
class NetGateOpenVpnService : VpnService() {

    data class OpenVpnConnectInfo(
        val tunnelAddresses: String,
        val endpoint: String
    )

    interface Listener {
        fun onStatusChanged(stageMessage: String)
        fun onDisconnected(reason: String?)
    }

    private val isForeground = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_START_FOREGROUND, false) == true && !isForeground.get()) {
            promoteForegroundSafely()
        }
        activeServiceRef.set(this)
        serviceReadyDeferred?.complete(this)
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        val session = activeSessionRef.getAndSet(null)
        val intentional = isIntentionalStop.get()
        runCatching { session?.stop() }
        if (!intentional) {
            sessionListener?.onDisconnected("VPN permission revoked by Android or another VPN started")
        }
        stopSelfSafe()
        super.onRevoke()
    }

    override fun onDestroy() {
        activeServiceRef.compareAndSet(this, null)
        stopSelfSafe()
        super.onDestroy()
    }

    private fun promoteForegroundSafely() {
        runCatching {
            val channelId = "netgate_vpn_guard_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (mgr != null && mgr.getNotificationChannel(channelId) == null) {
                    val ch = NotificationChannel(
                        channelId,
                        getString(R.string.vpn_guard_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setShowBadge(false)
                    }
                    mgr.createNotificationChannel(ch)
                }
            }
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_vpn_tunnel_shield)
                .setContentTitle("NetGate OpenVPN Tunnel")
                .setContentText("Connecting OpenVPN3 tunnel…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1095,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1095, notification)
            }
            isForeground.set(true)
        }
    }

    private fun stopSelfSafe() {
        activeServiceRef.compareAndSet(this, null)
        if (isForeground.getAndSet(false)) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
        }
        runCatching { stopSelf() }
    }

    private fun startOpenVpnSession(
        profile: VpnGuardProfile,
        listener: Listener,
        connectDeferred: CompletableDeferred<Result<OpenVpnConnectInfo>>
    ) {
        stopActiveSessionInternal(intentional = true)
        isIntentionalStop.set(false)
        sessionListener = listener

        val runtimeConfig = OpenVpnConfigHelper.prepareRuntimeConfig(profile.rawConfig)
        val (username, password) = OpenVpnConfigHelper.extractCredentials(profile.rawConfig)

        val session = OpenVpn3ClientSession(
            service = this,
            profile = profile,
            runtimeOvpnConfig = runtimeConfig,
            username = username,
            password = password,
            listener = listener,
            connectDeferred = connectDeferred
        )
        activeSessionRef.set(session)

        val thread = Thread({
            try {
                session.execute()
            } catch (t: Throwable) {
                if (!connectDeferred.isCompleted) {
                    connectDeferred.complete(
                        Result.failure(IllegalStateException(t.message ?: "OpenVPN3 execution failed", t))
                    )
                }
            } finally {
                val wasIntentional = isIntentionalStop.get()
                if (activeSessionRef.compareAndSet(session, null) && !wasIntentional) {
                    listener.onDisconnected(session.lastFatalError ?: "OpenVPN tunnel closed")
                }
                runCatching { session.delete() }
            }
        }, "NetGate-OpenVPN3")
        activeThreadRef.set(thread)
        thread.start()
    }

    private class OpenVpn3ClientSession(
        private val service: NetGateOpenVpnService,
        private val profile: VpnGuardProfile,
        private val runtimeOvpnConfig: String,
        private val username: String,
        private val password: String,
        private val listener: Listener,
        private val connectDeferred: CompletableDeferred<Result<OpenVpnConnectInfo>>
    ) : ClientAPI_OpenVPNClient() {

        @Volatile
        var lastFatalError: String? = null

        private var mtu: Int = profile.mtu.coerceIn(1200, 1500)
        private var localIpv4: CIDRIP? = null
        private var localIpv6: String? = null
        private var remoteEndpoint: String = profile.endpointHost
        private val dnsServers = LinkedHashSet<String>()
        private val routesV4 = NetworkSpace()
        private val routesV6 = NetworkSpace()
        private var rerouteIpv4 = false
        private var rerouteIpv6 = false

        fun execute() {
            val cfg = ClientAPI_Config().apply {
                content = runtimeOvpnConfig
                tunPersist = true
                externalPkiAlias = "extpki"
                compressionMode = "asym"
                hwAddrOverride = runCatching {
                    NetworkUtils.getFakeMacAddrFromSAAID(service.contentResolver)
                }.getOrDefault("02:00:00:00:00:01")
                info = true
                allowLocalLanAccess = false
                retryOnAuthFailed = false
                enableLegacyAlgorithms = true
                enableRouteEmulation = false
            }

            val eval = eval_config(cfg)
            if (eval.error) {
                val msg = eval.message.ifBlank { "Invalid OpenVPN .ovpn configuration" }
                lastFatalError = msg
                if (!connectDeferred.isCompleted) {
                    connectDeferred.complete(Result.failure(IllegalArgumentException(msg)))
                }
                return
            }

            if (eval.remoteHost.isNotBlank()) {
                val port = eval.remotePort.ifBlank { "1194" }
                val proto = eval.remoteProto.ifBlank { "udp" }
                remoteEndpoint = "${eval.remoteHost}:$port ($proto)"
            }

            if (!eval.autologin || username.isNotBlank() || password.isNotBlank()) {
                if (username.isBlank() && password.isBlank() && OpenVpnConfigHelper.requiresAuthUserPass(runtimeOvpnConfig)) {
                    val msg = "This .ovpn profile requires a username & password. Paste or re-import with credentials."
                    lastFatalError = msg
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(Result.failure(IllegalArgumentException(msg)))
                    }
                    return
                }
                val creds = ClientAPI_ProvideCreds().apply {
                    this.username = this@OpenVpn3ClientSession.username
                    this.password = this@OpenVpn3ClientSession.password
                }
                val credStatus = provide_creds(creds)
                if (credStatus.error) {
                    val msg = credStatus.message.ifBlank { "Failed to apply OpenVPN credentials" }
                    lastFatalError = msg
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(Result.failure(IllegalStateException(msg)))
                    }
                    return
                }
            }

            val status = connect()
            if (status.error) {
                val msg = status.message.ifBlank { status.status.ifBlank { "OpenVPN connection error" } }
                lastFatalError = msg
                if (!connectDeferred.isCompleted) {
                    connectDeferred.complete(Result.failure(IllegalStateException(msg)))
                }
            } else if (!connectDeferred.isCompleted) {
                val msg = lastFatalError ?: "OpenVPN tunnel ended before completing handshake"
                connectDeferred.complete(Result.failure(IllegalStateException(msg)))
            }
        }

        override fun tun_builder_new(): Boolean {
            mtu = profile.mtu.coerceIn(1200, 1500)
            localIpv4 = null
            localIpv6 = null
            dnsServers.clear()
            routesV4.clear()
            routesV6.clear()
            rerouteIpv4 = false
            rerouteIpv6 = false
            return true
        }

        override fun tun_builder_set_layer(layer: Int): Boolean = layer == 3

        override fun tun_builder_set_remote_address(address: String, ipv6: Boolean): Boolean {
            if (address.isNotBlank()) {
                remoteEndpoint = if (remoteEndpoint.contains(':')) {
                    "$address:${remoteEndpoint.substringAfter(':').substringBefore(' ')}"
                } else {
                    address
                }
            }
            return true
        }

        override fun tun_builder_add_address(
            address: String,
            prefixLength: Int,
            gateway: String,
            ipv6: Boolean,
            net30: Boolean
        ): Boolean {
            if (ipv6) {
                localIpv6 = "$address/$prefixLength"
            } else {
                localIpv4 = CIDRIP(address, prefixLength)
            }
            return true
        }

        override fun tun_builder_set_mtu(mtu: Int): Boolean {
            if (mtu in 576..65535) {
                this.mtu = mtu
            }
            return true
        }

        override fun tun_builder_reroute_gw(ipv4: Boolean, ipv6: Boolean, flags: Long): Boolean {
            if ((flags and 65536L) != 0L) {
                return true
            }
            if (ipv4) rerouteIpv4 = true
            if (ipv6) rerouteIpv6 = true
            return true
        }

        override fun tun_builder_add_route(
            address: String,
            prefixLength: Int,
            metric: Int,
            ipv6: Boolean
        ): Boolean {
            if (address == "remote_host") return false
            return runCatching {
                if (ipv6) {
                    val v6 = InetAddress.getByName(address) as? Inet6Address ?: return false
                    routesV6.addIPv6(v6, prefixLength, true)
                } else {
                    routesV4.addIP(CIDRIP(address, prefixLength), true)
                }
                true
            }.getOrDefault(false)
        }

        override fun tun_builder_exclude_route(
            address: String,
            prefixLength: Int,
            metric: Int,
            ipv6: Boolean
        ): Boolean {
            if (address == "remote_host") return false
            return runCatching {
                if (ipv6) {
                    val v6 = InetAddress.getByName(address) as? Inet6Address ?: return false
                    routesV6.addIPv6(v6, prefixLength, false)
                } else {
                    routesV4.addIP(CIDRIP(address, prefixLength), false)
                }
                true
            }.getOrDefault(false)
        }

        override fun tun_builder_set_dns_options(dns: DnsOptions): Boolean {
            runCatching {
                val serversMap = dns.servers ?: return@runCatching
                for (entry in serversMap.entries) {
                    val addresses = entry.value?.addresses ?: continue
                    for (addr in addresses) {
                        val ip = addr.address?.trim().orEmpty()
                        if (ip.isNotEmpty()) {
                            dnsServers.add(ip)
                        }
                    }
                }
            }
            return true
        }

        override fun tun_builder_set_session_name(name: String): Boolean = true

        override fun tun_builder_set_proxy_http(host: String, port: Int): Boolean = true

        override fun tun_builder_set_proxy_https(host: String, port: Int): Boolean = true

        override fun tun_builder_persist(): Boolean = true

        override fun tun_builder_get_local_networks(ipv6: Boolean): ClientAPI_StringVec {
            val vec = ClientAPI_StringVec()
            val cm = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                runCatching {
                    val locals = NetworkUtils.getLocalNetworks(cm, ipv6)
                    for (net in locals) {
                        vec.add(net)
                    }
                }
            }
            return vec
        }

        override fun tun_builder_establish(): Int {
            return runCatching {
                val builder = service.Builder()
                    .setSession("NetGate OpenVPN • ${profile.name}")
                    .setMtu(mtu)

                localIpv4?.let { v4 ->
                    builder.addAddress(v4.ip, v4.len)
                }
                localIpv6?.let { v6 ->
                    val parts = v6.split('/')
                    if (parts.size == 2) {
                        val prefix = parts[1].toIntOrNull() ?: 64
                        builder.addAddress(parts[0], prefix)
                    }
                }

                if (rerouteIpv4) {
                    routesV4.addIP(CIDRIP("0.0.0.0", 0), true)
                }
                if (rerouteIpv6) {
                    runCatching {
                        val allV6 = InetAddress.getByName("::") as Inet6Address
                        routesV6.addIPv6(allV6, 0, true)
                    }
                }

                val v4Positive = routesV4.positiveIPList
                if (v4Positive.isNotEmpty()) {
                    for (net in v4Positive) {
                        runCatching {
                            builder.addRoute(net.iPv4Address, net.networkMask)
                        }
                    }
                } else if (localIpv4 != null) {
                    builder.addRoute("0.0.0.0", 0)
                }

                val v6Positive = routesV6.positiveIPList
                for (net in v6Positive) {
                    runCatching {
                        builder.addRoute(net.iPv6Address, net.networkMask)
                    }
                }

                val effectiveDns = if (dnsServers.isNotEmpty()) {
                    dnsServers.toList()
                } else {
                    listOf("1.1.1.1", "1.0.0.1")
                }
                for (dnsIp in effectiveDns) {
                    runCatching { builder.addDnsServer(dnsIp) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                applySplitTunneling(builder)

                val pfd = builder.establish() ?: return -1
                pfd.detachFd()
            }.getOrElse { err ->
                lastFatalError = err.message ?: "Failed to establish OpenVPN TUN interface"
                -1
            }
        }

        private fun applySplitTunneling(builder: VpnService.Builder) {
            val pm = service.packageManager
            val installedTargets = profile.targetPackages
                .map { it.trim() }
                .filter { it.isNotEmpty() && isPackageInstalled(pm, it) }

            when (profile.routingMode) {
                VpnGuardRoutingMode.SELECTED_APPS -> {
                    require(installedTargets.isNotEmpty()) {
                        "None of the selected split-tunnel apps are installed on this device"
                    }
                    installedTargets.forEach { pkg ->
                        builder.addAllowedApplication(pkg)
                    }
                }
                VpnGuardRoutingMode.EXCLUDE_APPS -> {
                    runCatching { builder.addDisallowedApplication(service.packageName) }
                    installedTargets.forEach { pkg ->
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
                VpnGuardRoutingMode.WHOLE_SYSTEM -> {
                    // Exclude NetGate itself so DNS probes and OpenVPN control remain loop-free
                    runCatching { builder.addDisallowedApplication(service.packageName) }
                }
            }
        }

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                true
            }.getOrDefault(false)
        }

        override fun socket_protect(socket: Int, remote: String, ipv6: Boolean): Boolean {
            return service.protect(socket)
        }

        override fun pause_on_connection_timeout(): Boolean = false

        override fun external_pki_cert_request(req: ClientAPI_ExternalPKICertRequest) {
            req.error = true
            req.errorText = "External PKI is not supported"
        }

        override fun external_pki_sign_request(req: ClientAPI_ExternalPKISignRequest) {
            req.error = true
            req.errorText = "External PKI is not supported"
        }

        override fun log(loginfo: ClientAPI_LogInfo) {
            // Keep quiet unless debugging
        }

        override fun event(ev: ClientAPI_Event) {
            val name = ev.name.orEmpty()
            val info = ev.info.orEmpty()
            val isError = ev.error
            val isFatal = ev.fatal

            if (isError || isFatal) {
                val msg = when (name) {
                    "AUTH_FAILED" -> "Authentication failed — check your OpenVPN username/password"
                    "CERT_VERIFY_FAIL" -> "OpenVPN server certificate verification failed"
                    "CONNECTION_TIMEOUT" -> "OpenVPN connection timed out — try another server or TCP/UDP port"
                    "TRANSPORT_ERROR" -> "OpenVPN transport error: ${info.ifBlank { "server unreachable" }}"
                    "TUN_IFACE_CREATE" -> "Unable to open Android VPN TUN interface: $info"
                    else -> info.ifBlank { "OpenVPN error ($name)" }
                }
                lastFatalError = msg
                if (isFatal && !connectDeferred.isCompleted) {
                    connectDeferred.complete(Result.failure(IllegalStateException(msg)))
                }
                return
            }

            when (name) {
                "CONNECTED" -> {
                    val assignedAddrs = listOfNotNull(
                        localIpv4?.toString(),
                        localIpv6
                    ).joinToString(", ").ifBlank { "Assigned" }
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(
                            Result.success(
                                OpenVpnConnectInfo(
                                    tunnelAddresses = assignedAddrs,
                                    endpoint = remoteEndpoint
                                )
                            )
                        )
                    }
                }
                "RESOLVE", "WAIT", "CONNECTING", "GET_CONFIG", "ASSIGN_IP", "RECONNECTING" -> {
                    listener.onStatusChanged(name)
                }
                "DISCONNECTED" -> {
                    if (!connectDeferred.isCompleted) {
                        val msg = lastFatalError ?: info.ifBlank { "OpenVPN disconnected before handshake completed" }
                        connectDeferred.complete(Result.failure(IllegalStateException(msg)))
                    } else if (!isIntentionalStop.get()) {
                        listener.onDisconnected(lastFatalError ?: info.ifBlank { "OpenVPN disconnected" })
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_START_FOREGROUND = "extra_start_foreground"
        private const val CONNECT_TIMEOUT_MS = 28_000L

        private val nativeLoaded = AtomicBoolean(false)
        private val activeServiceRef = AtomicReference<NetGateOpenVpnService?>(null)
        private val activeSessionRef = AtomicReference<OpenVpn3ClientSession?>(null)
        private val activeThreadRef = AtomicReference<Thread?>(null)
        private val isIntentionalStop = AtomicBoolean(false)

        @Volatile
        private var serviceReadyDeferred: CompletableDeferred<NetGateOpenVpnService>? = null

        @Volatile
        private var sessionListener: Listener? = null

        fun ensureNativeLoaded(): Result<Unit> = runCatching {
            if (!nativeLoaded.get()) {
                System.loadLibrary("ovpn3")
                nativeLoaded.set(true)
            }
        }

        suspend fun connectProfile(
            context: Context,
            profile: VpnGuardProfile,
            listener: Listener
        ): Result<OpenVpnConnectInfo> = withContext(Dispatchers.IO) {
            val nativeRes = ensureNativeLoaded()
            if (nativeRes.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "OpenVPN3 native library could not load on this device",
                        nativeRes.exceptionOrNull()
                    )
                )
            }

            val appContext = context.applicationContext
            val service = obtainService(appContext)
                ?: return@withContext Result.failure(
                    IllegalStateException("Unable to start NetGate OpenVPN service")
                )

            val deferred = CompletableDeferred<Result<OpenVpnConnectInfo>>()
            service.startOpenVpnSession(profile, listener, deferred)

            val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                deferred.await()
            }
            if (result == null) {
                stopActiveSessionInternal(intentional = true)
                service.stopSelfSafe()
                Result.failure(IllegalStateException("OpenVPN handshake timed out after 28s — try another server"))
            } else {
                if (result.isFailure) {
                    stopActiveSessionInternal(intentional = true)
                    service.stopSelfSafe()
                }
                result
            }
        }

        fun disconnect() {
            stopActiveSessionInternal(intentional = true)
            activeServiceRef.get()?.stopSelfSafe()
        }

        fun readTransportStats(): Pair<Long, Long> {
            val session = activeSessionRef.get() ?: return 0L to 0L
            return runCatching {
                val stats = session.transport_stats()
                val rx = stats.bytesIn.coerceAtLeast(0L)
                val tx = stats.bytesOut.coerceAtLeast(0L)
                rx to tx
            }.getOrDefault(0L to 0L)
        }

        private fun stopActiveSessionInternal(intentional: Boolean) {
            isIntentionalStop.set(intentional)
            val session = activeSessionRef.getAndSet(null)
            runCatching { session?.stop() }
            val thread = activeThreadRef.getAndSet(null)
            if (thread != null && thread !== Thread.currentThread()) {
                runCatching { thread.join(1200L) }
            }
            sessionListener = null
        }

        private suspend fun obtainService(appContext: Context): NetGateOpenVpnService? {
            activeServiceRef.get()?.let { return it }
            val ready = CompletableDeferred<NetGateOpenVpnService>()
            serviceReadyDeferred = ready

            val startedNormally = runCatching {
                appContext.startService(Intent(appContext, NetGateOpenVpnService::class.java))
                true
            }.getOrDefault(false)

            if (!startedNormally) {
                runCatching {
                    val fgIntent = Intent(appContext, NetGateOpenVpnService::class.java).apply {
                        putExtra(EXTRA_START_FOREGROUND, true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(fgIntent)
                    } else {
                        appContext.startService(fgIntent)
                    }
                }
            }

            return withTimeoutOrNull(4_000L) { ready.await() }.also {
                serviceReadyDeferred = null
            }
        }
    }
}
