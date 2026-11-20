# What's New

### New Features
- **Hotspot Proxy Sharing (No Root):**
  - Added a dedicated **Hotspot & Wi-Fi** tab with a LAN-accessible HTTP/HTTPS relay proxy (`0.0.0.0:8890`, with `:8080`, `:3128`, or custom port presets) to share filtered or VPN-routed connections with tethered devices.
  - **NetGate Filter Sharing:** When NetGate Firewall is active, connected Hotspot clients inherit your Ad Blocker, YouTube Ad Block, Website Blocklist/Whitelist, URL tracking parameter stripping, SafeSearch, and YouTube Restricted Mode rules.
  - **External VPN App Relay:** When NetGate Firewall is paused and a third-party VPN app is connected, Hotspot Proxy traffic routes directly through that active VPN tunnel.
  - Auto-detects active Hotspot interfaces (`ap*`, `wlan1`, `swlan*`, `rndis*`) with 1-tap copy for the `IP:Port` endpoint, an interactive non-root setup guide, and a quick shortcut to Android Hotspot Settings.
- **Per-Device Hotspot Tracking, Blocking & Data Caps:**
  - Tracks live download/upload usage (`↓` / `↑`), request counts, and last accessed host for each connected client IP.
  - Lets you assign custom device nicknames, toggle instant **Block / Allow** per IP, set per-device data usage caps (`100 MB` – `5 GB` or Unlimited), reset session counters, and view 24-hour OS Tethering totals (`UID_TETHERING` when Usage Access is granted).
- **Global Wi-Fi Traffic Lock & Automation:**
  - **Global Wi-Fi Traffic Lock:** 1-tap switch that pauses all device Wi-Fi app traffic through NetGate's local VPN sinkhole without root access, paired with a live alert banner on the main Firewall screen.
  - **Disable Wi-Fi on Timer:** Schedule `5m`, `15m`, `30m`, `1h`, or custom countdown timers via `AlarmManager` to automatically lock all Wi-Fi app traffic on expiration.
  - **Auto-Lock When Idle:** Monitors live Wi-Fi throughput and automatically locks Wi-Fi traffic when usage stays below `2 KB/s` for your selected idle duration (`3m` – `30m`).
  - **1-Tap Wi-Fi Panel Notifications:** Lock notifications include **Wi-Fi Panel** (opens Android's system Wi-Fi switch overlay) and **Restore Wi-Fi** actions.

### Improvements & Verification
- Added unit and Robolectric Compose UI tests covering per-client Hotspot blocking and data caps, Hotspot interface detection, Wi-Fi idle throughput state machine, DataStore persistence, and **Hotspot & Wi-Fi** UI controls.
- Rebuilt `NetGate-debug.apk` with the new **Hotspot & Wi-Fi** tab and automation services.
