package com.aistudio.netgate.service

import android.content.Context
import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.SmartProtectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection

data class ParsedFilterRule(
    val domain: String,
    val isException: Boolean
)

data class AdRuleMatchMeta(
    val listId: String,
    val listTitle: String,
    val blockReason: AdBlockReason
)

data class AdLookupResult(
    val isBlocked: Boolean,
    val matchedRuleDomain: String = "",
    val filterListTitle: String = "",
    val blockReason: AdBlockReason = AdBlockReason.NONE,
    val latencyMicros: Long = 0L
)

data class AdEnginePerfStats(
    val totalDomains: Int = AdBlockFilterEngine.BUILTIN_AD_CORE_DOMAINS.size,
    val duplicatesRemovedCount: Int = 0,
    val compressedCacheBytes: Long = 0L,
    val avgLookupLatencyMicros: Long = 0L,
    val lastBuildDurationMs: Long = 0L
)

class AdBlockMatcherSnapshot(
    val blockedDomains: Set<String>,
    val exceptionDomains: Set<String>,
    val domainMeta: Map<String, AdRuleMatchMeta> = emptyMap(),
    val duplicatesRemovedCount: Int = 0,
    val buildDurationMs: Long = 0L
) {
    private val lookupCache = ConcurrentHashMap<String, AdLookupResult>(512)
    private val lookupCount = AtomicLong(0L)
    private val totalLatencyNanos = AtomicLong(0L)

    val totalBlockedCount: Int get() = blockedDomains.size

    val averageLookupMicros: Long
        get() {
            val count = lookupCount.get()
            return if (count <= 0L) 0L else (totalLatencyNanos.get() / count) / 1_000L
        }

    fun isEssentialPassthrough(domain: String): Boolean {
        return matchesDomainOrParent(domain, ESSENTIAL_PASSTHROUGH_DOMAINS)
    }

    fun isBlockedByAdFilter(domain: String): Boolean {
        return matchBlockedRule(domain).isBlocked
    }

    fun matchBlockedRule(domain: String): AdLookupResult {
        val startNs = System.nanoTime()
        val normalized = cleanQueriedDomain(domain) ?: return AdLookupResult(isBlocked = false)

        val cached = lookupCache[normalized]
        if (cached != null) {
            val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1_000L)
            recordLatency(elapsedNs)
            return cached.copy(latencyMicros = elapsedNs / 1_000L)
        }

        val computed = if (matchesNormalizedOrParent(normalized, exceptionDomains)) {
            AdLookupResult(isBlocked = false)
        } else {
            findBlockedMatch(normalized)
        }

        val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1_000L)
        val resultWithTiming = computed.copy(latencyMicros = elapsedNs / 1_000L)
        if (lookupCache.size >= MAX_LOOKUP_CACHE_ENTRIES) {
            lookupCache.clear()
        }
        lookupCache[normalized] = resultWithTiming
        recordLatency(elapsedNs)
        return resultWithTiming
    }

    private fun recordLatency(elapsedNs: Long) {
        val count = lookupCount.incrementAndGet()
        totalLatencyNanos.addAndGet(elapsedNs)
        AdBlockFilterEngine.reportLookupLatencyMicros((totalLatencyNanos.get() / count) / 1_000L)
    }

    private fun findBlockedMatch(normalized: String): AdLookupResult {
        if (blockedDomains.isEmpty()) return AdLookupResult(isBlocked = false)
        var candidate = normalized
        while (true) {
            if (candidate in blockedDomains) {
                val meta = domainMeta[candidate]
                return AdLookupResult(
                    isBlocked = true,
                    matchedRuleDomain = candidate,
                    filterListTitle = meta?.listTitle ?: "NetGate Core Ad Shield",
                    blockReason = meta?.blockReason ?: inferReasonFromDomain(candidate)
                )
            }
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx == candidate.lastIndex) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return AdLookupResult(isBlocked = false)
    }

    private fun inferReasonFromDomain(domain: String): AdBlockReason {
        return when {
            "telemetry" in domain || "metrics" in domain || "xiaomi" in domain ||
                "samsung" in domain || "huawei" in domain -> AdBlockReason.TELEMETRY
            "analytics" in domain || "mixpanel" in domain || "amplitude" in domain ||
                "hotjar" in domain || "segment" in domain || "adjust" in domain ||
                "appsflyer" in domain || "branch" in domain || "pixel" in domain -> AdBlockReason.TRACKER
            "phish" in domain -> AdBlockReason.PHISHING
            "malware" in domain -> AdBlockReason.MALWARE
            else -> AdBlockReason.ADS
        }
    }

    private fun matchesDomainOrParent(domain: String, targetSet: Set<String>): Boolean {
        val normalized = cleanQueriedDomain(domain) ?: return false
        return matchesNormalizedOrParent(normalized, targetSet)
    }

    private fun matchesNormalizedOrParent(normalized: String, targetSet: Set<String>): Boolean {
        if (targetSet.isEmpty()) return false
        var candidate = normalized
        while (true) {
            if (candidate in targetSet) return true
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx == candidate.lastIndex) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return false
    }

    private fun cleanQueriedDomain(raw: String): String? {
        val cleaned = raw.trim().trimEnd('.').lowercase()
        if (cleaned.isEmpty() || '.' !in cleaned) return null
        return cleaned
    }

    companion object {
        private const val MAX_LOOKUP_CACHE_ENTRIES = 4096
        val EMPTY = AdBlockMatcherSnapshot(emptySet(), emptySet())

        val ESSENTIAL_PASSTHROUGH_DOMAINS: Set<String> = setOf(
            // Android connectivity checks & captive portal detection
            "connectivitycheck.gstatic.com",
            "connectivitycheck.android.com",
            "clients3.google.com",
            "clients4.google.com",
            "captive.apple.com",
            "www.msftconnecttest.com",
            "detectportal.firefox.com",
            // Push notifications, OAuth, Play & carrier services
            "mtalk.google.com",
            "alt1-mtalk.google.com",
            "alt2-mtalk.google.com",
            "fcm.googleapis.com",
            "firebaseinstallations.googleapis.com",
            "android.clients.google.com",
            "play.googleapis.com",
            "accounts.google.com",
            "oauth2.googleapis.com",
            // Payment gateways, 3DS & banking authentication
            "checkout.stripe.com",
            "js.stripe.com",
            "api.stripe.com",
            "www.paypal.com",
            "api-m.paypal.com",
            "pay.google.com",
            "wallet.google.com",
            "verifiedbyvisa.com",
            "mastercard.com",
            "npci.org.in",
            // Time sync & emergency/OS CRL/OCSP
            "time.android.com",
            "time.google.com",
            "pool.ntp.org",
            "ocsp.pki.goog",
            "crl.pki.goog"
        )
    }
}

