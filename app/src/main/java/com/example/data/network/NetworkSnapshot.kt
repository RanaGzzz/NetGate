package com.example.data.network

import com.example.data.model.ActiveNetworkType

/**
 * Link-layer information for one network, copied out of `LinkProperties`.
 *
 * `null` [NetworkInterfaceSnapshot.linkProperties] means the platform had no link properties for that
 * network (common for VPN placeholders); DNS lists are then empty rather than failing.
 */
data class LinkPropertiesSnapshot(
    val interfaceName: String? = null,
    val dnsServers: List<String> = emptyList(),
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val gatewayAddress: String? = null,
    val mtu: Int = 0,
    val httpProxyHost: String? = null,
    val httpProxyPort: Int = 0
) {
    val hasIpv6Dns: Boolean
        get() = dnsServers.any { it.contains(':') }

    val hasHttpProxy: Boolean
        get() = !httpProxyHost.isNullOrBlank()
}

/**
 * One network as seen by `ConnectivityManager`: capabilities plus optional link properties.
 *
 * @property handle stable identifier for this capture (network handle/string), used to match the
 *   active network.
 */
data class NetworkInterfaceSnapshot(
    val handle: String,
    val capabilities: NetworkCapabilitiesInfo? = null,
    val linkProperties: LinkPropertiesSnapshot? = null
) {
    val transport: NetworkTransport
        get() = capabilities?.primaryTransport ?: NetworkTransport.OTHER

    val interfaceName: String?
        get() = linkProperties?.interfaceName

    val dnsServers: List<String>
        get() = linkProperties?.dnsServers.orEmpty()

    val ipv4Addresses: List<String>
        get() = linkProperties?.ipv4Addresses.orEmpty()

    val ipv6Addresses: List<String>
        get() = linkProperties?.ipv6Addresses.orEmpty()

    /** Same rule the diagnostics used before: VPN transport or missing `NOT_VPN`. */
    val isVpn: Boolean
        get() = capabilities?.isVpn == true

    val hasInternetCapability: Boolean
        get() = capabilities?.hasInternet == true

    /** A physical (non-VPN) network that actually reaches the internet. */
    val isPhysicalWithInternet: Boolean
        get() = capabilities != null && !isVpn && hasInternetCapability

    val hasIpv6Dns: Boolean
        get() = linkProperties?.hasIpv6Dns == true

    val hasHttpProxy: Boolean
        get() = linkProperties?.hasHttpProxy == true
}

/**
 * Point-in-time view of every network Android currently knows about.
 *
 * A single capture is shared by the DNS connection checker, the DNS leak tester, the security audit
 * and the network monitor, so they agree on which interfaces are VPN vs physical and on the DNS
 * servers each one exposes. IPv4 and IPv6 DNS entries are preserved as reported.
 */
data class NetworkSnapshot(
    val interfaces: List<NetworkInterfaceSnapshot> = emptyList(),
    val activeNetworkHandle: String? = null,
    val capturedAtMs: Long = 0L
) {
    val vpnInterfaces: List<NetworkInterfaceSnapshot>
        get() = interfaces.filter { it.capabilities != null && it.isVpn }

    val physicalInterfaces: List<NetworkInterfaceSnapshot>
        get() = interfaces.filter { it.capabilities != null && !it.isVpn }

    val activeNetwork: NetworkInterfaceSnapshot?
        get() = activeNetworkHandle?.let { handle -> interfaces.firstOrNull { it.handle == handle } }

    /** DNS servers exposed inside VPN networks (TUN DNS), deduplicated, IPv4 and IPv6. */
    fun vpnDnsServers(): List<String> =
        vpnInterfaces.flatMap { it.dnsServers }.distinct()

    /** DNS servers exposed by physical networks that have internet capability. */
    fun physicalDnsServers(): List<String> =
        interfaces.filter { it.isPhysicalWithInternet }.flatMap { it.dnsServers }.distinct()

    val hasVpnIpv6Dns: Boolean
        get() = vpnInterfaces.any { it.hasIpv6Dns }

    val hasVpnHttpProxy: Boolean
        get() = vpnInterfaces.any { it.hasHttpProxy }

    val isVpnActive: Boolean
        get() = vpnInterfaces.isNotEmpty()

    val isOffline: Boolean
        get() = activePhysicalNetwork() == null

    /**
     * Physical network to report to the monitor/UI, using the previous preference order:
     * the active network when it is physical and online, otherwise Wi-Fi, then cellular, then any
     * other physical transport.
     */
    fun activePhysicalNetwork(): NetworkInterfaceSnapshot? {
        val physical = interfaces.filter { it.isPhysicalWithInternet }
        if (physical.isEmpty()) return null
        activeNetwork?.takeIf { it.isPhysicalWithInternet }?.let { return it }
        return physical.firstOrNull { it.transport == NetworkTransport.WIFI }
            ?: physical.firstOrNull { it.transport == NetworkTransport.CELLULAR }
            ?: physical.first()
    }

    /** Active network type exactly as the previous `findBestPhysicalNetwork` mapping produced. */
    fun activeNetworkType(): ActiveNetworkType = when (activePhysicalNetwork()?.transport) {
        NetworkTransport.WIFI -> ActiveNetworkType.WIFI
        NetworkTransport.CELLULAR -> ActiveNetworkType.MOBILE_DATA
        null -> ActiveNetworkType.NONE
        else -> ActiveNetworkType.OTHER
    }
}
