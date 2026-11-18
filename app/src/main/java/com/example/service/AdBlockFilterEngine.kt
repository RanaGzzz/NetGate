package com.example.service

import android.content.Context
import com.example.data.local.AdFilterListEntity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

data class ParsedFilterRule(
    val domain: String,
    val isException: Boolean
)

class AdBlockMatcherSnapshot(
    val blockedDomains: Set<String>,
    val exceptionDomains: Set<String>
) {
    val totalBlockedCount: Int get() = blockedDomains.size

    fun isEssentialPassthrough(domain: String): Boolean {
        return matchesDomainOrParent(domain, ESSENTIAL_PASSTHROUGH_DOMAINS)
    }

    fun isBlockedByAdFilter(domain: String): Boolean {
        val normalized = cleanQueriedDomain(domain) ?: return false
        if (matchesNormalizedOrParent(normalized, exceptionDomains)) {
            return false
        }
        return matchesNormalizedOrParent(normalized, blockedDomains)
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

    val DEFAULT_AD_FILTER_LISTS: List<AdFilterListEntity> = listOf(
        AdFilterListEntity(
            id = "stevenblack",
            title = "StevenBlack Unified Hosts",
            description = "Consolidates adware, malvertising, and tracking hosts across major curated lists.",
            sourceUrl = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            category = "Ads & Malware",
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
            id = "oisd_big",
            title = "OISD Big Blocklist",
            description = "High-coverage anti-ad & tracker list tuned for near-zero false positives.",
            sourceUrl = "https://big.oisd.nl/domainswild",
            category = "Comprehensive",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_pro",
            title = "HaGeZi Multi Pro",
            description = "Sweeping protection against ads, analytics, tracking pixels, and native telemetry.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/pro.txt",
            category = "Privacy & Trackers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "hagezi_tif",
            title = "HaGeZi Threat Intelligence",
            description = "Blocks phishing, scam links, crypto drainers, spyware, and C2 infrastructure.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.txt",
            category = "Phishing & Security",
            isEnabled = true,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "easylist_privacy",
            title = "EasyPrivacy Trackers",
            description = "Blocks invasive behavioral analytics, ad pixels, and cross-site trackers.",
            sourceUrl = "https://v.firebog.net/hosts/Easyprivacy.txt",
            category = "Trackers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "adaway_hosts",
            title = "AdAway Mobile Blocklist",
            description = "Targets in-app mobile ad SDK banners, video interstitials, and SDK beacons.",
            sourceUrl = "https://adaway.org/hosts.txt",
            category = "Mobile Ads",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "peter_lowe",
            title = "Peter Lowe's Ad & Tracker List",
            description = "Lightweight, zero-bloat list targeting major ad networks and analytics beacons.",
            sourceUrl = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            category = "Ad Servers",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "urlhaus_malware",
            title = "URLHaus Malware Shield",
            description = "Abuse.ch curated threat feed blocking active malware distribution domains.",
            sourceUrl = "https://malware-filter.gitlab.io/urlhaus-filter/urlhaus-filter-hosts-online.txt",
            category = "Malware",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        ),
        AdFilterListEntity(
            id = "oem_telemetry",
            title = "OEM & Device Telemetry Shield",
            description = "Blocks vendor diagnostics, smart-device analytics, and background telemetry.",
            sourceUrl = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/native.xiaomi.txt",
            category = "OEM Telemetry",
            isEnabled = false,
            domainCount = 0,
            lastSyncedAt = 0L
        )
    )

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

    fun getCacheFile(context: Context, listId: String): File {
        val dir = File(context.filesDir, AD_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$listId.hosts")
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
                setRequestProperty("User-Agent", "NetGate-AdBlock/1.0")
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

            val cacheFile = getCacheFile(context, list.id)
            val tempFile = File(cacheFile.parentFile, "${list.id}.tmp")
            tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (ex in exceptions) {
                    writer.write("@@")
                    writer.write(ex)
                    writer.newLine()
                }
                for (domain in blocked) {
                    writer.write(domain)
                    writer.newLine()
                }
            }
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            tempFile.renameTo(cacheFile)

            cachedMatcherRef.set(null)
            Result.success(blocked.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    fun buildMatcherSnapshot(
        context: Context,
        enabledLists: List<AdFilterListEntity>
    ): AdBlockMatcherSnapshot {
        val blocked = HashSet<String>(BUILTIN_AD_CORE_DOMAINS.size + 4096).apply {
            addAll(BUILTIN_AD_CORE_DOMAINS)
        }
        val exceptions = HashSet<String>(256)

        for (list in enabledLists) {
            if (!list.isEnabled) continue
            val cacheFile = getCacheFile(context, list.id)
            if (!cacheFile.exists() || !cacheFile.canRead()) continue
            runCatching {
                cacheFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEach
                        if (line.startsWith("@@")) {
                            val ex = line.removePrefix("@@")
                            if (ex.isNotEmpty()) exceptions.add(ex)
                        } else {
                            blocked.add(line)
                        }
                    }
                }
            }
        }

        val snapshot = AdBlockMatcherSnapshot(
            blockedDomains = blocked,
            exceptionDomains = exceptions
        )
        cachedMatcherRef.set(snapshot)
        return snapshot
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
                connectTimeout = 2500
                readTimeout = 2500
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
