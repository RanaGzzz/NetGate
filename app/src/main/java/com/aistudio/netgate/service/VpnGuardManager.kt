package com.aistudio.netgate.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardProtocol
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.data.model.VpnGuardRuntimeState
import com.aistudio.netgate.service.session.VpnSessionCoordinator
import com.aistudio.netgate.service.session.VpnSessionPorts
import com.aistudio.netgate.service.session.VpnSlotOwner
import com.aistudio.netgate.service.session.VpnTransitionResult
import com.aistudio.netgate.service.vpn.TunnelWatchdog
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object VpnGuardManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializes whole connect/disconnect operations. The VPN slot itself is serialized by
     * [VpnSessionCoordinator]; this mutex additionally keeps the runtime state updates of concurrent
     * UI/notification requests in order.
     */
    private val stateMutex = Mutex()

    private val coordinator: VpnSessionCoordinator
        get() = VpnSessionCoordinator.instance

    private val _runtimeState = MutableStateFlow(VpnGuardRuntimeState())
    val runtimeState: StateFlow<VpnGuardRuntimeState> = _runtimeState.asStateFlow()

    @Volatile
    private var backend: GoBackend? = null
    private var statsJob: Job? = null

    /** True while either the WireGuard or OpenVPN tunnel is up. */
    @Volatile
    private var tunnelUp: Boolean = false

    @Volatile
    private var activeProtocol: VpnGuardProtocol? = null

    /** Set while a user/coordinator-initiated teardown is running, so DOWN is not "unexpected". */
    @Volatile
    private var intentionalDisconnect: Boolean = false

    @Volatile
    private var lastAppContext: Context? = null

    /** Last profile the user (or the automation) connected, used by auto-reconnect. */
    @Volatile
    var lastProfile: VpnGuardProfile? = null
        private set

    private val activeTunnel = object : Tunnel {
        override fun getName(): String = "netgate_vpn_guard"

        override fun onStateChange(newState: Tunnel.State) {
            // While OpenVPN owns the VPN slot, WireGuard's backend still emits its own DOWN/TOGGLE
            // events (we push it DOWN before every switch). Those used to be applied to the shared
            // runtime state, marking a healthy OpenVPN session as DISCONNECTED and stopping its
            // status service, so the Wi-Fi/VPN indicators kept vanishing after switching endpoints.
            if (activeProtocol == VpnGuardProtocol.OPENVPN) {
                return
            }
            val mapped = when (newState) {
                Tunnel.State.UP -> VpnGuardConnectionState.CONNECTED
                Tunnel.State.DOWN -> VpnGuardConnectionState.DISCONNECTED
                Tunnel.State.TOGGLE -> VpnGuardConnectionState.CONNECTING
            }
            val previous = _runtimeState.value.connectionState
            if (newState == Tunnel.State.UP) {
                tunnelUp = true
                activeProtocol = VpnGuardProtocol.WIREGUARD
            } else if (newState == Tunnel.State.DOWN) {
                if (activeProtocol == VpnGuardProtocol.WIREGUARD) {
                    tunnelUp = false
                    activeProtocol = null
                }
            }
            _runtimeState.value = _runtimeState.value.copy(connectionState = mapped)

            val wasConnected = previous == VpnGuardConnectionState.CONNECTED ||
                previous == VpnGuardConnectionState.DISCONNECTING
            if (newState == Tunnel.State.DOWN && wasConnected && !intentionalDisconnect) {
                // Requirement: unexpected disconnects release ownership, stop telemetry and the
                // foreground service, but never silently start another VPN endpoint.
                stopStatsPolling()
                lastAppContext?.let { VpnGuardService.stop(it) }
                scope.launch {
                    coordinator.handleUnexpectedTunnelDisconnect(
                        "The WireGuard tunnel disconnected unexpectedly"
                    )
                    lastAppContext?.let { TunnelWatchdog.onUnexpectedDrop(it) }
                    _runtimeState.value = _runtimeState.value.copy(
                        pausedLocalFirewallForVpn = coordinator.state.value.firewallRestorePending
                    )
                }
            }
        }
    }

    private fun getOrCreateBackend(context: Context): GoBackend {
        backend?.let { return it }
        return synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }
    }

    internal fun filterInstalledPackages(
        context: Context,
        packages: Set<String>
    ): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val pm = context.packageManager
        return packages.filter { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }.toSet()
    }

    /**
     * Prevents overlapping connect requests (tapping WARP and then an OpenVPN profile). Requests used
     * to queue on [stateMutex], so each one tore the fresh tunnel back down and rebuilt it, which
     * looked like a crash/hang and made the system network icons flicker.
     */
    private val connectRequestGuard = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun connect(
        context: Context,
        profile: VpnGuardProfile
    ): Result<Unit> {
        if (!connectRequestGuard.compareAndSet(false, true)) {
            return Result.failure(
                IllegalStateException("A VPN connection change is already running — please wait a moment")
            )
        }
        return try {
            connectInternal(context, profile)
        } finally {
            connectRequestGuard.set(false)
        }
    }

    private suspend fun connectInternal(
        context: Context,
        profile: VpnGuardProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val appContext = context.applicationContext
            lastAppContext = appContext
            lastProfile = profile
            TunnelWatchdog.ensureStarted(appContext)

            val wasFirewallActive = NetGateVpnService.runtimeState.value.isServiceRunning ||
                coordinator.state.value.firewallRestorePending

            val effectivePackages = when (profile.routingMode) {
                VpnGuardRoutingMode.WHOLE_SYSTEM -> emptySet()
                VpnGuardRoutingMode.SELECTED_APPS -> {
                    val installed = filterInstalledPackages(appContext, profile.targetPackages)
                    if (installed.isEmpty()) {
                        val err = IllegalStateException(
                            "None of the selected apps are installed on this device"
                        )
                        _runtimeState.value = VpnGuardRuntimeState(
                            connectionState = VpnGuardConnectionState.ERROR,
                            protocol = profile.protocol,
                            activeProfileId = profile.id,
                            activeProfileName = profile.name,
                            endpoint = profile.endpointHost,
                            activeServerIp = extractHostFromEndpoint(profile.endpointHost),
                            routingMode = profile.routingMode,
                            errorMessage = err.message,
                            pausedLocalFirewallForVpn = wasFirewallActive
                        )
                        return@withLock Result.failure(err)
                    }
                    installed
                }
                VpnGuardRoutingMode.EXCLUDE_APPS ->
                    filterInstalledPackages(appContext, profile.targetPackages)
            }

            // Seamlessly tear down any active WireGuard/OpenVPN tunnel before switching profiles,
            // preserving firewallRestorePending so "Switch to Firewall" stays available.
            if (tunnelUp || coordinator.state.value.owner == VpnSlotOwner.TUNNEL) {
                stopStatsPolling()
                intentionalDisconnect = true
                try {
                    coordinator.disconnectTunnel(
                        ports = sessionPorts(appContext),
                        restoreFirewallRequested = false
                    )
                } finally {
                    intentionalDisconnect = false
                }
            }

            _runtimeState.value = _runtimeState.value.copy(
                connectionState = VpnGuardConnectionState.CONNECTING,
                protocol = profile.protocol,
                activeProfileId = profile.id,
                activeProfileName = profile.name,
                endpoint = profile.endpointHost,
                activeServerIp = extractHostFromEndpoint(profile.endpointHost),
                tunnelAddresses = profile.interfaceAddresses,
                routingMode = profile.routingMode,
                routedPackagesCount = if (profile.routingMode == VpnGuardRoutingMode.WHOLE_SYSTEM) 0 else effectivePackages.size,
                errorMessage = null,
                pausedLocalFirewallForVpn = wasFirewallActive
            )

            return@withLock runCatching {
                when (profile.protocol) {
                    VpnGuardProtocol.WIREGUARD -> connectWireGuardLocked(
                        appContext = appContext,
                        profile = profile,
                        effectivePackages = effectivePackages
                    )
                    VpnGuardProtocol.OPENVPN -> connectOpenVpnLocked(
                        appContext = appContext,
                        profile = profile,
                        effectivePackages = effectivePackages
                    )
                }
            }.onFailure { err ->
                stopStatsPolling()
                VpnGuardService.stop(appContext)
                val friendlyMessage = formatTunnelError(err, profile.protocol)
                _runtimeState.value = VpnGuardRuntimeState(
                    connectionState = VpnGuardConnectionState.ERROR,
                    protocol = profile.protocol,
                    activeProfileId = profile.id,
                    activeProfileName = profile.name,
                    endpoint = profile.endpointHost,
                    activeServerIp = extractHostFromEndpoint(profile.endpointHost),
                    routingMode = profile.routingMode,
                    errorMessage = friendlyMessage,
                    pausedLocalFirewallForVpn = coordinator.state.value.firewallRestorePending
                )
            }
        }
    }

    private suspend fun connectWireGuardLocked(
        appContext: Context,
        profile: VpnGuardProfile,
        effectivePackages: Set<String>
    ) {
        val endpointHostOnly = extractHostFromEndpoint(profile.endpointHost)
        val resolvedFallbackIp = WarpConfigGenerator.resolveHostForWireGuard(endpointHostOnly)
        val runtimeRawConfig = if (resolvedFallbackIp != null) {
            WireGuardConfigHelper.replaceEndpointHost(profile.rawConfig, resolvedFallbackIp)
        } else {
            profile.rawConfig
        }
        val runtimeProfile = profile.copy(
            rawConfig = runtimeRawConfig,
            targetPackages = effectivePackages
        )
        val wgConfig = WireGuardConfigHelper.buildWireGuardConfig(runtimeProfile).getOrThrow()

        val transition = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
            coordinator.connectTunnel(
            ports = sessionPorts(
                appContext = appContext,
                activateTunnel = {
                    runCatching { NetGateOpenVpnService.disconnect() }
                    val goBackend = getOrCreateBackend(appContext)
                    runCatching {
                        goBackend.setState(activeTunnel, Tunnel.State.DOWN, null)
                    }
                    goBackend.setState(activeTunnel, Tunnel.State.UP, wgConfig)
                    activeProtocol = VpnGuardProtocol.WIREGUARD
                    tunnelUp = true

                    val now = System.currentTimeMillis()
                    _runtimeState.value = _runtimeState.value.copy(
                        connectionState = VpnGuardConnectionState.CONNECTED,
                        protocol = VpnGuardProtocol.WIREGUARD,
                        connectedSinceMs = now,
                        rxBytes = 0L,
                        txBytes = 0L,
                        rxSpeedBytesPerSec = 0L,
                        txSpeedBytesPerSec = 0L,
                        errorMessage = null
                    )
                    // Start foreground status service only AFTER the tunnel is active to
                    // avoid FGS timing crashes on error.
                    VpnGuardService.start(appContext)
                    startStatsPolling(appContext) {
                        val stats = runCatching { goBackend.getStatistics(activeTunnel) }.getOrNull()
                        if (stats != null) stats.totalRx() to stats.totalTx() else null
                    }
                }
            ),
            permissionGranted = VpnService.prepare(appContext) == null
            )
        } ?: throw IllegalStateException(
            "Switching to WireGuard timed out after ${TRANSITION_TIMEOUT_MS} ms — the previous VPN endpoint did not release the slot"
        )

        when (transition) {
            is VpnTransitionResult.Success -> Unit
            is VpnTransitionResult.Rejected ->
                throw IllegalStateException(transition.message)
            is VpnTransitionResult.Failed ->
                throw transition.cause ?: IllegalStateException(transition.message)
            else -> throw IllegalStateException(transition.describe())
        }
    }

    private suspend fun connectOpenVpnLocked(
        appContext: Context,
        profile: VpnGuardProfile,
        effectivePackages: Set<String>
    ) {
        val runtimeProfile = profile.copy(targetPackages = effectivePackages)
        val ovpnListener = object : NetGateOpenVpnService.Listener {
            override fun onStatusChanged(stageMessage: String) {
                // Keep CONNECTING state clean while handshake completes
            }

            override fun onDisconnected(reason: String?) {
                if (intentionalDisconnect) return
                tunnelUp = false
                if (activeProtocol == VpnGuardProtocol.OPENVPN) {
                    activeProtocol = null
                }
                stopStatsPolling()
                VpnGuardService.stop(appContext)
                scope.launch {
                    val msg = reason?.takeIf { it.isNotBlank() }
                        ?: "The OpenVPN tunnel disconnected unexpectedly"
                    coordinator.handleUnexpectedTunnelDisconnect(msg)
                    TunnelWatchdog.onUnexpectedDrop(appContext)
                    _runtimeState.value = _runtimeState.value.copy(
                        connectionState = VpnGuardConnectionState.DISCONNECTED,
                        errorMessage = reason,
                        pausedLocalFirewallForVpn = coordinator.state.value.firewallRestorePending
                    )
                }
            }
        }

        val transition = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
            coordinator.connectTunnel(
            ports = sessionPorts(
                appContext = appContext,
                activateTunnel = {
                    runCatching { backend?.setState(activeTunnel, Tunnel.State.DOWN, null) }
                    val info = NetGateOpenVpnService.connectProfile(
                        context = appContext,
                        profile = runtimeProfile,
                        listener = ovpnListener
                    ).getOrThrow()

                    activeProtocol = VpnGuardProtocol.OPENVPN
                    tunnelUp = true

                    val effectiveEndpoint = info.endpoint.ifBlank { profile.endpointHost }
                    val now = System.currentTimeMillis()
                    _runtimeState.value = _runtimeState.value.copy(
                        connectionState = VpnGuardConnectionState.CONNECTED,
                        protocol = VpnGuardProtocol.OPENVPN,
                        endpoint = effectiveEndpoint,
                        activeServerIp = extractHostFromEndpoint(effectiveEndpoint),
                        tunnelAddresses = info.tunnelAddresses.ifBlank { "Assigned" },
                        connectedSinceMs = now,
                        rxBytes = 0L,
                        txBytes = 0L,
                        rxSpeedBytesPerSec = 0L,
                        txSpeedBytesPerSec = 0L,
                        errorMessage = null
                    )
                    VpnGuardService.start(appContext)
                    startStatsPolling(appContext) {
                        NetGateOpenVpnService.readTransportStats()
                    }
                }
            ),
            permissionGranted = VpnService.prepare(appContext) == null
            )
        } ?: throw IllegalStateException(
            "Switching to OpenVPN timed out after ${TRANSITION_TIMEOUT_MS} ms — the previous VPN endpoint did not release the slot"
        )

        when (transition) {
            is VpnTransitionResult.Success -> Unit
            is VpnTransitionResult.Rejected ->
                throw IllegalStateException(transition.message)
            is VpnTransitionResult.Failed ->
                throw transition.cause ?: IllegalStateException(transition.message)
            else -> throw IllegalStateException(transition.describe())
        }
    }

    suspend fun disconnect(
        context: Context,
        restoreFirewallIfPaused: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val appContext = context.applicationContext
            lastAppContext = appContext
            _runtimeState.value = _runtimeState.value.copy(
                connectionState = VpnGuardConnectionState.DISCONNECTING
            )
            stopStatsPolling()
            TunnelWatchdog.onIntentionalDisconnect(appContext)

            intentionalDisconnect = true
            val transition = try {
                withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                    coordinator.disconnectTunnel(
                        ports = sessionPorts(appContext),
                        restoreFirewallRequested = restoreFirewallIfPaused
                    )
                } ?: VpnTransitionResult.Failed(
                    stage = VpnTransitionResult.Stage.TUNNEL_STOP,
                    message = "The VPN tunnel did not stop within $DISCONNECT_TIMEOUT_MS ms",
                    timedOut = true
                )
            } finally {
                intentionalDisconnect = false
            }
            VpnGuardService.stop(appContext)

            val session = coordinator.state.value
            when (transition) {
                is VpnTransitionResult.Failed -> {
                    _runtimeState.value = VpnGuardRuntimeState(
                        connectionState = VpnGuardConnectionState.ERROR,
                        protocol = _runtimeState.value.protocol,
                        activeProfileId = _runtimeState.value.activeProfileId,
                        activeProfileName = _runtimeState.value.activeProfileName,
                        endpoint = _runtimeState.value.endpoint,
                        activeServerIp = _runtimeState.value.activeServerIp,
                        routingMode = _runtimeState.value.routingMode,
                        errorMessage = transition.message,
                        pausedLocalFirewallForVpn = session.firewallRestorePending
                    )
                    Result.failure(IllegalStateException(transition.message, transition.cause))
                }
                else -> {
                    _runtimeState.value = VpnGuardRuntimeState(
                        connectionState = VpnGuardConnectionState.DISCONNECTED,
                        pausedLocalFirewallForVpn = session.firewallRestorePending
                    )
                    Result.success(Unit)
                }
            }
        }
    }

    fun disconnectAsync(
        context: Context,
        restoreFirewallIfPaused: Boolean = false
    ) {
        val appContext = context.applicationContext
        scope.launch {
            disconnect(appContext, restoreFirewallIfPaused)
        }
    }

    /**
     * Endpoints handed to [VpnSessionCoordinator]. Production wiring for the real firewall service
     * and both the WireGuard and OpenVPN3 backends.
     */
    private fun sessionPorts(
        appContext: Context,
        activateTunnel: suspend () -> Unit = {}
    ): VpnSessionPorts = VpnSessionPorts(
        firewallRunning = { NetGateVpnService.runtimeState.value.isServiceRunning },
        stopFirewall = { NetGateVpnService.stop(appContext) },
        startFirewall = { NetGateVpnService.startOrReload(appContext) },
        tunnelRunning = { tunnelUp },
        activateTunnel = activateTunnel,
        deactivateTunnel = {
            runCatching {
                backend?.setState(activeTunnel, Tunnel.State.DOWN, null)
            }
            runCatching {
                NetGateOpenVpnService.disconnect()
            }
            activeProtocol = null
            tunnelUp = false
            stopStatsPolling()
            VpnGuardService.stop(appContext)
        }
    )

    /** Loads a saved profile for network automation / auto-reconnect. */
    suspend fun profileById(context: Context, profileId: String): VpnGuardProfile? =
        withContext(Dispatchers.IO) {
            runCatching {
                NetGateDatabase.getInstance(context.applicationContext)
                    .vpnGuardProfileDao()
                    .getProfileById(profileId)
                    ?.toModel()
            }.getOrNull()
        }

    private fun extractHostFromEndpoint(endpoint: String): String {
        val clean = endpoint.trim().substringBefore(' ')
        if (clean.isEmpty()) return ""
        return if (clean.startsWith("[")) {
            clean.substringAfter("[").substringBefore("]")
        } else {
            clean.substringBeforeLast(':', clean)
        }
    }

    private fun formatTunnelError(err: Throwable, protocol: VpnGuardProtocol): String {
        if (err is BackendException) {
            return when (err.reason) {
                BackendException.Reason.VPN_NOT_AUTHORIZED -> "Android VPN permission is required before connecting"
                BackendException.Reason.DNS_RESOLUTION_FAILURE -> "Could not resolve VPN server address — check your internet connection"
                BackendException.Reason.TUN_CREATION_ERROR -> "Could not create VPN tunnel interface — close other VPN apps and retry"
                BackendException.Reason.GO_ACTIVATION_ERROR_CODE -> "WireGuard tunnel activation failed (code ${err.format?.contentToString() ?: "error"})"
                else -> err.message ?: "Failed to bring up WireGuard tunnel"
            }
        }
        val linkErr = (err as? UnsatisfiedLinkError) ?: (err.cause as? UnsatisfiedLinkError)
        if (linkErr != null) {
            return "${protocol.label} native library could not be loaded on this device ABI (${linkErr.message ?: "incompatible native lib"}). You can still export the .${protocol.fileExtension} file."
        }
        return err.message?.takeIf { it.isNotBlank() } ?: "Failed to bring up ${protocol.label} tunnel"
    }

    private fun startStatsPolling(
        context: Context,
        readStats: () -> Pair<Long, Long>?
    ) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var prevRx = 0L
            var prevTx = 0L
            var firstSample = true
            while (isActive) {
                delay(1000L)
                val current = _runtimeState.value
                if (current.connectionState != VpnGuardConnectionState.CONNECTED) {
                    break
                }
                val stats = runCatching { readStats() }.getOrNull()
                if (stats != null) {
                    val (rx, tx) = stats
                    val rxRate = if (firstSample) 0L else (rx - prevRx).coerceAtLeast(0L)
                    val txRate = if (firstSample) 0L else (tx - prevTx).coerceAtLeast(0L)
                    prevRx = rx
                    prevTx = tx
                    firstSample = false
                    _runtimeState.value = _runtimeState.value.copy(
                        rxBytes = rx,
                        txBytes = tx,
                        rxSpeedBytesPerSec = rxRate,
                        txSpeedBytesPerSec = txRate
                    )
                    VpnGuardService.updateNotification(context, _runtimeState.value)
                }
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    private const val TRANSITION_TIMEOUT_MS = 45_000L
    private const val DISCONNECT_TIMEOUT_MS = 20_000L
}
