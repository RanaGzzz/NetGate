package com.example.service.session

/**
 * The component that currently owns Android's single VpnService slot inside NetGate.
 *
 * Android permits exactly one active [android.net.VpnService] per user profile, so this owner is
 * exclusive: it is never [VpnSlotOwner.NONE] while more than one of NetGate's own VPN endpoints is
 * up, and the tunnel is never started while [VpnSlotOwner.LOCAL_FIREWALL] is still active.
 */
enum class VpnSlotOwner {
    /** No NetGate VPN endpoint holds the platform VPN slot. */
    NONE,

    /** [com.example.service.NetGateVpnService] (the local per-app firewall) holds the slot. */
    LOCAL_FIREWALL,

    /** The WireGuard/GoBackend tunnel holds the slot (its own VpnService). */
    TUNNEL
}

/**
 * Fine-grained phase of the session state machine. [VpnSlotOwner] says *who* owns the slot, the
 * phase says *what the coordinator is doing* at this instant.
 */
enum class VpnTransitionPhase {
    IDLE,
    STOPPING_LOCAL_FIREWALL,
    STARTING_TUNNEL,
    ACTIVE_TUNNEL,
    STOPPING_TUNNEL,
    STARTING_LOCAL_FIREWALL,
    ERROR
}

/**
 * Observable session snapshot owned by [VpnSessionCoordinator].
 *
 * @property owner current exclusive owner of the platform VPN slot.
 * @property phase step of the transition that is running (or the last one that ran).
 * @property transitionInFlight true while the coordinator mutex is held by a transition.
 * @property firewallRestorePending the local firewall was running and had to be stopped to free the
 *   VPN slot, so it is allowed to be restored later. Restoration only happens for an explicit
 *   request (see [VpnSessionCoordinator.disconnectTunnel]).
 * @property firewallWasRunningBeforeTunnel sticky record of the firewall state observed before the
 *   tunnel took the slot, used to describe the paused state in the UI.
 * @property unexpectedDisconnect true when the tunnel went down without a coordinator request.
 * @property lastResult outcome of the most recent transition.
 */
data class VpnSessionState(
    val owner: VpnSlotOwner = VpnSlotOwner.NONE,
    val phase: VpnTransitionPhase = VpnTransitionPhase.IDLE,
    val transitionInFlight: Boolean = false,
    val firewallRestorePending: Boolean = false,
    val firewallWasRunningBeforeTunnel: Boolean = false,
    val unexpectedDisconnect: Boolean = false,
    val lastResult: VpnTransitionResult = VpnTransitionResult.Idle,
    val updatedAtMs: Long = 0L
) {
    val isTunnelOwned: Boolean
        get() = owner == VpnSlotOwner.TUNNEL

    val isLocalFirewallOwned: Boolean
        get() = owner == VpnSlotOwner.LOCAL_FIREWALL
}

/**
 * Bounded waits used while a VPN endpoint gives up (or takes) the platform VPN slot.
 *
 * Every value is a hard upper bound: the coordinator gives up and reports a timeout instead of
 * polling forever, and it never starts a replacement endpoint after a timeout.
 */
data class VpnTransitionTimeouts(
    /** Maximum time to wait for [NetGateVpnService] to release the slot after it is stopped. */
    val firewallStopTimeoutMs: Long = 2_500L,

    /** Maximum time to wait for the local firewall to report itself running again. */
    val firewallStartTimeoutMs: Long = 2_500L,

    /** Maximum time to wait for the tunnel to report itself down after teardown. */
    val tunnelStopTimeoutMs: Long = 3_000L,

    /** Poll interval for the waits above. */
    val pollIntervalMs: Long = 50L
) {
    init {
        require(firewallStopTimeoutMs > 0 && firewallStartTimeoutMs > 0 && tunnelStopTimeoutMs > 0) {
            "VPN transition timeouts must be positive"
        }
        require(pollIntervalMs > 0) { "pollIntervalMs must be positive" }
    }
}
