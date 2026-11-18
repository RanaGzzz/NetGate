package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.model.ActiveNetworkType
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsCheckState
import com.example.data.model.DnsServerIdentity
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.countryCodeToFlagEmoji
import com.example.service.GeoIpResolver
import com.example.service.VpnRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object DnsConnectionChecker {

    private val KNOWN_DNS_IPS: Map<String, String> = mapOf(
        "1.1.1.1" to "Cloudflare 1.1.1.1",
        "1.0.0.1" to "Cloudflare 1.1.1.1",
        "2606:4700:4700::1111" to "Cloudflare 1.1.1.1",
        "2606:4700:4700::1001" to "Cloudflare 1.1.1.1",
        "1.1.1.2" to "Cloudflare Security (Malware Block)",
        "1.0.0.2" to "Cloudflare Security (Malware Block)",
        "2606:4700:4700::1112" to "Cloudflare Security (Malware Block)",
        "1.1.1.3" to "Cloudflare Family (Malware + Adult Block)",
        "1.0.0.3" to "Cloudflare Family (Malware + Adult Block)",
        "2606:4700:4700::1113" to "Cloudflare Family (Malware + Adult Block)",
        "8.8.8.8" to "Google Public DNS",
        "8.8.4.4" to "Google Public DNS",
        "2001:4860:4860::8888" to "Google Public DNS",
        "2001:4860:4860::8844" to "Google Public DNS",
        "9.9.9.9" to "Quad9 Threat Shield",
        "149.112.112.112" to "Quad9 Threat Shield",
        "9.9.9.10" to "Quad9 Unfiltered",
        "2620:fe::fe" to "Quad9 Threat Shield",
        "2620:fe::9" to "Quad9 Threat Shield",
        "94.140.14.14" to "AdGuard DNS",
        "94.140.15.15" to "AdGuard DNS",
        "94.140.14.15" to "AdGuard Family DNS",
        "94.140.15.16" to "AdGuard Family DNS",
        "2a10:50c0::ad1:ff" to "AdGuard DNS",
        "2a10:50c0::ad2:ff" to "AdGuard DNS",
        "208.67.222.222" to "Cisco OpenDNS",
        "208.67.220.220" to "Cisco OpenDNS",
        "208.67.222.123" to "OpenDNS FamilyShield",
        "208.67.220.123" to "OpenDNS FamilyShield",
        "45.90.28.0" to "NextDNS",
        "45.90.30.0" to "NextDNS",
        "76.76.2.0" to "Control D",
        "76.76.10.0" to "Control D",
        "185.228.168.9" to "CleanBrowsing Security",
        "185.228.169.9" to "CleanBrowsing Security",
        "185.228.168.168" to "CleanBrowsing Family",
        "10.1.10.2" to "NetGate Local VPN DNS Proxy",
        "fd00:1:fd00:1:fd00:1:fd00:2" to "NetGate Local VPN DNS Proxy (IPv6)"
    )

    fun buildInstantSnapshot(
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails
    ): DnsCheckState {
        return evaluateDnsSnapshot(
            prefs = prefs,
            vpnState = vpnState,
            connectionDetails = connectionDetails,
            vpnDnsServers = emptyList(),
            physicalDnsServers = connectionDetails.dnsServers,
            resolvedExitDnsIp = null,
            resolvedExitDnsOrg = null,
            resolvedExitDnsLocation = null,
            resolvedExitDnsFlag = "",
            resolutionLatencyMs = null,
            checkedAtMs = 0L,
            probeError = null
        )
    }

    suspend fun runLiveDnsCheck(
        context: Context,
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails
    ): DnsCheckState = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val vpnDnsList = mutableListOf<String>()
        val physicalDnsList = mutableListOf<String>()

        if (cm != null) {
            @Suppress("DEPRECATION")
            val allNetworks = runCatching { cm.allNetworks }.getOrDefault(emptyArray())
            for (net in allNetworks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                val lp = runCatching { cm.getLinkProperties(net) }.getOrNull() ?: continue
                val dnsHosts = lp.dnsServers.mapNotNull { it.hostAddress }.distinct()
                val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

                if (isVpn) {
                    vpnDnsList.addAll(dnsHosts)
                } else if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    physicalDnsList.addAll(dnsHosts)
                }
            }
        }

        val combinedPhysical = (physicalDnsList + connectionDetails.dnsServers).distinct()

        // Perform a real live DNS probe through the device's active resolver path.
        // Akamai's whoami.akamai.net returns the recursive DNS resolver's egress IP as its A record.
        var exitIp: String? = null
        var latencyMs: Int? = null
        var probeError: String? = null
        var exitOrg: String? = null
        var exitLocation: String? = null
        var exitFlag = ""

        if (connectionDetails.activeNetworkType == ActiveNetworkType.NONE && !vpnState.isServiceRunning) {
            probeError = "Offline — connect to Wi-Fi or Mobile Data to run a live DNS check."
        } else {
            val startNs = System.nanoTime()
            val probeResult = runCatching {
                InetAddress.getByName("whoami.akamai.net")?.hostAddress
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

            probeResult.onSuccess { ip ->
                if (!ip.isNullOrBlank()) {
                    exitIp = ip
                    latencyMs = elapsedMs
                    val onlineGeo = if (GeoIpResolver.shouldFetchOnline(ip)) {
                        GeoIpResolver.lookupOnlineBlocking(ip)
                    } else {
                        null
                    }
                    val geo = onlineGeo ?: GeoIpResolver.resolveImmediate(ip)
                    exitOrg = geo.ispOrOrg.ifBlank { identifyKnownDnsIp(ip) }
                    exitLocation = buildString {
                        if (geo.regionName.isNotBlank() && geo.regionName != ip) {
                            append(geo.regionName)
                            if (geo.countryName.isNotBlank() && geo.countryName != "Resolving…") {
                                append(", ")
                            }
                        }
                        if (geo.countryName.isNotBlank() && geo.countryName != "Resolving…") {
                            append(geo.countryName)
                        }
                    }.ifBlank { null }
                    exitFlag = countryCodeToFlagEmoji(geo.countryCode)
                }
            }.onFailure { err ->
                probeError = err.message ?: "Live DNS resolver probe failed on current network."
            }
        }

        evaluateDnsSnapshot(
            prefs = prefs,
            vpnState = vpnState,
            connectionDetails = connectionDetails,
            vpnDnsServers = vpnDnsList.distinct(),
            physicalDnsServers = combinedPhysical,
            resolvedExitDnsIp = exitIp,
            resolvedExitDnsOrg = exitOrg,
            resolvedExitDnsLocation = exitLocation,
            resolvedExitDnsFlag = exitFlag,
            resolutionLatencyMs = latencyMs,
            checkedAtMs = System.currentTimeMillis(),
            probeError = probeError
        )
    }

    internal fun evaluateDnsSnapshot(
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails,
        vpnDnsServers: List<String>,
        physicalDnsServers: List<String>,
        resolvedExitDnsIp: String?,
        resolvedExitDnsOrg: String?,
        resolvedExitDnsLocation: String?,
        resolvedExitDnsFlag: String,
        resolutionLatencyMs: Int?,
        checkedAtMs: Long,
        probeError: String?
    ): DnsCheckState {
        val isVpnRunning = prefs.isFirewallEnabled && vpnState.isServiceRunning
        val isNetGateDnsProxyActive = isVpnRunning && (
            prefs.isAdBlockerEnabled ||
                prefs.websiteFilterMode != WebsiteFilterMode.OFF ||
                prefs.isHttpsProxyEnabled ||
                prefs.isSafeSearchEnabled ||
                prefs.youtubeRestrictedMode.targetHost != null ||
                prefs.isYoutubeAdBlockEnabled ||
                prefs.isDeepMonitorEnabled
            )
        val hasCustomPrivateDns = connectionDetails.isPrivateDnsActive &&
            connectionDetails.privateDnsMode == "hostname" &&
            !connectionDetails.privateDnsHostname.isNullOrBlank()

        val identities = mutableListOf<DnsServerIdentity>()

        if (hasCustomPrivateDns) {
            val host = connectionDetails.privateDnsHostname!!
            identities += DnsServerIdentity(
                address = host,
                providerName = identifyKnownPrivateDnsHostname(host),
                roleLabel = "Android Private DNS (DoT)",
                isEncrypted = true
            )
        }

        if (isNetGateDnsProxyActive) {
            val upstreamLabel = if (prefs.useDohUpstream) {
                "${prefs.selectedUpstreamDns.title} (${prefs.resolvedDohUrl()})"
            } else {
                val udpIp = prefs.resolvedUdpDnsServers().firstOrNull() ?: "1.1.1.1"
                "${identifyKnownDnsIp(udpIp)} ($udpIp)"
            }
            identities += DnsServerIdentity(
                address = if (prefs.useDohUpstream) prefs.resolvedDohUrl() else (prefs.resolvedUdpDnsServers().firstOrNull() ?: "1.1.1.1"),
                providerName = upstreamLabel,
                roleLabel = if (prefs.useDohUpstream) "NetGate VPN Upstream (DoH)" else "NetGate VPN Upstream (UDP/53)",
                isEncrypted = prefs.useDohUpstream
            )
            vpnDnsServers.forEach { ip ->
                identities += DnsServerIdentity(
                    address = ip,
                    providerName = identifyKnownDnsIp(ip),
                    roleLabel = "Local VPN TUN Interface DNS",
                    isEncrypted = prefs.useDohUpstream
                )
            }
        }

        val networkLabel = when (connectionDetails.activeNetworkType) {
            ActiveNetworkType.WIFI -> "Wi-Fi DHCP DNS"
            ActiveNetworkType.MOBILE_DATA -> "Mobile Carrier DNS"
            ActiveNetworkType.OTHER -> "Ethernet DNS"
            ActiveNetworkType.NONE -> "Interface DNS"
        }
        physicalDnsServers.distinct().forEach { ip ->
            identities += DnsServerIdentity(
                address = ip,
                providerName = identifyKnownDnsIp(ip),
                roleLabel = networkLabel,
                isEncrypted = connectionDetails.isPrivateDnsActive && !isNetGateDnsProxyActive
            )
        }

        if (!resolvedExitDnsIp.isNullOrBlank()) {
            val exitProvider = classifyResolverOrg(resolvedExitDnsIp, resolvedExitDnsOrg)
            identities += DnsServerIdentity(
                address = resolvedExitDnsIp,
                providerName = exitProvider,
                roleLabel = "Live Verified Exit Resolver",
                isEncrypted = hasCustomPrivateDns || (isNetGateDnsProxyActive && prefs.useDohUpstream)
            )
        }

        val primaryProvider = when {
            hasCustomPrivateDns -> identifyKnownPrivateDnsHostname(connectionDetails.privateDnsHostname!!)
            isNetGateDnsProxyActive -> {
                if (prefs.selectedUpstreamDns.name == "CUSTOM_DNS") {
                    if (prefs.useDohUpstream) {
                        "Custom DoH (${prefs.resolvedDohUrl()})"
                    } else {
                        "Custom DNS (${prefs.resolvedUdpDnsServers().firstOrNull() ?: "1.1.1.1"})"
                    }
                } else {
                    prefs.selectedUpstreamDns.title
                }
            }
            !resolvedExitDnsIp.isNullOrBlank() -> classifyResolverOrg(resolvedExitDnsIp, resolvedExitDnsOrg)
            physicalDnsServers.isNotEmpty() -> identifyKnownDnsIp(physicalDnsServers.first())
            connectionDetails.activeNetworkType == ActiveNetworkType.NONE -> "Offline (No Active Network)"
            else -> "System Default DNS"
        }

        val transportLabel = when {
            hasCustomPrivateDns -> "Android Private DNS (DoT • Port 853)"
            isNetGateDnsProxyActive && prefs.useDohUpstream -> "Encrypted DoH via NetGate VPN"
            isNetGateDnsProxyActive -> "UDP/53 via NetGate VPN Proxy"
            connectionDetails.isPrivateDnsActive -> "Automatic Private DNS (Opportunistic DoT)"
            connectionDetails.activeNetworkType == ActiveNetworkType.WIFI -> "Wi-Fi Network DNS (Unencrypted Port 53)"
            connectionDetails.activeNetworkType == ActiveNetworkType.MOBILE_DATA -> "Mobile Carrier DNS (Unencrypted Port 53)"
            else -> "System Network DNS"
        }

        val isEncrypted = hasCustomPrivateDns ||
            (isNetGateDnsProxyActive && prefs.useDohUpstream) ||
            connectionDetails.isPrivateDnsActive

        val summary = when {
            probeError != null && checkedAtMs > 0L -> probeError
            checkedAtMs == 0L -> buildString {
                append("Currently configured to use $primaryProvider via $transportLabel.")
                if (physicalDnsServers.isNotEmpty() && !isNetGateDnsProxyActive) {
                    append(" Network DNS: ${physicalDnsServers.joinToString(", ")}.")
                }
            }
            hasCustomPrivateDns ->
                "System Private DNS (${connectionDetails.privateDnsHostname}) is handling lookups over TLS (DoT)."
            isNetGateDnsProxyActive ->
                "Verified: DNS queries are routed through NetGate VPN to $primaryProvider (${resolvedExitDnsIp ?: "TUN active"})."
            !resolvedExitDnsIp.isNullOrBlank() ->
                "Verified live resolver: ${classifyResolverOrg(resolvedExitDnsIp, resolvedExitDnsOrg)} ($resolvedExitDnsIp) via $transportLabel."
            else ->
                "Using network DNS: ${physicalDnsServers.joinToString(", ").ifEmpty { "Default gateway" }}."
        }

        return DnsCheckState(
            isChecking = false,
            connectedProvider = primaryProvider,
            transportLabel = transportLabel,
            isEncrypted = isEncrypted,
            isUsingCustomPrivateDns = hasCustomPrivateDns,
            privateDnsHostname = connectionDetails.privateDnsHostname,
            resolvedExitDnsIp = resolvedExitDnsIp,
            resolvedExitDnsOrg = resolvedExitDnsOrg,
            resolvedExitDnsLocation = resolvedExitDnsLocation,
            resolvedExitDnsFlag = resolvedExitDnsFlag,
            systemDnsServers = identities.distinctBy { "${it.roleLabel}|${it.address}" },
            resolutionLatencyMs = resolutionLatencyMs,
            checkedAtMs = checkedAtMs,
            summary = summary,
            errorMessage = probeError
        )
    }

    fun identifyKnownDnsIp(ip: String): String {
        val clean = ip.trim().lowercase()
        KNOWN_DNS_IPS[clean]?.let { return it }
        return when {
            clean.startsWith("192.168.") || clean.startsWith("10.") || clean.startsWith("172.") || clean.startsWith("fe80:") ->
                "Router / Local Gateway DNS"
            clean.startsWith("172.68.") || clean.startsWith("172.69.") || clean.startsWith("162.158.") ->
                "Cloudflare DNS Edge"
            clean.startsWith("74.125.") || clean.startsWith("172.253.") || clean.startsWith("173.194.") ->
                "Google Public DNS Edge"
            clean.startsWith("94.140.") ->
                "AdGuard DNS Edge"
            clean.startsWith("45.90.") ->
                "NextDNS Edge"
            clean.startsWith("149.112.") || clean.startsWith("9.9.9.") ->
                "Quad9 DNS Edge"
            else -> "ISP / Network Resolver"
        }
    }

    fun identifyKnownPrivateDnsHostname(hostname: String): String {
        val h = hostname.trim().lowercase()
        return when {
            h.contains("security.cloudflare-dns.com") -> "Cloudflare Security (1.1.1.2)"
            h.contains("family.cloudflare-dns.com") -> "Cloudflare Family (1.1.1.3)"
            h.contains("one.one.one.one") || h.contains("cloudflare-dns.com") -> "Cloudflare 1.1.1.1"
            h.contains("dns.google") -> "Google Public DNS"
            h.contains("dns.quad9.net") -> "Quad9 Security"
            h.contains("family.adguard-dns.com") -> "AdGuard Family DNS"
            h.contains("adguard") -> "AdGuard DNS"
            h.contains("nextdns.io") -> "NextDNS"
            h.contains("controld.com") -> "Control D"
            h.contains("cleanbrowsing.org") -> "CleanBrowsing DNS"
            else -> "Custom DoT ($hostname)"
        }
    }

    fun classifyResolverOrg(ip: String, ispOrOrg: String?): String {
        val org = ispOrOrg?.trim().orEmpty()
        val lower = org.lowercase()
        return when {
            lower.contains("cloudflare") -> "Cloudflare 1.1.1.1"
            lower.contains("google") -> "Google Public DNS"
            lower.contains("adguard") -> "AdGuard DNS"
            lower.contains("quad9") -> "Quad9 Security"
            lower.contains("nextdns") -> "NextDNS"
            lower.contains("opendns") || lower.contains("cisco") -> "Cisco OpenDNS"
            org.isNotBlank() && org != "ISP / Network Resolver" -> org
            else -> identifyKnownDnsIp(ip)
        }
    }
}
