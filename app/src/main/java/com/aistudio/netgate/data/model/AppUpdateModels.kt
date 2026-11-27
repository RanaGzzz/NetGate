package com.aistudio.netgate.data.model

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR
}

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val publishedAt: String,
    val publishedAtIso: String = "",
    val htmlUrl: String,
    val apkDownloadUrl: String? = null,
    val apkName: String? = null,
    val apkSizeBytes: Long = 0L,
    val isPrerelease: Boolean = false
) {
    val preferredOpenUrl: String
        get() = apkDownloadUrl?.takeIf { it.isNotBlank() } ?: htmlUrl
}

data class AppUpdateUiState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val latestRelease: GitHubReleaseInfo? = null,
    val recentReleases: List<GitHubReleaseInfo> = emptyList(),
    val lastCheckedAtMs: Long = 0L,
    val errorMessage: String? = null,
    val otaBuild: OtaBuildInfo? = null,
    val otaReason: String = "",
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0,
    val canInstallDirectly: Boolean = false
)

data class ChangelogSection(
    val heading: String,
    val bullets: List<String>
)

data class ChangelogEntry(
    val versionLabel: String,
    val subtitle: String,
    val sections: List<ChangelogSection>,
    val isRemoteLive: Boolean = false,
    val releaseUrl: String? = null
)

object BundledChangelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionLabel = "NetGate 3.9.8",
            subtitle = "Firewall That Actually Blocks, Bounded DNS & Leaner Writes",
            sections = listOf(
                ChangelogSection(
                    heading = "Per-App Firewall",
                    bullets = listOf(
                        "Blocked apps are now cut off by the VPN route itself on Wi-Fi and mobile data, including raw IPs, QUIC and DNS-over-HTTPS.",
                        "A new Blocking Priority switch explains the tradeoff and defaults to blocks first; turn it off to keep DNS ad filtering and block apps through DNS and the proxy.",
                        "Apps without a launcher icon, including background services, now appear in the firewall list."
                    )
                ),
                ChangelogSection(
                    heading = "Performance",
                    bullets = listOf(
                        "DNS lookups are bounded to 16 handlers with a 64-query cap instead of one coroutine per packet.",
                        "Dashboard rankings update at most once per second instead of sorting on every DNS packet.",
                        "Connection history is merged in memory and flushed in one transaction instead of a database write per connection.",
                        "Filter log hit counts evict the coldest entries instead of wiping every count."
                    )
                ),
                ChangelogSection(
                    heading = "Reliability",
                    bullets = listOf(
                        "TUN writes stop cleanly on interface rebuilds, the logcat collector reaps its process, and proxy relays release their sockets.",
                        "Crash reports are seeded once per launch, and updates install only when the APK carries the same signing certificate as the installed app."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.7",
            subtitle = "Persistent Wi-Fi Joins, Compact Ruleset, Low Power & Beta Channel",
            sections = listOf(
                ChangelogSection(
                    heading = "Network & Usage",
                    bullets = listOf(
                        "Wi-Fi networks joined from the app are handed to the system as suggestions, so Android keeps auto-joining them after NetGate is closed.",
                        "The hotspot proxy declares the Android 17 local-network permission and says why it stopped instead of failing silently."
                    )
                ),
                ChangelogSection(
                    heading = "Performance",
                    bullets = listOf(
                        "Blocked domains are stored as packed 64-bit hashes instead of strings, so a full ruleset fits in a fraction of the heap."
                    )
                ),
                ChangelogSection(
                    heading = "Battery",
                    bullets = listOf(
                        "Low Power Mode refreshes live speed every 5 seconds and rebuilds the status-bar icon less often; filtering is unaffected."
                    )
                ),
                ChangelogSection(
                    heading = "Updates & Diagnostics",
                    bullets = listOf(
                        "An Update Channel setting lets the in-app updater include pre-release builds.",
                        "Copied diagnostic logs now include the app version, build and device details."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.6",
            subtitle = "Frosted-Glass Blur, Protection Profiles, Diagnostics Privacy & Backup Layout",
            sections = listOf(
                ChangelogSection(
                    heading = "Interface & Appearance",
                    bullets = listOf(
                        "Added Frosted-glass blur engine featuring heavy gaussian diffusion, glowing gradient mesh, and crystalline inner highlights.",
                        "Smart Protection Profiles now feature an elevated pill layout with vivid chartreuse active chips and dark translucent cards.",
                        "Moved Status Bar Speed Icon controls into the Background & System Settings accordion for a cleaner Network & Usage dashboard.",
                        "Fixed Encrypted Backup & Restore action buttons so Export and Choose File sit side-by-side with matched height and no text wrapping."
                    )
                ),
                ChangelogSection(
                    heading = "Diagnostics & Privacy",
                    bullets = listOf(
                        "The diagnostics exporter now strictly captures NetGate's own process instead of the whole device logcat.",
                        "All real user browsing hostnames are automatically redacted when exporting or copying diagnostics logs for total privacy.",
                        "Resolved idle background memory climb by eliminating ART GC feedback loops and batching log update emissions."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.5",
            subtitle = "Unified Protection Dashboard and Wi-Fi Connect",
            sections = listOf(
                ChangelogSection(
                    heading = "Ad & Website Blocker",
                    bullets = listOf(
                        "The analytics metrics now live in the Ad & Tracker Blocker card, with Top Blocked Domains and Top Apps listed in the card below.",
                        "The filter engine stats row wraps instead of squeezing its text into a vertical column and leaving an empty gap."
                    )
                ),
                ChangelogSection(
                    heading = "Network & Usage",
                    bullets = listOf(
                        "Saved and nearby Wi-Fi cards gain a Connect action, with a password prompt for secured networks.",
                        "A joined network is now actually used: the request keeps internet access instead of creating a local-only connection.",
                        "WEP networks no longer open a password dialog they cannot accept; Android 10+ points to the system Wi-Fi settings instead."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.4",
            subtitle = "Interface Alignment and Layout Fixes",
            sections = listOf(
                ChangelogSection(
                    heading = "Interface",
                    bullets = listOf(
                        "Settings badges and chevrons line up in one fixed right-hand column no matter how long the title is.",
                        "The SNI proxy statistics are three equal tiles, and domain rule Import, Export, Backup and Clear have their own row.",
                        "The duplicated refresh button is gone from the app bar; the per-screen refresh controls remain."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.3",
            subtitle = "In-App Crash Logs and Filter Memory Budget Visibility",
            sections = listOf(
                ChangelogSection(
                    heading = "Troubleshooting",
                    bullets = listOf(
                        "Crashes are saved inside the app with their stack trace, cause, version and device details, ready to read or copy from Settings → Diagnostics & Crash Logs.",
                        "The ad-block dashboard reports how many enabled filter lists the memory budget skipped, so a missing block can be explained."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9.2",
            subtitle = "Startup Stability and Update Preferences",
            sections = listOf(
                ChangelogSection(
                    heading = "Stability",
                    bullets = listOf(
                        "Fixed a launch crash when a connectivity callback arrived before its refresh guard was initialized.",
                        "Automatic update checks now respect the saved preference."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.9",
            subtitle = "Filter Sync, Smart Profile and VPN Switching Stability",
            sections = listOf(
                ChangelogSection(
                    heading = "Stability & Performance",
                    bullets = listOf(
                        "Filter sync rebuilds the ruleset once when it finishes, and only one matcher build runs at a time, so downloading lists no longer slows the app down.",
                        "Smart protection profiles write the whole list selection in a single batch instead of one row at a time.",
                        "The filter engine sizes its in-memory domain set from the device heap, shares one compact owner index per domain, and keeps the previous working ruleset if a build runs out of memory.",
                        "Dashboard analytics refresh at most once per second and filter latency at most every two seconds, so busy traffic stays smooth."
                    )
                ),
                ChangelogSection(
                    heading = "VPN Guard",
                    bullets = listOf(
                        "Only one WARP or OpenVPN connection change runs at a time, with timeouts if the previous tunnel does not release the slot.",
                        "WireGuard state callbacks are ignored while OpenVPN owns the tunnel, so OpenVPN sessions are no longer marked disconnected.",
                        "Wi-Fi and mobile data indicators keep their last stable details while a VPN is active instead of disappearing.",
                        "Network-change auto-reconnect is skipped while an endpoint switch is already running."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.8",
            subtitle = "Adblock & Website Blocker Overhaul and Automatic Updates",
            sections = listOf(
                ChangelogSection(
                    heading = "Ad & Website Blocker",
                    bullets = listOf(
                        "Added 21 trusted free filter subscriptions, with 8 high-performance lists enabled by default for balanced, fast protection on new installations.",
                        "The live filter log shows query details, originating app, responsible list, action, block reason and hit counts, with quick allow, block, copy, Whois, VirusTotal and false-positive actions.",
                        "Added temporary and permanent per-domain allow/block rules, editable whitelists and blacklists, domain-rule imports, Adblock text exports, and JSON backup/restore."
                    )
                ),
                ChangelogSection(
                    heading = "Insights & Performance",
                    bullets = listOf(
                        "The analytics dashboard tracks request totals, blocked requests, category breakdowns, top blocked domains and top apps.",
                        "Added Balanced, Maximum Protection, Privacy Focused, Family Safe and Gaming Mode profiles, with Balanced selected by default.",
                        "Improved filtering with rule deduplication, asynchronous default-list loading, cached DNS lookups and GZIP-compressed storage."
                    )
                ),
                ChangelogSection(
                    heading = "Updates",
                    bullets = listOf(
                        "NetGate checks for OTA and GitHub release updates automatically in the background when the app launches."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.7",
            subtitle = "Kill Switch, Encrypted Backup, Hotspot Controls & OTA Updates",
            sections = listOf(
                ChangelogSection(
                    heading = "Protection & Reliability",
                    bullets = listOf(
                        "VPN kill switch blocks traffic while a tunnel recovers, and auto-reconnect retries the last profile after drops and Wi-Fi ↔ mobile handovers.",
                        "Trusted Wi-Fi auto-VPN connects the last VPN Guard profile on unknown SSIDs, with Android 16/17 battery-saver and background guidance."
                    )
                ),
                ChangelogSection(
                    heading = "Monitoring & Hotspot",
                    bullets = listOf(
                        "Live app-to-domain inspector tags each connection with its app and adds 1-tap Block App / Allow App rules.",
                        "Hotspot PAC file + QR setup, active-tunnel sharing, per-device filtering profiles, speed limits, data caps and new-device approval."
                    )
                ),
                ChangelogSection(
                    heading = "Backup & Updates",
                    bullets = listOf(
                        "Encrypted AES-256 backup and restore for rules, site lists, DNS settings and VPN profiles.",
                        "In-app OTA updates with manifest and GitHub Releases fallback, direct APK install, and rebuilt-APK detection for the same version."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.6",
            subtitle = "Android Compatibility, Build Identity & Smaller Downloads",
            sections = listOf(
                ChangelogSection(
                    heading = "Android Compatibility",
                    bullets = listOf(
                        "Network monitoring now guards MTU and per-interface traffic-counter APIs that are unavailable on older Android versions, reporting unsupported values instead of risking a crash."
                    )
                ),
                ChangelogSection(
                    heading = "About & Release Downloads",
                    bullets = listOf(
                        "The About card now shows the APK's short source commit and UTC build time.",
                        "Release APKs are smaller through R8 code and resource shrinking, with separate downloads for each supported CPU architecture and a universal APK.",
                        "Fixed release shrinking failures from OkHttp's optional TLS providers; Android continues to use its platform TLS implementation."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.5",
            subtitle = "Wi-Fi Diagnostics, Liquid Glass Material You Widget, New Icons & Releases",
            sections = listOf(
                ChangelogSection(
                    heading = "New Features",
                    bullets = listOf(
                        "Wi-Fi Diagnostics in Network & Usage: inspect connected and device-saved/nearby Wi-Fi networks with Standard (Wi-Fi 4/5/6/7), Frequency & Band (2.4/5/6 GHz + Channel), RSSI Signal Strength (dBm), Tx/Rx Link Speed, and Signal Quality thresholds (-67 dBm or stronger = Good).",
                        "Quick Shield Home Screen Widget (Liquid Glass + Material You): multi-layer refractive Liquid Glass widget with Android 12+ Material You dynamic color palettes and 1-tap VPN, DPI, Ad Block & Hotspot toggles.",
                        "Exclude from Recents & Battery Saver Control: hide NetGate from Android Recent Apps while filtering stays active, and request unrestricted background battery mode directly from Settings.",
                        "3 New NetGate Launcher Icons & Updated VPN Emblem: Cyber Gate, Tunnel Core, and Packet Bastion adaptive icons join Lime Shield, plus an updated encrypted globe emblem for VPN Guard.",
                        "GitHub Update Checker, Changelog & VPN/DPI Logs: check GitHub Releases for new APKs, browse release notes, and inspect live passive VPN Guard & DPI bypass telemetry in Settings."
                    )
                ),
                ChangelogSection(
                    heading = "Fixes",
                    bullets = listOf(
                        "Android 16 & Android 17 real-time status bar speed icon: upgraded speed monitor channel (netgate_speed_monitor_channel_v2) to silent-sound IMPORTANCE_DEFAULT without silent-ranking flags so SystemUI keeps the live speed readout visible."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.4",
            subtitle = "Interface & Status Bar Monitor Refinement",
            sections = listOf(
                ChangelogSection(
                    heading = "Interface",
                    bullets = listOf(
                        "Top glass toast: in-app messages appear as a Liquid Glass pill at the top of the screen so the floating navigation bar and bottom controls stay uncovered.",
                        "Distinct app icon styles: Lime Shield plus three custom NetGate-themed adaptive icon styles, each with an Android 13 monochrome layer."
                    )
                ),
                ChangelogSection(
                    heading = "Fixes",
                    bullets = listOf(
                        "Status bar network speed icon appears immediately with a larger readable glyph.",
                        "Added explicit guidance when Android blocks monitor notifications, with direct link to notification settings."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.3",
            subtitle = "Branding & Adaptive Icon Refresh",
            sections = listOf(
                ChangelogSection(
                    heading = "Branding",
                    bullets = listOf(
                        "New adaptive lime shield icon with verified badge and Android 13 monochrome support.",
                        "Refreshed project documentation around NetGate's core firewall, DNS, DPI bypass, and VPN Guard capabilities."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.2",
            subtitle = "Live Monitor Fix & Backdrop Customization",
            sections = listOf(
                ChangelogSection(
                    heading = "Live Monitor & Appearance",
                    bullets = listOf(
                        "System Network Monitor header keeps the LIVE badge aligned beside the network label on narrow screens.",
                        "Added animated backdrops (Circles, Rings, Mesh, Space, Shapes, Snow, Grid, Particles, Lava lamp, Random) with optional tilt parallax and seasonal tinting."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.1",
            subtitle = "Readability, Responsive Strips & Navigation",
            sections = listOf(
                ChangelogSection(
                    heading = "UI & Navigation",
                    bullets = listOf(
                        "Frosted cards provide full dark/light surface contrast for VPN Guard and quick-action headers.",
                        "DPI Autopilot badge and live network header strip wrap cleanly on compact portrait screens.",
                        "Settings About card displays installed app version and repository link."
                    )
                )
            )
        ),
        ChangelogEntry(
            versionLabel = "NetGate 3.0",
            subtitle = "DPI & SNI Hardening + Core Firewall Engine",
            sections = listOf(
                ChangelogSection(
                    heading = "DPI Bypass & Core Engine",
                    bullets = listOf(
                        "TLS Record Split re-frames ClientHello into separate handshake records split inside the SNI hostname.",
                        "Autopilot strategy cache (512-entry LRU) escalates TLS Record Split -> SNI Split -> Micro Chunks -> Direct.",
                        "QUIC HTTP/3 downgrade (UDP/443 + QTYPE 65 stripping), Resilient DoH/DoTCP DNS transport, and Anti-DNS Poisoning Guard.",
                        "Shared NetworkPolicyEngine across NetGateVpnService and HotspotProxyManager with whitelist precedence and immutable policy snapshots."
                    )
                )
            )
        )
    )
}
