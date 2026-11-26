# What's New — NetGate 3.9.6

### Interface & Appearance
- **Frosted-Glass Blur Engine:** Added the Frosted-glass blur engine featuring heavy gaussian diffusion, a radiant gradient mesh, and crystalline inner depth.
- **Smart Protection Profiles:** Updated to a sleek pill row with vivid chartreuse active chips and dark translucent cards matching the reference design.
- **Unified Speed Telemetry Settings:** Cleaned up the Network & Usage dashboard by moving all Status Bar Speed Icon controls into the Background & System Settings accordion.
- **Encrypted Backup Action Buttons:** Balanced the Export and Choose File button layout with single-line labels, compact padding, and matched heights.

### Diagnostics & Privacy
- **Process-Isolated Diagnostics:** The diagnostics exporter now strictly captures NetGate's own process instead of the whole device logcat.
- **Hostname Privacy Sanitization:** All real user browsing hostnames are automatically redacted when exporting or sharing diagnostics logs.
- **Idle Memory Optimization:** Eliminated ART GC feedback loops and batched StateFlow log emissions, keeping idle heap memory lean and responsive.

# What's New — NetGate 3.9.5

### Ad & Website Blocker
- **One protection dashboard:** the analytics metrics now live in the Ad & Tracker Blocker card — totals, category breakdowns and the filter engine stats together with the protection controls — and the separate card below lists the Top Blocked Domains and Top Apps.
- **Fixed the broken engine stats row:** "Memory budget: N lists skipped" no longer squeezes into a single vertical letter per line and leaves a large empty gap in the card; the stats wrap onto extra lines instead.

### Network & Usage
- **Join Wi-Fi from the saved networks list:** each Wi-Fi card has a Connect action. Open networks join straight away, secured ones ask for the password, and the result is confirmed with a toast.
- **The joined network is actually used:** the join request now asks for internet access, so the phone moves its traffic (and the VPN) onto the network you just connected to instead of staying on the previous connection.
- **No dead-end WEP prompt:** a WEP network no longer opens a password dialog it could never accept; on Android 10 and newer it points you to the system Wi-Fi settings, where WEP can still be joined.

# What's New — NetGate 3.9.4

### Interface
- Settings rows keep their badge and chevron in one fixed column on the right, so the Appearance, Background & System, Verified, Suite and update badges line up no matter how long the title is.
- The SNI Proxy & HTTPS Filter statistics are three equal tiles (blocked, relayed and endpoint) instead of one stretched, wrapping tile.
- Import, Export, Backup and Clear for Domain Rules & App Exceptions sit on their own row and no longer squeeze the heading into vertical letters.
- Removed the duplicated refresh button from the app bar; the per-screen refresh controls remain.

# What's New — NetGate 3.9.3

### Troubleshooting
- **In-app crash logs:** if NetGate crashes, the exception, stack trace, cause, app version and device details are now saved to the app. Open Settings → Diagnostics & Crash Logs and tap any "App crash" entry to read it or copy it, so a crash can be reported and fixed instead of only appearing in logcat. The five most recent crashes are kept, and clearing the logs deletes them.
- **Filter memory budget is visible:** the ad-block dashboard now shows "Memory budget: N lists skipped" when the device heap limit was reached and some enabled filter lists could not be loaded, so a missing block can be explained instead of looking like a broken list.

# What's New — NetGate 3.9.2

### Stability
- Fixed a launch crash when Android delivered a connectivity callback before the ViewModel's callback guard was initialized.
- Automatic update checks now respect the saved preference.

# What's New — NetGate 3.9

### Stability & Performance
- **Filter sync no longer slows the app down:** a sync no longer drops the working ruleset or rebuilds the matcher after every downloaded list. The ruleset is rebuilt once when the sync finishes, and only one matcher build can run at a time, so back-to-back syncs cannot stack into a memory spike.
- **Smart protection profiles apply instantly:** picking Balanced, Maximum Protection, Privacy Focused, Family Safe or Gaming Mode now writes the whole list selection in a single database batch instead of 20+ separate row updates, each of which refreshed the whole dashboard.
- **Memory-aware filter engine:** the number of blocked domains kept in RAM is now sized from the device heap (120k–450k) instead of loading everything. Domains stored a full metadata object each; they now share a compact owner index, cutting memory per domain by roughly half. If a build still runs out of memory, the previous working ruleset is kept instead of the tunnel going down.
- **Smoother dashboard:** live analytics reach the UI at most once per second with a trailing update, and filter-latency stats publish at most every two seconds, so busy traffic no longer recomposes the screen hundreds of times per second.

