package com.example.service

import android.content.Context
import android.net.VpnService
import com.example.data.model.VpnGuardConnectionState
import com.example.data.model.VpnGuardProfile
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.VpnGuardRuntimeState
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

object VpnGuardManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    private val _runtimeState = MutableStateFlow(VpnGuardRuntimeState())
    val runtimeState: StateFlow<VpnGuardRuntimeState> = _runtimeState.asStateFlow()

    @Volatile
    private var backend: GoBackend? = null
    private var statsJob: Job? = null

    private val activeTunnel = object : Tunnel {
        override fun getName(): String = "netgate_vpn_guard"

        override fun onStateChange(newState: Tunnel.State) {
            val mapped = when (newState) {
                Tunnel.State.UP -> VpnGuardConnectionState.CONNECTED
                Tunnel.State.DOWN -> VpnGuardConnectionState.DISCONNECTED
                Tunnel.State.TOGGLE -> VpnGuardConnectionState.CONNECTING
            }
            _runtimeState.value = _runtimeState.value.copy(
                connectionState = mapped
            )
        }
    }

    private fun getOrCreateBackend(context: Context): GoBackend {
        backend?.let { return it }
        return synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }
    }

    suspend fun connect(
        context: Context,
        profile: VpnGuardProfile
    ): Result<Unit> = stateMutex.withLock {
        val appContext = context.applicationContext
        if (VpnService.prepare(appContext) != null) {
            val err = IllegalStateException("Android VPN permission is required before connecting")
            _runtimeState.value = VpnGuardRuntimeState(
                connectionState = VpnGuardConnectionState.ERROR,
                errorMessage = err.message
            )
            return@withLock Result.failure(err)
        }

        val wasFirewallActive = NetGateVpnService.runtimeState.value.isServiceRunning ||
            _runtimeState.value.pausedLocalFirewallForVpn

        _runtimeState.value = _runtimeState.value.copy(
            connectionState = VpnGuardConnectionState.CONNECTING,
            activeProfileId = profile.id,
            activeProfileName = profile.name,
            endpoint = profile.endpointHost,
            tunnelAddresses = profile.interfaceAddresses,
            routingMode = profile.routingMode,
            routedPackagesCount = if (profile.routingMode == VpnGuardRoutingMode.WHOLE_SYSTEM) 0 else profile.targetPackages.size,
            errorMessage = null,
            pausedLocalFirewallForVpn = wasFirewallActive
        )

        return@withLock runCatching {
            val wgConfig = WireGuardConfigHelper.buildWireGuardConfig(profile).getOrThrow()

            // Coordinate cleanly with NetGate local firewall TUN because Android permits 1 VpnService at a time
            if (NetGateVpnService.runtimeState.value.isServiceRunning) {
                NetGateVpnService.stop(appContext)
                repeat(30) {
                    if (!NetGateVpnService.runtimeState.value.isServiceRunning) return@repeat
                    delay(50L)
                }
            }

            VpnGuardService.start(appContext)

            val goBackend = getOrCreateBackend(appContext)
            runCatching {
                goBackend.setState(activeTunnel, Tunnel.State.DOWN, null)
            }
            goBackend.setState(activeTunnel, Tunnel.State.UP, wgConfig)

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
            startStatsPolling(appContext, goBackend)
        }.onFailure { err ->
            stopStatsPolling()
            VpnGuardService.stop(appContext)
            _runtimeState.value = VpnGuardRuntimeState(
                connectionState = VpnGuardConnectionState.ERROR,
                activeProfileId = profile.id,
                activeProfileName = profile.name,
                endpoint = profile.endpointHost,
                routingMode = profile.routingMode,
                errorMessage = err.message ?: "Failed to bring up WireGuard tunnel",
                pausedLocalFirewallForVpn = wasFirewallActive
            )
        }
    }

    suspend fun disconnect(
        context: Context,
        restoreFirewallIfPaused: Boolean = false
    ): Result<Unit> = stateMutex.withLock {
        val appContext = context.applicationContext
        val pausedFirewall = _runtimeState.value.pausedLocalFirewallForVpn
        _runtimeState.value = _runtimeState.value.copy(
            connectionState = VpnGuardConnectionState.DISCONNECTING
        )
        stopStatsPolling()

        val result = runCatching {
            backend?.setState(activeTunnel, Tunnel.State.DOWN, null)
            Unit
        }

        VpnGuardService.stop(appContext)
        _runtimeState.value = VpnGuardRuntimeState(
            connectionState = VpnGuardConnectionState.DISCONNECTED,
            pausedLocalFirewallForVpn = pausedFirewall && !restoreFirewallIfPaused
        )

        if (restoreFirewallIfPaused && pausedFirewall && VpnService.prepare(appContext) == null) {
            NetGateVpnService.startOrReload(appContext)
        }
        return@withLock result
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
