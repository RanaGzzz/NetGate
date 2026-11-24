package com.aistudio.netgate.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.data.model.VpnGuardRuntimeState
import com.aistudio.netgate.service.session.VpnSessionCoordinator
import com.aistudio.netgate.service.session.VpnSessionPorts
import com.aistudio.netgate.service.session.VpnTransitionResult
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

    /** True while the WireGuard tunnel itself is up (kept separate from the published UI state). */
    @Volatile
    private var tunnelUp: Boolean = false

    /** Set while a user/coordinator-initiated teardown is running, so DOWN is not "unexpected". */
    @Volatile
    private var intentionalDisconnect: Boolean = false

    @Volatile
    private var lastAppContext: Context? = null

    private val activeTunnel = object : Tunnel {
        override fun getName(): String = "netgate_vpn_guard"

        override fun onStateChange(newState: Tunnel.State) {
            val mapped = when (newState) {
                Tunnel.State.UP -> VpnGuardConnectionState.CONNECTED
                Tunnel.State.DOWN -> VpnGuardConnectionState.DISCONNECTED
                Tunnel.State.TOGGLE -> VpnGuardConnectionState.CONNECTING
            }
            val previous = _runtimeState.value.connectionState
            if (newState == Tunnel.State.UP) {
                tunnelUp = true
            } else if (newState == Tunnel.State.DOWN) {
                tunnelUp = false
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

    suspend fun connect(
        context: Context,
        profile: VpnGuardProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val appContext = context.applicationContext
            lastAppContext = appContext

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
                            activeProfileId = profile.id,
                            activeProfileName = profile.name,
                            endpoint = profile.endpointHost,
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

            _runtimeState.value = _runtimeState.value.copy(
                connectionState = VpnGuardConnectionState.CONNECTING,
                activeProfileId = profile.id,
                activeProfileName = profile.name,
                endpoint = profile.endpointHost,
                tunnelAddresses = profile.interfaceAddresses,
                routingMode = profile.routingMode,
                routedPackagesCount = if (profile.routingMode == VpnGuardRoutingMode.WHOLE_SYSTEM) 0 else effectivePackages.size,
                errorMessage = null,
                pausedLocalFirewallForVpn = wasFirewallActive
            )

            return@withLock runCatching {
                val endpointHostOnly = profile.endpointHost
                    .trim()
                    .let { ep ->
                        if (ep.startsWith("[")) {
                            ep.substringAfter("[").substringBefore("]")
                        } else {
                            ep.substringBeforeLast(':', ep)
                        }
                    }
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

                val transition = coordinator.connectTunnel(
                    ports = sessionPorts(
                        appContext = appContext,
                        activateTunnel = {
                            val goBackend = getOrCreateBackend(appContext)
                            runCatching {
                                goBackend.setState(activeTunnel, Tunnel.State.DOWN, null)
                            }
                            goBackend.setState(activeTunnel, Tunnel.State.UP, wgConfig)
                            tunnelUp = true

                            val now = System.currentTimeMillis()
                            _runtimeState.value = _runtimeState.value.copy(
                                connectionState = VpnGuardConnectionState.CONNECTED,
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
                            startStatsPolling(appContext, goBackend)
                        }
                    ),
                    permissionGranted = VpnService.prepare(appContext) == null
                )

                when (transition) {
                    is VpnTransitionResult.Success -> Unit
                    is VpnTransitionResult.Rejected ->
                        throw IllegalStateException(transition.message)
                    is VpnTransitionResult.Failed ->
                        throw transition.cause ?: IllegalStateException(transition.message)
                    else -> throw IllegalStateException(transition.describe())
                }
            }.onFailure { err ->
                stopStatsPolling()
                VpnGuardService.stop(appContext)
                val friendlyMessage = formatTunnelError(err)
                _runtimeState.value = VpnGuardRuntimeState(
                    connectionState = VpnGuardConnectionState.ERROR,
                    activeProfileId = profile.id,
                    activeProfileName = profile.name,
                    endpoint = profile.endpointHost,
                    routingMode = profile.routingMode,
                    errorMessage = friendlyMessage,
                    pausedLocalFirewallForVpn = coordinator.state.value.firewallRestorePending
                )
            }
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

            intentionalDisconnect = true
            val transition = try {
                coordinator.disconnectTunnel(
                    ports = sessionPorts(appContext),
                    restoreFirewallRequested = restoreFirewallIfPaused
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
                        activeProfileId = _runtimeState.value.activeProfileId,
                        activeProfileName = _runtimeState.value.activeProfileName,
                        endpoint = _runtimeState.value.endpoint,
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
     * and the WireGuard backend.
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
            val goBackend = backend
            if (goBackend != null) {
                goBackend.setState(activeTunnel, Tunnel.State.DOWN, null)
            }
            tunnelUp = false
            stopStatsPolling()
            VpnGuardService.stop(appContext)
        }
    )

    private fun formatTunnelError(err: Throwable): String {
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
            return "WireGuard native library could not be loaded on this device ABI (${linkErr.message ?: "incompatible native lib"}). You can still export the .conf file."
        }
        return err.message?.takeIf { it.isNotBlank() } ?: "Failed to bring up WireGuard tunnel"
    }

    private fun startStatsPolling(context: Context, goBackend: GoBackend) {
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
                val stats = runCatching { goBackend.getStatistics(activeTunnel) }.getOrNull()
                if (stats != null) {
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
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
}
