# NetGate

**A root-free Android firewall, website blocker, and network monitor.**

NetGate uses Android's local `VpnService` to manage per-app network access and filter websites. It also tracks network usage, runs connection speed tests, and helps configure Private DNS.

## Features

- **Per-app firewall:** Control Wi-Fi and mobile data separately, manage apps in batches, and filter the app list by network status.
- **Countdown timers:** Schedule an app's Wi-Fi, mobile data, or both to be blocked after a chosen duration.
- **Website blocker:** Use a domain blocklist with whitelist exceptions, or enable whitelist-only lockdown mode.
- **DNS protection:** Filter IPv4 and IPv6 DNS traffic, block browser DoH discovery, and inspect recently blocked domains.
- **Usage monitoring:** View per-app and historical Wi-Fi/mobile data usage, separated into sent (`Tx`) and received (`Rx`).
- **Network tools:** Run a speed test, inspect connection details, and check or configure Android Private DNS.

## Screenshots

Add screenshots to `docs/screenshots/`, then replace these placeholders with image links.

| Firewall | Website blocker | Data usage |
|---|---|---|
| *Screenshot placeholder — `docs/screenshots/firewall.png`* | *Screenshot placeholder — `docs/screenshots/websites.png`* | *Screenshot placeholder — `docs/screenshots/usage.png`* |

## Download APK

Download `app-debug.apk` from the [NetGate Releases page](https://github.com/RanaGzzz/NetGate/releases). Open a release and select **app-debug.apk** under **Assets**.

The APK is a debug build for testing, not a Play Store release.

## Build locally

Install Git, JDK 21, Gradle 9.3.1, and the Android SDK platform used by this project (API 36, extension 1). The repository does not include a Gradle wrapper.

Clone the repository and enter its directory:

```shell
git clone https://github.com/RanaGzzz/NetGate.git
cd NetGate
```

If you don't already have a local `.env`, create one from the example. Add any required local secrets there; do not commit `.env`.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

On macOS or Linux, use `test -f .env || cp .env.example .env`.

Create the debug signing key once per checkout. Keep this key for subsequent local builds if you want to install new builds over earlier ones.

```shell
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Build the debug APK:

```shell
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected Android device with `adb` available, run:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How it works

- **App firewall:** Applies rules for the active network transport using Android's local VPN interface, without requiring root.
- **Website filtering:** Proxies DNS requests through the VPN, forwards permitted queries using protected sockets, and blocks domains according to the selected mode. Selected apps can bypass website filtering.
- **Persistence:** Room stores network rules, timers, website rules, usage snapshots, and daily traffic history.

> **Private DNS:** Android allows one active `VpnService` at a time. When website filtering is enabled, set Android Private DNS to **Automatic** or **Off** so DNS requests can pass through NetGate's filter.

## Permissions

| Permission / declaration | Purpose |
|---|---|
| `BIND_VPN_SERVICE` | Runs the local firewall and website filter |
| `INTERNET` | Supports speed tests and DNS forwarding |
| `ACCESS_NETWORK_STATE` | Detects the active network and connection details |
| `ACCESS_WIFI_STATE` | Reads Wi-Fi link information |
| `PACKAGE_USAGE_STATS` *(optional)* | Reads per-app network usage from `NetworkStatsManager` |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps firewall rules and timers active in the background |
| `POST_NOTIFICATIONS` *(optional)* | Shows the firewall status notification |
| `<queries>` for launcher apps | Finds apps that can be launched |

## Technology

- **Language and UI:** Kotlin, Jetpack Compose, Material 3
- **Architecture:** MVVM, repositories, coroutines, and `StateFlow`
- **Storage:** Room and Jetpack DataStore
- **Networking:** Android `VpnService`, `ConnectivityManager`, `NetworkStatsManager`, `WifiManager`, and `AlarmManager`
