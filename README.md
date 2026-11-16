# NetGate

A fast, **root-free** and **banking-safe** per-app firewall & website blocker for Android (**Android 7.0+ / API 24 – Android 16 / API 36**). Block or allow Wi-Fi and Mobile Data per app, filter or lock down specific websites with **Blocklist**, **Whitelist**, and **Strict Whitelist Lockdown** modes, visualize **Blocked vs. Allowed** data consumption with a Room-backed bar chart, run a built-in speed test, and configure **Cloudflare `1.1.1.1` Private DNS**—all anchored by a **constant floating frosted-glass bottom dock**.

---

## What's New

- **Website Blocker & Strict Lockdown Tab (`Websites`):**
  - **Three Filter Modes:**
    - **Off:** Website filtering disabled; only per-app Wi-Fi / Mobile Data rules run.
    - **Blocklist:** Blocks domains in your Blocklist (including subdomains like `*.domain.com`) while letting other websites through, with Whitelist entries acting as explicit exceptions.
    - **Whitelist Only (Lockdown):** Blocks **all** internet domains by default (`NXDOMAIN`)—allowing internet access **only** to your **Whitelisted domains** and **Selected Allowed Apps**.
  - **Manual Entry, Quick Chips & Live Feed:** Paste a URL or type any domain (`https://www.example.com/path` auto-normalizes to `example.com`), use 1-tap quick-add domain chips, toggle or delete rules anytime, and whitelist recently blocked domains in one tap.
  - **Selected Allowed Apps Bypass:** Search and pick trusted apps that bypass Website Blocker & Lockdown via `VpnService.Builder.addDisallowedApplication(...)` while respecting per-app firewall blocks.
- **New Futuristic Adaptive App Icon:**
  - Glowing emerald (`#10B981`) and electric-cyan (`#38BDF8`) holographic hexagonal cyber-gate emblem on a deep `#0A1626` obsidian backdrop, packaged across adaptive XML layers and `mdpi`–`xxxhdpi` square & round PNG densities.
- **Constant Floating Frosted-Glass Bottom Dock Across All Options:**
  - Converted **Speed Test** and **1.1.1.1 DNS** from overlay sheets into full in-app screens so the translucent frosted capsule dock remains **constantly visible and active** across all five tabs (**Firewall**, **Websites**, **Data Usage**, **Speed Test**, **1.1.1.1 DNS**) on phones (and adapts to a persistent `NavigationRail` on tablets & foldables).
- **Room-Backed Data Usage Bar Chart (`Blocked vs. Allowed`):**
  - Upgrades Room to `version 3` (`MIGRATION_1_2` + `MIGRATION_2_3` preserving all existing rules), joining `app_traffic_usage` (`NetworkStatsManager`) with `app_network_rules` via SQLite `LEFT JOIN` to animate **Blocked vs. Allowed** totals (`Total` & `Wi-Fi vs Data` split modes).

---

## Features

- **Batch Select & Per-App Firewall:** Tap **Select** (or long-press any app) to apply **Block Wi-Fi**, **Block Data**, **Block Both**, or **Allow All**, or toggle Wi-Fi & cellular independently on each app row.
- **Specific Website Blocker & Whitelist Lockdown:** Enforce Blocklist rules, maintain Whitelist exceptions, or lock down the entire internet except selected websites and selected apps.
- **Blocked vs. Allowed Data Bar Chart:** Compare Wi-Fi and Mobile Data consumed by blocked vs. allowed apps (`24h`, `7 Days`, `30 Days`) with tap-to-filter per-app breakdowns.
- **Cloudflare `1.1.1.1` Encrypted DNS Protection:** Check live Android **Private DNS** status, copy Cloudflare/Google/Quad9 hostnames, jump straight to system Private DNS settings, and test Cloudflare DoH latency.
- **Built-In Network Speed Test:** Measure Ping, Jitter, Download (`Mbps`), and Upload (`Mbps`) on your active Wi-Fi or Mobile Data connection with multi-CDN fallback.
- **Connection & Wi-Fi Telemetry:** View Wi-Fi standard (`Wi-Fi 4/5/6/7`), band (`2.4 / 5 / 6 GHz`), signal strength (`dBm`), Tx/Rx link speeds, interface name, IPv4/IPv6 addresses, gateway, DNS servers, Private DNS mode, and metered status.
- **Banking-Safe (Zero Accessibility Permissions):** Uses **no** Accessibility Service, **no** screen overlays, and **no** root—so banking and payment apps are never flagged or disrupted.