### VPN Guard
- **WARP ↔ OpenVPN switching is reliable:** only one connection change runs at a time, switching waits at most 45 seconds for the previous tunnel to release the slot (20 seconds for a disconnect), and WireGuard state callbacks are ignored while OpenVPN owns the tunnel, so a healthy OpenVPN session is no longer marked as disconnected.
- **Wi-Fi and mobile data indicators stay put:** while a VPN is active and the device briefly stops reporting the underlying network, the connection chips keep the last stable Wi-Fi/mobile details instead of flipping to OFF.
- **Auto-reconnect no longer fights a manual switch:** network-change reconnects are skipped while an endpoint switch is already in flight.

# What's New — NetGate 3.8

### Ad & Website Blocker
- **21 free filter subscriptions:** added trusted lists including OISD Big, AdGuard DNS Filter, HaGeZi Multi NORMAL and Threat Intelligence, StevenBlack Unified Hosts, EasyPrivacy, AdAway Mobile Ads, Peter Lowe's, URLHaus Malware, OEM & Device Telemetry, Phishing Army, OpenPhish, HaGeZi Badware Hoster and Dynamic DNS, NoTrack Tracker Blocklist, WindowsSpyBlocker, Samsung/Xiaomi/Huawei Telemetry, and Firebog lists.
- **Balanced & Fast defaults:** new installations enable 8 high-performance lists by default: OISD Big, AdGuard DNS Filter, HaGeZi Multi NORMAL, HaGeZi Threat Intelligence, EasyPrivacy, AdAway Mobile Ads, URLHaus Malware, and OEM & Device Telemetry.
- **Dedicated filter log:** live entries show timestamps, queried domain and full URL, originating app name/package, responsible filter list, action, block reason (Ads, Tracker, Malware, Phishing, Telemetry, or Custom Rule), and hit counts.
- **Per-domain controls:** allow or block a domain, temporarily allow it for 1 hour, 24 hours, or 7 days, or set a permanent allow/block rule from query logs or manual entry.
- **User-managed lists & backup:** manage whitelists and blacklists, import domain rules, export standard Adblock text, and back up or restore all supported settings with JSON.
- **Analytics dashboard:** track total and blocked requests, category breakdowns, top blocked domains, and apps generating the most traffic.
- **Quick log actions:** instantly allow with a duration selector, block, copy a domain, look it up with Whois, scan it with VirusTotal, or report a false positive.
- **Smart protection profiles:** choose Balanced, Maximum Protection, Privacy Focused, Family Safe, or Gaming Mode; Balanced is the default.
- **Faster filtering:** deduplicate rules, load default lists asynchronously, cache lookups with ConcurrentHashMap for sub-10 ms DNS filtering latency, and compress stored data with GZIP.

### Updates
- **Automatic update checks:** check for OTA and GitHub release updates in the background when the app launches.

# What's New — NetGate 3.7

### Protection & Reliability
- **VPN kill switch & auto-reconnect:** if a WireGuard or OpenVPN tunnel drops, NetGate blocks traffic until it recovers, then retries the last profile automatically after a drop or a Wi-Fi ↔ mobile handover.
- **Trusted Wi-Fi auto-VPN:** mark home/work networks as trusted and optionally connect the last VPN Guard profile automatically on unknown SSIDs.
- **Android 16/17 background reliability:** added battery-optimization guidance and foreground-service hardening so filtering survives Doze, battery saver and background restrictions.

### Monitoring & Rules
- **Live app-to-domain inspector:** the network monitor now tags each connection with the app that made it, with 1-tap Block App and Allow App rules from the live feed.
- **Split-tunnel presets:** one-tap Browsers Only, Messaging Only and Bypass Banking & LAN templates for VPN Guard profiles, resolved against the apps actually installed.

### Hotspot
- **PAC file + QR setup:** serve a proxy auto-config file over the hotspot and show a scannable QR code so phones, laptops and TVs configure the proxy in one step.
- **Share the active tunnel:** route tethered traffic through the active VPN Guard tunnel or an external VPN, with clipboard and QR helpers.
- **Per-device controls:** filter profiles (Standard, Kids, Strict), per-client speed limits and data caps, plus new-device approval that holds unknown clients until you allow them.

### Backup & Updates
- **Encrypted backup & restore:** export and import rules, site lists, DNS settings and VPN profiles in a single AES-256-GCM encrypted file.
- **In-app OTA updates:** check the update manifest with a GitHub Releases fallback, download the APK in-app, verify and install it through the system installer, and detect a rebuilt APK for the same version.

# What's New — NetGate 3.6

### Android Compatibility
- **Network diagnostics on older Android:** guarded MTU and per-interface traffic-counter APIs that are unavailable on older OS versions, so unsupported values are reported as unknown instead of risking a monitor crash.

### About & Release Downloads
- **Build identity:** the About card now shows the APK's short source commit and UTC build time.
- **Smaller release APKs:** enabled R8 code and resource shrinking and added separate `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` downloads alongside the universal APK.
- **Release build compatibility:** fixed R8 failures from OkHttp's optional Bouncy Castle, Conscrypt, and OpenJSSE TLS providers; Android continues to use its platform TLS provider.

