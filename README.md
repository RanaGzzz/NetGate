# NetGate

A root-free Android per-app firewall, website blocker, and network traffic monitor powered by Android's local `VpnService`, `NetworkStatsManager`, and Room persistence. Control Wi-Fi and Mobile Data access per application, schedule automatic countdown timers to cut off app connectivity, filter or lock down websites across browsers, monitor Sent/Received (`Tx`/`Rx`) historical usage, run a built-in speed test, and configure encrypted Private DNS from a single unified workspace.

---

## Key Features

### 1. Per-App Wi-Fi & Mobile Data Firewall
- **Granular Network Toggles:** Independently enable or block **Wi-Fi** and **Mobile Data** for every installed app without requiring root access.
- **Batch Management:** Enter multi-select mode (via **Select** or long-press) to apply **Block Wi-Fi**, **Block Data**, **Block Both**, or **Allow All** across multiple apps at once.
- **Instant Transport Detection:** Automatically monitors active Wi-Fi and cellular networks via `ConnectivityManager.NetworkCallback` and enforces the matching rule set immediately when switching networks.
- **Search & Smart Filters:** Filter apps by **All Apps**, **Restricted**, **Wi-Fi Off**, **Data Off**, **Allowed**, or **Timers**.

### 2. Per-App Auto-Disable Countdown Timers
- **Scheduled Cutoffs:** Set a countdown timer on any app to automatically disable **Wi-Fi & Data**, **Wi-Fi Only**, or **Data Only** when the timer reaches `00:00`.
- **Presets & Custom Durations:** Pick quick presets (`1m`, `5m`, `15m`, `30m`, `1h`, `2h`) or adjust any custom duration in minutes.
- **Live Indicators & Background Persistence:** Active timers display a live countdown badge directly in the app list, persist in Room across restarts, and execute reliably via `NetGateVpnService` and `AlarmManager`.

### 3. Website Blocker & Lockdown Mode
- **Three Filtering Modes:**
  - **Off:** Website filtering disabled; only per-app firewall rules apply.
  - **Blocklist:** Blocks domains in your Blocklist (including all subdomains such as `*.domain.com`) while allowing Whitelist exceptions.
  - **Whitelist Only (Lockdown):** Blocks all internet domains by default (`NXDOMAIN`), permitting access only to explicitly **Whitelisted websites** and **Selected Allowed Apps**.
- **Anti-DoH & Dual-Stack IPv4/IPv6 Protection:** Intercepts both IPv4 and IPv6 UDP/53 DNS queries, blocks browser DoH canary/discovery domains (`use-application-dns.net`, `_dns.resolver.arpa`), and returns immediate TCP `RST` responses on major public DoH/DoT resolver IPs so modern browsers (including Chrome and Firefox) respect your domain rules.
- **Manual Entry & Live Query Feed:** Paste full URLs or root domains with automatic normalization, add popular domains in one tap, inspect recently blocked domains, and allow trusted apps to bypass website filtering.

### 4. Sent/Received & Historical Data Usage Monitor
- **Sent vs. Received Breakdown:** Calculates and displays total **Received (`↓ Rx`)** and **Sent (`↑ Tx`)** traffic across both Wi-Fi and Mobile Data using Android's `NetworkStatsManager`.
- **Room Historical Daily Bar Chart:** Archives per-day Wi-Fi and Mobile `Rx`/`Tx` totals into Room (`app_traffic_history`) for multi-day trend visualization with tap-to-inspect daily details.
- **Blocked vs. Allowed Comparison:** Compares total Wi-Fi and cellular data consumed by restricted versus allowed apps across `24h`, `7 Days`, and `30 Days` windows, alongside per-app `Rx`/`Tx` breakdowns.

### 5. Network Speed Test & Private DNS Tools
- **Built-In Speed Test:** Measures Ping, Jitter, Download (`Mbps`), and Upload (`Mbps`) on the active Wi-Fi or Mobile Data connection.
- **Encrypted DNS (`1.1.1.1`) Assistant:** Inspects live Android Private DNS status, provides quick-copy hostnames for Cloudflare (`one.one.one.one`, Malware, and Family), Google DNS, and Quad9, tests Cloudflare DoH latency, and opens system Private DNS settings directly.
- **Connection Telemetry:** Displays active transport type, interface name, IPv4/IPv6 addresses, gateway, DNS servers, metered status, and Wi-Fi link telemetry (band, standard, RSSI signal strength, and Tx/Rx link speeds).

---

## Architecture & How It Works

### Local `VpnService` Data Plane
- **App Firewall Mode (`Website Mode = Off`):** Routes only apps blocked on the active transport into a local sinkhole TUN interface (`addAllowedApplication`), leaving allowed apps untouched on the physical network.
- **Website Filtering Mode (`Blocklist` / `Whitelist Only`):** Runs a dual-stack IPv4/IPv6 UDP/53 DNS proxy and DoH/DoT TCP `RST` guard inside `NetGateVpnService`. Blocked domains receive immediate `NXDOMAIN` replies, permitted queries forward via protected sockets (`protect()`), and selected bypass apps route outside the filter (`addDisallowedApplication`).

### Room Database Persistence
- `app_network_rules`: Per-app Wi-Fi and Mobile Data firewall rules.
- `app_network_timers`: Scheduled per-app countdown timers with expiration timestamps and target transport flags.
- `website_rules`: Enabled/disabled Blocklist (`BLOCK`) and Whitelist (`ALLOW`) domain rules.
- `app_traffic_usage`: Synced per-app Wi-Fi and Mobile `Rx`/`Tx` usage snapshots for the active time window.
- `app_traffic_history`: Daily archived Wi-Fi and Mobile `Rx`/`Tx` records keyed by calendar day and package name.

### Private DNS Note
Android permits one active `VpnService` at a time. When using the **Website Blocker**, keep Android **Private DNS** set to **Automatic** or **Off** so DNS lookups pass through NetGate's local DNS filter. When using only the per-app firewall, Private DNS (`one.one.one.one`) can remain enabled alongside NetGate.

---

## Permissions

| Permission / Declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Establishes the local on-device firewall and DNS filtering tunnel |
| `INTERNET` | Powers the built-in speed test, Cloudflare DoH latency probe, and upstream DNS forwarding |
| `ACCESS_NETWORK_STATE` | Detects active Wi-Fi vs. Mobile Data networks, link properties, and Private DNS status |
| `ACCESS_WIFI_STATE` | Reads Wi-Fi radio state, band, link speed, and signal strength |
| `PACKAGE_USAGE_STATS` *(optional)* | Reads per-app `Rx`/`Tx` Wi-Fi and Mobile Data bytes from `NetworkStatsManager` |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps firewall rules, website filtering, and countdown timers enforced reliably in the background |
| `POST_NOTIFICATIONS` *(optional)* | Displays the persistent firewall status notification with a quick **Turn Off** action |
| `<queries>` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) | Enumerates launchable user and system apps cleanly |

---

## Technology Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material 3, Custom Canvas Charts
- **Architecture & Concurrency:** MVVM, Repository Pattern, Kotlin Coroutines & `StateFlow`
- **Storage:** Room Database, Jetpack DataStore Preferences
- **System Networking:** Android `VpnService`, `ConnectivityManager`, `NetworkStatsManager`, `WifiManager`, `AlarmManager`
