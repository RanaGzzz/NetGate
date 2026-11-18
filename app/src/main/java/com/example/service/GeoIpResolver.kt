package com.example.service

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class GeoLocationResult(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val regionName: String,
    val ispOrOrg: String,
    val isPrivateOrLan: Boolean = false,
    val isAuthoritative: Boolean = false
)

object GeoIpResolver {

    private val cache = ConcurrentHashMap<String, GeoLocationResult>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val KNOWN_INFRA_IPS: Map<String, GeoLocationResult> = mapOf(
        "1.1.1.1" to GeoLocationResult("1.1.1.1", "US", "United States", "Global Anycast", "Cloudflare, Inc.", isAuthoritative = true),
        "1.0.0.1" to GeoLocationResult("1.0.0.1", "US", "United States", "Global Anycast", "Cloudflare, Inc.", isAuthoritative = true),
        "1.1.1.2" to GeoLocationResult("1.1.1.2", "US", "United States", "Security Anycast", "Cloudflare, Inc.", isAuthoritative = true),
        "1.1.1.3" to GeoLocationResult("1.1.1.3", "US", "United States", "Family Anycast", "Cloudflare, Inc.", isAuthoritative = true),
        "8.8.8.8" to GeoLocationResult("8.8.8.8", "US", "United States", "Mountain View, CA", "Google LLC", isAuthoritative = true),
        "8.8.4.4" to GeoLocationResult("8.8.4.4", "US", "United States", "Mountain View, CA", "Google LLC", isAuthoritative = true),
        "9.9.9.9" to GeoLocationResult("9.9.9.9", "CH", "Switzerland", "Zurich Anycast", "Quad9 Foundation", isAuthoritative = true),
        "149.112.112.112" to GeoLocationResult("149.112.112.112", "CH", "Switzerland", "Zurich Anycast", "Quad9 Foundation", isAuthoritative = true),
        "94.140.14.14" to GeoLocationResult("94.140.14.14", "CY", "Cyprus", "Limassol", "AdGuard Software", isAuthoritative = true),
        "94.140.15.15" to GeoLocationResult("94.140.15.15", "CY", "Cyprus", "Limassol", "AdGuard Software", isAuthoritative = true),
        "208.67.222.222" to GeoLocationResult("208.67.222.222", "US", "United States", "San Francisco, CA", "Cisco OpenDNS", isAuthoritative = true)
    )

    private val CCTLD_HINTS: Map<String, Pair<String, String>> = mapOf(
        "us" to ("US" to "United States"),
        "uk" to ("GB" to "United Kingdom"),
        "de" to ("DE" to "Germany"),
        "fr" to ("FR" to "France"),
        "jp" to ("JP" to "Japan"),
        "ca" to ("CA" to "Canada"),
        "au" to ("AU" to "Australia"),
        "in" to ("IN" to "India"),
        "pk" to ("PK" to "Pakistan"),
        "cn" to ("CN" to "China"),
        "ru" to ("RU" to "Russia"),
        "br" to ("BR" to "Brazil"),
        "nl" to ("NL" to "Netherlands"),
        "sg" to ("SG" to "Singapore"),
        "ae" to ("AE" to "United Arab Emirates"),
        "sa" to ("SA" to "Saudi Arabia"),
        "kr" to ("KR" to "South Korea"),
        "it" to ("IT" to "Italy"),
        "es" to ("ES" to "Spain"),
        "ch" to ("CH" to "Switzerland"),
        "se" to ("SE" to "Sweden"),
        "no" to ("NO" to "Norway"),
        "pl" to ("PL" to "Poland"),
        "tr" to ("TR" to "Turkey"),
        "id" to ("ID" to "Indonesia")
    )

    fun resolveImmediate(remoteIp: String, domain: String = ""): GeoLocationResult {
        val cleanIp = remoteIp.trim()
        if (cleanIp.isNotEmpty()) {
            cache[cleanIp]?.let { return it }
            KNOWN_INFRA_IPS[cleanIp]?.let {
                cache[cleanIp] = it
                return it
            }
            if (isPrivateOrLocalIp(cleanIp)) {
                return GeoLocationResult(
                    ip = cleanIp,
                    countryCode = "LAN",
                    countryName = "Local Network",
                    regionName = "Private Subnet",
                    ispOrOrg = "Device / Router",
                    isPrivateOrLan = true,
                    isAuthoritative = true
                )
            }
        }

        // Quick heuristic from well-known platform/ccTLD suffixes while live GeoIP loads
        val cleanDomain = domain.trim().lowercase()
        val tld = cleanDomain.substringAfterLast('.', "")
        CCTLD_HINTS[tld]?.let { (code, country) ->
            return GeoLocationResult(
                ip = cleanIp,
                countryCode = code,
                countryName = country,
                regionName = "ccTLD .$tld",
                ispOrOrg = inferOrgHint(cleanDomain),
                isAuthoritative = false
            )
        }

        val orgHint = inferOrgHint(cleanDomain)
        if (orgHint.isNotBlank()) {
            return GeoLocationResult(
                ip = cleanIp,
                countryCode = "US",
                countryName = "United States",
                regionName = "Global CDN / Edge",
                ispOrOrg = orgHint,
                isAuthoritative = false
            )
        }

        return GeoLocationResult(
            ip = cleanIp,
            countryCode = "??",
            countryName = if (cleanIp.isBlank()) "DNS Lookup" else "Resolving…",
            regionName = if (cleanIp.isBlank()) "Pending IP" else cleanIp,
            ispOrOrg = "",
            isAuthoritative = false
        )
    }

