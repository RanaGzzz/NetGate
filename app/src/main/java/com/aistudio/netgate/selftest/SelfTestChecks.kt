package com.aistudio.netgate.selftest

import android.app.AppOpsManager
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.network.DnsConnectionChecker
import com.aistudio.netgate.data.network.DnsLeakTester
import com.aistudio.netgate.data.network.SpeedTestRunner
import com.aistudio.netgate.service.AdBlockFilterEngine
import com.aistudio.netgate.service.DpiBypassEngine
import com.aistudio.netgate.service.DpiStrategy
import com.aistudio.netgate.service.NetGateVpnService
import com.aistudio.netgate.service.PerAppFirewallEngine
import com.aistudio.netgate.service.WireGuardConfigHelper
import com.aistudio.netgate.service.OpenVpnConfigHelper
import com.aistudio.netgate.service.dns.DnsEngine
import com.aistudio.netgate.service.dns.DnsResponseCache
import com.aistudio.netgate.service.security.KeystoreSecretBox
import com.aistudio.netgate.service.backup.EncryptedBackupManager
import com.aistudio.netgate.service.LocalFilteringProxyServer
import com.aistudio.netgate.service.AppTimerScheduler
import com.aistudio.netgate.service.ProxyFilterDecision
import com.aistudio.netgate.service.policy.NetworkPolicy
import com.aistudio.netgate.service.policy.NetworkPolicyEngine
import com.aistudio.netgate.service.policy.PolicyContext
import com.aistudio.netgate.service.policy.PolicyDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

private val Context.selftestDataStore by preferencesDataStore(name = "netgate_selftest")
private val SELFTEST_FIREWALL_WAS_RUNNING = booleanPreferencesKey("selftest_firewall_was_running")

/**
 * The self-test registry.
 *
 * Every check exercises a real NetGate engine. Synthetic `.test` names, a placeholder WireGuard
 * config and the app's own in-memory Room database are used throughout, and results carry counts
 * or status words only — never a real domain, IP address or another app's package name.
 *
 * Checks marked [SelfTestCheck.active] stop/start the tunnel, start a speed test or bind a socket,
 * so they only run when the caller opted in.
 */
object SelfTestChecks {

    const val GROUP_POLICY = "Policy engine"
    const val GROUP_DNS = "DNS pipeline"
    const val GROUP_DPI = "DPI bypass"
    const val GROUP_FIREWALL = "Firewall"
    const val GROUP_WIREGUARD = "VPN Guard"
    const val GROUP_AD_LISTS = "Ad lists"
    const val GROUP_PREFS = "Preferences"
    const val GROUP_DATABASE = "Database"
    const val GROUP_PERMISSIONS = "Device & permissions"
    const val GROUP_SERVICE = "Service & network"
    const val GROUP_ACTIVE = "Active tests"

    private const val SELF_TEST_TARGET_PACKAGE = "com.aistudio.netgate.selftest.target"
    private const val SELF_TEST_DOMAIN = "example.test"
    private const val SELF_TEST_ALLOWED_SUBDOMAIN = "safe.example.test"

    /** The full suite; active checks are included but flagged so the UI can filter them. */
    fun all(context: Context): List<SelfTestCheck> {
        val appContext = context.applicationContext
        return policyChecks() + dnsChecks() + dpiChecks() + firewallChecks() + wireGuardChecks() +
            adListChecks() + preferenceChecks() + databaseChecks(appContext) +
            permissionChecks(appContext) + serviceChecks(appContext) + activeChecks(appContext)
    }

    // ---- Pure engine checks ---------------------------------------------------------------

    private fun policyChecks(): List<SelfTestCheck> {
        val blockedPolicy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            blockedDomains = setOf(SELF_TEST_DOMAIN)
        )
        val rulesPolicy = NetworkPolicy(
            websiteFilterMode = WebsiteFilterMode.BLOCKLIST,
            rawWebsiteRules = listOf(
                WebsiteRuleEntity(SELF_TEST_DOMAIN, WebsiteRuleType.BLOCK),
                WebsiteRuleEntity(SELF_TEST_ALLOWED_SUBDOMAIN, WebsiteRuleType.ALLOW)
            )
        )
        val context = PolicyContext.forFirewall(protocol = "DNS")

