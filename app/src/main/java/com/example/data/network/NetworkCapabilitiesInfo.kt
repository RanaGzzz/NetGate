package com.example.data.network

/**
 * Transport families NetGate distinguishes when describing a network.
 *
 * Kept as a small pure enum so diagnostics and tests do not depend on
 * `android.net.NetworkCapabilities` directly.
 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    OTHER
}

/**
 * Immutable copy of the `NetworkCapabilities` values NetGate relies on.
 *
 * The VPN distinction is the important one: Android marks a network as VPN either by advertising the
 * VPN transport or by *not* advertising `NET_CAPABILITY_NOT_VPN`. Both signals are preserved here so
 * callers keep the exact same classification the diagnostics used before.
 */
data class NetworkCapabilitiesInfo(
    val hasInternet: Boolean = false,
    val notVpn: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = false,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val transports: Set<NetworkTransport> = emptySet()
) {
    fun hasTransport(transport: NetworkTransport): Boolean = transport in transports

    /** VPN transport flag as reported by the platform (used by the security audit). */
    val hasVpnTransport: Boolean
        get() = NetworkTransport.VPN in transports

    /** Matches the previous diagnostics check: `TRANSPORT_VPN || !NOT_VPN`. */
    val isVpn: Boolean
        get() = hasVpnTransport || !notVpn

    /** Primary transport in the order the old selection logic preferred. */
    val primaryTransport: NetworkTransport
        get() = when {
            NetworkTransport.WIFI in transports -> NetworkTransport.WIFI
            NetworkTransport.CELLULAR in transports -> NetworkTransport.CELLULAR
            NetworkTransport.ETHERNET in transports -> NetworkTransport.ETHERNET
            NetworkTransport.VPN in transports -> NetworkTransport.VPN
            NetworkTransport.BLUETOOTH in transports -> NetworkTransport.BLUETOOTH
            else -> NetworkTransport.OTHER
        }
}
