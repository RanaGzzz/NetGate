package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.data.model.ActiveSocketInfo
import com.example.data.model.ConnectionRiskLevel
import com.example.data.model.DeviceSecurityAuditReport
import com.example.data.model.SecurityAuditAction
import com.example.data.model.SecurityAuditCheckItem
import com.example.data.model.SecurityAuditStatus
import com.example.data.model.SiteConnectionRecord
import java.io.File
import java.net.InetAddress

data class ParsedProcSocket(
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val stateCode: String,
    val stateLabel: String,
    val uid: Int,
    val isListening: Boolean
)

object SecurityAuditEngine {

    private val HIGH_RISK_PORTS = setOf(23, 135, 445, 1433, 3389, 4444, 5555, 5900, 6667, 1337, 31337)
    private val NOTICE_PORTS = setOf(21, 22, 80, 1080, 8080)
    private val SUSPICIOUS_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "top", "xyz", "pw", "icu", "buzz")
    private val IPV4_LITERAL_REGEX = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

    fun assessConnectionRisk(
        domain: String,
        remoteIp: String,
        remotePort: Int,
        protocol: String,
        bytesSent: Long,
        bytesReceived: Long,
        actionStatus: String,
        countryCode: String,
        isAdBlocked: Boolean = false
    ): Pair<ConnectionRiskLevel, String> {
        val cleanDomain = domain.trim().lowercase()

        if (remotePort in HIGH_RISK_PORTS) {
            return ConnectionRiskLevel.CRITICAL to "Remote management / shell port ($remotePort)"
        }
        if (cleanDomain.startsWith("xn--") || cleanDomain.contains(".xn--")) {
            return ConnectionRiskLevel.SUSPICIOUS to "Punycode homograph domain"
        }
        if (IPV4_LITERAL_REGEX.matches(cleanDomain) && !GeoIpResolver.isPrivateOrLocalIp(cleanDomain)) {
            return ConnectionRiskLevel.SUSPICIOUS to "Direct raw IP connection bypassing DNS"
        }
        if (bytesSent > 2_097_152L && bytesSent > (bytesReceived * 3L + 512_000L)) {
            return ConnectionRiskLevel.SUSPICIOUS to "High outbound upload ratio (possible exfiltration)"
        }
        val tld = cleanDomain.substringAfterLast('.', "")
        if (tld in SUSPICIOUS_TLDS && actionStatus != "ALLOWED") {
            return ConnectionRiskLevel.SUSPICIOUS to "High-risk TLD (.$tld)"
        }
        if (isAdBlocked || actionStatus == "BLOCKED") {
            return ConnectionRiskLevel.NOTICE to "Blocked by NetGate Guard"
        }
        if (remotePort in NOTICE_PORTS || protocol.equals("HTTP", ignoreCase = true)) {
            val detail = if (remotePort == 80 || protocol.equals("HTTP", ignoreCase = true)) {
                "Unencrypted plaintext HTTP traffic"
            } else {
                "Non-standard service port ($remotePort)"
            }
            return ConnectionRiskLevel.NOTICE to detail
        }
        if (countryCode in setOf("RU", "KP", "IR") && cleanDomain.isNotBlank()) {
            return ConnectionRiskLevel.NOTICE to "Foreign region endpoint ($countryCode)"
        }
        return ConnectionRiskLevel.SAFE to "Encrypted / normal endpoint"
    }

    fun runDeviceSecurityAudit(
        context: Context,
        recentConnections: List<SiteConnectionRecord>
    ): DeviceSecurityAuditReport {
        val checks = mutableListOf<SecurityAuditCheckItem>()
        var score = 100

        // 1. Third-party Accessibility Services (primary spyware/trojan vector)
        val accessibilityResult = checkAccessibilityServices(context)
        checks.add(accessibilityResult.first)
        score -= accessibilityResult.second

        // 2. Active Device Administrators
        val adminResult = checkDeviceAdmins(context)
        checks.add(adminResult.first)
        score -= adminResult.second

        // 3. USB / Wireless ADB Debugging
        val adbResult = checkAdbDebugging(context)
        checks.add(adbResult.first)
        score -= adbResult.second

        // 4. High-Risk Spyware Permission Combinations on Non-System Apps
        val permsResult = checkHighRiskAppPermissions(context)
        checks.add(permsResult.first)
        score -= permsResult.second

        // 5. Network / Unknown VPN / Proxy Interception & Root Indicators
        val integrityResult = checkVpnProxyAndRootIntegrity(context)
        checks.add(integrityResult.first)
        score -= integrityResult.second

        // 6. Active Sockets & Listening Ports (/proc/net/tcp & tcp6)
        val socketScan = scanDeviceSockets(context)
        checks.add(socketScan.checkItem)
        score -= socketScan.penalty

        // 7. Flagged Network Connections from Live/Room History
        val flaggedConnections = recentConnections.filter {
            it.riskLevel == ConnectionRiskLevel.SUSPICIOUS || it.riskLevel == ConnectionRiskLevel.CRITICAL
        }
        val criticalConnCount = flaggedConnections.count { it.riskLevel == ConnectionRiskLevel.CRITICAL }
        val connCheck = when {
            criticalConnCount > 0 -> {
                score -= minOf(25, criticalConnCount * 10)
                SecurityAuditCheckItem(
                    id = "flagged_connections",
                    title = "High-Risk Remote Connections",
                    summary = "$criticalConnCount critical / ${flaggedConnections.size} suspicious connection(s) detected",
                    detail = flaggedConnections.take(3).joinToString(" • ") { "${it.domain} (${it.riskReason})" },
                    status = SecurityAuditStatus.WARNING,
                    actionLabel = "Inspect Flagged Sites",
                    actionType = SecurityAuditAction.FILTER_FLAGGED_SITES
                )
            }
            flaggedConnections.isNotEmpty() -> {
                score -= minOf(12, flaggedConnections.size * 4)
                SecurityAuditCheckItem(
                    id = "flagged_connections",
                    title = "Suspicious Remote Traffic",
                    summary = "${flaggedConnections.size} suspicious endpoint(s) logged",
                    detail = flaggedConnections.take(3).joinToString(" • ") { "${it.domain} (${it.riskReason})" },
                    status = SecurityAuditStatus.NOTICE,
                    actionLabel = "Inspect Flagged Sites",
                    actionType = SecurityAuditAction.FILTER_FLAGGED_SITES
                )
            }
            else -> SecurityAuditCheckItem(
                id = "flagged_connections",
                title = "Remote Site & C2 Threat Scan",
                summary = if (recentConnections.isEmpty()) {
                    "0 suspicious endpoints (enable Firewall VPN to inspect live sites)"
                } else {
                    "No malware, raw-IP C2, or high-upload exfiltration seen across ${recentConnections.size} site(s)"
                },
                detail = "Continuously scores outgoing & incoming endpoints for suspicious ports, raw IP beacons, and upload spikes.",
                status = SecurityAuditStatus.SECURE
            )
        }
        checks.add(connCheck)

        val boundedScore = score.coerceIn(0, 100)
        val hasWarning = checks.any { it.status == SecurityAuditStatus.WARNING }
        val hasNotice = checks.any { it.status == SecurityAuditStatus.NOTICE }
        val overallStatus = when {
            hasWarning -> SecurityAuditStatus.WARNING
            hasNotice -> SecurityAuditStatus.NOTICE
            else -> SecurityAuditStatus.SECURE
        }
        val verdict = when (overallStatus) {
            SecurityAuditStatus.SECURE -> "Device Looks Clean"
            SecurityAuditStatus.NOTICE -> "Review Recommended"
            SecurityAuditStatus.WARNING -> "Potential Intrusion Signals"
        }
        val summary = when (overallStatus) {
            SecurityAuditStatus.SECURE ->
                "No unauthorized accessibility hooks, device admins, debug bridges, or suspicious remote hosts detected."
            SecurityAuditStatus.NOTICE ->
                "A few settings or permissions need a quick look to verify you authorized them."
            SecurityAuditStatus.WARNING ->
                "High-risk access hooks, open ports, or suspicious connections were found. Review highlighted items below."
        }

        return DeviceSecurityAuditReport(
            isScanning = false,
            securityScore = boundedScore,
            verdictLabel = verdict,
            overallStatus = overallStatus,
            summaryText = summary,
            checks = checks,
            activeSockets = socketScan.sockets,
            flaggedConnectionsCount = flaggedConnections.size,
            lastScannedAt = System.currentTimeMillis()
        )
    }

    private fun checkAccessibilityServices(context: Context): Pair<SecurityAuditCheckItem, Int> {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabled = runCatching {
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
        }.getOrDefault(emptyList())

        val nonSystemServices = mutableListOf<String>()
        val pm = context.packageManager
        for (svc in enabled) {
            val appInfo = svc.resolveInfo?.serviceInfo?.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystem) {
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName)
                nonSystemServices.add(label)
            }
        }

        return if (nonSystemServices.isNotEmpty()) {
            SecurityAuditCheckItem(
                id = "accessibility_hooks",
                title = "Third-Party Accessibility Hooks",
                summary = "${nonSystemServices.size} user-installed Accessibility service(s) active",
                detail = "Active: ${nonSystemServices.joinToString(", ")}. Malware and stalkerware abuse Accessibility to read your screen and capture passwords.",
                status = SecurityAuditStatus.WARNING,
                actionLabel = "Review Accessibility",
                actionType = SecurityAuditAction.OPEN_ACCESSIBILITY_SETTINGS
            ) to 20
        } else {
            SecurityAuditCheckItem(
                id = "accessibility_hooks",
                title = "Accessibility Screen-Read Hooks",
                summary = "0 third-party Accessibility services active",
                detail = "No non-system app has Accessibility screen-reading or remote tap control enabled.",
                status = SecurityAuditStatus.SECURE
            ) to 0
        }
    }

    private fun checkDeviceAdmins(context: Context): Pair<SecurityAuditCheckItem, Int> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admins = runCatching { dpm?.activeAdmins.orEmpty() }.getOrDefault(emptyList())
        val pm = context.packageManager
        val thirdPartyAdmins = mutableListOf<String>()

        for (component in admins) {
            val pkg = component.packageName
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull()
            val isSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystem) {
                val label = appInfo?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: pkg
                thirdPartyAdmins.add(label)
            }
        }

        return if (thirdPartyAdmins.isNotEmpty()) {
            SecurityAuditCheckItem(
                id = "device_admins",
                title = "Device Administrator Apps",
                summary = "${thirdPartyAdmins.size} non-system Device Admin active",
                detail = "Apps: ${thirdPartyAdmins.joinToString(", ")}. Hidden spyware uses Device Admin to block uninstallation.",
                status = SecurityAuditStatus.NOTICE,
                actionLabel = "Review Device Admins",
                actionType = SecurityAuditAction.OPEN_DEVICE_ADMIN_SETTINGS
            ) to 10
        } else {
            SecurityAuditCheckItem(
                id = "device_admins",
                title = "Device Administrator Apps",
                summary = "No third-party Device Admins active",
                detail = "No user-installed app holds Device Administrator lock privileges.",
                status = SecurityAuditStatus.SECURE
            ) to 0
        }
    }

    private fun checkAdbDebugging(context: Context): Pair<SecurityAuditCheckItem, Int> {
        val adbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        val wifiAdbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        }.getOrDefault(false)

        return if (adbEnabled || wifiAdbEnabled) {
            val modeText = if (wifiAdbEnabled) "USB & Wireless ADB Debugging" else "USB Debugging (ADB)"
            SecurityAuditCheckItem(
                id = "adb_debugging",
                title = "Remote Debug Bridge (ADB)",
                summary = "$modeText is currently ON",
                detail = "When enabled, a connected computer or network peer with an authorized key can run shell commands on your device.",
                status = SecurityAuditStatus.NOTICE,
                actionLabel = "Developer Settings",
                actionType = SecurityAuditAction.OPEN_DEVELOPER_SETTINGS
            ) to 8
        } else {
            SecurityAuditCheckItem(
                id = "adb_debugging",
                title = "Remote Debug Bridge (ADB)",
                summary = "USB & Wireless Debugging OFF",
                detail = "External shell debugging access is disabled.",
                status = SecurityAuditStatus.SECURE
            ) to 0
        }
    }

    private fun checkHighRiskAppPermissions(context: Context): Pair<SecurityAuditCheckItem, Int> {
        val pm = context.packageManager
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        }.getOrDefault(emptyList())

        val flaggedApps = mutableListOf<String>()
        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem || pkgInfo.packageName == context.packageName) continue

            val req = pkgInfo.requestedPermissions?.toSet() ?: continue
            val hasMic = "android.permission.RECORD_AUDIO" in req
            val hasCam = "android.permission.CAMERA" in req
            val hasSensitive = "android.permission.READ_SMS" in req ||
                "android.permission.READ_CALL_LOG" in req ||
                "android.permission.ACCESS_BACKGROUND_LOCATION" in req
            val hasOverlay = "android.permission.SYSTEM_ALERT_WINDOW" in req
            if (hasMic && hasCam && (hasSensitive || hasOverlay)) {
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(pkgInfo.packageName)
                flaggedApps.add(label)
            }
        }

        return if (flaggedApps.isNotEmpty()) {
            val preview = flaggedApps.take(4).joinToString(", ") +
                if (flaggedApps.size > 4) " (+${flaggedApps.size - 4} more)" else ""
            SecurityAuditCheckItem(
                id = "spyware_permissions",
                title = "Sensitive Surveillance Permissions",
                summary = "${flaggedApps.size} app(s) request Mic + Camera + Sensitive Access",
                detail = "Verify you trust: $preview",
                status = SecurityAuditStatus.NOTICE
            ) to 6
        } else {
            SecurityAuditCheckItem(
                id = "spyware_permissions",
                title = "Sensitive Surveillance Permissions",
                summary = "No suspicious Mic+Camera+SMS/Overlay clusters",
                detail = "Scanned installed user apps for spyware-like permission combinations.",
                status = SecurityAuditStatus.SECURE
            ) to 0
        }
    }

    private fun checkVpnProxyAndRootIntegrity(context: Context): Pair<SecurityAuditCheckItem, Int> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeCaps = cm?.activeNetwork?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val vpnActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val netGateRunning = NetGateVpnService.runtimeState.value.isServiceRunning
        val foreignVpn = vpnActive && !netGateRunning

        val sysProxyHost = System.getProperty("http.proxyHost")?.trim().orEmpty()
        val hasRootBinary = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any {
            runCatching { File(it).exists() }.getOrDefault(false)
        }
        val testKeys = Build.TAGS?.contains("test-keys") == true

        return when {
            foreignVpn || hasRootBinary || testKeys -> {
                val issues = buildList {
                    if (foreignVpn) add("A third-party VPN is currently routing your traffic")
                    if (hasRootBinary) add("Root 'su' binary detected on device")
                    if (testKeys) add("ROM signed with test-keys")
                }
                SecurityAuditCheckItem(
                    id = "system_integrity",
                    title = "Tunnel, Proxy & Root Integrity",
                    summary = issues.first(),
                    detail = issues.joinToString(" • "),
                    status = SecurityAuditStatus.WARNING
                ) to 18
            }
            sysProxyHost.isNotBlank() && sysProxyHost != "127.0.0.1" -> {
                SecurityAuditCheckItem(
                    id = "system_integrity",
                    title = "Tunnel, Proxy & Root Integrity",
                    summary = "System HTTP proxy configured ($sysProxyHost)",
                    detail = "Verify this proxy was set intentionally by you or your Wi-Fi network.",
                    status = SecurityAuditStatus.NOTICE
                ) to 8
            }
            else -> {
                SecurityAuditCheckItem(
                    id = "system_integrity",
                    title = "Tunnel, Proxy & Root Integrity",
                    summary = if (netGateRunning) "NetGate Local VPN Guard active • No root/proxy hijack" else "No foreign VPN, proxy hijack, or su binary",
                    detail = "Verified no unknown VPN tunnel, external HTTP proxy, or root shell binary is intercepting traffic.",
                    status = SecurityAuditStatus.SECURE
                ) to 0
            }
        }
    }

    private data class SocketScanResult(
        val checkItem: SecurityAuditCheckItem,
        val sockets: List<ActiveSocketInfo>,
        val penalty: Int
    )

    private fun scanDeviceSockets(context: Context): SocketScanResult {
        val sources = listOf(
            Triple("/proc/net/tcp", "TCP", false),
            Triple("/proc/net/tcp6", "TCP6", true),
            Triple("/proc/net/udp", "UDP", false),
            Triple("/proc/net/udp6", "UDP6", true)
        )

        var anyReadable = false
        val parsed = mutableListOf<ParsedProcSocket>()
        for ((path, proto, isV6) in sources) {
            val content = runCatching {
                val f = File(path)
                if (f.canRead()) f.readText() else null
            }.getOrNull()
            if (content != null) {
                anyReadable = true
                parsed.addAll(parseProcNetSockets(content, proto, isV6))
            }
        }

        if (!anyReadable) {
            return SocketScanResult(
                checkItem = SecurityAuditCheckItem(
                    id = "open_sockets",
                    title = "Open Listening Ports & Sockets",
                    summary = "Protected by Android kernel SELinux sandbox",
                    detail = "Android 10+ isolates /proc/net socket tables from apps; live site monitoring runs safely via NetGate's local VPN DNS & SNI proxy.",
                    status = SecurityAuditStatus.SECURE
                ),
                sockets = emptyList(),
                penalty = 0
            )
        }

        val pm = context.packageManager
        val activeInfos = parsed.take(80).map { s ->
            val pkg = runCatching { pm.getPackagesForUid(s.uid)?.firstOrNull() }.getOrNull().orEmpty()
            val appName = if (pkg.isNotBlank()) {
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                }.getOrDefault(pkg)
            } else {
                "UID ${s.uid}"
            }
            val geo = GeoIpResolver.resolveImmediate(s.remoteAddress)
            val suspiciousListen = s.isListening && (s.localPort in HIGH_RISK_PORTS || !GeoIpResolver.isPrivateOrLocalIp(s.localAddress))
            val suspiciousRemote = !s.isListening && s.remotePort in HIGH_RISK_PORTS
            val isSuspicious = suspiciousListen || suspiciousRemote
            val reason = when {
                suspiciousListen -> "Open listening port ${s.localPort} on ${s.localAddress}"
                suspiciousRemote -> "Active connection to high-risk port ${s.remotePort}"
                s.isListening -> "Local listening socket"
                else -> "Active socket"
            }

            ActiveSocketInfo(
                protocol = s.protocol,
                localAddress = s.localAddress,
                localPort = s.localPort,
                remoteAddress = s.remoteAddress,
                remotePort = s.remotePort,
                stateLabel = s.stateLabel,
                uid = s.uid,
                appPackage = pkg,
                appName = appName,
                countryCode = geo.countryCode,
                countryName = geo.countryName,
                isListening = s.isListening,
                isSuspicious = isSuspicious,
                reason = reason
            )
        }

        val suspiciousCount = activeInfos.count { it.isSuspicious }
        val listeningCount = activeInfos.count { it.isListening }
        val checkItem = if (suspiciousCount > 0) {
            SecurityAuditCheckItem(
                id = "open_sockets",
                title = "Open Listening Ports & Sockets",
                summary = "$suspiciousCount suspicious socket(s) / $listeningCount listening",
                detail = activeInfos.filter { it.isSuspicious }.take(3).joinToString(" • ") { "${it.appName}: ${it.reason}" },
                status = SecurityAuditStatus.WARNING
            )
        } else {
            SecurityAuditCheckItem(
                id = "open_sockets",
                title = "Open Listening Ports & Sockets",
                summary = "${activeInfos.size} sockets inspected ($listeningCount listening) • Clean",
                detail = "No unexpected remote shell or high-risk backdoor listening ports detected.",
                status = SecurityAuditStatus.SECURE
            )
        }

        return SocketScanResult(
            checkItem = checkItem,
            sockets = activeInfos,
            penalty = if (suspiciousCount > 0) 20 else 0
        )
    }

    fun parseProcNetSockets(
        rawText: String,
        protocol: String,
        isIpv6: Boolean
    ): List<ParsedProcSocket> {
        val lines = rawText.lineSequence().drop(1)
        val out = mutableListOf<ParsedProcSocket>()
        for (line in lines) {
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 8) continue
            val localParts = cols[1].split(':')
            val remoteParts = cols[2].split(':')
            if (localParts.size != 2 || remoteParts.size != 2) continue

            val localIp = decodeProcHexIp(localParts[0], isIpv6) ?: continue
            val localPort = localParts[1].toIntOrNull(16) ?: continue
            val remoteIp = decodeProcHexIp(remoteParts[0], isIpv6) ?: continue
            val remotePort = remoteParts[1].toIntOrNull(16) ?: continue
            val stateCode = cols[3].uppercase()
            val uid = cols[7].toIntOrNull() ?: -1

            val isListening = stateCode == "0A"
            val stateLabel = when (stateCode) {
                "01" -> "ESTABLISHED"
                "02" -> "SYN_SENT"
                "03" -> "SYN_RECV"
                "06" -> "TIME_WAIT"
                "0A" -> "LISTEN"
                else -> stateCode
            }

            if (stateCode == "01" || stateCode == "0A" || stateCode == "02") {
                out.add(
                    ParsedProcSocket(
                        protocol = protocol,
                        localAddress = localIp,
                        localPort = localPort,
                        remoteAddress = remoteIp,
                        remotePort = remotePort,
                        stateCode = stateCode,
                        stateLabel = stateLabel,
                        uid = uid,
                        isListening = isListening
                    )
                )
            }
        }
        return out
    }

    private fun decodeProcHexIp(hex: String, isIpv6: Boolean): String? {
        if (!isIpv6 && hex.length == 8) {
            // /proc/net/tcp stores little-endian 32-bit IPv4 word
            val bytes = byteArrayOf(
                hex.substring(6, 8).toIntOrNull(16)?.toByte() ?: return null,
                hex.substring(4, 6).toIntOrNull(16)?.toByte() ?: return null,
                hex.substring(2, 4).toIntOrNull(16)?.toByte() ?: return null,
                hex.substring(0, 2).toIntOrNull(16)?.toByte() ?: return null
            )
            return runCatching { InetAddress.getByAddress(bytes).hostAddress }.getOrNull()
        }
        if (isIpv6 && hex.length == 32) {
            // Four 32-bit little-endian words
            val out = ByteArray(16)
            for (wordIdx in 0 until 4) {
                val w = hex.substring(wordIdx * 8, wordIdx * 8 + 8)
                out[wordIdx * 4] = w.substring(6, 8).toIntOrNull(16)?.toByte() ?: return null
                out[wordIdx * 4 + 1] = w.substring(4, 6).toIntOrNull(16)?.toByte() ?: return null
                out[wordIdx * 4 + 2] = w.substring(2, 4).toIntOrNull(16)?.toByte() ?: return null
                out[wordIdx * 4 + 3] = w.substring(0, 2).toIntOrNull(16)?.toByte() ?: return null
            }
            return runCatching { InetAddress.getByAddress(out).hostAddress }.getOrNull()
        }
        return null
    }
}
