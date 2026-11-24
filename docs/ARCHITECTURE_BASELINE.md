# NetGate — Architecture Baseline & Refactoring Report

**Project:** NetGate  
**Package Namespace:** `com.aistudio.netgate`  
**Application ID:** `com.aistudio.netgate.xkqmpt`  
**Target / Compile SDK:** Android 36 (minorApiLevel 1) | **Min SDK:** 24 (Android 7.0)  
**Toolchain:** Kotlin 2.2.10, Jetpack Compose BOM 2025.12.00, AGP 9.1.1, KSP 2.3.5, Java 11 / desugar_jdk_libs 2.1.5  
**Audit Purpose:** Baseline Architecture, System Entry Points, Database/Preference Contracts, and Refactoring Safeguards  

---

## 1. System Architecture & Component Hierarchy

NetGate is a modern rootless Android firewall, DNS filtering shield, and VPN routing utility. It employs an MVVM / Domain Engine architecture, balancing interactive Jetpack Compose UI sheets with long-running `specialUse` foreground services and low-level pure-Kotlin protocol packet parsers.

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│  MainActivity ──> FirewallScreen ──> [Components & Sheets]            │
│       │                   ▲                                            │
│       ▼                   │                                            │
│  StartupSplashOverlay     └── FirewallViewModel (StateFlow<UiState>)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        DATA & REPOSITORY LAYER                         │
│  FirewallRepository ──> NetGateDatabase (Room v8) & FirewallPreferences │
│         │                                                              │
│         ├── DnsConnectionChecker / DnsLeakTester / SpeedTestRunner     │
│         └── VpnGateClient (OkHttp DoH-resilient client)                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    SERVICE & ENGINE RUNTIME LAYER                      │
│                                                                        │
│  ┌───────────────────────┐             ┌─────────────────────────────┐ │
│  │   NetGateVpnService   │ (Tunnel TUN)│       VpnGuardService       │ │
│  │  - TunPacketInspector │             │  - WireGuard GoBackend      │ │
│  │  - PerAppFirewall     │             │  - WarpConfigGenerator      │ │
│  │  - DnsPacketFilter    │             │  - WireGuardConfigHelper    │ │
│  │  - AdBlockEngine      │             └─────────────────────────────┘ │
│  └───────────┬───────────┘                                             │
│              │                                                         │
│              ▼                                                         │
│  ┌─────────────────────────────┐       ┌─────────────────────────────┐ │
│  │ LocalFilteringProxyServer   │       │    HotspotProxyService      │ │
│  │  - 127.0.0.1:8899 (TUN/VPN) │       │  - 0.0.0.0:8890 (Tethering) │ │
│  │  - DpiBypassEngine          │       │  - HotspotProxyManager      │ │
│  │  - GeoIpResolver            │       │  - Per-client caps & labels │ │
│  └─────────────────────────────┘       └─────────────────────────────┘ │
│                                                                        │
│  ┌─────────────────────────────┐       ┌─────────────────────────────┐ │
│  │   NetworkMonitorService     │       │   WifiAutomationManager     │ │
│  │  - SystemNetworkMonitor     │       │   AppTimerScheduler         │ │
│  │  - StatusBarIconRenderer    │       │   SecurityAuditEngine       │ │
│  └─────────────────────────────┘       └─────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Functional Subsystems & Entry Points

