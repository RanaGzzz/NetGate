# NetGate

A fast, **root-free** and **banking-safe** per-app Wi-Fi & Mobile Data firewall for Android (**Android 7.0+ / API 24 – Android 16 / API 36**). Select multiple apps at once to block or allow Wi-Fi and Mobile Data, inspect live network & Wi-Fi telemetry, run an integrated speed test, and enable **Cloudflare `1.1.1.1` Private DNS** to bypass ISP DNS blocks—all without leaving NetGate.

---

## What's New

- **Cloudflare `1.1.1.1` DNS Protection (Concurrent Bypass Helper):**
  - Tap **`1.1.1.1 DNS`** in the firewall header card (displays **`1.1.1.1 On`** when Cloudflare Private DNS is active).
  - Live detection of Android's **Private DNS** state (Cloudflare, custom provider hostname, Automatic mode, or default ISP DNS).
  - Built-in encrypted DNS hostname presets (`one.one.one.one`, `security.cloudflare-dns.com`, `family.cloudflare-dns.com`, `dns.google`, `dns.quad9.net`) with **1-tap Copy & Open Private DNS Settings** and a 3-step setup guide.
  - Live **Cloudflare DoH Reachability & Latency Check** (`cloudflare-dns.com`) directly inside the sheet.
  - Works **alongside NetGate's `VpnService` firewall** simultaneously without displacing your local firewall.
- **Upgraded Network Speed Test:** Multi-mirror HTTPS download fallback and fixed-length upload streaming alongside TCP Ping & Jitter, displayed in an animated speedometer sheet.
- **Connection & Wi-Fi Info + Private DNS Telemetry:** Simultaneous **Wi-Fi** and **Mobile Data** ON/OFF radio chips, full Wi-Fi link stats, IP/DNS routing details, and live **Private DNS** status.
- **Frosted Glass UI + Material You:** Translucent glassmorphism cards over a subtle blurred backdrop with **Material You Dynamic Color** (Android 12+) and **Auto / Day / Night** theme switching.

---

## Features

- **Batch Select & Apply:** Tap **Select** (or long-press any app) to pick multiple apps—or **Select All**—and apply **Block Wi-Fi**, **Block Data**, **Block Both**, or **Allow All** in one tap.
- **Per-App Wi-Fi & Mobile Data Toggles:** Control Wi-Fi and cellular access independently right from each app row.
- **Cloudflare `1.1.1.1` Encrypted DNS Helper:** Detect current Private DNS, pick a Cloudflare/Google/Quad9 hostname, copy and jump straight to system Private DNS settings, and verify live Cloudflare DoH reachability.
- **Connection & Wi-Fi Telemetry:** View Wi-Fi standard (Wi-Fi 4/5/6/7), frequency band (`2.4 / 5 / 6 GHz`), signal strength (`dBm`), Tx/Rx link speed, interface name, IPv4/IPv6 addresses, gateway, DNS servers, Private DNS mode, bandwidth estimate, and metered status.
- **Built-In Speed Test:** Measure Ping, Jitter, Download (`Mbps`), and Upload (`Mbps`) on your active Wi-Fi or Mobile Data connection with automatic multi-CDN fallback.
- **Frosted Glass + Material You Themes:** Switch between **Auto**, **Day**, and **Night** modes from the top bar or settings, with optional Material You wallpaper dynamic colors.
- **Banking-Safe (Zero Accessibility Permissions):** Uses **no** Accessibility Service, **no** screen overlays, and **no** root—so banking and payment apps are never flagged or disrupted.
- **1-Tap System App Info Shortcut:** Open any app's Android App Info page directly from its detail sheet whenever you want to inspect system data usage settings.

---

## How It Works

### Per-App Firewall (`VpnService`)
NetGate uses Android's standard local `VpnService` paired with a `ConnectivityManager.NetworkCallback` monitor:

1. It tracks whether your device's active physical internet connection is **Wi-Fi** or **Mobile Data**.
2. Using `VpnService.Builder.addAllowedApplication(...)`, **only apps blocked on the active connection** are routed into the local sinkhole tunnel.
3. All allowed apps bypass the local tunnel completely and run at full native speed.

### Cloudflare `1.1.1.1` DNS Protection Alongside NetGate
Android permits only **one active `VpnService`** at a time, so running an external VPN app pauses NetGate's local firewall. Instead, configuring **Android Private DNS (DNS-over-TLS)** with `one.one.one.one` encrypts DNS queries at the OS level so ISP DNS filtering is bypassed **while NetGate's per-app firewall remains active**.

---

## Permissions

NetGate requests only the standard permissions needed for its firewall, telemetry, DNS check, and speed test:

| Permission / Declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Creates the local on-device firewall tunnel (user consent prompt on first enable) |
| `INTERNET` | Runs the built-in network speed test and Cloudflare DoH reachability check |
| `ACCESS_NETWORK_STATE` | Detects active Wi-Fi vs. Mobile Data transport, IP/DNS link properties, and Private DNS status |
| `ACCESS_WIFI_STATE` | Reads Wi-Fi radio state, band, link speed, and RSSI signal strength |
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

- **Language & UI:** Kotlin, Jetpack Compose, Material Design 3 (Material You Dynamic Color)
- **Persistence:** Room (`AppNetworkRuleEntity`) & Jetpack DataStore Preferences
- **Networking & Background:** Android `VpnService`, `ConnectivityManager`, `WifiManager`, `LinkProperties` Private DNS detection, Kotlin Coroutines & `StateFlow`
- **Testing:** JUnit 4, Kotlin Coroutines Test, Robolectric
