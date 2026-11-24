package com.aistudio.netgate.service.proxy

import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Tracks which ports are currently held by NetGate's own proxy listeners.
 *
 * This is the in-process half of port conflict prevention: the OS already refuses a second bind on a
 * port that is actively listened on, but by then the caller has lost the reason and (for the hotspot)
 * the user sees a generic bind failure. The registry lets the coordinator decide up front:
 *
 *  * the local firewall proxy falls back to an ephemeral loopback port (existing behavior), and
 *  * the hotspot proxy reports a clear conflict without touching the socket.
 *
 * Ports held by other apps are still detected by the real bind in
 * [com.aistudio.netgate.service.LocalFilteringProxyServer.start], which preserves the previous fallback rules.
 */
class ProxyPortRegistry {

    private val lock = Any()
    private val claims = LinkedHashMap<ProxyRole, Int>()

    /**
     * Claims [port] for [role]. Returns the role that already owns the port, or null when the claim
     * succeeded. A role re-claiming its own port is allowed and is a no-op.
     */
    fun claim(role: ProxyRole, port: Int): ProxyRole? = synchronized(lock) {
        val owner = claims.entries.firstOrNull { it.value == port }?.key
        if (owner != null && owner != role) return owner
        claims[role] = port
        null
    }

    /** Returns the role that holds [port], ignoring [role]'s own claim. */
    fun claimedByOther(role: ProxyRole, port: Int): ProxyRole? = synchronized(lock) {
        claims.entries.firstOrNull { it.value == port && it.key != role }?.key
    }

    fun release(role: ProxyRole) {
        synchronized(lock) { claims.remove(role) }
    }

    fun activePort(role: ProxyRole): Int? = synchronized(lock) { claims[role] }

    fun snapshot(): Map<ProxyRole, Int> = synchronized(lock) { claims.toMap() }

    companion object {
        /**
         * Probes whether [bindHost]:[port] can be bound right now. Diagnostic only — real listeners
         * still rely on the bind inside the proxy server, so no race can make this authoritative.
         */
        fun isPortAvailable(bindHost: String, port: Int): Boolean {
            if (port <= 0) return false
            return runCatching {
                ServerSocket().use { socket ->
                    socket.reuseAddress = false
                    socket.bind(InetSocketAddress(bindHost, port), 1)
                }
                true
            }.getOrDefault(false)
        }
    }
}
