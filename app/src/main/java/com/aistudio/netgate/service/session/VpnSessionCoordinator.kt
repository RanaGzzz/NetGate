package com.aistudio.netgate.service.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the two VPN endpoints of this app as a single, serialized state machine.
 *
 * Android allows one active [android.net.VpnService] per user profile. NetGate has two endpoints that
 * both want that slot:
 *
 *  * the local per-app firewall ([com.aistudio.netgate.service.NetGateVpnService]), and
 *  * the WireGuard/WARP tunnel (WireGuard's own `GoBackend$VpnService`).
 *
 * Every ownership change goes through [transitionMutex], so transitions are serialized no matter
 * which thread/ViewModel/notification action triggers them. A replacement endpoint is only started
 * after the previous owner has *actually* stopped, and every wait is bounded by
 * [VpnTransitionTimeouts]. If a wait expires, the transition fails and nothing new is started.
 *
 * State machine
 * -------------
 * ```
 *                connectTunnel
 *  NONE/FIREWALL ───────────────► STOPPING_LOCAL_FIREWALL ──► STARTING_TUNNEL ──► ACTIVE_TUNNEL
 *        ▲                              │ failure                   │ failure           │
 *        │                              ▼                           ▼                   │
 *        │                            ERROR                       ERROR ◄───────────────┘
 *        │                                                        (restore firewall if
 *        │                                                         it had been paused)
 *        │
 *        │  disconnectTunnel
 *  ACTIVE_TUNNEL ───────────────► STOPPING_TUNNEL ──► NONE ──(restore requested)──► STARTING_LOCAL_FIREWALL
 *                                       │ failure             │                          │ failure
 *                                       ▼                     ▼                          ▼
 *                                     ERROR (slot kept,   NONE stays                ERROR (pending kept,
 *                                     firewall never       firewallRestorePending)   firewall stays down)
 *                                     restored)
 *
 *  ACTIVE_TUNNEL ──(WireGuard UP -> DOWN on its own)──► NONE + unexpectedDisconnect
 * ```
 *
 * Invariants
 * ----------
 * 1. At most one owner is recorded at any time, and a tunnel is only activated after the local
 *    firewall reported itself stopped (or was never running).
 * 2. The local firewall is only restored when the caller requested restoration *and* the firewall
 *    was previously stopped to free the slot ([VpnSessionState.firewallRestorePending]).
 * 3. A failure or timeout never starts a replacement endpoint; the session lands in
 *    [VpnTransitionPhase.ERROR] with a [VpnTransitionResult] describing what happened.
 *
 * This class is deliberately pure Kotlin: endpoints are supplied as [VpnSessionPorts] so the
 * transitions can be tested without Android or WireGuard.
 */
class VpnSessionCoordinator(
    private val timeouts: VpnTransitionTimeouts = VpnTransitionTimeouts()
) {

    private val transitionMutex = Mutex()

    private val _state = MutableStateFlow(VpnSessionState())
    val state: StateFlow<VpnSessionState> = _state.asStateFlow()

    /**
     * Claims the VPN slot for the local firewall.
     *
     * Returns false when a transition is in flight or when the tunnel owns the slot. The caller
     * ([com.aistudio.netgate.service.NetGateVpnService]) must stop itself in that case instead of calling
     * `establish()`, which would otherwise fail or silently displace the tunnel.
     */
    fun claimLocalFirewall(): Boolean {
        if (!transitionMutex.tryLock()) return false
        return try {
            when (_state.value.owner) {
                VpnSlotOwner.LOCAL_FIREWALL -> true
                VpnSlotOwner.TUNNEL -> false
                VpnSlotOwner.NONE -> {
                    update {
                        it.copy(
                            owner = VpnSlotOwner.LOCAL_FIREWALL,
                            phase = VpnTransitionPhase.IDLE,
                            transitionInFlight = false,
                            unexpectedDisconnect = false
                        )
                    }
                    true
                }
            }
        } finally {
            transitionMutex.unlock()
        }
    }

    /** Releases the slot if the local firewall owned it. Safe to call repeatedly. */
    fun releaseLocalFirewall() {
        if (!transitionMutex.tryLock()) return
        try {
            if (_state.value.owner == VpnSlotOwner.LOCAL_FIREWALL) {
                update {
                    it.copy(owner = VpnSlotOwner.NONE, phase = VpnTransitionPhase.IDLE)
                }
            }
        } finally {
            transitionMutex.unlock()
        }
    }

    /**
     * Brings the tunnel up, taking the slot away from the local firewall if needed.
     *
     * The tunnel activation lambda runs only after the firewall confirmed it stopped. If the firewall
     * does not stop within [VpnTransitionTimeouts.firewallStopTimeoutMs], the transition fails with
     * [VpnTransitionResult.Stage.LOCAL_FIREWALL_STOP] and the tunnel is **not** started.
     *
     * @param permissionGranted result of `VpnService.prepare(context) == null`.
     * @param ports live endpoints to drive.
     */
    suspend fun connectTunnel(
        ports: VpnSessionPorts,
        permissionGranted: Boolean
    ): VpnTransitionResult = transitionMutex.withLock {
        val current = _state.value

        if (current.owner == VpnSlotOwner.TUNNEL && ports.tunnelRunning()) {
            return@withLock stateAndReturn(
                current.copy(
                    phase = VpnTransitionPhase.ACTIVE_TUNNEL,
                    lastResult = VpnTransitionResult.Rejected(
                        VpnTransitionResult.RejectionReason.OWNERSHIP_CONFLICT,
                        "A VPN Guard tunnel is already active"
                    )
                )
            )
        }

        if (!permissionGranted) {
            return@withLock stateAndReturn(
                current.copy(
                    phase = VpnTransitionPhase.ERROR,
                    transitionInFlight = false,
                    lastResult = VpnTransitionResult.Rejected(
                        VpnTransitionResult.RejectionReason.VPN_PERMISSION_DENIED,
                        "Android VPN permission is required before connecting"
                    )
                )
            )
        }

        update {
            it.copy(
                phase = VpnTransitionPhase.STOPPING_LOCAL_FIREWALL,
                transitionInFlight = true,
                unexpectedDisconnect = false
            )
        }

        val firewallWasRunning = ports.firewallRunning()
        // Once the firewall has been stopped to free the slot it is "required" again on the way out.
        val restorePending = current.firewallRestorePending || firewallWasRunning

        if (firewallWasRunning) {
            ports.stopFirewall()
            val stopped = awaitCondition(timeouts.firewallStopTimeoutMs) { !ports.firewallRunning() }
            if (!stopped) {
                return@withLock stateAndReturn(
                    _state.value.copy(
                        owner = if (ports.firewallRunning()) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                        phase = VpnTransitionPhase.ERROR,
                        transitionInFlight = false,
                        firewallRestorePending = restorePending,
                        firewallWasRunningBeforeTunnel = true,
                        lastResult = VpnTransitionResult.Failed(
                            stage = VpnTransitionResult.Stage.LOCAL_FIREWALL_STOP,
                            message = "The local firewall did not stop within " +
                                "${timeouts.firewallStopTimeoutMs} ms; VPN Guard was not started",
                            timedOut = true
                        )
                    )
                )
            }
        }

        update {
            it.copy(
                owner = VpnSlotOwner.NONE,
                phase = VpnTransitionPhase.STARTING_TUNNEL,
                transitionInFlight = true,
                firewallRestorePending = restorePending,
                firewallWasRunningBeforeTunnel = it.firewallWasRunningBeforeTunnel || firewallWasRunning
            )
        }

        val activation = runCatching { ports.activateTunnel() }
        val failure = activation.exceptionOrNull()
        if (failure != null) {
            // Best effort: make sure a partially created tunnel does not keep the slot.
            runCatching { ports.deactivateTunnel() }
            val restored = restoreFirewallLocked(ports, required = restorePending)
            return@withLock stateAndReturn(
                _state.value.copy(
                    owner = if (restored) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                    phase = if (restored) VpnTransitionPhase.IDLE else VpnTransitionPhase.ERROR,
                    transitionInFlight = false,
                    firewallRestorePending = restorePending && !restored,
                    lastResult = VpnTransitionResult.Failed(
                        stage = VpnTransitionResult.Stage.TUNNEL_START,
                        message = failure.message ?: "Failed to bring up WireGuard tunnel",
                        cause = failure
                    )
                )
            )
        }

        stateAndReturn(
            _state.value.copy(
                owner = VpnSlotOwner.TUNNEL,
                phase = VpnTransitionPhase.ACTIVE_TUNNEL,
                transitionInFlight = false,
                unexpectedDisconnect = false,
                lastResult = VpnTransitionResult.Success(
                    from = if (firewallWasRunning) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                    to = VpnSlotOwner.TUNNEL
                )
            )
        )
    }

    /**
     * Tears the tunnel down and optionally restores the local firewall.
     *
     * The firewall is restored only when [restoreFirewallRequested] is true **and** it had been
     * stopped to free the slot earlier ([VpnSessionState.firewallRestorePending]). If the tunnel does
     * not report itself down within [VpnTransitionTimeouts.tunnelStopTimeoutMs], the firewall stays
     * down — starting it would mean two VPN endpoints at once.
     */
    suspend fun disconnectTunnel(
        ports: VpnSessionPorts,
        restoreFirewallRequested: Boolean = false
    ): VpnTransitionResult = transitionMutex.withLock {
        val current = _state.value
        val tunnelRunning = ports.tunnelRunning()

        if (current.owner != VpnSlotOwner.TUNNEL && !tunnelRunning) {
            val restored = if (restoreFirewallRequested && current.firewallRestorePending) {
                restoreFirewallLocked(ports, required = true)
            } else {
                false
            }
            return@withLock stateAndReturn(
                current.copy(
                    owner = if (restored) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                    phase = VpnTransitionPhase.IDLE,
                    transitionInFlight = false,
                    firewallRestorePending = current.firewallRestorePending && !restored,
                    lastResult = VpnTransitionResult.Success(
                        from = VpnSlotOwner.TUNNEL,
                        to = if (restored) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                        restoredFirewall = restored
                    )
                )
            )
        }

        update { it.copy(phase = VpnTransitionPhase.STOPPING_TUNNEL, transitionInFlight = true) }

        val teardown = runCatching { ports.deactivateTunnel() }
        val teardownError = teardown.exceptionOrNull()
        if (teardownError != null) {
            return@withLock stateAndReturn(
                _state.value.copy(
                    owner = VpnSlotOwner.TUNNEL,
                    phase = VpnTransitionPhase.ERROR,
                    transitionInFlight = false,
                    lastResult = VpnTransitionResult.Failed(
                        stage = VpnTransitionResult.Stage.TUNNEL_STOP,
                        message = teardownError.message ?: "Failed to stop WireGuard tunnel",
                        cause = teardownError
                    )
                )
            )
        }

        val stopped = awaitCondition(timeouts.tunnelStopTimeoutMs) { !ports.tunnelRunning() }
        if (!stopped) {
            return@withLock stateAndReturn(
                _state.value.copy(
                    owner = VpnSlotOwner.TUNNEL,
                    phase = VpnTransitionPhase.ERROR,
                    transitionInFlight = false,
                    lastResult = VpnTransitionResult.Failed(
                        stage = VpnTransitionResult.Stage.TUNNEL_STOP,
                        message = "The tunnel did not report itself down within " +
                            "${timeouts.tunnelStopTimeoutMs} ms",
                        timedOut = true
                    )
                )
            )
        }

        update {
            it.copy(
                owner = VpnSlotOwner.NONE,
                phase = VpnTransitionPhase.IDLE,
                transitionInFlight = false,
                unexpectedDisconnect = false
            )
        }

        val pending = _state.value.firewallRestorePending
        val shouldRestore = restoreFirewallRequested && pending
        val restored = if (shouldRestore) restoreFirewallLocked(ports, required = true) else false

        stateAndReturn(
            _state.value.copy(
                owner = if (restored) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                phase = VpnTransitionPhase.IDLE,
                transitionInFlight = false,
                firewallRestorePending = pending && !restored,
                lastResult = VpnTransitionResult.Success(
                    from = VpnSlotOwner.TUNNEL,
                    to = if (restored) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                    restoredFirewall = restored
                )
            )
        )
    }

    /**
     * Called when WireGuard reports the tunnel down without a coordinator request.
     *
     * Releases ownership and records the event; nothing is started automatically, and the firewall
     * stays paused (the user did not ask for it to come back). Restoration stays available through
     * [disconnectTunnel] with [disconnectTunnel]'s restore flag.
     */
    suspend fun handleUnexpectedTunnelDisconnect(
        message: String = "The VPN tunnel disconnected unexpectedly"
    ): VpnTransitionResult = transitionMutex.withLock {
        val current = _state.value
        if (current.owner != VpnSlotOwner.TUNNEL) {
            return@withLock current.lastResult
        }
        stateAndReturn(
            current.copy(
                owner = VpnSlotOwner.NONE,
                phase = VpnTransitionPhase.IDLE,
                transitionInFlight = false,
                unexpectedDisconnect = true,
                lastResult = VpnTransitionResult.UnexpectedDisconnect(message)
            )
        )
    }

    /**
     * Starts the local firewall if it had been paused for the tunnel or if the caller explicitly
     * needs it back. Returns false when the firewall is already running or could not be confirmed.
     */
    suspend fun restoreLocalFirewall(ports: VpnSessionPorts): Boolean = transitionMutex.withLock {
        if (_state.value.owner == VpnSlotOwner.TUNNEL || ports.tunnelRunning()) return@withLock false
        restoreFirewallLocked(ports, required = true)
    }

    /** Must be called with [transitionMutex] held. */
    private suspend fun restoreFirewallLocked(ports: VpnSessionPorts, required: Boolean): Boolean {
        if (!required) return false
        if (ports.firewallRunning()) {
            update {
                it.copy(
                    owner = VpnSlotOwner.LOCAL_FIREWALL,
                    firewallRestorePending = false,
                    firewallWasRunningBeforeTunnel = false
                )
            }
            return true
        }

        update { it.copy(phase = VpnTransitionPhase.STARTING_LOCAL_FIREWALL) }
        val start = runCatching { ports.startFirewall() }
        if (start.isFailure) {
            update { it.copy(phase = VpnTransitionPhase.ERROR) }
            return false
        }

        val ready = awaitCondition(timeouts.firewallStartTimeoutMs) { ports.firewallRunning() }
        update {
            it.copy(
                owner = if (ready) VpnSlotOwner.LOCAL_FIREWALL else VpnSlotOwner.NONE,
                phase = if (ready) VpnTransitionPhase.IDLE else VpnTransitionPhase.ERROR,
                firewallRestorePending = !ready,
                firewallWasRunningBeforeTunnel = !ready
            )
        }
        return ready
    }

    /** Bounded poll; returns false instead of waiting forever. */
    private suspend fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        if (condition()) return true
        return withTimeoutOrNull(timeoutMs) {
            while (!condition()) {
                delay(timeouts.pollIntervalMs)
            }
            true
        } ?: false
    }

    private fun update(transform: (VpnSessionState) -> VpnSessionState) {
        _state.value = transform(_state.value).copy(updatedAtMs = System.currentTimeMillis())
    }

    private fun stateAndReturn(next: VpnSessionState): VpnTransitionResult {
        _state.value = next.copy(updatedAtMs = System.currentTimeMillis())
        return next.lastResult
    }

    companion object {
        /** Process-wide coordinator used by the services and [com.aistudio.netgate.service.VpnGuardManager]. */
        val instance: VpnSessionCoordinator by lazy { VpnSessionCoordinator() }
    }
}

/**
 * Live endpoints the coordinator drives. Implementations are supplied by
 * [com.aistudio.netgate.service.VpnGuardManager] in production and by fakes in tests, so transitions can be
 * exercised without Android, WireGuard or a real TUN device.
 */
class VpnSessionPorts(
    /** True while [com.aistudio.netgate.service.NetGateVpnService] is running/holds the slot. */
    val firewallRunning: () -> Boolean,
    /** Asks the local firewall to stop; [firewallRunning] must eventually turn false. */
    val stopFirewall: () -> Unit,
    /** Asks the local firewall to start again. */
    val startFirewall: () -> Unit,
    /** True while the WireGuard tunnel is up. */
    val tunnelRunning: () -> Boolean,
    /** Brings the WireGuard tunnel up; throwing means the transition failed. */
    val activateTunnel: suspend () -> Unit,
    /** Tears the WireGuard tunnel down; throwing means shutdown failed. */
    val deactivateTunnel: suspend () -> Unit
)