object AdBlockFilterEngine {

    private const val AD_CACHE_DIR = "adblock"
    private const val MAX_DOMAINS_PER_LIST = 350_000
    private val IPV4_LITERAL_REGEX = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
    private val HOSTNAME_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

    private val cachedMatcherRef = AtomicReference<AdBlockMatcherSnapshot?>(null)
    private val _perfStatsFlow = MutableStateFlow(AdEnginePerfStats())
    val perfStatsFlow: StateFlow<AdEnginePerfStats> = _perfStatsFlow.asStateFlow()

    val LEGACY_REMOVED_LIST_IDS: Set<String> = setOf("hagezi_pro")

    val BALANCED_DEFAULT_LIST_IDS: Set<String> = setOf(
        "oisd_big",
        "adguard_dns",
        "hagezi_normal",
        "hagezi_tif",
        "easylist_privacy",
        "adaway_hosts",
        "urlhaus_malware",
        "oem_telemetry"
    )

    val DEFAULT_AD_FILTER_LISTS: List<AdFilterListEntity> = listOf(
        AdFilterListEntity(
            id = "oisd_big",
            title = "OISD Big",
            description = "High-coverage anti-ad & tracker list tuned for near-zero false positives.",
            sourceUrl = "https://big.oisd.nl/domainswild",
            category = "Ads & Trackers",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "adguard_dns",
            title = "AdGuard DNS Filter",
            description = "Comprehensive mobile ad, banner, telemetry, and tracking filter in Adblock syntax.",
            sourceUrl = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            category = "Ads & Trackers",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_normal",
            title = "HaGeZi Multi NORMAL",
            description = "Balanced multi-source filter against ads, affiliate links, tracking & telemetry.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/multi.txt",
            category = "Ads & Trackers",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_tif",
            title = "HaGeZi Threat Intelligence",
            description = "Blocks malware, phishing, scam links, crypto drainers, spyware & C2 infrastructure.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.txt",
            category = "Malware & Threat",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "easylist_privacy",
            title = "EasyPrivacy",
            description = "Blocks invasive behavioral analytics, ad pixels, and cross-site trackers.",
            sourceUrl = "https://v.firebog.net/hosts/Easyprivacy.txt",
            category = "Trackers",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "adaway_hosts",
            title = "AdAway Mobile Ads",
            description = "Targets in-app mobile ad SDK banners, video interstitials, and SDK beacons.",
            sourceUrl = "https://adaway.org/hosts.txt",
            category = "Mobile Ads",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "urlhaus_malware",
            title = "URLHaus Malware",
            description = "Abuse.ch curated threat feed blocking active malware distribution domains.",
            sourceUrl = "https://malware-filter.gitlab.io/urlhaus-filter/urlhaus-filter-hosts-online.txt",
            category = "Malware",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "oem_telemetry",
            title = "OEM & Device Telemetry",
            description = "Blocks vendor diagnostics, Android SDK metrics, and background device telemetry.",
            sourceUrl = "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/android-tracking.txt",
            category = "Telemetry",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "stevenblack",
            title = "StevenBlack Unified Hosts",
            description = "Consolidates adware, malvertising, and tracking hosts across major curated lists.",
            sourceUrl = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            category = "Ads & Malware",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "peter_lowe",
            title = "Peter Lowe's Ad & Tracking Servers",
            description = "Lightweight, zero-bloat list targeting major ad networks and analytics beacons.",
            sourceUrl = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            category = "Trackers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "phishing_army",
            title = "Phishing Army",
            description = "Curated real-time feed of verified phishing & credential-harvesting domains.",
            sourceUrl = "https://phishing.army/download/phishing_army_blocklist.txt",
            category = "Phishing",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "openphish",
            title = "OpenPhish",
            description = "Live phishing URL feed automatically extracted into blocked threat hostnames.",
            sourceUrl = "https://openphish.com/feed.txt",
            category = "Phishing",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_badware",
            title = "HaGeZi Badware Hoster",
            description = "Blocks abusive hosting providers & compromised free hosts serving payloads.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/hoster.txt",
            category = "Malware",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_dyndns",
            title = "HaGeZi Dynamic DNS",
            description = "Blocks abused dynamic DNS subdomains frequently used for phishing & RATs.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/dyndns.txt",
            category = "Malware & Threat",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "notrack_tracker",
            title = "NoTrack Tracker Blocklist",
            description = "Dedicated tracker list stopping cross-site profiling & telemetry beacons.",
            sourceUrl = "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt",
            category = "Trackers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "windows_spy_blocker",
            title = "WindowsSpyBlocker",
            description = "Blocks Microsoft Windows diagnostic, telemetry, and tracking endpoints.",
            sourceUrl = "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
            category = "Telemetry",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "samsung_telemetry",
            title = "Samsung Telemetry Blocklist",
            description = "HaGeZi native shield blocking Samsung smart-device telemetry & ad beacons.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/native.samsung.txt",
            category = "Telemetry",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "xiaomi_telemetry",
            title = "Xiaomi Telemetry Blocklist",
            description = "HaGeZi native shield blocking Xiaomi MIUI telemetry & system analytics.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/native.xiaomi.txt",
            category = "Telemetry",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "huawei_telemetry",
            title = "Huawei Telemetry Blocklist",
            description = "HaGeZi native shield blocking Huawei device analytics & background reporting.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/native.huawei.txt",
            category = "Telemetry",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "firebog_advertising",
            title = "Firebog Advertising",
            description = "Firebog ticked advertising list filtering web & in-app display ads.",
            sourceUrl = "https://v.firebog.net/hosts/Easylist.txt",
            category = "Ads",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "firebog_tracking",
            title = "Firebog Tracking & Telemetry",
            description = "Firebog ticked tracking list covering analytics & behavioral scripts.",
            sourceUrl = "https://v.firebog.net/hosts/static/w3kbl.txt",
            category = "Trackers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        )
    )

    fun blockReasonForList(listId: String, category: String = ""): AdBlockReason {
        return when {
            listId in setOf("phishing_army", "openphish") ||
                category.contains("Phishing", ignoreCase = true) -> AdBlockReason.PHISHING
            listId in setOf("urlhaus_malware", "hagezi_tif", "hagezi_badware", "hagezi_dyndns") ||
                category.contains("Malware", ignoreCase = true) -> AdBlockReason.MALWARE
            listId in setOf(
                "oem_telemetry",
                "windows_spy_blocker",
                "samsung_telemetry",
                "xiaomi_telemetry",
                "huawei_telemetry"
            ) || category.contains("Telemetry", ignoreCase = true) -> AdBlockReason.TELEMETRY
            listId in setOf("easylist_privacy", "peter_lowe", "notrack_tracker", "firebog_tracking") ||
                category.contains("Tracker", ignoreCase = true) -> AdBlockReason.TRACKER
            else -> AdBlockReason.ADS
        }
    }

    fun enabledListIdsForProfile(
        profile: SmartProtectionProfile,
        availableListIds: Collection<String>
    ): Set<String> {
        val availableSet = availableListIds.toSet()
        return when (profile) {
            SmartProtectionProfile.BALANCED -> BALANCED_DEFAULT_LIST_IDS intersect availableSet
            SmartProtectionProfile.MAXIMUM_PROTECTION -> availableSet
            SmartProtectionProfile.PRIVACY_FOCUSED -> setOf(
                "oisd_big",
                "adguard_dns",
                "hagezi_normal",
                "stevenblack",
                "easylist_privacy",
                "peter_lowe",
                "notrack_tracker",
                "windows_spy_blocker",
                "oem_telemetry",
                "samsung_telemetry",
                "xiaomi_telemetry",
                "huawei_telemetry",
                "firebog_tracking"
            ) intersect availableSet
            SmartProtectionProfile.FAMILY_SAFE -> (BALANCED_DEFAULT_LIST_IDS + setOf(
                "phishing_army",
                "openphish"
            )) intersect availableSet
            SmartProtectionProfile.GAMING_MODE -> setOf(
                "oisd_big",
                "adaway_hosts",
                "hagezi_tif",
                "urlhaus_malware"
            ) intersect availableSet
        }
    }

    internal fun reportLookupLatencyMicros(avgMicros: Long) {
        val current = _perfStatsFlow.value
        if (current.avgLookupLatencyMicros != avgMicros) {
            _perfStatsFlow.value = current.copy(avgLookupLatencyMicros = avgMicros)
        }
    }

    val BUILTIN_AD_CORE_DOMAINS: Set<String> = setOf(
        // Major ad networks & SSPs
        "doubleclick.net",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "googleadservices.com",
        "tpc.googlesyndication.com",
        "ads.google.com",
        "afs.googlesyndication.com",
        "admob.com",
        "app-measurement.com",
        "ads.yahoo.com",
        "advertising.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "adroll.com",
        "adcolony.com",
        "applovin.com",
        "applvn.com",
        "vungle.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "inmobi.com",
        "chartboost.com",
        "mopub.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "mgid.com",
        "smartadserver.com",
        "casalemedia.com",
        "indexww.com",
        "bidswitch.net",
        "spotxchange.com",
        "fyber.com",
        "smaato.net",
        "tapjoy.com",
        "startappservice.com",
        "pangle.io",
        "byteoversea.com",
        "pangolin-dsp-toutiao.com",
        "liftoff.io",
        "kargo.com",
        "sharethrough.com",
        "triplelift.com",
        "33across.com",
        "undertone.com",
        "yieldmo.com",
        "mediavine.com",
        "raptive.com",
        "cafemedia.com",
        "ezoic.net",
        "buysellads.com",
        "carbonads.net",
        // Mobile & web analytics / telemetry / attribution SDKs
        "google-analytics.com",
        "ssl.google-analytics.com",
        "analytics.google.com",
        "firebase-settings.crashlytics.com",
        "crashlyticsreports-pa.googleapis.com",
        "adjust.com",
        "app.adjust.com",
        "appsflyer.com",
        "launches.appsflyer.com",
        "branch.io",
        "api2.branch.io",
        "kochava.com",
        "singular.net",
        "mixpanel.com",
        "api.mixpanel.com",
        "amplitude.com",
        "api.amplitude.com",
        "segment.io",
        "api.segment.io",
        "segment.com",
        "cdn.segment.com",
        "flurry.com",
        "data.flurry.com",
        "bugsnag.com",
        "sentry.io",
        "hotjar.com",
        "script.hotjar.com",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "clarity.ms",
        "heap.io",
        "heapanalytics.com",
        "kissmetrics.com",
        "optimizely.com",
        "quantserve.com",
        "scorecardresearch.com",
        "comscore.com",
        "omtrdc.net",
        "2o7.net",
        "demdex.net",
        "bluekai.com",
        "krxd.net",
        "exelator.com",
        "eyeota.net",
        "lotame.com",
        "crwdcntrl.net",
        "rlcdn.com",
        "adsafeprotected.com",
        "moatads.com",
        "doubleverify.com",
        "imrworldwide.com",
        "chartbeat.com",
        "parsely.com",
        "newrelic.com",
        "bam.nr-data.net",
        "datadoghq.com",
        // Social & platform ad trackers
        "an.facebook.com",
        "pixel.facebook.com",
        "graph.facebook.com",
        "ads.twitter.com",
        "analytics.twitter.com",
        "ads-api.twitter.com",
        "ads.linkedin.com",
        "px.ads.linkedin.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "log-va.tiktokv.com",
        "ads.pinterest.com",
        "trk.pinterest.com",
        "sc-analytics.snapchat.com",
        "tr.snapchat.com",
        "events.reddit.com",
        "alb.reddit.com",
        // OEM & OS telemetry / ad SDKs
        "ads.samsung.com",
        "samsungads.com",
        "smetrics.samsung.com",
        "nmetrics.samsung.com",
        "data.mistat.xiaomi.com",
        "ad.xiaomi.com",
        "sdkconfig.ad.xiaomi.com",
        "tracking.miui.com",
        "metrics.icloud.com",
        "iadsdk.apple.com",
        "metrics.mzstatic.com",
        "telemetry.microsoft.com",
        "vortex.data.microsoft.com",
        "settings-win.data.microsoft.com",
        "watson.telemetry.microsoft.com",
        "aax-us-east.amazon-adsystem.com",
        "z-na.amazon-adsystem.com",
        "amazon-adsystem.com",
        "mads.amazon.com",
        "adtago.s3.amazonaws.com",
        // Adblock telemetry/popups
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "adsterra.com",
        "hilltopads.net",
        "clickadu.com",
        "monetag.com"
    )

    private val LOCAL_HOST_ENTRIES = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "ip6-localnet",
        "ip6-mcastprefix",
        "ip6-allnodes",
        "ip6-allrouters",
        "ip6-allhosts",
        "0.0.0.0"
    )

