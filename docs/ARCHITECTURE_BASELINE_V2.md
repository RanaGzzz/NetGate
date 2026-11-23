# NetGate — Architecture Baseline, Dependency Map & Regression Checklist (v2)

**Audit type:** read-only static baseline (no source, schema, UI or build behaviour changed)
**Repository:** `RanaGzzz/NetGate` @ `369dafa` (*refactor: centralize hotspot and DNS policy logic*)
**Branch:** `mods/netgate-custom` (local only — `main` untouched)
**Audit date:** 22 November 2026
**Supersedes:** `docs/ARCHITECTURE_BASELINE.md` (several claims have drifted — see §9)

---

## 0. Scope, method and limits

| Item | Detail |
| :--- | :--- |
| Kotlin sources inspected | **91 files / 41,512 lines** (79 `main`, 11 `test`, 1 `androidTest`) |
| Build files | `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` |
| Manifest | `app/src/main/AndroidManifest.xml` (4 declared services, 1 activity, 1 receiver, 1 provider) |
| Data layer | 8 `@Entity` classes, 7 `@Dao` interfaces, `NetGateDatabase` v8, 7 migrations |
| Preferences | 51 DataStore keys + 5 SharedPreferences keys |
| Tests | 11 unit test classes (**101** `@Test` methods) + 1 instrumented placeholder |
| Files modified by the audit | **None.** This document is the only addition |

**Verification limits:** this is a *static* reading of source. Build/dependency facts come from the Gradle files. See §12 for the toolchain now added to the project and what remains unverified.

---

## 1. What the app is, in one paragraph

NetGate is a root-free Android network-control utility built on a single local `VpnService`. It multiplexes five jobs through one TUN interface: per-app Wi-Fi/mobile-data blocking (kernel sinkhole), DNS interception with ad/website/SafeSearch filtering and DoH/DoTCP upstreams, a loopback HTTP/HTTPS filtering proxy with TLS-SNI inspection and DPI-bypass packet reshaping, a separate hotspot relay proxy for tethered clients with per-IP caps, and live traffic/telemetry logging. A second, independent `VpnService` path (`VpnGuardService` + WireGuard `GoBackend`) provides user-supplied WireGuard/WARP tunnels and must arbitrate with the firewall for Android's single VPN slot.

---

## 2. Layer map (verified file inventory)

```
app/src/main/java/com/example/
├── MainActivity.kt                    225   Compose host, VPN consent, notification perms, splash
├── ui/                                      29 files — Compose UI + single ViewModel
│   ├── FirewallScreen.kt            1,249   Root scaffold + nav destinations
│   ├── FirewallViewModel.kt         2,230   ★ God-object: all state, all actions
│   ├── WebsiteBlockerScreen.kt      1,982   5-tab blocker surface
│   ├── DataUsageScreen.kt           1,108   Monitor / sites / regions / audit
│   ├── components/ (20 files)       ~10.7k  Sheets, cards, charts, glass, splash, nav bar
│   └── theme/ (4 files)               901   Theme, colors, type, BlurEngine (RenderEffect)
├── data/                                    22 files
│   ├── local/ (17 files)                     Room entities/DAOs, database, DataStore prefs
│   ├── model/FirewallModels.kt      1,187   All enums + UI/runtime models + formatters
│   ├── network/ (3 files)           1,400   DnsConnectionChecker, DnsLeakTester, SpeedTestRunner
│   └── repository/FirewallRepository.kt 975 Single repository (Room + prefs + platform APIs)
└── service/                                 28 files — runtime engines & Android components
    ├── NetGateVpnService.kt         2,071   ★ TUN lifecycle, DNS loop, proxy host, notifications
    ├── LocalFilteringProxyServer.kt   941   HTTP/HTTPS CONNECT proxy, SNI, DPI, tracking strip
    ├── DnsPacketFilter.kt             834   DNS/IP/TCP/UDP packet constructors & parsers
    ├── AdBlockFilterEngine.kt         778   Hosts/Adblock parsing, matcher snapshot, DoH client
    ├── SecurityAuditEngine.kt         601   /proc socket scan, accessibility/admin/ADB audit
    ├── HotspotProxyManager.kt         419   Hotspot relay state, per-client stats/caps
    ├── SystemNetworkMonitor.kt        410   TrafficStats sampling, daily totals persistence
    ├── WifiAutomationManager.kt       331   Idle state machine, Wi-Fi timer alarms/notification
    ├── DpiBypassEngine.kt             315   ClientHello split/chunk/desync + autopilot cache
    ├── VpnGuardManager.kt             297   WireGuard GoBackend lifecycle + stats polling
    ├── ConnectionMonitorHub.kt        275   Live connection deque + Room merge/risk tagging
    ├── NetworkMonitorService.kt       260   FGS status-bar speed icon
    ├── WireGuardConfigHelper.kt       254   Config build/split-tunnel/endpoint rewrite
    ├── HotspotProxyService.kt         237   FGS for hotspot relay
    ├── GeoIpResolver.kt               232   Offline heuristic + online enrichment
    ├── VpnGuardService.kt             213   FGS for external VPN
    ├── PerAppFirewallEngine.kt        210   Pure tunnel-plan + blocked-packet classification
    ├── WarpConfigGenerator.kt         190   WARP registration → WireGuard profile
    ├── VpnGateClient.kt               170   Public VPN Gate server list (OkHttp)
    ├── StatusBarIconRenderer.kt       146   Bitmap speed icon rendering
    ├── TunPacketInspector.kt          115   IPv4/IPv6 header parse
    ├── AppTimerScheduler.kt            84   AlarmManager scheduling
    ├── AppTimerReceiver.kt             47   Timer broadcast entry
    └── policy/ (5 files)              520   NetworkPolicy, Engine, Context, Decision, Normalizer
```

