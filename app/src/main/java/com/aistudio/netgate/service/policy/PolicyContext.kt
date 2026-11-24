package com.aistudio.netgate.service.policy

/**
 * Execution context for evaluating a network request against [NetworkPolicyEngine].
 * Distinguishes local VPN firewall traffic from tethered hotspot client requests.
 */
data class PolicyContext(
    val source: PolicySource,
    val clientIp: String? = null,
    val appPackage: String? = null,
    val appUid: Int? = null,
    val clientBytesSent: Long = 0L,
    val clientBytesReceived: Long = 0L,
    val pathAndQuery: String? = null,
    val protocol: String = "TCP"
) {
    enum class PolicySource {
        /**
         * Device-local traffic intercepted via Android VpnService TUN interface.
         */
        FIREWALL_VPN,

        /**
         * Tethered Wi-Fi / USB hotspot client traffic intercepted via Hotspot Proxy server.
         */
        HOTSPOT_CLIENT
    }

    val isHotspot: Boolean get() = source == PolicySource.HOTSPOT_CLIENT
    val isFirewall: Boolean get() = source == PolicySource.FIREWALL_VPN

    companion object {
        fun forFirewall(
            appPackage: String? = null,
            appUid: Int? = null,
            pathAndQuery: String? = null,
            protocol: String = "TCP"
        ): PolicyContext = PolicyContext(
            source = PolicySource.FIREWALL_VPN,
            appPackage = appPackage,
            appUid = appUid,
            pathAndQuery = pathAndQuery,
            protocol = protocol
        )

        fun forHotspot(
            clientIp: String,
            clientBytesSent: Long = 0L,
            clientBytesReceived: Long = 0L,
            pathAndQuery: String? = null,
            protocol: String = "TCP"
        ): PolicyContext = PolicyContext(
            source = PolicySource.HOTSPOT_CLIENT,
            clientIp = clientIp,
            clientBytesSent = clientBytesSent,
            clientBytesReceived = clientBytesReceived,
            pathAndQuery = pathAndQuery,
            protocol = protocol
        )

        fun forHotspotClient(
            clientIp: String,
            clientBytesSent: Long = 0L,
            clientBytesReceived: Long = 0L,
            pathAndQuery: String? = null,
            protocol: String = "TCP"
        ): PolicyContext = forHotspot(
            clientIp = clientIp,
            clientBytesSent = clientBytesSent,
            clientBytesReceived = clientBytesReceived,
            pathAndQuery = pathAndQuery,
            protocol = protocol
        )
    }
}