| Subsystem | Primary Entry Point | Trigger / Invocation Pattern | Key Runtime Classes |
| :--- | :--- | :--- | :--- |
| **Local Firewall VPN** | `com.aistudio.netgate.service.NetGateVpnService` (`android.net.VpnService`) | User toggles Master Firewall switch in UI. Initiates `VpnService.prepare(context)` consent and calls `NetGateVpnService.startOrReload(context)`. | `NetGateVpnService`, `PerAppFirewallEngine`, `TunPacketInspector` |
| **VPN Guard (WireGuard / WARP)** | `com.aistudio.netgate.service.VpnGuardService` & `com.wireguard.android.backend.GoBackend$VpnService` | User selects profile and taps Connect in VPN Guard. `VpnGuardManager.connect(context, profile)` pauses local firewall, writes `IS_FIREWALL_ENABLED = false`, and binds WireGuard tunnel. | `VpnGuardService`, `VpnGuardManager`, `WireGuardConfigHelper`, `WarpConfigGenerator` |
| **DNS Packet Interception & DoH** | Loop inside `NetGateVpnService.vpnLoop()` intercepting port UDP/53 | Intercepts all port 53 packets; matches domains against AdBlock, website rules, and SafeSearch aliases. Synthesizes NXDOMAIN/NODATA or forwards over HTTPS (RFC 8484). | `DnsPacketFilter`, `AdBlockFilterEngine`, `DnsConnectionChecker`, `DnsLeakTester` |
| **Local Filtering Proxy Server** | ServerSocket listening on `127.0.0.1:8899` | Started during `NetGateVpnService` boot. Serves HTTP, HTTPS `CONNECT`, TLS ClientHello SNI inspection, and tracking URL parameter stripping. | `LocalFilteringProxyServer`, `DpiBypassEngine`, `GeoIpResolver` |
| **DPI Bypass Shield** | Pre-routing filter inside `LocalFilteringProxyServer` | Active when `DpiBypassMode != OFF`. Fragments TLS ClientHello SNI bytes, desynchronizes HTTP Host headers, downgrades QUIC, and suppresses spoofed DNS responses. | `DpiBypassEngine`, `DnsPacketFilter.stripQuicSvcbRecords()`, `DnsPacketFilter.isLikelyForgedDnsResponse()` |
| **Hotspot Proxy Sharing** | `com.aistudio.netgate.service.HotspotProxyService` listening on `0.0.0.0:8890` | User enables Hotspot Proxy in Hotspot & Wi-Fi settings sheet. Invokes `HotspotProxyManager.startProxy(context, port)` across tethered interfaces. | `HotspotProxyService`, `HotspotProxyManager`, `LocalFilteringProxyServer` |
| **Wi-Fi Automation & Timers** | `AppTimerReceiver` (`BroadcastReceiver`) & `WifiAutomationManager` | `AppTimerScheduler` sets `AlarmManager.setExactAndAllowWhileIdle()`. Monitored every 1s by ViewModel and 3s by `NetGateVpnService` loop. | `AppTimerScheduler`, `AppTimerReceiver`, `WifiAutomationManager` |
| **System Speed & Telemetry Monitor** | `com.aistudio.netgate.service.NetworkMonitorService` | User enables Status Bar Speed Icon or opens live telemetry dashboard. Executes 1 Hz `TrafficStats` delta polling loop. | `NetworkMonitorService`, `SystemNetworkMonitor`, `StatusBarIconRenderer` |
| **Security & Kernel Socket Audit** | `SecurityAuditEngine.runFullAudit()` | Triggered on demand or when opening Audit tab. Reads `/proc/net/{tcp,tcp6,udp,udp6}`, audits Accessibility services, device admins, and active VPNs. | `SecurityAuditEngine`, `ConnectionMonitorHub` |

---

## 3. Dependency Map & Build Configuration

### 3.1 Active Gradle Plugins
* `com.android.application` (AGP `9.1.1`)
* `org.jetbrains.kotlin.plugin.compose` (Kotlin `2.2.10`)
* `com.google.devtools.ksp` (`2.3.5`)
* `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` (`2.0.1`)
* `com.google.gms.google-services` (`4.5.0` with `MissingGoogleServicesStrategy.WARN`)