    fun shouldFetchOnline(ip: String): Boolean {
        if (ip.isBlank()) return false
        if (isPrivateOrLocalIp(ip)) return false
        val existing = cache[ip]
        if (existing != null && existing.isAuthoritative) return false
        return true
    }

    fun lookupOnlineBlocking(ip: String): GeoLocationResult? {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || isPrivateOrLocalIp(cleanIp)) return null
        cache[cleanIp]?.takeIf { it.isAuthoritative }?.let { return it }
        if (!inFlight.add(cleanIp)) return null

        return try {
            val url = URL("https://ipwho.is/$cleanIp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NetGate-Android/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val parsed = parseGeoJson(cleanIp, body)
            if (parsed != null) {
                cache[cleanIp] = parsed
            }
            parsed
        } catch (_: Exception) {
            null
        } finally {
            inFlight.remove(cleanIp)
        }
    }

    fun parseGeoJson(ip: String, jsonText: String): GeoLocationResult? {
        return runCatching {
            val obj = JSONObject(jsonText)
            if (obj.has("success") && !obj.optBoolean("success", true)) {
                return null
            }
            val countryCode = obj.optString("country_code", "").trim().uppercase()
                .ifBlank { "??" }
            val countryName = obj.optString("country", "").trim()
                .ifBlank { "Unknown Country" }
            val city = obj.optString("city", "").trim()
            val region = obj.optString("region", "").trim()
            val regionDisplay = when {
                city.isNotBlank() && region.isNotBlank() && !city.equals(region, ignoreCase = true) ->
                    "$city, $region"
                region.isNotBlank() -> region
                city.isNotBlank() -> city
                else -> "Network Edge"
            }
            val connectionObj = obj.optJSONObject("connection")
            val org = connectionObj?.optString("org", "")?.trim().orEmpty()
                .ifBlank { connectionObj?.optString("isp", "")?.trim().orEmpty() }
                .ifBlank { obj.optString("org", "").trim() }

            GeoLocationResult(
                ip = ip,
                countryCode = countryCode,
                countryName = countryName,
                regionName = regionDisplay,
                ispOrOrg = org,
                isPrivateOrLan = false,
                isAuthoritative = true
            )
        }.getOrNull()
    }

    fun isPrivateOrLocalIp(ip: String): Boolean {
        val clean = ip.trim().lowercase()
        if (clean.isEmpty()) return false
        if (clean == "0.0.0.0" || clean == "::" || clean == "::1" || clean.startsWith("127.")) return true
        if (clean.startsWith("10.") || clean.startsWith("192.168.") || clean.startsWith("169.254.")) return true
        if (clean.startsWith("172.")) {
            val secondOctet = clean.removePrefix("172.").substringBefore('.').toIntOrNull()
            if (secondOctet != null && secondOctet in 16..31) return true
        }
        if (clean.startsWith("fe80:") || clean.startsWith("fc00:") || clean.startsWith("fd00:")) return true
        return false
    }

    private fun inferOrgHint(domain: String): String {
        return when {
            domain.endsWith("google.com") || domain.endsWith("googleapis.com") || domain.endsWith("gstatic.com") || domain.endsWith("youtube.com") ->
                "Google LLC"
            domain.endsWith("cloudflare.com") || domain.endsWith("cloudflare-dns.com") ->
                "Cloudflare, Inc."
            domain.endsWith("facebook.com") || domain.endsWith("instagram.com") || domain.endsWith("whatsapp.net") || domain.endsWith("fbcdn.net") ->
                "Meta Platforms"
            domain.endsWith("microsoft.com") || domain.endsWith("live.com") || domain.endsWith("bing.com") || domain.endsWith("office.com") ->
                "Microsoft Corp."
            domain.endsWith("amazonaws.com") || domain.endsWith("amazon.com") ->
                "Amazon AWS"
            domain.endsWith("apple.com") || domain.endsWith("icloud.com") ->
                "Apple Inc."
            domain.endsWith("github.com") || domain.endsWith("githubusercontent.com") ->
                "GitHub / Microsoft"
            else -> ""
        }
    }
}