### Dependency direction (enforced only by convention)

```
UI (Compose + ViewModel)
   │  reads Flow<UiState>, calls intent-style functions
   ▼
FirewallRepository ──► Room DAOs + FirewallPreferences (DataStore)
   │  some direct platform calls (PackageManager, NetworkStats, Connectivity, /proc)
   ▼
Engines (pure-ish objects)  ◄── also called DIRECTLY by UI/ViewModel and by services
   │
   ▼
Android components: NetGateVpnService / VpnGuardService / HotspotProxyService /
                    NetworkMonitorService / AppTimerReceiver
```

**There is no DI container, no module boundaries and no service interfaces.** Every engine is a Kotlin `object` or a `class` constructed at the call site, and services reach straight into `NetGateDatabase.getInstance()` and `FirewallPreferences(context)`.

---

## 3. Android entry points (manifest-verified)

| Component | Type | Declared | Entry conditions |
| :--- | :--- | :--- | :--- |
| `MainActivity` | launcher activity | `exported=true`; `onNewIntent` handled | LAUNCHER intent; notification taps pass `extra_destination` (`AppDestination` name) |
| `NetGateVpnService` | `VpnService` FGS | `BIND_VPN_SERVICE`, `foregroundServiceType=specialUse`, subtype "Local per-app Wi-Fi and mobile data firewall" | `VpnService.prepare()` consent → `ACTION_START` / `ACTION_RELOAD_RULES`; `ACTION_STOP`; `onRevoke()` |
| `NetworkMonitorService` | `Service` FGS | `specialUse`, subtype "status bar speed" | `ACTION_START` / `ACTION_STOP` from ViewModel after POST_NOTIFICATIONS |
| `HotspotProxyService` | `Service` FGS | `specialUse`, subtype "hotspot proxy" | `ACTION_START` / `ACTION_STOP`; observes Room + prefs itself |
| `VpnGuardService` | `Service` FGS | `specialUse`, subtype "WireGuard/WARP" | started by `VpnGuardManager.connect()` **after** the tunnel is UP; `ACTION_STOP` / `ACTION_UPDATE_NOTIFICATION` |
| `com.wireguard.android.backend.GoBackend$VpnService` | `VpnService` | merged via `tools:node="merge"` from the WireGuard AAR | owned by `GoBackend` |
| `AppTimerReceiver` | `BroadcastReceiver` | `exported=false` | alarms: `ACTION_CHECK_APP_TIMERS`, `ACTION_GLOBAL_WIFI_TIMER`, `ACTION_UNLOCK_GLOBAL_WIFI` |
| `FileProvider` | provider | `${applicationId}.fileprovider` | config export/sharing paths |

**Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS` (optional, user-granted via Settings).

---

## 4. Subsystem entry points (call-site verified)

Every row names the *actual* function that starts the subsystem in the current code.

| Subsystem | Entry point | Invoked from | Notes |
| :--- | :--- | :--- | :--- |
| Firewall / VPN | `NetGateVpnService.startOrReload(ctx)` → `ACTION_START` | `MainActivity` (post-consent effect), `FirewallViewModel.onFirewallPermissionGranted` | `onStartCommand` handles START / STOP / RELOAD_RULES |
| Firewall stop | `NetGateVpnService.stop(ctx)` → `ACTION_STOP` | `ViewModel.disableFirewall`, `VpnGuardManager.connect` | Also `onRevoke()` when user/OS revokes |
| Tunnel planning | `PerAppFirewallEngine.buildTunnelPlan(...)` | `NetGateVpnService.rebuildTunnel` | Pure function; modes `DISABLED`, `GLOBAL_WIFI_LOCK`, `PER_APP_SINKHOLE`, `HYBRID_DNS_AND_APP_FILTER` |
| Sinkhole packet loop | `NetGateVpnService.drainPackets(pfd, blockedPackages)` | `rebuildTunnel` | Writes NXDOMAIN / TCP RST / ICMP unreachable |
| DNS loop | `NetGateVpnService.runDnsProxyLoop(pfd)` → `handleDnsQuery(query, output)` | `rebuildTunnel` (HYBRID mode) | Policy eval → safe-rewrite → upstream resolve → `publishRuntimeState` |
| DNS upstream | `resolveDnsUpstream` → `forwardDohWithBackup` / `forwardDnsOverTcp` / `forwardDnsQuery` | DNS loop | Transport chosen by `DnsTransportMode`; anti-poison via `DpiBypassEngine.isLikelyForgedDnsResponse` |
| DoH client | `AdBlockFilterEngine.forwardDnsOverHttps(payload, url)` | VPN service, `FirewallRepository.testCloudflareDohLatency`, `DnsLeakTester` | RFC 8484 POST |
| Loopback filter proxy | `LocalFilteringProxyServer(...).start()` | `NetGateVpnService.ensureProxyServerStartedLocked` | Built from 12 injected lambdas (§6.3) |
| DPI bypass | `DpiBypassEngine.resolveStrategy/writeTlsClientHello/writeHttpRequest` | `LocalFilteringProxyServer` | Always via the proxy; never a standalone service |
| Hotspot relay | `HotspotProxyManager.startOrUpdate(ctx, prefs)` | `FirewallViewModel.setHotspotProxyEnabled` | Starts `HotspotProxyService` FGS + proxy instance on `0.0.0.0` |
| Hotspot client policy | `NetworkPolicyEngine.evaluate(domain, PolicyContext.forHotspot(...), policy)` | `HotspotProxyManager.evaluateRequestForHotspot` | Client ban/cap evaluated before content rules |
| Status-bar speed | `NetworkMonitorService.start(ctx)` | `ViewModel.setStatusBarSpeedEnabled`, `MainActivity` effect | FGS + 1 Hz `SystemNetworkMonitor.sampleNow` |
| Traffic sampling | `SystemNetworkMonitor.sampleNow(ctx)` / `telemetryFlow` | NetworkMonitorService, ViewModel, VPN service (idle check) | Persists daily totals in SharedPreferences |
| Wi-Fi idle lock | `WifiAutomationManager.evaluateIdleSample(...)` + `WifiIdleStateMachine` | ViewModel (1 s tick), `NetGateVpnService.observeWifiAutomation` (2 s tick) | Two independent tickers drive the same singleton |
| Wi-Fi/app timers | `AppTimerScheduler.scheduleNextAlarm` → `AppTimerReceiver` → `processDueTimers` | Repository (`scheduleAppTimer`), VPN service, ViewModel | `AlarmManager.setExactAndAllowWhileIdle` |
| External VPN | `VpnGuardManager.connect(ctx, profile)` | ViewModel (`connectVpnGuardProfile`) | Stops local firewall, waits ≤1.5 s, brings WireGuard UP, then starts FGS |
| Connection logging | `ConnectionMonitorHub.recordConnection(...)` | 6 call sites in `NetGateVpnService` only | Writes Room `connection_events` + updates live deque |
| Security audit | `SecurityAuditEngine.runDeviceSecurityAudit(ctx, recent)` | `FirewallRepository.runDeviceSecurityAudit` | Reads `/proc/net/*`, accessibility, device admins, ADB |
| Site/region data | `GeoIpResolver.resolveImmediate(...)` | `ConnectionMonitorHub` | Offline heuristics first, optional online fill |
| Speed test | `SpeedTestRunner.runTest(label)` / `runVideoPlaybackTest(...)` | ViewModel | `Flow<SpeedTestState>` |
| DNS leak / check | `DnsLeakTester.runDiagnostic`, `DnsConnectionChecker.runLiveDnsCheck` | Repository → ViewModel | Read-only diagnostics |

### In-process intent actions & extras (string contract)

```
com.example.service.ACTION_START / ACTION_STOP / ACTION_RELOAD_RULES        NetGateVpnService
com.example.service.NETWORK_MONITOR_START / NETWORK_MONITOR_STOP            NetworkMonitorService
com.example.service.HOTSPOT_PROXY_START / HOTSPOT_PROXY_STOP                HotspotProxyService
com.example.action.VPN_GUARD_START / VPN_GUARD_STOP / VPN_GUARD_UPDATE      VpnGuardService (private)
com.example.service.ACTION_CHECK_APP_TIMERS                                 AppTimerScheduler → receiver
com.example.service.ACTION_GLOBAL_WIFI_TIMER / ACTION_UNLOCK_GLOBAL_WIFI    WifiAutomationManager → receiver
extra_destination                                                          MainActivity (AppDestination name)
```

---

## 5. Persistence contracts

### 5.1 Room — `netgate_rules.db`, **version 8**, `exportSchema = false`

Singleton via `NetGateDatabase.getInstance(context)`; migrations 1→8 registered, **no** `fallbackToDestructiveMigration`.

| # | Table | Entity | DAO | Key / indices |
| :-- | :--- | :--- | :--- | :--- |
| 1 | `app_network_rules` | `AppNetworkRuleEntity` | `AppNetworkRuleDao` | PK `packageName`; `blockWifi`, `blockMobileData`, `updatedAt` |
| 2 | `app_traffic_usage` | `AppTrafficUsageEntity` | `AppTrafficUsageDao` | PK `packageName`; wifi/mobile totals + rx/tx split, `periodDays`, `syncedAt` |
| 3 | `app_traffic_history` | `AppTrafficHistoryEntity` | `AppTrafficUsageDao` | composite PK (`dateEpochDay`,`packageName`); rx/tx per transport, `recordedAt` |
| 4 | `website_rules` | `WebsiteRuleEntity` | `WebsiteRuleDao` | PK `domain`; `ruleType` = `BLOCK`\|`ALLOW`, `isEnabled`, `createdAt` |
| 5 | `app_network_timers` | `AppTimerEntity` | `AppTimerDao` | PK `packageName`; block flags, `durationMillis`, `expiresAtMillis`, `createdAtMillis` |
| 6 | `ad_filter_lists` | `AdFilterListEntity` | `AdFilterListDao` | PK `id`; `title`, `sourceUrl`, `category`, `isEnabled`, `domainCount`, `lastSyncedAt` |
| 7 | `connection_events` | `ConnectionEventEntity` | `ConnectionHistoryDao` | PK auto `id`; **unique** (`domain`,`remoteIp`,`appPackage`,`actionStatus`); indices `lastSeenAt`, `countryCode`; trimmed to 400 rows |
| 8 | `vpn_guard_profiles` | `VpnGuardProfileEntity` | `VpnGuardProfileDao` | PK `id`; raw WireGuard config, endpoint, DNS, MTU, routing mode, packages CSV |

**Migration chain** (all additive, none destructive):

| Migration | What it does |
| :--- | :--- |
| 1→2 | create `app_traffic_usage` |
| 2→3 | create `website_rules` |
| 3→4 | add `wifiRxBytes`/`wifiTxBytes`/`mobileRxBytes`/`mobileTxBytes`; create `app_traffic_history` |
| 4→5 | create `app_network_timers` |
| 5→6 | create `ad_filter_lists` |
| 6→7 | create `connection_events` + unique index + 2 indices |
| 7→8 | create `vpn_guard_profiles` |

> Because `exportSchema = false`, there are **no schema JSONs** in `app/schemas`, so `MigrationTestHelper` cannot be used as-is. Any future schema change needs schema export enabled first.

### 5.2 DataStore — `netgate_preferences` (51 keys, all in `FirewallPreferences.Keys`)

| Group | Keys |
| :--- | :--- |
| Firewall core (5) | `is_firewall_enabled`, `show_offline_apps`, `show_system_apps`, `default_block_wifi`, `default_block_data` |
| Theme & motion (5) | `theme_mode`, `theme_style`, `use_dynamic_color`, `blur_engine_style`, `is_blur_animation_enabled` |
| Website & DNS (7) | `website_filter_mode`, `website_bypass_packages`, `selected_upstream_dns`, `custom_doh_url`, `custom_udp_dns_ip`, `dns_transport_mode`, `lifetime_ads_blocked_count`* |
| Ad blocker & safety (6) | `is_ad_blocker_enabled`, `is_essential_passthrough_enabled`, `use_doh_upstream`, `is_safe_search_enabled`, `youtube_restricted_mode`, `is_youtube_ad_block_enabled` |
| HTTPS proxy & DPI (12) | `is_https_proxy_enabled`, `https_proxy_port`, `is_proxy_strip_tracking_params`, `sni_proxy_preset`, `custom_sni_proxy_host`, `custom_sni_proxy_port`, `dpi_bypass_mode`, `dpi_fragment_chunk_size`, `dpi_fragment_delay_ms`, `dpi_idle_keepalive_enabled`, `block_quic_for_dpi_bypass`, `anti_dns_poison_enabled` |
| Status bar (4) | `is_status_bar_speed_enabled`, `status_bar_speed_style`, `show_status_bar_daily_usage`, `is_deep_monitor_enabled` |
| Hotspot & Wi-Fi automation (12) | `hotspot_proxy_enabled`, `hotspot_proxy_port`, `hotspot_apply_netgate_filters`, `hotspot_blocked_client_ips`, `hotspot_client_caps_set`, `hotspot_client_labels_set`, `global_wifi_blocked`, `global_wifi_timer_expires_at`, `global_wifi_timer_duration_ms`, `auto_block_wifi_on_idle`, `auto_block_wifi_idle_minutes`, `auto_block_wifi_panel_notify` |

\* `lifetime_ads_blocked_count` is a counter (`Long`), not a toggle. Set/map keys store `"<ip>|<value>"` strings because DataStore has no map type.

Defaults are applied in `preferencesFlow` with safe coercion: ports `1024..65535`, DPI chunk `1..32`, delay `0..15`, idle minutes `1..240`, timer `1..720`.

### 5.3 SharedPreferences — `netgate_live_usage_prefs` (5 keys, in `SystemNetworkMonitor`)

| Key | Type | Purpose |
| :--- | :--- | :--- |
| `day_key` | `Long` | Local day bucket for the daily baseline |
| `today_wifi_rx` | `Long` | Accumulated Wi-Fi download for the day |
| `today_wifi_tx` | `Long` | Accumulated Wi-Fi upload |
| `today_mobile_rx` | `Long` | Accumulated mobile download |
| `today_mobile_tx` | `Long` | Accumulated mobile upload |

### 5.4 Build-time / non-DB stores

- Ad-list cache files: `filesDir/<ad cache dir>/<listId>` written by `AdBlockFilterEngine.downloadAndCacheFilterList`, parsed into an in-memory `AdBlockMatcherSnapshot` (guarded by a static `cachedMatcherRef`).
- `BuildConfig` via the Secrets plugin reading `.env` / `.env.example` (no secrets currently required).

---

## 6. Public interfaces & cross-service contracts

### 6.1 Actual Kotlin interfaces in the app

Only **8** — and 7 of them are Room DAOs:

```
AppNetworkRuleDao, AppTrafficUsageDao, WebsiteRuleDao, AppTimerDao,
AdFilterListDao, ConnectionHistoryDao, VpnGuardProfileDao   (interfaces)
PolicyDecision                                              (sealed interface)
```

`PolicyDecision` is the only interface-like *behavioural* contract: `Allow(reason, isWhitelisted, isPassthrough, aliasHost)` / `Block(reason, isAdBlock, category)` with `BlockCategory` ∈ {GENERIC, WEBSITE_BLOCKLIST, WHITELIST_LOCKDOWN, AD_TRACKER, YOUTUBE_AD, APP_FIREWALL, DOH_BYPASS_CANARY, HOTSPOT_CLIENT_BANNED, HOTSPOT_DATA_CAP}, plus `toProxyFilterDecision()`.

Everything else (services, engines, repository) is a concrete `class` or `object` — **no interfaces, no fakes, no test seams**.

### 6.2 Singleton state as the real cross-service API

These `object` singletons expose `StateFlow`/mutable statics that other components read directly. They are the de-facto service bus:

| Singleton | Exposed contract | Consumed by |
| :--- | :--- | :--- |
| `NetGateVpnService.runtimeState` | `StateFlow<VpnRuntimeState>` (30 fields: DNS/proxy/ad counters, DPI counters, recent logs) | ViewModel, `VpnGuardManager`, `HotspotProxyManager`, `DnsLeakTester`, `DnsConnectionChecker` |
| `NetGateVpnService.clearRecentDnsLogs()` / `flushDnsAndAutopilotCache(ctx)` | static commands | ViewModel |
| `VpnGuardManager.runtimeState` | `StateFlow<VpnGuardRuntimeState>` | ViewModel, `VpnGuardService` |
| `HotspotProxyManager.runtimeState` | `StateFlow<HotspotProxyRuntimeState>` + `clientStatsMap` | ViewModel, `HotspotProxyService` |
| `SystemNetworkMonitor.telemetryFlow` / `isServiceActive` | `StateFlow<LiveNetworkTelemetry>` | ViewModel, services |
| `WifiAutomationManager.idleStatus` | `StateFlow<WifiIdleStatus>` + alarm helpers | ViewModel, VPN service |
| `ConnectionMonitorHub.liveConnectionsFlow` | `StateFlow<List<SiteConnectionRecord>>` | ViewModel/DataUsage |
| `DpiBypassEngine` autopilot cache | static map + `clearAutopilotCache()` | proxy server, VPN service |
| `AdBlockFilterEngine.cachedMatcherRef` | static snapshot cache | VPN service, hotspot manager, repository |

### 6.3 `LocalFilteringProxyServer` — the widest behavioural seam

It takes **12 constructor lambdas**, which is the closest thing the codebase has to dependency injection and the best existing test seam:

```
protectSocket, isClientBlocked, clientBlockReason, evaluateRequest, resolveTargetHost,
resolveTargetAddresses, upstreamProxyProvider, dpiBypassConfigProvider,
onDpiBypassApplied, onRequestLogged, onClientTraffic, onTrafficRelayed
```

### 6.4 Cross-service dependency edges (who calls whom)

```
MainActivity ──► FirewallViewModel ──► FirewallRepository ──► DAOs + FirewallPreferences
     │                  │                    └──► DnsLeakTester / DnsConnectionChecker / SecurityAuditEngine
     │                  ├──► NetGateVpnService.startOrReload/stop/flushDnsAndAutopilotCache
     │                  ├──► HotspotProxyService.start/stop + HotspotProxyManager.startOrUpdate
     │                  ├──► NetworkMonitorService.start/stop
     │                  ├──► VpnGuardManager.connect/disconnect
     │                  └──► WifiAutomationManager.setGlobalWifiLocked/scheduleWifiTimer
     ▼
NetGateVpnService ──► NetGateDatabase (rules, website, ad lists, timers)
     │            ──► FirewallPreferences
     │            ──► PerAppFirewallEngine, TunPacketInspector, DnsPacketFilter, DpiBypassEngine
     │            ──► AdBlockFilterEngine, NetworkPolicyEngine, LocalFilteringProxyServer
     │            ──► ConnectionMonitorHub ──► GeoIpResolver + SecurityAuditEngine + Room
     │            ──► AppTimerScheduler / WifiAutomationManager / SystemNetworkMonitor
     ▼
VpnGuardManager ──► NetGateVpnService (stop + poll runtimeState)   ◄── single-VPN arbitration
HotspotProxyManager ──► NetworkPolicyEngine, LocalFilteringProxyServer, NetGateVpnService.runtimeState
HotspotProxyService ──► HotspotProxyManager (refresh loop 1 s)
NetworkMonitorService ──► SystemNetworkMonitor + StatusBarIconRenderer
```

**Notable inversions / leaks to fix during refactor:**

1. `WifiAutomationManager` and `AppTimerScheduler` construct their own `FirewallPreferences`/database instead of receiving the repository.
2. `DnsConnectionChecker`/`DnsLeakTester` depend on `VpnRuntimeState` (a service type) for pure diagnostics.
3. `HotspotProxyManager` reads `NetGateVpnService.runtimeState` for reachability — a service→singleton→service cycle.
4. `ViewModel` calls engines (`AdBlockFilterEngine`, `SystemNetworkMonitor`, `WifiAutomationManager`) directly, bypassing the repository.
5. UI models (`FirewallModels.kt`) contain formatting helpers (`formatDataBytes`, `formatSpeedBytesPerSec`, flag emoji) used by services — model↔service coupling.

---

## 7. Refactoring risk register (ranked)

| # | Risk | Evidence in code | Why it blocks a safe refactor |
| :-- | :--- | :--- | :--- |
| R1 | **Single Android VPN slot arbitration** | `VpnGuardManager.connect()` stops `NetGateVpnService`, then polls `runtimeState.isServiceRunning` in a 30×50 ms loop before bringing WireGuard UP | Race/flaky on slow teardown; failure path leaves no tunnel and firewall disabled |
| R2 | **Two god objects** | `FirewallViewModel` 2,230 lines / ~120 public functions; `NetGateVpnService` 2,071 lines | Any extraction must preserve ~60 state fields and the exact `UiState` shape |
| R3 | **Service singletons as global state** | 63 `object`/`@Volatile`/singleton declarations under `service/`; `_runtimeState` + `dnsLogQueue` + `recentBlockedQueue` live in the **companion object** of a `Service` | State survives service recreation; tests and multiple instances share queues; hard to reset deterministically |
| R4 | **Dual automation tickers** | Wi-Fi idle evaluated by ViewModel (1 s) *and* VPN service (`delay(2000L)` loop); timers by AlarmManager *and* service collector | Duplicate lock/notification events; refactors must keep both or remove one deliberately |
| R5 | **No schema export / no migration tests** | `exportSchema = false`, no `app/schemas` | Cannot safely bump DB version; a refactor touching entities has no safety net |
| R6 | **Repository bypassed by engines** | `WifiAutomationManager`, `AppTimerScheduler`, `HotspotProxyManager` build `FirewallPreferences`/`NetGateDatabase` themselves | Data-layer refactor must sweep these ad-hoc constructions |
| R7 | **Policy snapshot correctness is the product's core** | `NetworkPolicyEngine.evaluate` precedence order is documented and tested; `NetworkPolicy` is an immutable snapshot rebuilt in `NetGateVpnService.observeRules` | Reordering steps silently breaks whitelist/DoH-canary security invariants |
| R8 | **Packet writers are hand-rolled** | `DnsPacketFilter` builds IPv4/IPv6 UDP/TCP/ICMP packets with manual checksums; `DpiBypassEngine` slices ClientHello byte ranges | Any refactor here is high-blast-radius; only unit tests protect it |
| R9 | **Lifetime counters flushed asynchronously** | `pendingLifetimeAdsDelta` + `scheduleFlushLifetimeAds()` writes DataStore from `NetGateVpnService` | A refactor moving counter ownership risks lost increments on kill |
| R10 | **Privacy surface** | `allowBackup=true`; Room DB + prefs contain browsing domains, per-app usage history, hotspot client IPs/labels | Backup/export rules must be reviewed before shipping any data refactor |
| R11 | **Build config drift** | `namespace = com.example`, `applicationId = com.aistudio.netgate.xkqmpt`; release keystore path/env vars from AI Studio; Google Services plugin with `WARN` passthrough; `metadata.json` advertises a Gemini capability nothing uses | Renaming packages or wiring Firebase must not disturb signing or the Secrets plugin |
| R12 | **CI builds only** | `.github/workflows/build-apk.yml` runs `gradle assembleDebug` and uploads the APK — **never runs tests** | Regressions pass CI silently; the checklist below is the real gate |

---

## 8. Existing test inventory & coverage gaps

### 8.1 What exists (101 JVM/Robolectric tests)

| Test class | Tests | Covers |
| :--- | :-- | :--- |
| `NetworkPolicyEngineTest` | 19 | Domain normalisation, rule precedence, whitelist lockdown, DoH canary, UID/package blocks, hotspot bans/caps, firewall↔hotspot parity, snapshot immutability |
| `ExampleRobolectricTest` | 35 | Room joins, repository rule CRUD, timers, ad-list seeding/sync parsing, DNS packet parse/build (v4+v6), SNI proxy prefs, formatters, TrafficRateCalculator, status-bar icon, DNS leak evaluator, SafeSearch rewriting |
| `DpiBypassTest` | 12 | SNI byte-range detection, split/chunk/desync writers, autopilot escalation + flush, forged-DNS detection, QTYPE 65 NODATA, tunnel-mode switch, proxy multi-chunk path, DIRECT fallback |
| `VpnGuardTest` | 8 | WireGuard config parse/build, split-tunnel, WARP registration JSON, VPNGate CSV, guard UI, uninstalled-app failure |
| `PerAppVpnFirewallTest` | 7 | Tunnel plans, global Wi-Fi lock priority, sinkhole classification, ICMP unreachable, service consent/stop lifecycle |
| `VideoPlaybackTest` | 6 | Tier evaluation for 4K/mid/slow/jitter + UI |
| `HotspotAndWifiTest` | 5 | Client policy, interface matcher, idle state machine, prefs persistence, sheet UI |
| `ScreenshotGeneratorTest` | 4 | Compose renders → writes PNGs to `docs/screenshots` (side-effecting test) |
| `ThemeStyleTest` | 3 | Theme persistence, motion spec, settings sheet switching |
| `StartupSplashTest` | 1 | Splash branding + skip |
| `ExampleUnitTest` | 1 | `2 + 2 == 4` (template leftover) |
| `ExampleInstrumentedTest` (androidTest) | 1 | Package-name placeholder only |

All Robolectric suites pin `@Config(sdk = [35])` or `[36]`.

### 8.2 Missing critical coverage (ordered by refactor risk)

1. **Room migrations 1→8** — zero migration tests; impossible today without schema export.
2. **DNS upstream failover & anti-poison wired inside the service** — `handleDnsQuery` → DoH → backup DoH → DoT → UDP path is untested end-to-end; only packet builders and the forged-IP predicate are unit-tested.
3. **`LocalFilteringProxyServer` HTTP path** — 403 block body, tracking-parameter stripping, client quota abort, and CONNECT relay accounting are unverified (tests cover authority parsing, SNI extraction, TLS write paths).
4. **`NetGateVpnService` tunnel rebuild/diffing** — `TunnelConfigSignature` equality prevents restarts, but no test asserts "same signature ⇒ no teardown".
5. **`ConnectionMonitorHub`** — Room upsert/merge, risk escalation, 400-row trim, geo backfill, live deque ordering.
6. **`GeoIpResolver.parseGeoJson` / online lookup / private-IP handling.**
7. **`SecurityAuditEngine`** — `/proc` parser, accessibility/admin/ADB checks, scoring thresholds.
8. **`SystemNetworkMonitor` daily rollover** — SharedPreferences baseline reset across midnight and service restart.
9. **`WifiAutomationManager` alarms / `AppTimerReceiver`** — broadcast delivery, `goAsync()` completion, `setExactAndAllowWhileIdle` fallbacks.
10. **`HotspotProxyService` / `NetworkMonitorService` / `VpnGuardService` foreground lifecycles** — START_STICKY, notification actions, POST_NOTIFICATIONS denial.
11. **`FirewallViewModel` state machine** — no test drives UiState combinations (though Compose screens are rendered individually).
12. **Instrumented/device tests** — none for real `VpnService`, actual DNS resolution, tethering, or process death.
13. **Golden-image/visual regression** — screenshots are generated but never compared.

---

## 9. Corrections to `docs/ARCHITECTURE_BASELINE.md`

The earlier baseline doc is now inaccurate in specific, load-bearing ways. Do not refactor against those names.

| Old statement | Verified current reality |
| :--- | :--- |
| `NetGateVpnService.vpnLoop()` intercepts UDP/53 | No `vpnLoop`. Two loops: `drainPackets()` (sinkhole rejects) and `runDnsProxyLoop()` → `handleDnsQuery()` |
| `DnsPacketFilter.stripQuicSvcbRecords()` | No such API. QTYPE 65 downgrade is inline in `handleDnsQuery` using `extractQueryType` + `buildNoDataDnsResponsePacket`; UDP/443 is dropped at packet classification |
| `SecurityAuditEngine.runFullAudit()` | Actual entry: `SecurityAuditEngine.runDeviceSecurityAudit(context, recentConnections)` |
| `HotspotProxyManager.startProxy(context, port)` | Actual entry: `HotspotProxyManager.startOrUpdate(context, prefs)` |
| SharedPreferences keys `saved_day_epoch`, `baseline_total_rx`, `baseline_total_tx`, `baseline_mobile_rx`, `baseline_mobile_tx` | Actual keys: `day_key`, `today_wifi_rx`, `today_wifi_tx`, `today_mobile_rx`, `today_mobile_tx` |
| `DpiBypassMode`: `OFF`, `AUTOPILOT`, `SPLIT_SNI`, `HOST_DESYNC` | `OFF`, `AUTOPILOT`, `SNI_SPLIT`, `SNI_CHUNKS`, `HTTP_DESYNC` |
| `DnsTransportMode`: `PLAIN_UDP`, `DOH_WITH_FALLBACK`, `TCP_FALLBACK` | `DOH_WITH_FALLBACK`, `DNS_OVER_TCP`, `UDP_THEN_TCP` |
| `VpnGuardProviderType`: `WIREGUARD`, `CLOUDFLARE_WARP`, `VPNGATE` | `CUSTOM_WIREGUARD`, `CLOUDFLARE_WARP`, `PROTON_VPN`, `WINDSCRIBE`, `MULLVAD_OR_OTHER` (VPNGate is a *server list*, not a provider type) |
| `FirewallViewModel` 2,226 lines / `NetGateVpnService` 2,116 lines | 2,230 / 2,071 lines |
| Hotspot proxy "invokes startProxy across tethered interfaces" | `HotspotProxyManager` binds **one** proxy on the detected tethering interface and refreshes environment state each tick |

Accurate claims worth keeping: 51 DataStore keys, Room v8 with 7 migrations, 8 tables/7 DAOs, `PolicyDecision` adapter, single-VPN arbitration, monolith sizes, singleton bypass of the data layer.

---

## 10. Regression-test checklist

Run every tier that a change can touch. Tiers 0–2 are cheap; tier 3 is the only real proof for a VPN app.

### Tier 0 — Build & toolchain gate (every change)

- [ ] `.env` present (`test -f .env || cp .env.example .env`) — the Secrets plugin reads it
- [ ] `debug.keystore` present (README keytool one-liner) or CI generates it
- [ ] `./gradlew :app:testDebugUnitTest` exits **0**
- [ ] `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-universal-debug.apk` (plus per-ABI APKs)
- [ ] No new `fallbackToDestructiveMigration`, no `allowMainThreadQueries`

### Tier 1 — Pure logic (must stay green, per PR)

- [ ] `NetworkPolicyEngineTest` — rule precedence unchanged: hotspot ban/cap → hotspot relay bypass → UID/package block → DoH canary → website mode → passthrough → YouTube → ad/tracker → allow
- [ ] `DpiBypassTest` — SNI range, split/chunk/desync writers, autopilot escalation, forged-DNS predicate, QTYPE 65 NODATA
- [ ] `PerAppVpnFirewallTest` — `TunnelPlan` mode selection, global Wi-Fi priority, sinkhole rejection types
- [ ] `VpnGuardTest` — WireGuard/WARP config build, split-tunnel, VPNGate CSV
- [ ] `HotspotAndWifiTest` — client caps/bans, idle state machine

### Tier 2 — Persistence contracts (any data/prefs change)

- [ ] All 51 DataStore keys still read with correct defaults and coercion ranges
- [ ] The 5 `netgate_live_usage_prefs` keys (`day_key`, `today_wifi_rx/tx`, `today_mobile_rx/tx`) still round-trip
- [ ] Room v8 opens an **existing v7** database: all 7 migrations apply, no destructive fallback
- [ ] DAO behaviours unaffected: rule upsert/delete, timer due-query, ad-list `IGNORE` seed, connection merge + 400-row trim, geo backfill, vpn-guard routing update
- [ ] `FirewallPreferencesState` still maps every key 1:1 (no silently dropped field)

### Tier 3 — Critical user journeys (device, before/after each refactor step)

- [ ] **CUJ-01 Per-app isolation** — block Wi-Fi for one app → that app's traffic is sinkholed (NXDOMAIN/RST/ICMP), all other apps keep native speed; `TunnelPlan.mode == PER_APP_SINKHOLE`
- [ ] **CUJ-02 Global Wi-Fi lock** — lock all Wi-Fi traffic; switching to mobile data releases it (`GLOBAL_WIFI_LOCK` only on `WIFI`)
- [ ] **CUJ-03 DNS filtering** — ad domain returns synthetic NXDOMAIN with no upstream query; `VpnRuntimeState.blockedDnsQueriesCount` increments
- [ ] **CUJ-04 Website blocklist / whitelist lockdown** — blocklist blocks listed domain + subdomains; whitelist-only blocks everything else; a specific allow beats a parent block
- [ ] **CUJ-05 SafeSearch / YouTube Restrict** — aliases rewritten and answers rebound to the original hostname; whitelisted hosts never rewritten
- [ ] **CUJ-06 DoH bypass canary** — `use-application-dns.net` / `_dns.resolver.arpa` blocked even when whitelisted
- [ ] **CUJ-07 DNS transport modes** — DoH+DoTCP, DNS-over-TCP, UDP+DoTCP each resolve; backup DoH and UDP fallback engage when primary fails
- [ ] **CUJ-08 Anti-DNS-poison** — spoofed private/bogon answer discarded, counter increments
- [ ] **CUJ-09 Proxy + tracking strip** — `utm_*`/`fbclid`/`gclid` removed; blocked host returns 403 page; HTTPS CONNECT relayed; counters update
- [ ] **CUJ-10 DPI bypass** — Autopilot learns and escalates per host; SNI Split/Micro Chunks/HTTP Desync still load a TLS site; QUIC downgraded; cache flush resets learned count
- [ ] **CUJ-11 Hotspot sharing** — tethered client uses proxy; per-IP cap blocks at threshold; banned IP blocked; labels persist; stats reset works
- [ ] **CUJ-12 VPN arbitration** — connect WireGuard profile → local firewall stops cleanly → tunnel UP → disconnect → firewall restored if it was on
- [ ] **CUJ-13 Timers** — app timer blocks Wi-Fi/data at expiry (alarm + foreground), global Wi-Fi timer unlocks, idle auto-lock triggers below 2 KB/s
- [ ] **CUJ-14 Status bar** — speed icon renders for all 3 styles, shows daily usage, stops cleanly when toggled off
- [ ] **CUJ-15 Usage data** — usage-access grant → sync populates per-app/daily history; blocked-vs-allowed join stays correct
- [ ] **CUJ-16 Connections/audit** — sites list merges duplicates, risk levels escalate, geo backfills, audit report scoring unchanged
- [ ] **CUJ-17 Lifecycle** — VPN revoked by another app / by OS → firewall disabled, notification removed, no orphan proxy thread; service restart restores state
- [ ] **CUJ-18 Permissions** — denying POST_NOTIFICATIONS, VPN consent, or usage-access degrades gracefully without crash

### Refactor protocol (per step)

1. Take a baseline run of Tier 0+1 and record the exact test count.
2. Make one structural change only (no behaviour edits in the same commit).
3. Re-run Tier 0+1; diff any changed `VpnRuntimeState`/UiState fields.
4. For packet, DNS, proxy or policy code — add a focused unit test **before** touching it, since Tier 3 is manual.
5. Never change a database version without first enabling `exportSchema` and adding a migration test.

---

## 11. Suggested first refactor slices

Small, low-risk, test-protected, and they unblock everything else:

1. **Introduce DAO-free seams behind interfaces** — wrap `FirewallPreferences` and `NetGateDatabase` in interfaces; inject into `WifiAutomationManager`, `AppTimerScheduler`, `HotspotProxyManager` to kill R6.
2. **Enable `exportSchema = true`** and commit `app/schemas` (no version bump, no behaviour change) so migrations become testable.
3. **Extract `NetGateVpnService` collaborators** in this order: notification builder → DNS upstream resolver → proxy lifecycle (`ensureProxyServerStartedLocked`) → rule snapshot collector. Each is an independent unit with clear inputs.
4. **Replace companion-object queues** with an injected `RuntimeStatsStore`, keeping the public `runtimeState` flow shape so UI is untouched.
5. **Split `FirewallViewModel` by feature** (firewall, websites/adblock, data usage, hotspot/wifi, VPN guard) behind one facade that still exposes `uiState`.

Each slice is behaviour-preserving, so Tier 1 + a device pass on the matching CUJ is sufficient evidence.

---

## 12. Toolchain added to the project (this commit)

Previously missing and now provided:

| Item | Status |
| :--- | :--- |
| Gradle wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` pinned to Gradle 9.3.1 |
| `.env` | created locally from `.env.example` (git-ignored) |
| `debug.keystore` | generated locally (git-ignored) |
| JDK 21 / Android SDK 36.1 | installed in the build environment, not tracked |

See `docs/TOOLCHAIN.md` for the exact setup and the baseline test command.
