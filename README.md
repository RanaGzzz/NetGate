# NetGate

**A root-free Android firewall, website blocker, and network monitor.**

[![Build NetGate APK](https://github.com/RanaGzzz/NetGate/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/RanaGzzz/NetGate/actions/workflows/build-apk.yml)

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

## Download the APK

The GitHub Actions workflow builds a debug APK when code is pushed to `main` or `master`, and can also be started manually.

1. Open the [Build NetGate APK workflow](https://github.com/RanaGzzz/NetGate/actions/workflows/build-apk.yml).
2. Select a successful run and download the **NetGate-debug-apk** artifact.
3. Unzip the downloaded artifact to get `app-debug.apk`.

These are debug builds for testing, not Play Store releases. CI generates a temporary signing key for each run, so an APK may not install as an update over one signed by a previous run.

## Build locally

You need JDK 21, Gradle 9.3.1, and the Android SDK platform used by this project (API 36, extension 1). The repository does not include a Gradle wrapper.

Generate the debug keystore expected by the app's debug signing configuration (once per checkout):

```shell
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Then build the APK:

```shell
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

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
