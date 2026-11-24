package com.aistudio.netgate.service.session

/**
 * Result of a VPN slot transition performed by [VpnSessionCoordinator].
 *
 * A [Failed] transition always leaves the session in a state that is safe to display: the slot is
 * either still owned by the previous endpoint or explicitly unowned, and the local firewall is never
 * brought up while the tunnel might still be alive.
 */
sealed class VpnTransitionResult {

    /** No transition has run yet. */
    data object Idle : VpnTransitionResult()

    /**
     * The transition completed and the slot changed ownership.
     *
     * @property restoredFirewall true when the local firewall was started as part of this result.
     */
    data class Success(
        val from: VpnSlotOwner,
        val to: VpnSlotOwner,
        val restoredFirewall: Boolean = false
    ) : VpnTransitionResult()

    /**
     * The transition was refused before any endpoint was touched.
     *
     * @property reason why it was refused.
     */
    data class Rejected(
        val reason: RejectionReason,
        val message: String
    ) : VpnTransitionResult()

    /**
     * The transition started but did not complete.
     *
     * @property stage where it stopped.
     * @property timedOut true when a bounded wait expired; the replacement endpoint was not started.
     * @property cause original throwable, when the failure came from an endpoint.
     */
    data class Failed(
        val stage: Stage,
        val message: String,
        val timedOut: Boolean = false,
        val cause: Throwable? = null
    ) : VpnTransitionResult()

    /**
     * The tunnel dropped on its own (WireGuard reported DOWN while the tunnel owned the slot).
     * The coordinator has released its ownership; no endpoint is started automatically.
     */
    data class UnexpectedDisconnect(
        val message: String
    ) : VpnTransitionResult()

    enum class RejectionReason {
        /** Android VPN consent was not granted (`VpnService.prepare` returned an Intent). */
        VPN_PERMISSION_DENIED,

        /** Another NetGate endpoint already owns the slot. */
        OWNERSHIP_CONFLICT
    }

    enum class Stage {
        LOCAL_FIREWALL_STOP,
        TUNNEL_START,
        TUNNEL_STOP,
        LOCAL_FIREWALL_START
    }

    val isFailure: Boolean
        get() = this is Failed || this is Rejected

    /** Human-readable description used for logs and UI error messages. */
    fun describe(): String = when (this) {
        is Idle -> "idle"
        is Success -> "ok: ${from.name} -> ${to.name}" + if (restoredFirewall) " (firewall restored)" else ""
        is Rejected -> "rejected: $message"
        is Failed -> "failed at ${stage.name}: $message" + if (timedOut) " (timed out)" else ""
        is UnexpectedDisconnect -> "unexpected disconnect: $message"
    }
}