### 3.2 Active Runtime Dependencies
* **Jetpack Compose:** Compose BOM `2025.12.00` (`androidx.compose.material3:material3`, `androidx.compose.material:material-icons-extended`, `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview`)
* **Activity & Lifecycle:** `androidx.activity:activity-compose:1.10.1`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`, `lifecycle-runtime-compose:2.8.7`
* **Core & Concurrency:** `androidx.core:core-ktx:1.18.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`
* **Local Persistence:** `androidx.datastore:datastore-preferences:1.1.7`, `androidx.room:room-runtime:2.7.0`, `androidx.room:room-ktx:2.7.0` (KSP compiler `2.7.0`)
* **Networking & VPN:** `com.squareup.okhttp3:okhttp:4.10.0`, `com.wireguard.android:tunnel:1.0.20211029`
* **UI Effects:** `io.github.fletchmckee.liquid:liquid:1.1.1` (Liquid Glass theme engine)
* **Bytecode Desugaring:** `com.android.tools:desugar_jdk_libs:2.1.5`

### 3.3 Unused / Commented Template Dependencies
* `firebase-bom`, `firebase-firestore-ktx`, `firebase-auth-ktx`, `firebase-appcheck-playintegrity`, `firebase-ai`
* `androidx.credentials:credentials`, `googleid`
* `androidx.navigation:navigation-compose`
* `io.coil-kt:coil-compose`, `com.squareup.retrofit2:retrofit`, `moshi`
* `play-services-location`, `androidx.camera:camera-*`, `accompanist-permissions`

---

## 4. Preference Keys Registry (`FirewallPreferences`)

All persistent settings are stored in DataStore file `"netgate_preferences"` (with live usage cache in SharedPreferences `"netgate_live_usage_prefs"`).

### 4.1 DataStore (`netgate_preferences`) — 51 Keys

| Category | Key Identifier | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Core Firewall** | `is_firewall_enabled` | `Boolean` | `false` | Master toggle for local TUN VPN firewall |
| | `show_offline_apps` | `Boolean` | `false` | Display apps lacking `INTERNET` permission |
| | `show_system_apps` | `Boolean` | `true` | Display pre-installed Android system packages |
| | `default_block_wifi` | `Boolean` | `false` | Default Wi-Fi block status for newly installed packages |
| | `default_block_data` | `Boolean` | `false` | Default Mobile Data block status for newly installed packages |
| **Theming & Motion** | `theme_mode` | `String` | `"SYSTEM"` | `"LIGHT"`, `"DARK"`, or `"SYSTEM"` |
| | `theme_style` | `String` | `"NETGATE_DEFAULT"` | `"NETGATE_DEFAULT"`, `"EXPRESSIVE_MOTION"`, `"LIQUID_GLASS"`, `"MATERIAL_3_EXPRESSIVE"`, `"MATERIAL_YOU_LITE"` |
| | `use_dynamic_color` | `Boolean` | `true` | Android 12+ wallpaper dynamic color palette |
| | `blur_engine_style` | `String` | `"ACRYLIC_PRO"` | Blur engine shader radius and saturation preset |
| | `is_blur_animation_enabled` | `Boolean` | `true` | Spring animated backdrop translation and depth blur |
| **Website & DNS** | `website_filter_mode` | `String` | `"OFF"` | `"OFF"`, `"BLOCK_LISTED"`, or `"ALLOW_ONLY_LISTED"` |
| | `website_bypass_packages` | `Set<String>` | `emptySet()` | Packages exempt from domain filtering |
| | `selected_upstream_dns` | `String` | `"CLOUDFLARE_STANDARD"` | Upstream resolver preset (Cloudflare, Google, AdGuard, Quad9, etc.) |
| | `custom_doh_url` | `String` | `""` | User-defined DNS-over-HTTPS URL |
| | `custom_udp_dns_ip` | `String` | `""` | User-defined plain UDP DNS IP |
| | `dns_transport_mode` | `String` | `"DOH_WITH_FALLBACK"` | `"PLAIN_UDP"`, `"DOH_WITH_FALLBACK"`, or `"TCP_FALLBACK"` |
| **Ad Blocker & Safety** | `is_ad_blocker_enabled` | `Boolean` | `false` | Master toggle for AdBlock engine |
| | `is_essential_passthrough_enabled` | `Boolean` | `true` | Exempt FCM, Google Pay, and captive portal probes |
| | `use_doh_upstream` | `Boolean` | `true` | Resolve non-blocked queries via upstream DoH |
| | `lifetime_ads_blocked_count` | `Long` | `0L` | Cumulative ads and trackers blocked counter |
| | `is_safe_search_enabled` | `Boolean` | `false` | Enforce SafeSearch VIPs across Google, Bing, DDG, Brave |
| | `youtube_restricted_mode` | `String` | `"OFF"` | `"OFF"`, `"STRICT"`, or `"MODERATE"` |
| | `is_youtube_ad_block_enabled` | `Boolean` | `false` | Block known YouTube ad & tracking domains |
| **HTTPS Proxy & DPI** | `is_https_proxy_enabled` | `Boolean` | `false` | Run local HTTP/HTTPS filtering proxy |
| | `https_proxy_port` | `Int` | `8899` | Local TCP port for proxy server (1024–65535) |
| | `is_proxy_strip_tracking_params` | `Boolean` | `true` | Strip `utm_*`, `fbclid`, `gclid`, etc. |
| | `sni_proxy_preset` | `String` | `"STANDARD_DIRECT"` | SNI upstream routing profile preset |
| | `custom_sni_proxy_host` | `String` | `""` | Custom upstream SNI proxy hostname |
| | `custom_sni_proxy_port` | `Int` | `8080` | Custom upstream SNI proxy port |
| | `dpi_bypass_mode` | `String` | `"OFF"` | `"OFF"`, `"AUTOPILOT"`, `"SPLIT_SNI"`, `"HOST_DESYNC"` |
| | `dpi_fragment_chunk_size` | `Int` | `4` | Byte size for ClientHello SNI fragmentation (1–32) |
| | `dpi_fragment_delay_ms` | `Int` | `1` | Delay between fragmented SNI packets |
| | `dpi_idle_keepalive_enabled` | `Boolean` | `true` | Maintain TCP keep-alive on messaging sockets |
| | `block_quic_for_dpi_bypass` | `Boolean` | `true` | Drop UDP/443 & strip SVCB records to force TCP |
| | `anti_dns_poison_enabled` | `Boolean` | `true` | Detect and reject forged/sinkhole DNS responses |
| **Status Bar & Telemetry** | `is_status_bar_speed_enabled` | `Boolean` | `false` | Show persistent live upload/download speed icon |
| | `status_bar_speed_style` | `String` | `"DOWNLOAD_UPLOAD"` | Speed text formatting style for status bar |
| | `show_status_bar_daily_usage` | `Boolean` | `true` | Show today's data usage in notification |
| | `is_deep_monitor_enabled` | `Boolean` | `true` | Log sites, countries, byte counts to Room DB |
| **Hotspot & Automation** | `hotspot_proxy_enabled` | `Boolean` | `false` | Run proxy on tethered interfaces |
| | `hotspot_proxy_port` | `Int` | `8890` | Hotspot proxy listener port |
| | `hotspot_apply_netgate_filters` | `Boolean` | `true` | Apply ad/site rules to hotspot clients |
| | `hotspot_blocked_client_ips` | `Set<String>` | `emptySet()` | Set of banned client IP addresses |
| | `hotspot_client_caps_set` | `Set<String>` | `emptySet()` | Serialized byte quotas: `"<ip>\|<bytes>"` |
| | `hotspot_client_labels_set` | `Set<String>` | `emptySet()` | Serialized friendly labels: `"<ip>\|<label>"` |
| | `global_wifi_blocked` | `Boolean` | `false` | Global Wi-Fi lock blocking all apps on Wi-Fi |
| | `global_wifi_timer_expires_at` | `Long` | `0L` | Epoch timestamp when global Wi-Fi lock expires |
| | `global_wifi_timer_duration_ms` | `Long` | `0L` | Duration of active global Wi-Fi lock timer |
| | `auto_block_wifi_on_idle` | `Boolean` | `false` | Automatically lock Wi-Fi after idle period |
| | `auto_block_wifi_idle_minutes` | `Int` | `10` | Minutes of screen-off idle before lock |
| | `auto_block_wifi_panel_notify` | `Boolean` | `true` | Post notification when idle Wi-Fi lock activates |

### 4.2 SharedPreferences (`netgate_live_usage_prefs`) — 5 Keys
* `saved_day_epoch` (`Long`)
* `baseline_total_rx` (`Long`)
* `baseline_total_tx` (`Long`)
* `baseline_mobile_rx` (`Long`)
* `baseline_mobile_tx` (`Long`)

---

## 5. Room Database Schema Registry

* **Database Name:** `"netgate_rules.db"` (`NetGateDatabase`)
* **Current Version:** `8` (`exportSchema = false`)
* **Migration Chain:** `MIGRATION_1_2` through `MIGRATION_7_8` (all non-destructive SQL migrations)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ROOM DATABASE SCHEMA (v8)                       │
├─────────────────────────┬──────────────────────────────────────────────┤
│ Table Name              │ Primary Key & Schema Summary                 │
├─────────────────────────┼──────────────────────────────────────────────┤
│ app_network_rules       │ PK: packageName (TEXT)                       │
│                         │ Columns: blockWifi, blockMobileData,         │
│                         │          updatedAt                           │
├─────────────────────────┼──────────────────────────────────────────────┤
│ app_traffic_usage       │ PK: packageName (TEXT)                       │
│                         │ Columns: wifiBytes, mobileBytes, wifiRxBytes,│
│                         │          wifiTxBytes, mobileRxBytes,         │
│                         │          mobileTxBytes, periodDays, syncedAt │
├─────────────────────────┼──────────────────────────────────────────────┤
│ app_traffic_history     │ Composite PK: (dateEpochDay, packageName)    │
│                         │ Columns: wifiRxBytes, wifiTxBytes,           │
│                         │          mobileRxBytes, mobileTxBytes,       │
│                         │          recordedAt                          │
├─────────────────────────┼──────────────────────────────────────────────┤
│ website_rules           │ PK: domain (TEXT)                            │
│                         │ Columns: ruleType ("BLOCK"/"ALLOW"),         │
│                         │          isEnabled, createdAt                │
├─────────────────────────┼──────────────────────────────────────────────┤
│ app_network_timers      │ PK: packageName (TEXT)                       │
│                         │ Columns: blockWifiOnExpire,                  │
│                         │          blockMobileDataOnExpire,            │
│                         │          durationMillis, expiresAtMillis,    │
│                         │          createdAtMillis                     │
├─────────────────────────┼──────────────────────────────────────────────┤
│ ad_filter_lists         │ PK: id (TEXT)                                │
│                         │ Columns: title, description, sourceUrl,      │
│                         │          category, isEnabled, domainCount,   │
│                         │          lastSyncedAt                        │
├─────────────────────────┼──────────────────────────────────────────────┤
│ connection_events       │ PK: id (INTEGER autoincrement)               │
│                         │ Unique Index: (domain, remoteIp, appPackage, │
│                         │               actionStatus)                  │
│                         │ Indices: lastSeenAt, countryCode             │
│                         │ Columns: domain, remoteIp, remotePort,       │
│                         │          protocol, appPackage, appName,      │
│                         │          countryCode, countryName,           │
│                         │          regionName, ispOrOrg, bytesSent,    │
│                         │          bytesReceived, actionStatus,        │
│                         │          riskLevel, riskReason, hitCount,    │
│                         │          firstSeenAt, lastSeenAt             │
│                         │ Trigger: Trimmed to max 400 rows             │
├─────────────────────────┼──────────────────────────────────────────────┤
│ vpn_guard_profiles      │ PK: id (TEXT)                                │
│                         │ Columns: name, providerType ("WIREGUARD",    │
│                         │          "CLOUDFLARE_WARP", "VPNGATE"),      │
│                         │          rawConfig, endpointHost,            │
│                         │          interfaceAddresses, dnsServers,     │
│                         │          mtu, routingMode ("WHOLE_SYSTEM",   │
│                         │          "SELECTED_APPS", "EXCLUDE_APPS"),   │
│                         │          targetPackagesCsv, createdAt,       │
│                         │          lastConnectedAt                     │
└─────────────────────────┴──────────────────────────────────────────────┘
```

