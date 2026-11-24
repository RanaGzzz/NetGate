package com.aistudio.netgate.service.proxy

/**
 * The two independent proxies NetGate runs at the same time.
 *
 * They share [com.aistudio.netgate.service.LocalFilteringProxyServer] as the implementation but never share a
 * socket, a port or a lifecycle: the local firewall proxy is loopback-only, the hotspot proxy listens
 * on the tethering interfaces.
 */
enum class ProxyRole(val label: String) {
    /** HTTPS/SNI/DPI proxy used by the local firewall TUN, strictly bound to loopback. */
    LOCAL_FIREWALL("local firewall"),

    /** Proxy shared with hotspot/LAN clients, bound to the hotspot-facing interfaces. */
    HOTSPOT("hotspot")
}

/**
 * Validated description of one proxy listener.
 *
 * Port validation lives here so every caller (preferences, UI, coordinator) agrees on the same
 * range. Values coming from preferences are clamped exactly as before ([MIN_PORT]..[MAX_PORT]);
 * callers can keep passing raw preference values.
 */
data class ProxyConfiguration(
    val role: ProxyRole,
    val port: Int,
    val bindHost: String,
    val stripTrackingParams: Boolean
) {
    init {
        require(bindHost.isNotBlank()) { "Proxy bind host must not be blank" }
    }

    /** Port after centralized validation/clamping. */
    val validatedPort: Int
        get() = sanitizePort(port)

    fun withPort(newPort: Int): ProxyConfiguration = copy(port = sanitizePort(newPort))

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535

        /** Loopback binding for the local firewall proxy (never exposed to the network). */
        const val LOOPBACK_HOST = "127.0.0.1"

        /** Hotspot-facing binding: all interfaces, as before this refactor. */
        const val HOTSPOT_BIND_HOST = "0.0.0.0"

        fun sanitizePort(port: Int): Int = port.coerceIn(MIN_PORT, MAX_PORT)

        fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

        /** Local firewall proxy: always loopback, port clamped to the valid range. */
        fun localFirewall(
            port: Int,
            stripTrackingParams: Boolean
        ): ProxyConfiguration = ProxyConfiguration(
            role = ProxyRole.LOCAL_FIREWALL,
            port = sanitizePort(port),
            bindHost = LOOPBACK_HOST,
            stripTrackingParams = stripTrackingParams
        )

        /** Hotspot proxy: bound to all interfaces, port clamped to the valid range. */
        fun hotspot(
            port: Int,
            stripTrackingParams: Boolean
        ): ProxyConfiguration = ProxyConfiguration(
            role = ProxyRole.HOTSPOT,
            port = sanitizePort(port),
            bindHost = HOTSPOT_BIND_HOST,
            stripTrackingParams = stripTrackingParams
        )
    }
}