---

## How It Works

### 1. Per-App Firewall & Website DNS Guard (`VpnService`)
NetGate pairs Android's local `VpnService` with a `ConnectivityManager.NetworkCallback` transport monitor:

- **Per-App Only (`Website Mode = Off`):** Routes only apps blocked on the active network (Wi-Fi or Mobile Data) via `addAllowedApplication(...)` into a local sinkhole tunnel while allowed apps run untouched at native speed.
- **Website Filtering (`Blocklist` / `Whitelist Only`):** Runs an on-device IPv4/UDP port-53 DNS proxy (`10.1.10.2`) inside `NetGateVpnService`. Blocked or non-whitelisted domains receive immediate `NXDOMAIN` responses, allowed domains forward through `protect()`ed UDP sockets (`1.1.1.1` / `8.8.8.8`), and **Selected Allowed Apps** bypass the DNS filter cleanly via `addDisallowedApplication(...)`.

### 2. Room-Backed Persistence (`v3`)
- `app_network_rules`: Per-app Wi-Fi and Mobile Data block rules.
- `website_rules`: Manual & quick-added Blocklist (`BLOCK`) and Whitelist (`ALLOW`) domain rules with enable/disable state.
- `app_traffic_usage`: Synced `NetworkStatsManager` bytes joined with `app_network_rules` for the **Blocked vs. Allowed** bar chart.

### 3. Cloudflare `1.1.1.1` Private DNS Note
Android permits one active `VpnService` at a time. When using **Website Blocker**, set Android **Private DNS** to **Automatic** or **Off** so DNS queries pass through NetGate's local filter (an inline banner alerts you automatically if strict `hostname` Private DNS mode is active). When you only need per-app blocking plus encrypted DNS, enabling `one.one.one.one` runs seamlessly alongside NetGate's per-app firewall.

---

## Permissions

| Permission / Declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Creates the local on-device firewall and DNS filter tunnel |
| `INTERNET` | Runs the speed test, Cloudflare DoH reachability check, and upstream DNS forwarding for allowed domains |
| `ACCESS_NETWORK_STATE` | Detects active Wi-Fi vs. Mobile Data transport, IP/DNS link properties, and Private DNS status |
| `ACCESS_WIFI_STATE` | Reads Wi-Fi radio state, band, link speed, and RSSI signal strength |
| `PACKAGE_USAGE_STATS` *(optional, granted in Settings)* | Reads per-app Wi-Fi & Mobile Data bytes from `NetworkStatsManager` for the Data Usage bar chart |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps firewall and website rules enforced reliably in the background |
| `POST_NOTIFICATIONS` *(Android 13+, optional)* | Displays the ongoing firewall status notification with a quick **Turn Off** action |
| `<queries>` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) | Lists installed launchable apps without requesting `QUERY_ALL_PACKAGES` |

---

## Download or Build the APK

### Option 1: Pre-built Debug APK
1. Open the [`apk/`](apk/) folder in this repository.
2. Tap **`NetGate-debug.apk`** → **Download raw** and install it on your Android device.

### Option 2: GitHub Actions Build
Every push to `main` / `master` (or manual **workflow_dispatch**) triggers [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml):
1. Go to the **Actions** tab in GitHub.
2. Open the latest **Build NetGate APK** run.
3. Download the **`NetGate-debug-apk`** artifact and extract `app-debug.apk`.

### Option 3: Build Locally
Requires **JDK 21** and **Gradle 9.3.1** (or Android Studio):

```bash
base64 -d debug.keystore.base64 > debug.keystore
touch .env
gradle assembleDebug
```

Run unit & Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Custom Canvas Bar Chart & Persistent Floating Frosted-Glass Dock, Material Design 3 (Material You Dynamic Color)
- **Persistence:** Room v3 (`AppNetworkRuleEntity`, `AppTrafficUsageEntity`, `WebsiteRuleEntity`, `MIGRATION_1_2`, `MIGRATION_2_3`) & Jetpack DataStore Preferences
- **Networking & Telemetry:** Android `VpnService`, Custom RFC 1035 IPv4/UDP DNS Packet Filter (`DnsPacketFilter`), `NetworkStatsManager`, `ConnectivityManager`, `WifiManager`, Kotlin Coroutines & `StateFlow`
- **Testing:** JUnit 4, Kotlin Coroutines Test, Robolectric