---

## 6. Architectural Risk Hotspots for Refactoring

1. **Android Single VPN Slot Contention:**
   * Both `NetGateVpnService` (local per-app firewall) and `VpnGuardService` (WireGuard `GoBackend`) require the single Android `VpnService` interface.
   * `VpnGuardManager.connect()` pauses `NetGateVpnService` and polls `NetGateVpnService.runtimeState` in a 50 ms loop up to 2,000 ms. If interrupted, the app can be left in an unrouted state.
2. **Monolithic Source Files:**
   * `FirewallViewModel.kt` (2,226 lines) coordinates rules, timers, VPNGate, speed tests, security audits, hotspot tethering, and DPI bypass in a single class.
   * `NetGateVpnService.kt` (2,116 lines) merges raw byte buffer TUN parsing, notification lifecycle, Wi-Fi idle automation, timer scheduling, and proxy server lifecycle.
3. **Singleton Bypasses of Data Layer:**
   * Widespread use of mutable `object` singletons (`NetGateVpnService.runtimeState`, `VpnGuardManager`, `HotspotProxyManager`, `SystemNetworkMonitor`, `WifiAutomationManager`, `ConnectionMonitorHub`, `AdBlockFilterEngine`).
   * Several singletons call `NetGateDatabase.getInstance(context)` or `FirewallPreferences` directly rather than through `FirewallRepository`.
