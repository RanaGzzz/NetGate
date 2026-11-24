# What's New — NetGate 3.6

### Release Build Fixes
- **Release APK shrinking:** fixed R8 failures caused by OkHttp probing optional Bouncy Castle, Conscrypt, and OpenJSSE TLS providers that are not bundled with the Android app.

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
