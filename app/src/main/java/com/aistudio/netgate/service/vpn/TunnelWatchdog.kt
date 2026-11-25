package com.aistudio.netgate.service.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.aistudio.netgate.data.local.AdvancedPreferences
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.service.NetGateVpnService
import com.aistudio.netgate.service.VpnGuardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Watches the VPN Guard tunnel and keeps the connection honest:
 *
 *  * **Kill switch** — if the tunnel drops unexpectedly while the kill switch is enabled, the local
 *    firewall is brought back with a fail-closed lockdown so no app traffic leaks outside the tunnel.
 *  * **Auto-reconnect** — retries the last profile with a bounded backoff (3s, 8s, 20s, 45s, 90s).
 *  * **Network switch** — re-establishes the tunnel when the active transport moves between Wi-Fi and
 *    mobile data, because the old socket no longer routes.
 *  * **Trusted Wi-Fi** — connects on unknown SSIDs (when enabled) and pauses on trusted networks.
 */
object TunnelWatchdog {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefs = AdvancedPreferencesState()

    private var reconnectJob: Job? = null
    private var monitoredWifiJob: Job? = null

    @Volatile
    private var autoConnectedProfileId: String? = null

    @Volatile
    private var lastTransport: String = ""

    @Volatile
    private var lastSsid: String = ""

    fun ensureStarted(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (started) return
        synchronized(this) {
            if (started) return
            started = true

            val advanced = AdvancedPreferences(ctx)
            scope.launch {
                advanced.flow.collect { snapshot -> prefs = snapshot }
            }
            scope.launch {
                VpnGuardManager.runtimeState.collect { state ->
                    if (state.connectionState == VpnGuardConnectionState.CONNECTED) {
                        cancelReconnect()
                        if (prefs.killSwitchEngaged) {
                            advanced.setKillSwitchEngaged(false)
                        }
                    }
                }
            }
            registerNetworkCallback(ctx)
        }
    }

    /** Called by [VpnGuardManager] when a tunnel dies without a user request. */
    fun onUnexpectedDrop(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        ensureStarted(ctx)
        scope.launch {
            if (prefs.killSwitchEnabled) {
                AdvancedPreferences(ctx).setKillSwitchEngaged(true)
                runCatching { NetGateVpnService.startOrReload(ctx) }
            }
            scheduleReconnect(ctx, reason = "unexpected drop")
        }
    }

    /** Called by [VpnGuardManager] when the user disconnects on purpose. */
    fun onIntentionalDisconnect(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        autoConnectedProfileId = null
        cancelReconnect()
        scope.launch { AdvancedPreferences(ctx).setKillSwitchEngaged(false) }
    }

    /** Releases the fail-closed lockdown (e.g. after the user turns the firewall off). */
    fun releaseKillSwitch(context: Context) {
        val ctx = context.applicationContext
        scope.launch { AdvancedPreferences(ctx).setKillSwitchEngaged(false) }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun scheduleReconnect(context: Context, reason: String) {
        val profile = VpnGuardManager.lastProfile ?: return
        if (!prefs.autoReconnectEnabled) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delays = longArrayOf(3_000L, 8_000L, 20_000L, 45_000L, 90_000L)
            for (delayMs in delays) {
                delay(delayMs)
                if (VpnGuardManager.runtimeState.value.connectionState == VpnGuardConnectionState.CONNECTED) {
                    return@launch
                }
                val result = VpnGuardManager.connect(context, profile)
                if (result.isSuccess) {
                    AdvancedPreferences(context).setKillSwitchEngaged(false)
                    return@launch
                }
            }
            // Exhausted retries: keep the kill switch engaged and stay fail-closed.
            if (prefs.killSwitchEnabled) {
                AdvancedPreferences(context).setKillSwitchEngaged(true)
                runCatching { NetGateVpnService.startOrReload(context) }
            }
        }
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleNetworkChange(context)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val transport = transportLabel(networkCapabilities)
                handleTransportChange(context, transport, networkCapabilities)
            }

            override fun onLost(network: Network) {
                handleNetworkChange(context)
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun transportLabel(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    private fun handleTransportChange(
        context: Context,
        transport: String,
        caps: NetworkCapabilities
    ) {
        val previous = lastTransport
        lastTransport = transport

        monitoredWifiJob?.cancel()
        monitoredWifiJob = scope.launch {
            delay(1_200L)
            if (transport == "wifi") {
                handleWifiAutomation(context)
            }
            val connected = VpnGuardManager.runtimeState.value.connectionState ==
                VpnGuardConnectionState.CONNECTED
            val switchedBetweenWifiAndCellular =
                (previous == "wifi" && transport == "cell") ||
                    (previous == "cell" && transport == "wifi")
            if (connected &&
                switchedBetweenWifiAndCellular &&
                prefs.autoReconnectEnabled &&
                VpnGuardManager.lastProfile != null
            ) {
                reconnectAfterNetworkSwitch(context)
            }
        }
    }

    private fun handleNetworkChange(context: Context) {
        monitoredWifiJob?.cancel()
        monitoredWifiJob = scope.launch {
            delay(1_500L)
            handleWifiAutomation(context)
        }
    }

    private suspend fun reconnectAfterNetworkSwitch(context: Context) {
        val profile = VpnGuardManager.lastProfile ?: return
        VpnGuardManager.disconnect(context, restoreFirewallIfPaused = false)
        delay(1_000L)
        VpnGuardManager.connect(context, profile)
    }

    private suspend fun handleWifiAutomation(context: Context) {
        val ssid = currentSsid(context)
        if (ssid.isNullOrBlank()) {
            lastSsid = ""
            return
        }
        if (ssid == lastSsid) return
        lastSsid = ssid

        val connected = VpnGuardManager.runtimeState.value.connectionState ==
            VpnGuardConnectionState.CONNECTED
        val isTrusted = prefs.trustedWifiSsids.any { it.equals(ssid, ignoreCase = true) }

        if (!isTrusted && prefs.autoVpnOnUnknownWifi && !connected) {
            val advanced = AdvancedPreferences(context)
            val profile = prefs.lastAutoVpnProfileId
                ?.let { VpnGuardManager.profileById(context, it) }
                ?: VpnGuardManager.lastProfile
            if (profile != null) {
                autoConnectedProfileId = profile.id
                val result = VpnGuardManager.connect(context, profile)
                if (result.isSuccess) {
                    advanced.setLastAutoVpnProfileId(profile.id)
                }
            }
        } else if (isTrusted && connected && autoConnectedProfileId != null) {
            autoConnectedProfileId = null
            VpnGuardManager.disconnect(context, restoreFirewallIfPaused = true)
        }
    }

    private fun currentSsid(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val raw = runCatching { wifiManager.connectionInfo?.ssid }.getOrNull() ?: return null
        val clean = raw.trim().removePrefix("\"").removeSuffix("\"")
        if (clean.isEmpty() || clean.equals("<unknown ssid>", ignoreCase = true)) return null
        return clean
    }
}
