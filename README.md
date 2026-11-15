# NetGate

A fast, **root-free** and **banking-safe** per-app Wi-Fi & Mobile Data firewall for Android (Android 7.0+ / Android 16 ready), letting you select multiple apps at once to block or allow Wi-Fi and Mobile Data instead of toggling them manually one by one in System App Info.

---

## What's New

- **Network Speed Test:** Built-in real-time **Ping**, **Jitter**, **Download (Mbps)**, and **Upload (Mbps)** test with an animated speedometer gauge.
- **Live Radio & Connection Info:** Simultaneous **Wi-Fi** (`ON • Active` / `ON • Idle` / `OFF`) and **Mobile Data** (`ON • Active` / `ON` / `OFF`) status chips, plus a detailed **Connection & Wi-Fi Info** sheet.
- **Frosted Glass UI + Material You:** Translucent glassmorphism cards over an atmospheric blurred backdrop, **Material You Dynamic Color** (Android 12+), and **Auto / Day / Night** theme switching.
- **Banking-Safe Guarantee:** Zero Accessibility services, zero screen overlays, and zero root required.

---

## Features

- **Batch Select & Apply:** Tap **Select** (or long-press any app) to pick multiple apps—or **Select All**—and apply **Block Wi-Fi**, **Block Data**, **Block Both**, or **Allow All** in one tap.
- **Per-App Wi-Fi & Mobile Data Toggles:** Control Wi-Fi and cellular access independently right from each app row.
- **Connection & Wi-Fi Telemetry:** View Wi-Fi standard (Wi-Fi 4/5/6/7), frequency band (`2.4 / 5 / 6 GHz`), signal strength (`dBm`), Tx/Rx link speed, interface name, IPv4/IPv6 addresses, gateway, DNS servers, bandwidth estimate, and metered status.
- **Built-In Speed Test:** Measure Ping, Jitter, Download, and Upload speeds directly on your active Wi-Fi or Mobile Data connection.
- **Frosted Glass + Material You Themes:** Switch between **Auto**, **Day**, and **Night** modes from the top bar or settings, with optional Material You wallpaper dynamic colors.
- **Banking-Safe (Zero Accessibility Permissions):** Uses **no** Accessibility Service, **no** screen overlays, and **no** root—so banking and payment apps are never flagged or disrupted.
- **1-Tap System App Info Shortcut:** Open any app's Android App Info page directly from its detail sheet whenever you want to inspect system data usage settings.

---

## How It Works

NetGate uses Android's standard local `VpnService` paired with a `ConnectivityManager.NetworkCallback` monitor:

1. It tracks whether your device's active physical internet connection is **Wi-Fi** or **Mobile Data**.
2. Using `VpnService.Builder.addAllowedApplication(...)`, **only apps blocked on the active connection** are routed into the local sinkhole tunnel.
3. All allowed apps bypass the local tunnel completely and run at full native speed.

> **Note:** Android permits only one active VPN connection at a time. Enabling another VPN app will pause NetGate's local firewall.

---

## Permissions

NetGate requests the minimum standard permissions required:

| Permission / Declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Creates the local on-device firewall tunnel (user consent prompt on first enable) |
| `INTERNET` | Runs the built-in real network speed test (Ping, Download, Upload) |
| `ACCESS_NETWORK_STATE` | Detects active Wi-Fi vs. Mobile Data transport and IP/DNS link properties |
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
- **Networking & Background:** Android `VpnService`, `ConnectivityManager`, `WifiManager`, Kotlin Coroutines & `StateFlow`
- **Testing:** JUnit 4, Kotlin Coroutines Test, Robolectric
