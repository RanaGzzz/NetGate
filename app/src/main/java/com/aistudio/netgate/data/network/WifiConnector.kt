package com.aistudio.netgate.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Joins a Wi-Fi network straight from the app.
 *
 * Android 10 (API 29) and newer only lets a normal app join an access point through a
 * [WifiNetworkSpecifier] request: the system owns the credentials prompt and keeps the association
 * alive as long as the request is registered, so the callback is intentionally kept instead of being
 * unregistered right after the network becomes available.
 *
 * The request must ask for [NetworkCapabilities.NET_CAPABILITY_INTERNET]. Without it the specifier
 * creates a local-only network: the phone associates, but the system keeps the previous network as
 * the default route, so traffic (and the VPN) never moves to the network that was just joined.
 *
 * Older releases use the legacy [WifiManager] config path, which needs CHANGE_WIFI_STATE.
 */
object WifiConnector {

    private const val TAG = "WifiConnector"
    private const val REQUEST_TIMEOUT_MS = 45_000

    /** Shown instead of a password prompt: the specifier API has no WEP support. */
    const val WEP_UNSUPPORTED_MESSAGE =
        "WEP networks can only be joined from Android Wi-Fi Settings"

    enum class Security { OPEN, WEP, WPA }

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /** Maps the label produced by the diagnostics screen ("WPA2-PSK", "Open", "OWE (Enhanced Open)"). */
    fun securityOf(label: String): Security = when {
        label.isBlank() -> Security.WPA
        label.contains("open", ignoreCase = true) -> Security.OPEN
        label.contains("owe", ignoreCase = true) -> Security.OPEN
        label.contains("wep", ignoreCase = true) -> Security.WEP
        else -> Security.WPA
    }

    fun requiresPassphrase(label: String): Boolean =
        securityOf(label) != Security.OPEN && canJoinFromApp(label)

    /**
     * WEP can only be configured through the legacy [WifiManager] path, so from Android 10 on it has
     * to be joined from the system Wi-Fi settings instead of from the app.
     */
    fun canJoinFromApp(label: String): Boolean =
        securityOf(label) != Security.WEP || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun connect(
        context: Context,
        ssid: String,
        securityLabel: String,
        passphrase: String?,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val appContext = context.applicationContext
        val security = securityOf(securityLabel)
        if (security != Security.OPEN && passphrase.isNullOrEmpty()) {
            onResult(false, "Enter the password for $ssid")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (security == Security.WEP) {
                // WifiNetworkSpecifier has no WEP support, so the system UI has to do it.
                onResult(false, WEP_UNSUPPORTED_MESSAGE)
                return
            }
            connectWithSpecifier(appContext, ssid, security, passphrase, onResult)
        } else {
            connectLegacy(appContext, ssid, security, passphrase, onResult)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithSpecifier(
        context: Context,
        ssid: String,
        security: Security,
        passphrase: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            onResult(false, "Wi-Fi service unavailable")
            return
        }
        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        when (security) {
            Security.OPEN -> Unit
            // WEP is rejected before this point because the specifier does not support it.
            Security.WEP, Security.WPA -> specifierBuilder.setWpa2Passphrase(passphrase.orEmpty())
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Requesting INTERNET keeps the join from becoming a local-only network, which would
            // leave the device (and the tunnel) routing through the previous connection.
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        activeCallback?.let { previous ->
            runCatching { connectivityManager.unregisterNetworkCallback(previous) }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onResult(true, "Connected to $ssid")
            }

            override fun onUnavailable() {
                activeCallback = null
                onResult(false, "Couldn't connect to $ssid")
            }
        }
        activeCallback = callback
        runCatching {
            connectivityManager.requestNetwork(request, callback, REQUEST_TIMEOUT_MS)
        }.onFailure { error ->
            Log.w(TAG, "Wi-Fi request failed for $ssid", error)
            activeCallback = null
            onResult(false, "Couldn't connect to $ssid")
        }
    }

    @Suppress("DEPRECATION")
    private fun connectLegacy(
        context: Context,
        ssid: String,
        security: Security,
        passphrase: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null || !wifiManager.isWifiEnabled) {
            onResult(false, "Turn Wi-Fi on first")
            return
        }
        val configuration = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            when (security) {
                Security.OPEN -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                Security.WEP -> {
                    wepKeys[0] = "\"${passphrase.orEmpty()}\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                }
                Security.WPA -> {
                    preSharedKey = "\"${passphrase.orEmpty()}\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
            }
        }
        val networkId = runCatching { wifiManager.addNetwork(configuration) }.getOrDefault(-1)
        if (networkId == -1) {
            onResult(false, "Couldn't save $ssid — join it from Wi-Fi Settings")
            return
        }
        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()
        onResult(true, "Joining $ssid…")
    }

    /** Releases the last specifier request, dropping an app-scoped join. */
    fun disconnect(context: Context) {
        val callback = activeCallback ?: return
        activeCallback = null
        val connectivityManager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
