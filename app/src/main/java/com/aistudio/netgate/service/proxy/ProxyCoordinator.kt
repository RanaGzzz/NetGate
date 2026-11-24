package com.aistudio.netgate.service.proxy

import com.aistudio.netgate.service.LocalFilteringProxyServer

/**
 * Result of asking [ProxyCoordinator] to start a proxy listener.
 */
sealed class ProxyStartResult {

    /**
     * The listener is running.
     *
     * @property boundPort actual port the server bound (may differ from the configured port).
     * @property requestedPort port the caller configured.
     * @property usedEphemeralFallback true when the configured port could not be used and the server
     *   fell back to an ephemeral loopback port (preserved local-firewall behavior).
     */
    data class Started(
        val role: ProxyRole,
        val boundPort: Int,
        val requestedPort: Int,
        val usedEphemeralFallback: Boolean
    ) : ProxyStartResult()

    /** A listener for this role is already running with the same configuration. */
    data class AlreadyRunning(
        val role: ProxyRole,
        val boundPort: Int
    ) : ProxyStartResult()

    /** Another NetGate proxy owns the configured port; nothing was started. */
    data class PortConflict(
        val role: ProxyRole,
        val port: Int,
        val owner: ProxyRole,
        val message: String
    ) : ProxyStartResult()

    /** The bind failed (occupied by another app, permissions, ...). Nothing is running. */
    data class Failure(
        val role: ProxyRole,
        val port: Int,
        val message: String
    ) : ProxyStartResult()

    val isRunning: Boolean
        get() = this is Started || this is AlreadyRunning
}

/**
 * Owns the lifecycle of NetGate's two proxy listeners.
 *
 * * Keeps exactly one instance per [ProxyRole], so the local firewall proxy and the hotspot proxy are
 *   independent and can run at the same time.
 * * Centralizes port validation ([ProxyConfiguration]) and in-process conflict prevention
 *   ([ProxyPortRegistry]).
 * * Preserves the previous behavior on a failed local bind: the local firewall proxy falls back to an
 *   ephemeral loopback port, while the hotspot proxy reports the failure instead of silently moving
 *   to another port.
 * * Always stops and releases the old listener before creating a replacement, and tears down sockets,
 *   worker threads and executors inside [LocalFilteringProxyServer.stop].
 *
 * The proxy implementation itself is untouched: callers pass a factory that builds a
 * [LocalFilteringProxyServer] with their policy/DPI/telemetry callbacks.
 */
class ProxyCoordinator(
    private val registry: ProxyPortRegistry = ProxyPortRegistry()
) {

    private data class ActiveProxy(
        val configuration: ProxyConfiguration,
        val boundPort: Int,
        val server: LocalFilteringProxyServer
    )

    private val lock = Any()
    private val active = LinkedHashMap<ProxyRole, ActiveProxy>()

    /**
     * Starts (or restarts) the listener for [configuration]'s role.
     *
     * @param createServer builds the reusable proxy server for the effective bind port. The port may
     *   be 0, which means "pick an ephemeral port" for the local firewall fallback path.
     */
    fun start(
        configuration: ProxyConfiguration,
        createServer: (bindPort: Int, configuration: ProxyConfiguration) -> LocalFilteringProxyServer
    ): ProxyStartResult = synchronized(lock) {
        val role = configuration.role
        val requestedPort = configuration.validatedPort
        val normalized = configuration.copy(port = requestedPort)

        active[role]?.let { existing ->
            if (existing.server.isRunning && existing.configuration == normalized) {
                return ProxyStartResult.AlreadyRunning(role, existing.boundPort)
            }
            stopLocked(role)
        }

        val otherRole = registry.claimedByOther(role, requestedPort)
        val bindPort = when {
            otherRole == null -> requestedPort
            role == ProxyRole.LOCAL_FIREWALL -> 0 // preserved ephemeral loopback fallback
            else -> return ProxyStartResult.PortConflict(
                role = role,
                port = requestedPort,
                owner = otherRole,
                message = "Port $requestedPort is already used by the ${otherRole.label} proxy. " +
                    "Choose another port (e.g. 8890 or 8080)."
            )
        }

        val server = createServer(bindPort, normalized)
        val started = runCatching { server.start() }.getOrDefault(false)
        if (!started) {
            runCatching { server.stop() }
            return ProxyStartResult.Failure(
                role = role,
                port = requestedPort,
                message = "Could not bind ${role.label} proxy on port $requestedPort. " +
                    "Try another port (e.g. 8890 or 8080)."
            )
        }

        val boundPort = server.boundPort
        val conflictAfterBind = registry.claimedByOther(role, boundPort)
        if (conflictAfterBind != null) {
            runCatching { server.stop() }
            return ProxyStartResult.PortConflict(
                role = role,
                port = boundPort,
                owner = conflictAfterBind,
                message = "Port $boundPort is already used by the ${conflictAfterBind.label} proxy. " +
                    "Choose another port (e.g. 8890 or 8080)."
            )
        }

        registry.claim(role, boundPort)
        active[role] = ActiveProxy(
            // Keep the requested configuration so a repeated start with the same preferences is a
            // no-op even after an ephemeral fallback (no stop/rebind churn on every rebuild).
            configuration = normalized,
            boundPort = boundPort,
            server = server
        )
        ProxyStartResult.Started(
            role = role,
            boundPort = boundPort,
            requestedPort = requestedPort,
            usedEphemeralFallback = boundPort != requestedPort
        )
    }

    /** Stops the listener for [role] and releases its port. Safe to call when nothing is running. */
    fun stop(role: ProxyRole) {
        synchronized(lock) { stopLocked(role) }
    }

    /** Stops every NetGate proxy listener (e.g. on service shutdown). */
    fun stopAll() {
        synchronized(lock) {
            ProxyRole.values().forEach { role -> stopLocked(role) }
        }
    }

    fun isActive(role: ProxyRole): Boolean = synchronized(lock) {
        active[role]?.server?.isRunning == true
    }

    /** Actual bound port of the running listener for [role], or null when it is not running. */
    fun boundPort(role: ProxyRole): Int? = synchronized(lock) {
        active[role]?.takeIf { it.server.isRunning }?.boundPort
    }

    /** Configuration the listener was requested with (its port may differ from [boundPort]). */
    fun activeConfiguration(role: ProxyRole): ProxyConfiguration? = synchronized(lock) {
        active[role]?.configuration
    }

    fun activePorts(): Map<ProxyRole, Int> = synchronized(lock) {
        active.entries
            .filter { it.value.server.isRunning }
            .associate { it.key to it.value.boundPort }
    }

    private fun stopLocked(role: ProxyRole) {
        val existing = active.remove(role)
        registry.release(role)
        if (existing != null) {
            runCatching { existing.server.stop() }
        }
    }

    companion object {
        /** Process-wide coordinator used by the firewall service and the hotspot manager. */
        val instance: ProxyCoordinator by lazy { ProxyCoordinator() }
    }
}