### Release Engineering
- Hardened publishing with version/tag and `versionCode` checks, upload-certificate and APK metadata verification, per-ABI native-library checks, and SHA-256 checksums.
- Added release build provenance attestations, a CycloneDX SBOM, and uploaded R8 mappings for retracing.
- Pinned release actions, restricted workflow permissions, added lint and dependency-review gates, and automated dependency updates.
- Added shared Android CI setup, bounded workflow concurrency/timeouts, Gradle configuration-cache-safe build identity, and dependency-graph submission; removed the checked-in debug APK.
- Migrated Android source files from the sample `com.example` package to `com.aistudio.netgate`.

# What's New — NetGate 3.5

### New Features
- **Wi-Fi Tab in Network & Usage:** Added a dedicated **Wi-Fi** diagnostics view displaying live connected Wi-Fi and device-saved/nearby profiles with **Standard** (Wi-Fi 4/5/6/7), **Frequency & Band** (2.4 / 5 / 6 GHz + Channel), **Signal Strength** (`dBm` RSSI & %), **Link Speed** (Tx/Rx Mbps), and **Wi-Fi Quality** guidance highlighting **-67 dBm or stronger** as good signal strength.
- **Liquid Glass + Material You Widget:** Upgraded **NetGate Quick Shield** (`NetGateWidgetProvider`) with multi-layer refractive Liquid Glass backdrops/tiles powered by Android 12+ Material You dynamic colors (`system_accent1` / `system_neutral1` / `system_neutral2`) with adaptive Light & Dark fallbacks, plus 1-tap toggles for **VPN**, **DPI**, **Ad Block**, and **Hotspot**.
- **3 New NetGate Launcher Icons & Updated VPN Emblem:** Kept **Lime Shield** unchanged and regenerated the other 3 adaptive icons as **Cyber Gate**, **Tunnel Core**, and **Packet Bastion** with matching Android 13+ monochrome layers; replaced the key logo on VPN Guard with an encrypted globe emblem.
- **Exclude from Recents:** new toggle in **Firewall Settings** using `AppTask.setExcludeFromRecents` so NetGate stays hidden from Android's Recent Apps switcher while running.
- **Background & Battery Saver Controls:** added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` support, a live **Allowed / Restricted** badge with 1-tap exemption launcher in **Firewall Settings**, and a restriction reminder in **System Network Monitor**.
- **GitHub Updates, Changelog & VPN/DPI Logs:** added in-app GitHub Releases update checking, a searchable **Changelog** sheet, and a passive **VPN & DPI Bypass Logs** viewer in Settings.

### Fixes
- **Android 16 & Android 17 Status Bar Speed Icon:** migrated the speed monitor notification channel to `netgate_speed_monitor_channel_v2` at `IMPORTANCE_DEFAULT` (zero sound, vibration, or badges) and removed `.setSilent(true)` / `PRIORITY_LOW` so Android 16/17 SystemUI displays the real-time speed bitmap in the status bar, with explicit low-priority channel detection in **System Network Monitor**.

# What's New — NetGate 3.4

### Interface
- **Top glass toast:** in-app messages now appear as a Liquid Glass pill at the top of the screen instead of
  a snackbar at the bottom, so the floating navigation bar and bottom controls stay uncovered. Tap to dismiss.
- **Distinct app icon styles:** the icon picker offers **Lime Shield** alongside three custom NetGate adaptive icon styles, each with its own Android 13 monochrome layer.

### Fixes
- **Status bar network speed:** the live speed icon appears immediately instead of waiting for Android's
  foreground-service notification delay, and the glyph is drawn larger so it stays readable in the status bar.
- If Android blocks notifications, the monitor card now says so and links to the notification settings instead of
  showing an enabled switch with nothing visible; a refused foreground start now fall back cleanly.

# What's New — NetGate 3.3

### Branding
- **New app icon:** the lime shield mark with the verified badge is now the default adaptive launcher icon,
  with a monochrome layer for Android 13 themed icons and a legacy fallback for older releases.
- **README refresh:** project documentation rewritten around the app's core features, with the new logo and
  current screenshots.

# What's New — NetGate 3.2

### Live Monitor Fix
- **System Network Monitor header:** the **LIVE** badge no longer gets squeezed into vertical text next to the
  long card title. It now sits on the interface line beside the network label, and the title ellipsizes on
  narrow screens.

### Appearance
- **Background animation:** a new picker in Settings offers Circles, Rings, Mesh, Space, Shapes, Snow, Grid,
  Particles, Lava lamp, None and Random backdrops, rendered behind every screen.
- **Parallax effect:** the background can shift smoothly as the device tilts.
- **Seasonal themes:** an optional seasonal tint for the background animation.
- **App icon styles:** choose between Emerald, Neon, Violet and Amber adaptive launcher icons. All use a
  vector foreground with a monochrome layer for Android 13 themed icons.

# What's New — NetGate 3.1

### UI Fixes
- **Readable card titles:** frosted cards now provide the theme's surface content colour, so titles such as
  "VPN Guard Ready" and "Load or Generate VPN Configs" are no longer rendered black on dark themes.
- **DPI Autopilot badge:** the "Autopilot" mode badge no longer wraps or clips; long titles ellipsize instead.
- **Live network strip:** the speed/usage line in the header now stacks below 340dp so it reads correctly in
  portrait instead of being squeezed onto one line.
- **Hotspot usage access:** the OS Tethering tile in the Hotspot & Wi-Fi sheet is now tappable and opens the
  system Usage Access screen when usage data is unavailable.
- **Settings:** theme mode and theme style buttons no longer clip their labels; the startup-animation preview
  button was removed.
- **Navigation:** the VPN Guard tab uses a distinct key icon, and tabs read "Ad & Website Blocker",
  "Network & Usage", and "VPN Guard (WireGuard & WARP)".
- **About card:** Settings now shows the installed app version and links to the project repository.

# What's New — NetGate 3.0

### DPI & SNI Hardening
- **TLS Record Split strategy:** outbound TLS `ClientHello` handshakes can now be re-framed into two
  separate TLS handshake records split inside the SNI hostname, which defeats middleboxes that only
  inspect the first record of a connection.
- **Smarter Autopilot:** per-host strategy cache is LRU-bounded (512 entries) and escalates
  `TLS Record Split -> SNI Split -> Micro Chunks -> Direct` on handshake timeouts or DPI resets.
- **Multi-KB ClientHello support:** the local filtering proxy now assembles the complete ClientHello
  record (including post-quantum key shares) before filtering/segmenting it.
- **Policy re-checks during relay:** in-flight tunnels are torn down immediately when an app is
  banned or a daily quota is exhausted, instead of only at connection setup.

# What's New

### New Features
- **DPI Bypass Shield (No Root):**
  - Added rootless anti-censorship modes directly inside NetGate: **Autopilot** (learns working strategies per domain and automatically escalates on failure), **SNI Split** (fragments TLS `ClientHello` across the SNI hostname midpoint), **Micro Chunks**, and **HTTP Desync**.
  - Added **QUIC Downgrade** (`UDP/443` blocking and DNS `SVCB`/`HTTPS` `QTYPE 65` stripping) when DPI bypass is active so encrypted web traffic stays over TCP.
  - Added **DNS over TCP (`DoTCP`)**, **DoH + DoTCP**, and **UDP + DoTCP** transport options to bypass ISP UDP/53 throttling and blocks.
  - Added **Anti-DNS Poisoning** validation to detect and discard spoofed private/bogon DNS responses, plus a 1-tap **Flush DNS & Autopilot Cache** action and live bypass/poison/QUIC counters.
- **Core `VpnService` Per-App Firewall & TUN Inspector:**
  - Implemented `PerAppFirewallEngine` with kernel-enforced **Per-App Sinkhole** routing (only blocked apps enter TUN while permitted apps run at native speed) and **Hybrid DNS & App Filter** mode with UID-level DNS/SNI/HTTP enforcement.
  - Added `TunPacketInspector` for IPv4/IPv6 packet inspection and clean rejections (`NXDOMAIN`, `TCP RST`, `ICMP Port Unreachable`) with `TunnelConfigSignature` diffing to prevent unnecessary VPN restarts.
- **Tabbed Ad & Website Blocker:**
  - Separated the **Ad & Website Blocker** screen into 5 clean top-level tabs — **Ad Blocker**, **Websites**, **Safe & Kids**, **SNI & DPI**, and **DNS Logs** — with Back navigation support and scoped sub-tabs inside **Websites** (**Blocklist**, **Whitelist**, **Allowed Apps**).

### Improvements & Performance
- **Zero-Stutter Futuristic Animations:**
  - Moved ambient aurora backdrop motion into a zero-recomposition `DrawScope` canvas loop so scrolling lists never recompose during background motion.
  - Added quantized `RenderEffect` caching in `BlurEngine` to eliminate GPU shader re-uploads and frame drops during modal blur and screen transitions.
  - Added spring-driven indicator and transition animations across navigation tabs and expandable cards.
- **Documentation & Verification:**
  - Updated `README.md` with the latest NetGate architecture, DPI Bypass Shield, tabbed Ad & Website Blocker, Hotspot Proxy, and Wi-Fi automation details.
  - Verified with unit and Robolectric Compose UI tests covering DPI Bypass modes, `QTYPE 65` stripping, `VpnService` sinkhole routing, and the 5-tab Ad & Website Blocker layout.