    fun parseFilterLine(rawLine: String): ParsedFilterRule? {
        var line = rawLine.trim()
        if (line.isEmpty()) return null
        if (line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) return null
        // Skip Adblock cosmetic/HTML rules
        if ("##" in line || "#@#" in line || "#?#" in line || "$$" in line) return null

        // Strip inline comments
        val hashCommentIdx = line.indexOf(" #")
        if (hashCommentIdx >= 0) {
            line = line.substring(0, hashCommentIdx).trim()
        }
        if (line.isEmpty()) return null

        var isException = false
        if (line.startsWith("@@")) {
            isException = true
            line = line.removePrefix("@@").trim()
        }

        // Adblock syntax: ||example.com^ or ||example.com^$important
        if (line.startsWith("||")) {
            line = line.removePrefix("||")
            val caretIdx = line.indexOf('^')
            if (caretIdx >= 0) {
                val modifier = line.substring(caretIdx + 1)
                // Skip path-specific rules like ||example.com/ads.js except pure host rules
                if (modifier.isNotEmpty() && !modifier.startsWith("$")) return null
                line = line.substring(0, caretIdx)
            } else {
                val dollarIdx = line.indexOf('$')
                if (dollarIdx >= 0) {
                    line = line.substring(0, dollarIdx)
                }
            }
            if ('/' in line || '*' in line) return null
            val domain = validateNormalizedDomain(line) ?: return null
            return ParsedFilterRule(domain = domain, isException = isException)
        }

        // Wildcard domains format (e.g. OISD domainswild "*.doubleclick.net")
        if (line.startsWith("*.")) {
            val domain = validateNormalizedDomain(line.removePrefix("*.")) ?: return null
            return ParsedFilterRule(domain = domain, isException = isException)
        }

        // Full URL feeds (e.g. OpenPhish feed lines "https://bad.site/login")
        if (line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true)) {
            val host = runCatching { URL(line).host }.getOrNull().orEmpty()
            val domain = validateNormalizedDomain(host) ?: return null
            return ParsedFilterRule(domain = domain, isException = isException)
        }

