# NetGate

A fast, **root-free** and **banking-safe** per-app Wi-Fi & Mobile Data firewall for Android (**Android 7.0+ / API 24 – Android 16 / API 36**). Block or allow Wi-Fi and Mobile Data for multiple apps at once, visualize **Blocked vs. Allowed** app data consumption with a Room-backed bar chart, run a built-in network speed test, and enable **Cloudflare `1.1.1.1` Private DNS**—all from a sleek **floating frosted-glass capsule dock**.

---

## What's New

- **Floating Frosted-Glass Bottom Dock:**
  - Centered pill-shaped translucent glass dock hovering cleanly above the system navigation bar with specular sheen, subtle rim border, and an animated glowing capsule around the active icon.
  - Instant 1-tap access to **Firewall**, **Data Usage**, **Speed Test**, **1.1.1.1 DNS**, and **Connection Info** (adapts to a side `NavigationRail` on tablets & foldables).
- **Room-Backed Data Usage Screen & Bar Chart (Blocked vs. Allowed):**
  - Upgrades NetGate's Room database (`version 2` with `MIGRATION_1_2` preserving existing rules) with an `app_traffic_usage` table joined directly against `app_network_rules` via SQLite `LEFT JOIN`.
  - Syncs real per-app Wi-Fi and Mobile Data consumption from Android's `NetworkStatsManager` (`24h`, `7 Days`, `30 Days`) with an interactive **Blocked vs. Allowed** animated bar chart (`Total` and `Wi-Fi vs Data` modes) and tap-to-filter per-app breakdown.
- **Cloudflare `1.1.1.1` DNS Protection (Concurrent Bypass Helper):**
  - Detects live Android **Private DNS** status (`one.one.one.one`, custom provider, Automatic, or ISP default), provides encrypted DNS presets with **1-tap Copy & Open Private DNS Settings**, and runs a live **Cloudflare DoH Reachability & Latency Check**—working concurrently alongside NetGate's `VpnService` firewall.
- **Upgraded Network Speed Test:** Multi-mirror HTTPS download fallback and fixed-length upload streaming alongside TCP Ping & Jitter, displayed in an animated speedometer sheet.
- **Live Radio & Connection Info + Material You:** Simultaneous **Wi-Fi** and **Mobile Data** ON/OFF radio chips, full Wi-Fi/IP/DNS/Private DNS telemetry, **Material You Dynamic Color** (Android 12+), and **Auto / Day / Night** theme switching.

---

## Features

- **Batch Select & Apply:** Tap **Select** (or long-press any app) to pick multiple apps—or **Select All**—and apply **Block Wi-Fi**, **Block Data**, **Block Both**, or **Allow All** in one tap.
- **Per-App Wi-Fi & Mobile Data Toggles:** Control Wi-Fi and cellular access independently right from each app row.
- **Blocked vs. Allowed Data Bar Chart:** Track how much Wi-Fi and Mobile Data your blocked vs. allowed apps consume using Room SQL aggregation and `NetworkStatsManager`.
- **Cloudflare `1.1.1.1` Encrypted DNS Helper:** Pick a Cloudflare/Google/Quad9 hostname, copy and jump straight to system Private DNS settings, and verify live Cloudflare DoH reachability.
- **Connection & Wi-Fi Telemetry:** View Wi-Fi standard (Wi-Fi 4/5/6/7), frequency band (`2.4 / 5 / 6 GHz`), signal strength (`dBm`), Tx/Rx link speed, interface name, IPv4/IPv6 addresses, gateway, DNS servers, Private DNS mode, bandwidth estimate, and metered status.
- **Built-In Speed Test:** Measure Ping, Jitter, Download (`Mbps`), and Upload (`Mbps`) on your active Wi-Fi or Mobile Data connection with automatic multi-CDN fallback.
- **Floating Frosted Glass + Material You Themes:** Edge-to-edge frosted glass surfaces with **Auto**, **Day**, and **Night** modes plus optional Material You wallpaper dynamic colors.
- **Banking-Safe (Zero Accessibility Permissions):** Uses **no** Accessibility Service, **no** screen overlays, and **no** root—so banking and payment apps are never flagged or disrupted.
- **1-Tap System App Info Shortcut:** Open any app's Android App Info page directly from its detail sheet whenever you want to inspect system data usage settings.

---

## How It Works

### Per-App Firewall (`VpnService`)
NetGate uses Android's standard local `VpnService` paired with a `ConnectivityManager.NetworkCallback` monitor:

1. It tracks whether your device's active physical internet connection is **Wi-Fi** or **Mobile Data**.
2. Using `VpnService.Builder.addAllowedApplication(...)`, **only apps blocked on the active connection** are routed into the local sinkhole tunnel.
3. All allowed apps bypass the local tunnel completely and run at full native speed.

### Room-Backed Data Usage Aggregation
Real per-app Wi-Fi & Mobile Data stats from `NetworkStatsManager` are synced into Room (`app_traffic_usage`) and joined directly with `app_network_rules` (`LEFT JOIN`). Whenever you block or allow an app, the **Blocked vs. Allowed** totals and bar chart update automatically.

### Cloudflare `1.1.1.1` DNS Protection Alongside NetGate
Android permits only **one active `VpnService`** at a time, so running an external VPN app pauses NetGate's local firewall. Configuring **Android Private DNS (DNS-over-TLS)** with `one.one.one.one` encrypts DNS queries at the OS level so ISP DNS filtering is bypassed **while NetGate's per-app firewall remains active**.

---

## Permissions

NetGate requests only the standard permissions needed for its firewall, telemetry, DNS check, speed test, and data usage chart:

| Permission / Declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Creates the local on-device firewall tunnel (user consent prompt on first enable) |
| `INTERNET` | Runs the built-in network speed test and Cloudflare DoH reachability check |
| `ACCESS_NETWORK_STATE` | Detects active Wi-Fi vs. Mobile Data transport, IP/DNS link properties, and Private DNS status |
| `ACCESS_WIFI_STATE` | Reads Wi-Fi radio state, band, link speed, and RSSI signal strength |
| `PACKAGE_USAGE_STATS` *(optional, granted in Settings)* | Reads per-app Wi-Fi & Mobile Data bytes from `NetworkStatsManager` for the Data Usage bar chart |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps firewall rules enforced reliably in the background |
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

- **Language & UI:** Kotlin, Jetpack Compose, Custom Canvas Bar Chart & Floating Frosted Glass UI, Material Design 3 (Material You Dynamic Color)
- **Persistence:** Room (`AppNetworkRuleEntity`, `AppTrafficUsageEntity`, `MIGRATION_1_2`) & Jetpack DataStore Preferences
- **Networking & Telemetry:** Android `VpnService`, `NetworkStatsManager`, `ConnectivityManager`, `WifiManager`, `LinkProperties` Private DNS detection, Kotlin Coroutines & `StateFlow`
- **Testing:** JUnit 4, Kotlin Coroutines Test, Robolectric