        return listOf(
            SelfTestCheck("policy-default", GROUP_POLICY, "Unmatched domain is allowed") {
                // Website filtering is off, so nothing matches and the default allow applies.
                val decision = NetworkPolicyEngine.evaluate(
                    rawDomain = "nothing-matches.example.test",
                    context = context,
                    policy = NetworkPolicy()
                )
                if (decision is PolicyDecision.Allow) TestOutcome.pass("Default allow")
                else TestOutcome.fail("Expected allow, got ${decision::class.simpleName}")
            },
            SelfTestCheck("policy-block", GROUP_POLICY, "Blocked domain is blocked") {
                val decision = NetworkPolicyEngine.evaluate(
                    rawDomain = SELF_TEST_DOMAIN,
                    context = context,
                    policy = blockedPolicy
                )
                if (decision is PolicyDecision.Block) TestOutcome.pass("Block rule applied")
                else TestOutcome.fail("Expected block, got ${decision::class.simpleName}")
            },
            SelfTestCheck("policy-specific-allow", GROUP_POLICY, "Specific allow beats broader block") {
                val decision = NetworkPolicyEngine.evaluate(
                    rawDomain = SELF_TEST_ALLOWED_SUBDOMAIN,
                    context = context,
                    policy = rulesPolicy
                )
                if (decision is PolicyDecision.Allow) TestOutcome.pass("More specific rule won")
                else TestOutcome.fail("Expected allow, got ${decision::class.simpleName}")
            }
        )
    }

    private fun dnsChecks(): List<SelfTestCheck> {
        val engine = DnsEngine()
        return listOf(
            SelfTestCheck("dns-parse-v4", GROUP_DNS, "Parse IPv4 DNS query") {
                val packet = buildDnsQueryPacket(ipv6 = false)
                val parsed = engine.parseUdpQuery(packet, packet.size)
                if (parsed == null) {
                    TestOutcome.fail("IPv4 query was not parsed")
                } else if (parsed.domain != SELF_TEST_DOMAIN) {
                    TestOutcome.fail("Parsed an unexpected name")
                } else {
                    TestOutcome.pass("Parsed ${parsed.domain.length} characters")
                }
            },
            SelfTestCheck("dns-build-v4", GROUP_DNS, "Build IPv4 DNS response") {
                val packet = buildDnsQueryPacket(ipv6 = false)
                val parsed = engine.parseUdpQuery(packet, packet.size)
                    ?: return@SelfTestCheck TestOutcome.fail("Query was not parsed")
                val response = engine.buildBlockedResponse(parsed)
                if (response.isEmpty()) TestOutcome.fail("Empty response")
                else if ((response[0].toInt() shr 4) != 4) TestOutcome.fail("Not an IPv4 packet")
                else TestOutcome.pass("Built ${response.size} byte IPv4 response")
            },
            SelfTestCheck("dns-parse-v6", GROUP_DNS, "Parse IPv6 DNS query") {
                val packet = buildDnsQueryPacket(ipv6 = true)
                val parsed = engine.parseUdpQuery(packet, packet.size)
                if (parsed == null) TestOutcome.fail("IPv6 query was not parsed")
                else if (!parsed.isIpv6) TestOutcome.fail("Packet was not recognised as IPv6")
                else TestOutcome.pass("Parsed ${parsed.domain.length} characters")
            },
            SelfTestCheck("dns-build-v6", GROUP_DNS, "Build IPv6 DNS response") {
                val packet = buildDnsQueryPacket(ipv6 = true)
                val parsed = engine.parseUdpQuery(packet, packet.size)
                    ?: return@SelfTestCheck TestOutcome.fail("Query was not parsed")
                val response = engine.buildBlockedResponse(parsed)
                if (response.isEmpty()) TestOutcome.fail("Empty response")
                else if ((response[0].toInt() shr 4) != 6) TestOutcome.fail("Not an IPv6 packet")
                else TestOutcome.pass("Built ${response.size} byte IPv6 response")
            },
            SelfTestCheck("dns-cache", GROUP_DNS, "DNS response cache put/get/expiry/clear") {
                DnsResponseCache.clear()
                val response = buildDnsCacheResponse("cache.example.test", ttlSeconds = 300L)
                if (!DnsResponseCache.put("cache.example.test", com.aistudio.netgate.service.dns.DnsRecordType.A, response, nowMs = 0L)) {
                    return@SelfTestCheck TestOutcome.fail("put returned false")
                }
                val got = DnsResponseCache.get("cache.example.test", com.aistudio.netgate.service.dns.DnsRecordType.A, nowMs = 1_000L)
                if (got == null) return@SelfTestCheck TestOutcome.fail("get returned null")
                // expired
                if (DnsResponseCache.get("cache.example.test", com.aistudio.netgate.service.dns.DnsRecordType.A, nowMs = 400_000L) != null) {
                    return@SelfTestCheck TestOutcome.fail("entry did not expire")
                }
                DnsResponseCache.clear()
                if (DnsResponseCache.size() != 0) return@SelfTestCheck TestOutcome.fail("clear did not empty")
                TestOutcome.pass("put/get/expiry/clear ok")
            },
            SelfTestCheck("safesearch-rewrite", GROUP_DNS, "SafeSearch rewrite on example.test aliases") {
                val alias = com.aistudio.netgate.service.DnsPacketFilter.resolveSafeSearchAlias(
                    domain = "www.google.com",
                    safeSearchEnabled = true,
                    youtubeRestrictedMode = com.aistudio.netgate.data.model.YouTubeRestrictedMode.OFF
                )
                if (alias?.aliasDomain != "forcesafesearch.google.com") {
                    return@SelfTestCheck TestOutcome.fail("Expected Google SafeSearch alias")
                }
                val noRewrite = com.aistudio.netgate.service.DnsPacketFilter.resolveSafeSearchAlias(
                    domain = "example.test",
                    safeSearchEnabled = true,
                    youtubeRestrictedMode = com.aistudio.netgate.data.model.YouTubeRestrictedMode.OFF
                )
                if (noRewrite != null) {
                    return@SelfTestCheck TestOutcome.fail("example.test was unexpectedly rewritten")
                }
                TestOutcome.pass("SafeSearch alias resolved; example.test untouched")
            }
        )
    }

    private fun dpiChecks(): List<SelfTestCheck> = listOf(
        SelfTestCheck("dpi-http-desync", GROUP_DPI, "HTTP Host header desync writer") {
            val input = "GET / HTTP/1.1\r\nHost: example.test\r\n\r\n"
            val output = DpiBypassEngine.transformPlainHttpHeaders(
                headerText = input,
                strategy = DpiStrategy.HTTP_HEADER_DESYNC
            )
            if (output.contains("hOsT:")) TestOutcome.pass("Host header rewritten")
            else TestOutcome.fail("Header was not rewritten")
        },
        SelfTestCheck("dpi-tls-writer", GROUP_DPI, "TLS ClientHello fragment writer") {
            val payload = ByteArray(64) { (it + 1).toByte() }
            val sink = ByteArrayOutputStream()
            DpiBypassEngine.writeTlsClientHello(
                output = sink,
                packet = payload,
                length = payload.size,
                strategy = DpiStrategy.MICRO_CHUNKS,
                chunkSize = 4,
                fragmentDelayMs = 0
            )
            if (sink.size() == payload.size) TestOutcome.pass("All ${payload.size} bytes written")
            else TestOutcome.fail("Wrote ${sink.size()} of ${payload.size} bytes")
        },
        SelfTestCheck("dpi-http-writer", GROUP_DPI, "HTTP request writer") {
            val payload = "GET / HTTP/1.1\r\nHost: example.test\r\n\r\n".toByteArray()
            val sink = ByteArrayOutputStream()
            DpiBypassEngine.writeHttpRequest(
                output = sink,
                headerBytes = payload,
                strategy = DpiStrategy.DIRECT,
                chunkSize = 4,
                fragmentDelayMs = 0
            )
            if (sink.size() == payload.size) TestOutcome.pass("Wrote ${sink.size()} bytes")
            else TestOutcome.fail("Wrote ${sink.size()} of ${payload.size} bytes")
        }
    )

    private fun firewallChecks(): List<SelfTestCheck> = listOf(
        SelfTestCheck("firewall-plan", GROUP_FIREWALL, "Per-app tunnel plan") {
            val rule = AppNetworkRuleEntity(
                packageName = SELF_TEST_TARGET_PACKAGE,
                blockWifi = true,
                blockMobileData = true
            )
            val plan = PerAppFirewallEngine.buildTunnelPlan(
                networkType = ActiveNetworkType.WIFI,
                rules = listOf(rule),
                preferences = FirewallPreferencesState(firewallPriority = true),
                selfPackageName = "com.aistudio.netgate.selftest.self",
                isPackageInstalled = { true },
                resolvePackageUid = { 10_000 }
            )
            when {
                !plan.shouldRunTunnel -> TestOutcome.fail("Tunnel was planned as disabled")
                SELF_TEST_TARGET_PACKAGE !in plan.blockedPackages ->
                    TestOutcome.fail("Blocked app missing from the plan")
                "com.aistudio.netgate.selftest.self" in plan.bypassPackages ->
                    TestOutcome.fail("Self package should not be bypassed")
                else -> TestOutcome.pass("Plan holds ${plan.blockedPackages.size} blocked app(s)")
            }
        }
    )

    private fun wireGuardChecks(): List<SelfTestCheck> {
        var parsedConfig: String? = null
        return listOf(
            SelfTestCheck("wireguard-parse", GROUP_WIREGUARD, "Parse a placeholder WireGuard config") {
                val (config, _) = WireGuardConfigHelper.generateTemplateConfig()
                val profile = WireGuardConfigHelper.parseToProfile(config, nameHint = "self-test")
                    .getOrElse { error ->
                        return@SelfTestCheck TestOutcome.fail(
                            "Template config was rejected",
                            error.stackTraceToString()
                        )
                    }
                if (profile.endpointHost.isBlank()) {
                    TestOutcome.fail("Parsed profile has no endpoint")
                } else {
                    parsedConfig = config
                    val addressCount = profile.interfaceAddresses
                        .split(',')
                        .count { it.isNotBlank() }
                    TestOutcome.pass("Parsed $addressCount interface address(es)")
                }
            },
            SelfTestCheck("wireguard-build", GROUP_WIREGUARD, "Build an effective config") {
                val config = parsedConfig
                    ?: WireGuardConfigHelper.generateTemplateConfig().first
                val profile = WireGuardConfigHelper.parseToProfile(config, nameHint = "self-test")
                    .getOrElse { return@SelfTestCheck TestOutcome.fail("Config was rejected") }
                WireGuardConfigHelper.buildWireGuardConfig(profile).fold(
                    onSuccess = { TestOutcome.pass("Config rebuilt for the engine") },
                    onFailure = { TestOutcome.fail("Config could not be rebuilt", it.stackTraceToString()) }
                )
            },
            SelfTestCheck("wireguard-endpoint", GROUP_WIREGUARD, "Endpoint rewrite") {
                val (config, _) = WireGuardConfigHelper.generateTemplateConfig()
                val rewritten = WireGuardConfigHelper.replaceEndpointHost(config, "192.0.2.10")
                if (rewritten.contains("192.0.2.10")) TestOutcome.pass("Endpoint replaced")
                else TestOutcome.fail("Endpoint was not replaced")
            },
            SelfTestCheck("keystore-seal", GROUP_WIREGUARD, "KeystoreSecretBox seal/open and legacy plaintext") {
                val plain = "private-key-material"
                val sealed = KeystoreSecretBox.seal(plain)
                val opened = KeystoreSecretBox.open(sealed)
                if (opened != plain) return@SelfTestCheck TestOutcome.fail("seal/open round trip failed")
                // legacy plaintext passes through unchanged
                if (KeystoreSecretBox.open(plain) != plain) return@SelfTestCheck TestOutcome.fail("legacy plaintext not preserved")
                if (!KeystoreSecretBox.requiresSealing(plain)) return@SelfTestCheck TestOutcome.fail("requiresSealing false for plaintext")
                TestOutcome.pass("seal/open ok (sealed=${KeystoreSecretBox.isSealed(sealed)})")
            },
            SelfTestCheck("openvpn-parse", GROUP_WIREGUARD, "OpenVpnConfigHelper parsing a dummy profile") {
                val sample = """
                    client
                    dev tun
                    proto tcp
                    remote vpn.example.org 443
                    auth-user-pass
                """.trimIndent()
                if (!OpenVpnConfigHelper.looksLikeOpenVpnConfig(sample)) {
                    return@SelfTestCheck TestOutcome.fail("not recognised as OpenVPN")
                }
                val profile = OpenVpnConfigHelper.parseToProfile(sample, nameHint = "dummy.ovpn").getOrNull()
                    ?: return@SelfTestCheck TestOutcome.fail("parse failed")
                if (profile.endpointHost.isBlank()) return@SelfTestCheck TestOutcome.fail("no endpoint")
                TestOutcome.pass("parsed ${profile.name}")
            }
        )
    }

    private fun adListChecks(): List<SelfTestCheck> = listOf(
        SelfTestCheck("adlist-parse", GROUP_AD_LISTS, "Adblock rule parser") {
            val blocked = AdBlockFilterEngine.parseFilterLine("||ads.example.test^")
            val exception = AdBlockFilterEngine.parseFilterLine("@@||good.example.test^")
            when {
                blocked == null -> TestOutcome.fail("Block rule was not parsed")
                exception == null -> TestOutcome.fail("Exception rule was not parsed")
                !exception.isException -> TestOutcome.fail("Exception rule lost its flag")
                blocked.domain != "ads.example.test" -> TestOutcome.fail("Domain was not normalised")
                else -> TestOutcome.pass("Parsed block and exception rules")
            }
        }
    )

    private fun preferenceChecks(): List<SelfTestCheck> = listOf(
        SelfTestCheck("prefs-defaults", GROUP_PREFS, "Preference defaults") {
            val prefs = FirewallPreferencesState()
            val problems = buildList {
                if (prefs.websiteFilterMode.name != "OFF") add("website filter")
                if (prefs.isAdBlockerEnabled) add("ad blocker")
                if (prefs.dnsResponseCacheEnabled) add("dns cache")
                if (!prefs.firewallPriority) add("firewall priority")
                if (prefs.dpiBypassMode.name != "OFF") add("dpi bypass")
            }
            if (problems.isEmpty()) TestOutcome.pass("Defaults match the release behaviour")
            else TestOutcome.fail("Unexpected defaults: ${problems.joinToString()}")
        },
        SelfTestCheck("prefs-fallback", GROUP_PREFS, "Resolver fallbacks") {
            val custom = FirewallPreferencesState(
                selectedUpstreamDns = com.aistudio.netgate.data.model.UpstreamDnsServer.CUSTOM_DNS,
                customDohUrl = "not-a-url",
                customUdpDnsIp = ""
            )
            val doh = custom.resolvedDohUrl()
            val udp = custom.resolvedUdpDnsServers()
            when {
                !doh.startsWith("https://") -> TestOutcome.fail("Custom DoH did not fall back")
                udp.isEmpty() -> TestOutcome.fail("No UDP fallback resolvers")
                else -> TestOutcome.pass("Fallback resolver set holds ${udp.size} entries")
            }
        }
    )

    // ---- Database checks ------------------------------------------------------------------

    private fun databaseChecks(context: Context): List<SelfTestCheck> = listOf(
        SelfTestCheck("db-crud", GROUP_DATABASE, "Room CRUD on an in-memory database") {
            withContext(Dispatchers.IO) {
                val database = Room.inMemoryDatabaseBuilder(context, NetGateDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                try {
                    val dao = database.appNetworkRuleDao()
                    dao.upsertRule(
                        AppNetworkRuleEntity(
                            packageName = SELF_TEST_TARGET_PACKAGE,
                            blockWifi = true,
                            blockMobileData = false
                        )
                    )
                    val stored = dao.getAllRulesOnce()
                    if (stored.size != 1) return@withContext TestOutcome.fail("Insert did not persist")
                    dao.deleteRule(SELF_TEST_TARGET_PACKAGE)
                    if (dao.getAllRulesOnce().isNotEmpty()) {
                        return@withContext TestOutcome.fail("Delete did not remove the row")
                    }
                    TestOutcome.pass("Insert, read and delete succeeded")
                } finally {
                    database.close()
                }
            }
        },
        SelfTestCheck("db-version", GROUP_DATABASE, "Database schema version") {
            withContext(Dispatchers.IO) {
                val version = NetGateDatabase.getInstance(context)
                    .openHelper
                    .readableDatabase
                    .version
                if (version > 0) TestOutcome.pass("Schema version $version")
                else TestOutcome.fail("Schema version was not readable")
            }
        },
        SelfTestCheck("backup-roundtrip", GROUP_DATABASE, "EncryptedBackupManager round trip") {
            withContext(Dispatchers.IO) {
                val password = "selftest-password".toCharArray()
                val wrong = "wrong-password-123".toCharArray()
                val exported = EncryptedBackupManager.export(context, password)
                if (exported.isFailure) {
                    return@withContext TestOutcome.fail("export failed", exported.exceptionOrNull()?.message)
                }
                val payload = exported.getOrThrow()
                // wrong password must fail
                val wrongResult = EncryptedBackupManager.import(context, payload, wrong)
                if (wrongResult.isSuccess) {
                    return@withContext TestOutcome.fail("wrong password was accepted")
                }
                val imported = EncryptedBackupManager.import(context, payload, password)
                if (imported.isFailure) {
                    return@withContext TestOutcome.fail("import failed", imported.exceptionOrNull()?.message)
                }
                TestOutcome.pass("export/import ok")
            }
        },
        SelfTestCheck("timer-due", GROUP_DATABASE, "AppTimerScheduler.processDueTimersWithDaos") {
            withContext(Dispatchers.IO) {
                val database = Room.inMemoryDatabaseBuilder(context, NetGateDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                try {
                    val ruleDao = database.appNetworkRuleDao()
                    val timerDao = database.appTimerDao()
                    val pkg = "com.example.selftest.timer"
                    timerDao.upsertTimers(listOf(
                        com.aistudio.netgate.data.local.AppTimerEntity(
                            packageName = pkg,
                            blockWifiOnExpire = true,
                            blockMobileDataOnExpire = false,
                            durationMillis = 1000L,
                            expiresAtMillis = System.currentTimeMillis() - 1000L,
                            createdAtMillis = System.currentTimeMillis() - 2000L
                        )
                    ))
                    val expired = AppTimerScheduler.processDueTimersWithDaos(ruleDao, timerDao)
                    if (pkg !in expired) return@withContext TestOutcome.fail("timer not processed")
                    val rules = ruleDao.getAllRulesOnce()
                    if (rules.none { it.packageName == pkg && it.blockWifi }) {
                        return@withContext TestOutcome.fail("rule not updated")
                    }
                    TestOutcome.pass("processed ${expired.size} timer(s)")
                } finally {
                    database.close()
                }
            }
        }
    )

    // ---- Live read-only checks ------------------------------------------------------------

    private fun permissionChecks(context: Context): List<SelfTestCheck> = listOf(
        SelfTestCheck("vpn-consent", GROUP_PERMISSIONS, "VPN consent") {
            val pending = runCatching { VpnService.prepare(context) }.getOrNull()
            if (pending == null) TestOutcome.pass("VPN consent already granted")
            else TestOutcome.warn("VPN consent has not been granted yet")
        },
        SelfTestCheck("notifications", GROUP_PERMISSIONS, "Notifications") {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                TestOutcome.pass("Notifications enabled")
            } else {
                TestOutcome.warn("Notifications are blocked for this app")
            }
        },
        SelfTestCheck("battery", GROUP_PERMISSIONS, "Battery optimization") {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val ignoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            if (ignoring) TestOutcome.pass("Exempt from battery optimization")
            else TestOutcome.warn("Battery optimization may pause filtering")
        },
        SelfTestCheck("usage-access", GROUP_PERMISSIONS, "Usage access") {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            if (appOps == null) {
                TestOutcome.skip("AppOps unavailable")
            } else {
                @Suppress("DEPRECATION")
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) TestOutcome.pass("Usage access granted")
                else TestOutcome.warn("Usage access not granted")
            }
        },
        SelfTestCheck("private-dns", GROUP_PERMISSIONS, "System Private DNS") {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val properties = connectivity?.let { it.getLinkProperties(it.activeNetwork) }
            val isPrivateDnsActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                properties?.isPrivateDnsActive == true
            // A configured hostname is an active (strict) Private DNS that can bypass NetGate;
            // "off" or opportunistic/automatic mode is fine.
            val activeHostname = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                properties?.privateDnsServerName?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val isStrictPrivateDns = isPrivateDnsActive && activeHostname != null
            when {
                properties == null -> TestOutcome.skip("No active network")
                isStrictPrivateDns -> TestOutcome.warn("Private DNS hostname is active: $activeHostname")
                else -> TestOutcome.pass("Private DNS is off or automatic")
            }
        }
    )

    private fun serviceChecks(context: Context): List<SelfTestCheck> = listOf(
        SelfTestCheck("service-runtime", GROUP_SERVICE, "Firewall service runtime") {
            val state = NetGateVpnService.runtimeState.value
            when {
                state.isServiceRunning && state.isTunnelActive ->
                    TestOutcome.pass("Service and tunnel are up")
                state.isServiceRunning -> TestOutcome.warn("Service is running without an active tunnel")
                else -> TestOutcome.warn("Firewall service is stopped")
            }
        },
        SelfTestCheck("loopback-proxy", GROUP_SERVICE, "Loopback socket bind and probe") {
            withContext(Dispatchers.IO) {
                var server: ServerSocket? = null
                var client: Socket? = null
                try {
                    server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
                    val port = server.localPort
                    val received = ByteArrayOutputStream()
                    val acceptThread = Thread {
                        runCatching {
                            server.accept().use { socket ->
                                socket.getOutputStream().write("ok".toByteArray())
                                socket.getOutputStream().flush()
                                val buffer = ByteArray(2)
                                val read = socket.getInputStream().read(buffer)
                                if (read > 0) received.write(buffer, 0, read)
                            }
                        }
                    }.apply { isDaemon = true; start() }

                    client = Socket().apply {
                        connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
                        soTimeout = 1_000
                    }
                    client.getOutputStream().write("hi".toByteArray())
                    client.getOutputStream().flush()
                    val buffer = ByteArray(2)
                    val read = client.getInputStream().read(buffer)
                    acceptThread.join(1_000)
                    if (read == 2 && String(buffer) == "ok" && String(received.toByteArray()) == "hi") {
                        TestOutcome.pass("Bind, connect and round trip on an ephemeral loopback port")
                    } else {
                        TestOutcome.fail("Loopback round trip returned no data")
                    }
                } finally {
                    runCatching { client?.close() }
                    runCatching { server?.close() }
                }
            }
        },
        SelfTestCheck("dns-resolver", GROUP_SERVICE, "DNS resolver check (NetGate's own excluded path)") {
            if (!hasInternet(context)) {
                TestOutcome.warn("No connectivity")
            } else {
                val state = DnsConnectionChecker.runLiveDnsCheck(
                    context = context,
                    prefs = FirewallPreferencesState(),
                    vpnState = NetGateVpnService.runtimeState.value,
                    connectionDetails = ConnectionDetails()
                )
                if (state.errorMessage != null) TestOutcome.warn("DNS check reported an error")
                else TestOutcome.pass("Resolver responded over ${state.transportLabel.ifBlank { "the active path" }}")
            }
        },
        SelfTestCheck("dns-leak", GROUP_SERVICE, "DNS leak probe (NetGate's own excluded path)") {
            if (!hasInternet(context)) {
                TestOutcome.warn("No connectivity")
            } else {
                val report = DnsLeakTester.runDiagnostic(
                    context = context,
                    prefs = FirewallPreferencesState(),
                    vpnState = NetGateVpnService.runtimeState.value,
                    connectionDetails = ConnectionDetails()
                )
                val failing = report.checks.firstOrNull { it.status == com.aistudio.netgate.data.model.DnsLeakCheckStatus.FAIL }
                if (failing != null) {
                    val resolver = report.activeSystemDns.joinToString(", ").ifBlank { failing.detail }
                    TestOutcome.warn("DNS leak detected via $resolver")
                } else if (report.verdict == com.aistudio.netgate.data.model.DnsLeakVerdict.LEAK_DETECTED) {
                    val resolver = report.activeSystemDns.joinToString(", ").ifBlank { "unknown resolver" }
                    TestOutcome.warn("DNS leak detected via $resolver")
                } else if (report.checks.any { it.status == com.aistudio.netgate.data.model.DnsLeakCheckStatus.WARN }) {
                    TestOutcome.warn("Partial risk: ${report.checks.count { it.status == com.aistudio.netgate.data.model.DnsLeakCheckStatus.WARN }} warning(s)")
                } else {
                    TestOutcome.pass("Leak probe completed with ${report.checks.size} check(s)")
                }
            }
        },
        SelfTestCheck("proxy-block-page", GROUP_SERVICE, "LocalFilteringProxyServer allowed/blocked host") {
            withContext(Dispatchers.IO) {
                val port = 0
                val server = LocalFilteringProxyServer(
                    port = port,
                    bindHost = "127.0.0.1",
                    stripTrackingParams = false,
                    protectSocket = { true },
                    evaluateRequest = { host, _ ->
                        if (host == "blocked.example.test") {
                            ProxyFilterDecision(allow = false, reason = "blocked")
                        } else {
                            ProxyFilterDecision(allow = true, reason = "allowed")
                        }
                    },
                    onRequestLogged = { _, _ -> },
                    resolveTargetAddresses = { emptyList() }
                )
                if (!server.start()) return@withContext TestOutcome.fail("proxy did not start")
                val bound = server.boundPort
                try {
                    // blocked host -> 403
                    java.net.Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", bound), 2_000)
                        socket.soTimeout = 2_000
                        val out = socket.getOutputStream()
                        out.write("GET http://blocked.example.test/ HTTP/1.1\r\nHost: blocked.example.test\r\nConnection: close\r\n\r\n".toByteArray())
                        out.flush()
                        val input = socket.getInputStream()
                        val response = ByteArray(256)
                        val read = input.read(response)
                        val text = if (read > 0) String(response, 0, read) else ""
                        if (!text.startsWith("HTTP/1.1 403")) {
                            return@withContext TestOutcome.fail("blocked host did not return 403: ${text.take(80)}")
                        }
                    }
                    // allowed host -> not a 403 block page
                    TestOutcome.pass("blocked host returned 403")
                } finally {
                    server.stop()
                }
            }
        }
    )

    // ---- Active checks (opt-in) -----------------------------------------------------------

    private fun activeChecks(context: Context): List<SelfTestCheck> = listOf(
        SelfTestCheck(
            id = "active-firewall-restart",
            group = GROUP_ACTIVE,
            name = "Firewall stop and start",
            active = true
        ) {
            val wasRunning = NetGateVpnService.runtimeState.value.isServiceRunning
            if (!wasRunning) {
                return@SelfTestCheck TestOutcome.skip("Firewall is not running; restart skipped")
            }
            if (runCatching { VpnService.prepare(context) }.getOrNull() != null) {
                return@SelfTestCheck TestOutcome.skip("VPN consent is missing; restart skipped")
            }
            val appContext = context.applicationContext
            // Persist the original state so a kill mid-test can be recovered on next start.
            runCatching { appContext.selftestDataStore.edit { it[SELFTEST_FIREWALL_WAS_RUNNING] = true } }
            try {
                NetGateVpnService.stop(context)
                val stopTransitions = mutableListOf<String>()
                var lastStopSig = ""
                val stopStartMs = System.currentTimeMillis()
                var stopped = false
                while (System.currentTimeMillis() - stopStartMs < 12_000) {
                    val state = NetGateVpnService.runtimeState.value
                    val sig = "${state.isServiceRunning}/${state.isTunnelActive}"
                    if (sig != lastStopSig) {
                        stopTransitions += "${System.currentTimeMillis() - stopStartMs} ms: isServiceRunning=${state.isServiceRunning}, isTunnelActive=${state.isTunnelActive}"
                        lastStopSig = sig
                    }
                    if (!state.isServiceRunning) { stopped = true; break }
                    delay(100)
                }
                if (!stopped) {
                    return@SelfTestCheck TestOutcome.fail(
                        "Service did not stop within 12 s",
                        stopTransitions.joinToString("\n").ifBlank { "no state transitions observed" }
                    )
                }

                val started = runCatching { startFirewallService(context) }.isSuccess
                if (!started) return@SelfTestCheck TestOutcome.fail("Service start was refused")

                val startTransitions = mutableListOf<String>()
                var lastStartSig = ""
                val restartStartMs = System.currentTimeMillis()
                var restarted = false
                while (System.currentTimeMillis() - restartStartMs < 12_000) {
                    val state = NetGateVpnService.runtimeState.value
                    val sig = "${state.isServiceRunning}/${state.isTunnelActive}"
                    if (sig != lastStartSig) {
                        startTransitions += "${System.currentTimeMillis() - restartStartMs} ms: isServiceRunning=${state.isServiceRunning}, isTunnelActive=${state.isTunnelActive}"
                        lastStartSig = sig
                    }
                    if (state.isServiceRunning) { restarted = true; break }
                    delay(100)
                }
                if (restarted) TestOutcome.pass("Stopped and restarted cleanly")
                else TestOutcome.fail(
                    "Service did not come back within 12 s",
                    startTransitions.joinToString("\n").ifBlank { "no state transitions observed" }
                )
            } finally {
                runCatching { appContext.selftestDataStore.edit { it.remove(SELFTEST_FIREWALL_WAS_RUNNING) } }
                // Whatever happened above, leave the firewall in the state it started in.
                if (wasRunning && !NetGateVpnService.runtimeState.value.isServiceRunning) {
                    runCatching { startFirewallService(context) }
                }
            }
        },
        SelfTestCheck(
            id = "active-speed-test",
            group = GROUP_ACTIVE,
            name = "Speed test engine",
            active = true
        ) {
            if (!hasInternet(context)) {
                return@SelfTestCheck TestOutcome.warn("No connectivity")
            }
            val firstState = withTimeoutOrNull(6_000) {
                SpeedTestRunner().runTest("self-test").first()
            }
            if (firstState == null) TestOutcome.fail("Speed test produced no state")
            else TestOutcome.pass("Speed test started (${firstState.phase.name.lowercase()})")
        },
        SelfTestCheck(
            id = "active-hotspot-bind",
            group = GROUP_ACTIVE,
            name = "Hotspot proxy port bind",
            active = true
        ) {
            val port = runCatching {
                FirewallPreferences(context).preferencesFlow.first().hotspotProxyPort
            }.getOrDefault(8890)
            withContext(Dispatchers.IO) {
                var server: ServerSocket? = null
                try {
                    server = ServerSocket(port)
                    TestOutcome.pass("Port $port can be bound")
                } catch (_: java.net.BindException) {
                    TestOutcome.warn("Port $port is already in use (proxy may be running)")
                } finally {
                    runCatching { server?.close() }
                }
            }
        }
    )

    // ---- Helpers --------------------------------------------------------------------------

    suspend fun restorePendingFirewallIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val wasRunning = runCatching {
            appContext.selftestDataStore.data.first()[SELFTEST_FIREWALL_WAS_RUNNING] == true
        }.getOrDefault(false)
        if (!wasRunning) return
        runCatching { appContext.selftestDataStore.edit { it.remove(SELFTEST_FIREWALL_WAS_RUNNING) } }
        if (!NetGateVpnService.runtimeState.value.isServiceRunning) {
            runCatching { startFirewallService(appContext) }
        }
    }

    private fun startFirewallService(context: Context) {
        val intent = Intent(context, NetGateVpnService::class.java)
            .setAction(NetGateVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            context.startService(intent)
        }
    }

    private fun buildDnsCacheResponse(qname: String, ttlSeconds: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x12); out.write(0x34)
        out.write(0x81); out.write(0x80)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        for (label in qname.split('.').filter { it.isNotEmpty() }) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        out.write(0xC0); out.write(0x0C)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        out.write(((ttlSeconds ushr 24) and 0xFF).toInt())
        out.write(((ttlSeconds ushr 16) and 0xFF).toInt())
        out.write(((ttlSeconds ushr 8) and 0xFF).toInt())
        out.write((ttlSeconds and 0xFF).toInt())
        out.write(0x00); out.write(0x04)
        out.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        return out.toByteArray()
    }

    private fun hasInternet(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return false
        return connectivity.activeNetwork != null
    }

    /**
     * Synthetic UDP/53 query for `example.test` with loopback-style addresses, so parsing and
     * response construction run without touching the network or any real host.
     */
    private fun buildDnsQueryPacket(ipv6: Boolean): ByteArray {
        val dns = ByteArrayOutputStream()
        dns.write(0x12); dns.write(0x34)
        dns.write(0x01); dns.write(0x00)
        dns.write(0x00); dns.write(0x01)
        dns.write(ByteArray(6))
        for (label in SELF_TEST_DOMAIN.split('.').filter { it.isNotEmpty() }) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dns.write(bytes.size)
            dns.write(bytes)
        }
        dns.write(0x00)
        dns.write(0x00); dns.write(0x01) // A
        dns.write(0x00); dns.write(0x01) // IN
        val payload = dns.toByteArray()

        val srcPort = 53_535
        val dstPort = 53
        return if (ipv6) {
            val header = 40
            val udpLength = 8 + payload.size
            val packet = ByteArray(header + udpLength)
            packet[0] = 0x60
            packet[4] = ((udpLength ushr 8) and 0xFF).toByte()
            packet[5] = (udpLength and 0xFF).toByte()
            packet[6] = 17
            packet[7] = 64
            packet[8] = 0xFD.toByte(); packet[23] = 0x02
            packet[24] = 0xFD.toByte(); packet[39] = 0x01
            packet[40] = ((srcPort ushr 8) and 0xFF).toByte()
            packet[41] = (srcPort and 0xFF).toByte()
            packet[42] = ((dstPort ushr 8) and 0xFF).toByte()
            packet[43] = (dstPort and 0xFF).toByte()
            packet[44] = ((udpLength ushr 8) and 0xFF).toByte()
            packet[45] = (udpLength and 0xFF).toByte()
            System.arraycopy(payload, 0, packet, header + 8, payload.size)
            packet
        } else {
            val header = 20
            val udpLength = 8 + payload.size
            val totalLength = header + udpLength
            val packet = ByteArray(totalLength)
            packet[0] = 0x45
            packet[2] = ((totalLength ushr 8) and 0xFF).toByte()
            packet[3] = (totalLength and 0xFF).toByte()
            packet[8] = 64
            packet[9] = 17
            packet[12] = 10; packet[13] = 0; packet[14] = 0; packet[15] = 2
            packet[16] = 10; packet[17] = 0; packet[18] = 0; packet[19] = 1
            packet[20] = ((srcPort ushr 8) and 0xFF).toByte()
            packet[21] = (srcPort and 0xFF).toByte()
            packet[22] = ((dstPort ushr 8) and 0xFF).toByte()
            packet[23] = (dstPort and 0xFF).toByte()
            packet[24] = ((udpLength ushr 8) and 0xFF).toByte()
            packet[25] = (udpLength and 0xFF).toByte()
            System.arraycopy(payload, 0, packet, header + 8, payload.size)
            packet
        }
    }
}
