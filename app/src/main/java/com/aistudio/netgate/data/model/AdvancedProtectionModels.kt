package com.aistudio.netgate.data.model

/** Per-device filtering profile applied to hotspot proxy clients. */
enum class HotspotFilterProfile(val label: String, val subtitle: String) {
    STANDARD(
        label = "Standard",
        subtitle = "Uses NetGate's global ad, site and SafeSearch rules"
    ),
    KIDS(
        label = "Kids",
        subtitle = "Adds adult, gambling and tracker blocking on top of global rules"
    ),
    STRICT(
        label = "Strict",
        subtitle = "Allowlist only: only whitelisted domains are reachable"
    );

    companion object {
        fun fromRaw(raw: String?): HotspotFilterProfile =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STANDARD
    }
}

/** Bundled keyword/domain guards used by the Kids hotspot profile. */
object HotspotKidsGuard {

    private val blockedKeywords = listOf(
        "porn", "xvideos", "xhamster", "xnxx", "redtube", "youporn", "onlyfans",
        "brazzers", "chaturbate", "livejasmin", "stripchat", "cam4", "spankbang",
        "casino", "betting", "bet365", "1xbet", "poker", "roulette", "lottery",
        "torrent", "1337x", "thepiratebay", "rarbg", "yts.mx"
    )

    fun isBlocked(host: String): Boolean {
        val clean = host.trim().trimEnd('.').lowercase()
        if (clean.isEmpty()) return false
        return blockedKeywords.any { clean.contains(it) }
    }
}

/** 1-tap split-tunnel templates for VPN Guard profiles. */
enum class SplitTunnelPreset(val title: String, val subtitle: String) {
    BROWSERS_ONLY(
        title = "Browsers Only",
        subtitle = "Tunnel only the installed web browsers"
    ),
    MESSAGING_ONLY(
        title = "Messaging Only",
        subtitle = "Tunnel only the installed chat apps"
    ),
    BYPASS_BANKING_LAN(
        title = "Bypass Banking & LAN",
        subtitle = "Everything tunnels except banking and local-network apps"
    )
}

/** Resolution rules for the split-tunnel presets. */
object SplitTunnelPresets {

    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.ecosia.android",
        "org.chromium.chrome"
    )

    private val messagingPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.instagram.android",
        "com.discord",
        "com.slack",
        "com.microsoft.teams",
        "com.google.android.apps.messaging",
        "com.viber.voip",
        "com.skype.raider",
        "jp.naver.line.android",
        "com.tencent.mm",
        "com.kakao.talk"
    )

    private val knownBankPackages = setOf(
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.citi.citimobile",
        "com.usbank.mobilebanking",
        "com.americanexpress.android.acctsvcs.us",
        "com.discoverfinancial.mobile",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.samsung.android.spay",
        "com.google.android.apps.walletnfcrel",
        "com.revolut.revolut",
        "com.binance.dev",
        "com.coinbase.android",
        "com.monzo.android",
        "com.barclays.android.barclaysmobilebanking",
        "com.grppl.android.shell.halifax",
        "com.rbs.mobile.android.natwest",
        "uk.co.hsbc.hsbcukmobilebanking",
        "com.bbva.bbvacontigo",
        "com.santander.app",
        "com.db.pwcc.dbmobile"
    )

    /**
     * Resolves a preset to a routing mode and a package set. Only packages that are actually
     * installed are returned; the caller filters once more before saving.
     */
    fun resolve(
        preset: SplitTunnelPreset,
        installedPackages: Set<String>
    ): Pair<VpnGuardRoutingMode, Set<String>> {
        return when (preset) {
            SplitTunnelPreset.BROWSERS_ONLY ->
                VpnGuardRoutingMode.SELECTED_APPS to installedPackages.intersect(browserPackages)
            SplitTunnelPreset.MESSAGING_ONLY ->
                VpnGuardRoutingMode.SELECTED_APPS to installedPackages.intersect(messagingPackages)
            SplitTunnelPreset.BYPASS_BANKING_LAN -> {
                val banking = installedPackages.filter { pkg ->
                    pkg in knownBankPackages ||
                        pkg.contains("bank", ignoreCase = true) ||
                        pkg.contains("banking", ignoreCase = true)
                }.toSet()
                VpnGuardRoutingMode.EXCLUDE_APPS to banking
            }
        }
    }

    fun isKnownBankPackage(packageName: String): Boolean =
        packageName in knownBankPackages
}

/** Persisted state for kill switch, automation, hotspot security and OTA updates. */
data class AdvancedPreferencesState(
    val killSwitchEnabled: Boolean = true,
    val killSwitchEngaged: Boolean = false,
    val autoReconnectEnabled: Boolean = true,
    val trustedWifiSsids: Set<String> = emptySet(),
    val autoVpnOnUnknownWifi: Boolean = false,
    val lastAutoVpnProfileId: String? = null,
    val hotspotShareActiveTunnel: Boolean = false,
    val hotspotRequireApproval: Boolean = false,
    val hotspotApprovedClientIps: Set<String> = emptySet(),
    val hotspotPendingClientIps: Set<String> = emptySet(),
    val hotspotClientProfiles: Map<String, HotspotFilterProfile> = emptyMap(),
    val hotspotClientSpeedLimits: Map<String, Long> = emptyMap(),
    val otaManifestUrl: String = "",
    val autoCheckUpdates: Boolean = true
)

/** A build described by the OTA manifest (or a GitHub release fallback). */
data class OtaBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildId: String = "",
    val publishedAtMs: Long = 0L,
    val publishedLabel: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val notes: String = "",
    val htmlUrl: String = "",
    val source: String = "OTA"
)

data class OtaCheckResult(
    val build: OtaBuildInfo,
    val isUpdateAvailable: Boolean,
    val reason: String
)

/** UI state for encrypted backup export/restore. */
data class BackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val exportPayload: String? = null
)
