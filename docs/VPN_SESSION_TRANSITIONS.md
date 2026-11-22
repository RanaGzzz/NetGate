# VPN Session Transitions (NetGate)

Android allows **one active `VpnService` per user profile**. NetGate has two endpoints that both
need that slot, so ownership is now arbitrated by a single component instead of ad-hoc code in
`VpnGuardManager`.

| Endpoint | Implementation | Foreground service |
| --- | --- | --- |
| Local per-app firewall | `NetGateVpnService` (`android.net.VpnService`, `specialUse`, `BIND_VPN_SERVICE`) | itself (notification `4041`, channel `netgate_firewall_channel`) |
| WireGuard / WARP tunnel | WireGuard `GoBackend$VpnService` (library-declared, merged into the manifest) | `VpnGuardService` (notification `1094`, channel `netgate_vpn_guard_channel`) |

The GoBackend TUN implementation, routing modes, split-tunnel package sets and both foreground
services are unchanged. Only *who may own the slot, and when*, moved into
`com.example.service.session.VpnSessionCoordinator`.

## Components

* `VpnSessionCoordinator.kt` — the single owner of the state machine. All transitions run under one
  `Mutex`, every wait is a bounded poll (`withTimeoutOrNull` + `pollIntervalMs`), and no replacement
  endpoint is started until the previous one has confirmed it stopped.
* `VpnSessionState.kt` — `VpnSlotOwner`, `VpnTransitionPhase`, `VpnSessionState`,
  `VpnTransitionTimeouts`.
* `VpnTransitionResult.kt` — `Success` / `Rejected` / `Failed` / `UnexpectedDisconnect`, with the
  stage that failed and whether the failure was a timeout.
* `VpnSessionPorts` — the two endpoints as callbacks, so transitions are unit-testable without
  Android or WireGuard. Production wiring lives in `VpnGuardManager.sessionPorts(...)`.

## Owner model

`VpnSlotOwner` is exclusive:

| Owner | Meaning |
| --- | --- |
| `NONE` | No NetGate VPN endpoint holds the platform slot. |
| `LOCAL_FIREWALL` | `NetGateVpnService` holds it. |
| `TUNNEL` | WireGuard's `GoBackend$VpnService` holds it. |

`VpnSessionState.firewallRestorePending` records that the firewall was running and had to be stopped
to free the slot. It is the only gate for restoring the firewall later.

## State machine

```
                       connectTunnel(permission granted)
 NONE / LOCAL_FIREWALL ──► STOPPING_LOCAL_FIREWALL ──► STARTING_TUNNEL ──► ACTIVE_TUNNEL
                                  │ firewall not stopped      │ activation throws
                                  ▼                           ▼
                                ERROR                       ERROR ──► firewall restored if it
                            (tunnel NOT started)                        had been paused
                                                                            │
 ACTIVE_TUNNEL ──► STOPPING_TUNNEL ──► NONE ──(restore requested)──► STARTING_LOCAL_FIREWALL
                        │ teardown throws      │ restore not requested        │ start not confirmed
                        ▼                      ▼                              ▼
                      ERROR                  NONE (firewall                    ERROR (firewall
                 (slot kept = TUNNEL,         stays paused,                     stays down,
                  firewall never started)     pending kept)                     pending kept)

 ACTIVE_TUNNEL ──(WireGuard reports DOWN by itself)──► NONE + unexpectedDisconnect
```

### Transition rules

1. **Exclusivity.** The tunnel is activated only after `firewallRunning()` turns false, or when the
   firewall was never running. `NetGateVpnService` asks `claimLocalFirewall()` before it starts
   establishing; the claim is refused while the tunnel owns the slot or a transition is in flight, and
   the service then stops itself (it still calls `startForeground` first so an FGS start never
   crashes).
2. **Serialization.** `connectTunnel`, `disconnectTunnel`, `restoreLocalFirewall`,
   `handleUnexpectedTunnelDisconnect` and `claim/releaseLocalFirewall` all contend for one `Mutex`.
   Concurrent connect/connect and connect/disconnect calls therefore cannot overlap activation and
   teardown.