4. **Redundant Timer & Automation Polling:**
   * Timer checks and screen-off Wi-Fi idle evaluations run concurrently across the ViewModel (1 Hz), VPN loop (3 Hz), and `AppTimerReceiver` (AlarmManager).

---

## 7. Regression-Test Checklist for Refactoring

Prior to refactoring or modifying code, execute and verify the following Critical User Journeys (CUJs):

- [ ] **CUJ-01: Per-App Network Isolation**
  - Verify that adding a rule blocking Wi-Fi for `com.aistudio.netgate.test` generates a `TunnelPlan` containing that UID in the sinkhole route without affecting unblocked packages.
- [ ] **CUJ-02: DNS Interception & SafeSearch Rewriting**
  - Verify that UDP/53 queries for `google.com` return an `A` record rewritten to `forcesafesearch.google.com` (`216.239.38.120`) when SafeSearch is enabled.
- [ ] **CUJ-03: Ad & Tracker Dropping**
  - Verify that queries matching `||doubleclick.net^` or entries in `BUILTIN_AD_CORE_DOMAINS` return synthetic `NXDOMAIN` (0.0.0.0 / ::) immediately without contacting upstream resolvers.
- [ ] **CUJ-04: DPI Bypass & SNI Chunk Splitting**
  - Verify that a TLS ClientHello containing `SNI=example.com` has its SNI byte range identified correctly and divided into chunks conforming to `dpiFragmentChunkSize`.
- [ ] **CUJ-05: Cloudflare WARP Config Generation**
  - Verify that `WarpConfigGenerator` registers with Cloudflare's API, returns valid Curve25519 keys, and constructs a WireGuard configuration matching `com.wireguard.config.Config` syntax.
- [ ] **CUJ-06: VPN Service Arbitration & Handoff**
  - Verify that connecting a WireGuard profile pauses the local firewall without crashing or leaking unencrypted packets.
- [ ] **CUJ-07: Room v8 Schema & Migration Integrity**
  - Verify that all 8 tables and 7 DAOs instantiate and query properly without SQLite exceptions or missing column errors.
- [ ] **CUJ-08: DataStore Preferences State Consistency**
  - Verify that reading and editing any of the 51 preferences emits updated `FirewallPreferencesState` objects across coroutine flows without deadlock.