        // Standard hosts file format: "0.0.0.0 example.com" or "127.0.0.1 example.com"
        val parts = line.split(Regex("\\s+"))
        val candidate = when {
            parts.size >= 2 && (
                parts[0] == "0.0.0.0" ||
                    parts[0] == "127.0.0.1" ||
                    parts[0] == "::" ||
                    parts[0] == "::0" ||
                    parts[0] == "::1"
                ) -> parts[1].removePrefix("*.")
            parts.size == 1 -> parts[0].removePrefix("*.")
            else -> return null
        }

        val domain = validateNormalizedDomain(candidate) ?: return null
        return ParsedFilterRule(domain = domain, isException = isException)
    }

    private fun validateNormalizedDomain(raw: String): String? {
        val cleaned = raw.trim().trimEnd('.').lowercase()
        if (cleaned.length !in 3..253) return null
        if (cleaned in LOCAL_HOST_ENTRIES) return null
        if (IPV4_LITERAL_REGEX.matches(cleaned)) return null
        if (!HOSTNAME_REGEX.matches(cleaned)) return null
        return cleaned
    }

    private fun adCacheDir(context: Context): File {
        val dir = File(context.filesDir, AD_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCacheFile(context: Context, listId: String): File {
        return File(adCacheDir(context), "$listId.hosts.gz")
    }

    fun getLegacyCacheFile(context: Context, listId: String): File {
        return File(adCacheDir(context), "$listId.hosts")
    }

    fun deleteCacheFiles(context: Context, listId: String) {
        runCatching { getCacheFile(context, listId).delete() }
        runCatching { getLegacyCacheFile(context, listId).delete() }
    }

    fun getCompressedCacheBytes(context: Context, lists: List<AdFilterListEntity>): Long {
        var total = 0L
        for (list in lists) {
            if (!list.isEnabled) continue
            val gz = getCacheFile(context, list.id)
            if (gz.exists()) {
                total += gz.length()
            } else {
                val legacy = getLegacyCacheFile(context, list.id)
                if (legacy.exists()) total += legacy.length()
            }
        }
        return total
    }

    fun writeCompressedCache(
        context: Context,
        listId: String,
        blockedDomains: Collection<String>,
        exceptionDomains: Collection<String> = emptyList()
    ): File {
        val cacheFile = getCacheFile(context, listId)
        val tempFile = File(cacheFile.parentFile, "$listId.hosts.gz.tmp")
        GZIPOutputStream(FileOutputStream(tempFile)).bufferedWriter(Charsets.UTF_8).use { writer ->
            for (ex in exceptionDomains) {
                writer.write("@@")
                writer.write(ex)
                writer.newLine()
            }
            for (domain in blockedDomains) {
                writer.write(domain)
                writer.newLine()
            }
        }
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        tempFile.renameTo(cacheFile)
        val legacy = getLegacyCacheFile(context, listId)
        if (legacy.exists()) {
            runCatching { legacy.delete() }
        }
        return cacheFile
    }

    fun downloadAndCacheFilterList(context: Context, list: AdFilterListEntity): Result<Int> {
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL(list.sourceUrl)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("User-Agent", "NetGate-AdBlock/2.0")
                setRequestProperty("Accept", "text/plain,*/*")
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return Result.failure(IllegalStateException("${list.title}: HTTP $statusCode"))
            }

            val blocked = LinkedHashSet<String>(8192)
            val exceptions = LinkedHashSet<String>(512)

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8), 16_384).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    val parsed = parseFilterLine(line)
                    if (parsed != null) {
                        if (parsed.isException) {
                            exceptions.add(parsed.domain)
                        } else if (blocked.size < MAX_DOMAINS_PER_LIST) {
                            blocked.add(parsed.domain)
                        }
                    }
                    line = reader.readLine()
                }
            }

            writeCompressedCache(context, list.id, blocked, exceptions)
            cachedMatcherRef.set(null)
            Result.success(blocked.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    suspend fun buildMatcherSnapshotAsync(
        context: Context,
        enabledLists: List<AdFilterListEntity>
    ): AdBlockMatcherSnapshot = withContext(Dispatchers.IO) {
        buildMatcherSnapshot(context, enabledLists)
    }

    fun buildMatcherSnapshot(
        context: Context,
        enabledLists: List<AdFilterListEntity>
    ): AdBlockMatcherSnapshot {
        val startMs = System.currentTimeMillis()
        val blocked = HashSet<String>(BUILTIN_AD_CORE_DOMAINS.size + 4096)
        val domainMeta = HashMap<String, AdRuleMatchMeta>(BUILTIN_AD_CORE_DOMAINS.size + 4096)
        var rawEntriesCount = 0

        for (coreDomain in BUILTIN_AD_CORE_DOMAINS) {
            rawEntriesCount++
            if (blocked.add(coreDomain)) {
                domainMeta[coreDomain] = AdRuleMatchMeta(
                    listId = "builtin_core",
                    listTitle = "NetGate Core Ad Shield",
                    blockReason = inferCoreDomainReason(coreDomain)
                )
            }
        }
        val exceptions = HashSet<String>(256)

        for (list in enabledLists) {
            if (!list.isEnabled) continue
            val gzFile = getCacheFile(context, list.id)
            val legacyFile = getLegacyCacheFile(context, list.id)
            val useGzip = gzFile.exists() && gzFile.canRead()
            val targetFile = when {
                useGzip -> gzFile
                legacyFile.exists() && legacyFile.canRead() -> legacyFile
                else -> null
            } ?: continue

            val listReason = blockReasonForList(list.id, list.category)
            val meta = AdRuleMatchMeta(
                listId = list.id,
                listTitle = list.title,
                blockReason = listReason
            )

            runCatching {
                val inputStream = if (useGzip) {
                    GZIPInputStream(FileInputStream(targetFile))
                } else {
                    FileInputStream(targetFile)
                }
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 16_384).useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEach
                        if (line.startsWith("@@")) {
                            val ex = line.removePrefix("@@")
                            if (ex.isNotEmpty()) exceptions.add(ex)
                        } else {
                            rawEntriesCount++
                            if (blocked.add(line)) {
                                domainMeta[line] = meta
                            }
                        }
                    }
                }
            }
        }

        val duplicatesRemoved = (rawEntriesCount - blocked.size).coerceAtLeast(0)
        val buildDurationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = blocked,
            exceptionDomains = exceptions,
            domainMeta = domainMeta,
            duplicatesRemovedCount = duplicatesRemoved,
            buildDurationMs = buildDurationMs
        )
        cachedMatcherRef.set(snapshot)
        val compressedBytes = getCompressedCacheBytes(context, enabledLists)
        _perfStatsFlow.value = _perfStatsFlow.value.copy(
            totalDomains = blocked.size,
            duplicatesRemovedCount = duplicatesRemoved,
            compressedCacheBytes = compressedBytes,
            lastBuildDurationMs = buildDurationMs
        )
        return snapshot
    }

    private fun inferCoreDomainReason(domain: String): AdBlockReason {
        return when {
            "telemetry" in domain || "xiaomi" in domain || "samsung" in domain ||
                "metrics" in domain -> AdBlockReason.TELEMETRY
            "analytics" in domain || "mixpanel" in domain || "amplitude" in domain ||
                "hotjar" in domain || "segment" in domain || "adjust" in domain ||
                "appsflyer" in domain || "branch" in domain || "pixel" in domain -> AdBlockReason.TRACKER
            else -> AdBlockReason.ADS
        }
    }

    fun getLastLoadedDomainsCount(context: Context, enabledLists: List<AdFilterListEntity>): Int {
        val cached = cachedMatcherRef.get()
        if (cached != null) return cached.totalBlockedCount
        var total = BUILTIN_AD_CORE_DOMAINS.size
        for (list in enabledLists) {
            if (list.isEnabled) {
                total += list.domainCount
            }
        }
        return total
    }

    /**
     * Forwards a wireformat DNS query over RFC 8484 DNS-over-HTTPS to the chosen upstream server.
     * Because NetGateVpnService excludes itself via addDisallowedApplication(packageName),
     * HTTPS traffic bypasses the local TUN cleanly without packet loops.
     */
    fun forwardDnsOverHttps(
        dnsPayload: ByteArray,
        dohEndpointUrl: String = "https://1.1.1.1/dns-query"
    ): ByteArray? {
        val targetUrl = dohEndpointUrl.trim().ifEmpty { "https://1.1.1.1/dns-query" }
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1600
                readTimeout = 1600
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(dnsPayload.size)
            }
            connection.outputStream.use { it.write(dnsPayload) }
            if (connection.responseCode in 200..299) {
                val resp = connection.inputStream.use { it.readBytes() }
                if (resp.size >= 12) resp else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /**
     * Resolves A records via Cloudflare/upstream DoH JSON API as a fallback when ISP DNS fails or poisons.
     */
    fun resolveViaDoh(
        domain: String,
        dohUrl: String = "https://1.1.1.1/dns-query"
    ): List<String> {
        val cleanDomain = domain.trim().trimEnd('.')
        if (cleanDomain.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(cleanDomain, "UTF-8")
        val endpoint = dohUrl.trim().ifEmpty { "https://1.1.1.1/dns-query" }
        val separator = if ('?' in endpoint) '&' else '?'
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL("$endpoint${separator}name=$encoded&type=A")
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                useCaches = false
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("Connection", "close")
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val answers = JSONObject(jsonText).optJSONArray("Answer") ?: return emptyList()
            val ips = mutableListOf<String>()
            for (i in 0 until answers.length()) {
                val obj = answers.optJSONObject(i) ?: continue
                if (obj.optInt("type", -1) == 1) {
                    val data = obj.optString("data", "").trim()
                    if (IPV4_LITERAL_REGEX.matches(data)) {
                        ips.add(data)
                    }
                }
            }
            ips.distinct()
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private val YOUTUBE_AD_DOMAINS: Set<String> = setOf(
        "ads.youtube.com",
        "ad.youtube.com",
        "ads-api.youtube.com",
        "s.youtube.com",
        "s2.youtube.com",
        "video-stats.l.google.com",
        "video-stats.video.google.com",
        "googleads.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "ad.doubleclick.net",
        "m.doubleclick.net",
        "stats.g.doubleclick.net",
        "cm.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "adservice.google.com",
        "pagead-googlehosted.l.google.com"
    )

    private val YOUTUBE_AD_URL_FRAGMENTS = listOf(
        "/youtubei/v1/player/ad_break",
        "/api/stats/ads",
        "/api/stats/atr",
        "/ptracking",
        "/pagead/",
        "&adformat=",
        "?adformat=",
        "&oad=",
        "ctier=l"
    )

    private val AD_URL_PATH_FRAGMENTS = listOf(
        "/pagead/",
        "/doubleclick/",
        "/adservice/",
        "/adserver/",
        "/adsense/",
        "/adsystem/",
        "/gtag/js",
        "/analytics.js",
        "/gtm.js",
        "/fbevents.js",
        "/openx/",
        "/pubads",
        "/prebid"
    )

    private val TRACKING_QUERY_PARAMS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "mc_eid",
        "igshid",
        "yclid",
        "_hsenc",
        "_hsmi"
    )

    fun isYoutubeAdDomain(domain: String): Boolean {
        var candidate = domain.trim().trimEnd('.').lowercase()
        if (candidate.isEmpty() || '.' !in candidate) return false
        while (true) {
            if (candidate in YOUTUBE_AD_DOMAINS) return true
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx == candidate.lastIndex) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return false
    }

    fun isYoutubeAdUrlPath(pathAndQuery: String?): Boolean {
        if (pathAndQuery.isNullOrBlank()) return false
        val lower = pathAndQuery.lowercase()
        return YOUTUBE_AD_URL_FRAGMENTS.any { it in lower }
    }

    fun isAdOrTrackerUrlPath(pathAndQuery: String?): Boolean {
        if (pathAndQuery.isNullOrBlank()) return false
        val lower = pathAndQuery.lowercase()
        return AD_URL_PATH_FRAGMENTS.any { it in lower }
    }

    fun stripTrackingQueryParams(requestTarget: String): String {
        val qIdx = requestTarget.indexOf('?')
        if (qIdx < 0 || qIdx == requestTarget.lastIndex) return requestTarget

        val base = requestTarget.substring(0, qIdx)
        val rawQuery = requestTarget.substring(qIdx + 1)
        val kept = rawQuery.split('&').filter { param ->
            val key = param.substringBefore('=').lowercase()
            key.isNotEmpty() && key !in TRACKING_QUERY_PARAMS
        }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }
}