3. **Bounded waiting.** Firewall stop ≤ `firewallStopTimeoutMs` (2 500 ms), firewall start ≤
   `firewallStartTimeoutMs` (2 500 ms), tunnel teardown confirmation ≤ `tunnelStopTimeoutMs`
   (3 000 ms), polled every 50 ms. This replaces the old `repeat(30) { if (!running) return@repeat;
   delay(50) }` loop, which always slept 1.5 s and then activated the tunnel regardless.
4. **No replacement after a timeout.** `Failed(stage = LOCAL_FIREWALL_STOP, timedOut = true)` leaves
   the tunnel down. `Failed(stage = TUNNEL_STOP, …)` leaves the firewall down and ownership with the
   tunnel, because starting the firewall while the tunnel may still be alive would be a second VPN.
5. **Restoration policy.** The firewall is restored only when the caller requested it
   (`restoreFirewallIfPaused = true`) **and** `firewallRestorePending` is set — i.e. it had actually
   been paused for the tunnel. Restoration happens after the tunnel confirmed it is down.
6. **Failure handling.**
   * VPN permission denied → `Rejected(VPN_PERMISSION_DENIED)`, no endpoint touched.
   * Tunnel already active → `Rejected(OWNERSHIP_CONFLICT)`, existing tunnel untouched.
   * Tunnel activation failure → best-effort teardown, paused firewall restored, `ERROR` state and
     `Failed(TUNNEL_START)` (the manager maps it to the existing friendly WireGuard messages).
   * Tunnel shutdown failure or timeout → slot kept by the tunnel, firewall not restored, `ERROR`.
   * Unexpected disconnect → `handleUnexpectedTunnelDisconnect()` releases ownership, stops telemetry
     and `VpnGuardService`, keeps `firewallRestorePending`; nothing restarts automatically.

## Integration points

* `VpnGuardManager.connect` — validates routing mode/package set (unchanged), builds the WireGuard
  config (unchanged), then `coordinator.connectTunnel(...)`; on success starts `VpnGuardService` and
  telemetry exactly where it did before. `pausedLocalFirewallForVpn` is now sourced from
  `coordinator.state.firewallRestorePending`.
* `VpnGuardManager.disconnect` — `coordinator.disconnectTunnel(ports, restoreFirewallIfPaused)`, then
  the existing state/notification teardown. A shutdown failure surfaces as `ERROR` with the failure
  message instead of a silent `DISCONNECTED`.
* `VpnGuardManager.activeTunnel.onStateChange` — an unexpected `DOWN` while connected triggers the
  coordinator's unexpected-disconnect path; a `DOWN` caused by our own teardown is flagged
  `intentionalDisconnect` and ignored by that path.
* `NetGateVpnService.onStartCommand` — `claimLocalFirewall()` gate for `ACTION_START` and
  `ACTION_RELOAD_RULES`; `stopFirewall()`/`onDestroy()` release the claim.
* Manifest unchanged: `NetGateVpnService` keeps `BIND_VPN_SERVICE` + `specialUse`; `VpnGuardService`
  keeps its `specialUse` declaration; GoBackend's `VpnService` stays library-declared and merged.

## Test results

`app/src/test/java/com/example/service/session/VpnSessionCoordinatorTest.kt` — 17 tests, JVM only,
virtual time (`runTest`), no Robolectric/Android:

| Area | Tests |
| --- | --- |
| Ordering | firewall stops before activation; activation completes before teardown |
| Bounded waiting / `repeat(30)` fix | firewall never stops → failure at exactly 2 500 ms, zero activations; tunnel never reports down → failure at exactly 3 000 ms |
| Serialization / concurrency | concurrent connects → one activation; connect vs disconnect never overlap; claim refused mid-transition |
| Exclusive ownership | second connect rejected; claim refused while tunnel owns the slot; claim/release lifecycle |
| Restoration policy | restore only when requested and previously paused; no restore for a firewall that was never paused; `restoreLocalFirewall` refuses while tunnel is up |
| Failure handling | permission denial; tunnel start failure with firewall restore; shutdown failure keeps slot and never restores; unexpected disconnect |

Command:

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Result: **BUILD SUCCESSFUL — 135 tests, 0 failures, 0 errors, 0 skipped** (118 before this change,
+17 new), `app-debug.apk` assembled.

Limitations: these are JVM unit tests with fake endpoints. The real hand-off between
`NetGateVpnService` and WireGuard's `GoBackend$VpnService` (actual `establish()`/`setState` timing on
a device) was not exercised here and still needs an on-device check with both endpoints.
