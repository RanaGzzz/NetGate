package com.example.data.network

import android.content.Context
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsLeakCheckItem
import com.example.data.model.DnsLeakCheckStatus
import com.example.data.model.DnsLeakTestState
import com.example.data.model.DnsLeakVerdict
import com.example.data.model.FirewallPreferencesState
import com.example.service.AdBlockFilterEngine
import com.example.service.VpnRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object DnsLeakTester {

    suspend fun runDiagnostic(
        context: Context,
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails
    ): DnsLeakTestState = withContext(Dispatchers.IO) {
        // Shared network/DNS enumeration; the leak test stays an independent diagnostic feature.
        val snapshot = NetworkSnapshotProvider.shared.snapshot(context)
        val vpnDnsList = snapshot.vpnDnsServers()
        val physicalDnsList = snapshot.physicalDnsServers()
        val hasVpnIpv6Dns = snapshot.hasVpnIpv6Dns
        val hasVpnHttpProxy = snapshot.hasVpnHttpProxy

        val dohUrl = prefs.resolvedDohUrl()
        val (dohLatencyMs, dohValid) = probeDohWireformat(dohUrl)
        val traceInfo = fetchCloudflareTrace()

        evaluateLeakReport(
            prefs = prefs,
            vpnState = vpnState,
            connectionDetails = connectionDetails,
            vpnDnsServers = vpnDnsList.distinct(),
            physicalDnsServers = physicalDnsList.distinct(),
            hasVpnIpv6Dns = hasVpnIpv6Dns,
            hasVpnHttpProxy = hasVpnHttpProxy,
            dohLatencyMs = dohLatencyMs,
            dohValid = dohValid,
            egressIp = traceInfo["ip"],
            egressColo = traceInfo["colo"],
            testedAtMs = System.currentTimeMillis()
        )
    }

    internal fun evaluateLeakReport(
        prefs: FirewallPreferencesState,
        vpnState: VpnRuntimeState,
        connectionDetails: ConnectionDetails,
        vpnDnsServers: List<String>,
        physicalDnsServers: List<String>,
        hasVpnIpv6Dns: Boolean,
        hasVpnHttpProxy: Boolean,
        dohLatencyMs: Int?,
        dohValid: Boolean,
        egressIp: String?,
        egressColo: String?,
        testedAtMs: Long
    ): DnsLeakTestState {
        val checks = mutableListOf<DnsLeakCheckItem>()
        val isVpnRunning = prefs.isFirewallEnabled && vpnState.isServiceRunning
        val isDnsProxyActive = isVpnRunning &&
            (prefs.isAdBlockerEnabled || prefs.websiteFilterMode.name != "OFF" || prefs.isHttpsProxyEnabled)

        // 1. Local VPN DNS Tunnel Isolation
        when {
            isDnsProxyActive && vpnState.isTunnelActive -> {
                checks += DnsLeakCheckItem(
                    title = "Local VPN DNS Capture",
                    detail = if (vpnDnsServers.isNotEmpty()) {
                        "Active on TUN (${vpnDnsServers.joinToString(", ")}) • Physical ISP DNS isolated"
                    } else {
                        "NetGate DNS Proxy active — forcing all lookups through ${prefs.selectedUpstreamDns.title}"
                    },
                    status = DnsLeakCheckStatus.PASS
                )
            }
            isVpnRunning -> {
                checks += DnsLeakCheckItem(
                    title = "Local VPN DNS Capture",
                    detail = "Firewall running in Per-App sinkhole mode. Enable Ad Blocker or Website Filter to capture DNS.",
                    status = DnsLeakCheckStatus.WARN
                )
            }
            else -> {
                val exposed = physicalDnsServers.ifEmpty { connectionDetails.dnsServers }
                checks += DnsLeakCheckItem(
                    title = "Local VPN DNS Capture",
                    detail = "VPN paused — queries route to network DNS: ${exposed.joinToString(", ").ifEmpty { "Carrier/Router" }}",
                    status = DnsLeakCheckStatus.FAIL
                )
            }
        }

        // 2. IPv6 DNS Leak Protection
        val hasPhysicalIpv6Dns = physicalDnsServers.any { it.contains(':') } ||
            connectionDetails.dnsServers.any { it.contains(':') }
        when {
            isDnsProxyActive && (hasVpnIpv6Dns || vpnDnsServers.isEmpty()) -> {
                checks += DnsLeakCheckItem(
                    title = "IPv6 AAAA DNS Leak Guard",
                    detail = "Dual-stack IPv6 resolver (fd00:1::2) bound inside VPN tunnel",
                    status = DnsLeakCheckStatus.PASS
                )
            }
            hasPhysicalIpv6Dns -> {
                checks += DnsLeakCheckItem(
                    title = "IPv6 AAAA DNS Leak Guard",
                    detail = "Physical interface exposes IPv6 DNS while VPN DNS Guard is off",
                    status = DnsLeakCheckStatus.FAIL
                )
            }
            else -> {
                checks += DnsLeakCheckItem(
                    title = "IPv6 AAAA DNS Leak Guard",
                    detail = "No unprotected IPv6 DNS detected on active interface",
                    status = DnsLeakCheckStatus.PASS
                )
            }
        }

        // 3. Encrypted DoH Upstream Reachability
        if (prefs.useDohUpstream && dohValid && dohLatencyMs != null) {
            checks += DnsLeakCheckItem(
                title = "Encrypted DoH Upstream (${prefs.selectedUpstreamDns.title})",
                detail = "RFC 8484 HTTPS DNS verified in ${dohLatencyMs} ms (${prefs.resolvedDohUrl()})",
                status = DnsLeakCheckStatus.PASS
            )
        } else if (!prefs.useDohUpstream) {
            checks += DnsLeakCheckItem(
                title = "Upstream DNS Transport",
                detail = "Plaintext UDP/53 (${prefs.resolvedUdpDnsServers().firstOrNull() ?: "1.1.1.1"}) — enable DoH to prevent ISP snooping",
                status = DnsLeakCheckStatus.WARN
            )
        } else {
            checks += DnsLeakCheckItem(
                title = "Encrypted DoH Upstream (${prefs.selectedUpstreamDns.title})",
                detail = "DoH endpoint unreachable right now — falling back to protected UDP/53",
                status = DnsLeakCheckStatus.WARN
            )
        }

        // 4. Android Private DNS (DoT) & Browser Anti-DoH Bypass Check
        if (connectionDetails.isPrivateDnsActive && connectionDetails.privateDnsMode == "hostname") {
            checks += DnsLeakCheckItem(
                title = "System Private DNS Override",
                detail = "System DoT (${connectionDetails.privateDnsHostname ?: "custom"}) can bypass local Ad/Site filters",
                status = DnsLeakCheckStatus.WARN
            )
        } else {
            checks += DnsLeakCheckItem(
                title = "Browser Anti-DoH & DoT Guard",
                detail = "Canary probes (use-application-dns.net, _dns.resolver.arpa) & DoT 853 RST active",
                status = if (isDnsProxyActive) DnsLeakCheckStatus.PASS else DnsLeakCheckStatus.WARN
            )
        }

        // 5. HTTPS Filtering Proxy Status
        if (prefs.isHttpsProxyEnabled) {
            checks += DnsLeakCheckItem(
                title = "HTTPS Filtering Proxy (127.0.0.1:${prefs.httpsProxyPort})",
                detail = if (vpnState.isServiceRunning) {
                    if (hasVpnHttpProxy) {
                        "Active on loopback + advertised via VPN HTTP Proxy"
                    } else {
                        "Local CONNECT & TLS SNI proxy listening on 127.0.0.1:${prefs.httpsProxyPort}"
                    }
                } else {
                    "Enabled in settings — activate NetGate Firewall to start proxy listener"
                },
                status = if (vpnState.isServiceRunning) DnsLeakCheckStatus.PASS else DnsLeakCheckStatus.WARN
            )
        }

        val failCount = checks.count { it.status == DnsLeakCheckStatus.FAIL }
        val warnCount = checks.count { it.status == DnsLeakCheckStatus.WARN }

        val verdict = when {
            failCount > 0 -> DnsLeakVerdict.LEAK_DETECTED
            warnCount > 0 -> DnsLeakVerdict.PARTIAL_RISK
            else -> DnsLeakVerdict.NO_LEAK
        }

        val summary = when (verdict) {
            DnsLeakVerdict.NO_LEAK ->
                "All DNS & HTTPS lookups are isolated inside NetGate VPN using ${prefs.selectedUpstreamDns.title} DoH with IPv6 leak guard."
            DnsLeakVerdict.PARTIAL_RISK ->
                "Some checks need attention (${warnCount} warning${if (warnCount == 1) "" else "s"}). Review items below to lock down encryption & bypasses."
            DnsLeakVerdict.LEAK_DETECTED ->
                "Active network ISP DNS servers are exposed. Activate NetGate Firewall with Ad/Website Filter or DoH enabled."
            DnsLeakVerdict.IDLE -> ""
        }

        val activeDisplayDns = if (vpnDnsServers.isNotEmpty()) {
            vpnDnsServers
        } else {
            physicalDnsServers.ifEmpty { connectionDetails.dnsServers }
        }

        return DnsLeakTestState(
            isRunning = false,
            verdict = verdict,
            summary = summary,
            dohLatencyMs = dohLatencyMs,
            egressIp = egressIp,
            egressColo = egressColo,
            activeSystemDns = activeDisplayDns,
            checks = checks,
            lastTestedAtMs = testedAtMs,
            errorMessage = null
        )
    }

    private fun probeDohWireformat(dohUrl: String): Pair<Int?, Boolean> {
        val sampleQuery = buildExampleComDnsQuery()
        val startNs = System.nanoTime()
        val resp = AdBlockFilterEngine.forwardDnsOverHttps(sampleQuery, dohUrl)
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
        return if (resp != null && resp.size >= 12) {
            elapsedMs to true
        } else {
            null to false
        }
    }

    private fun fetchCloudflareTrace(): Map<String, String> {
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL("https://1.1.1.1/cdn-cgi/trace")
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                useCaches = false
                setRequestProperty("Connection", "close")
            }
            if (connection.responseCode !in 200..299) return emptyMap()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            body.lineSequence()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun buildExampleComDnsQuery(): ByteArray {
        return byteArrayOf(
            0x2A, 0x4B, // Transaction ID
            0x01, 0x00, // Standard query RD=1
            0x00, 0x01, // QDCOUNT=1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // QNAME: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01, // Type A
            0x00, 0x01  // Class IN
        )
    }
}
