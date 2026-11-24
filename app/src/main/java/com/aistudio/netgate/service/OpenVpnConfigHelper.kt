package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.VpnGateServer
import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardProviderType
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * Parses, normalizes, and builds OpenVPN (`.ovpn`) profiles for NetGate's built-in OpenVPN3
 * client engine, including direct one-tap connection support for VPNGate Live relays and custom
 * `.ovpn` files with optional username/password credentials.
 */
object OpenVpnConfigHelper {

    private const val META_USER_PREFIX = "#!netgate-user-b64:"
    private const val META_PASS_PREFIX = "#!netgate-pass-b64:"

    private val UNSUPPORTED_OR_WINDOWS_DIRECTIVES = setOf(
        "register-dns",
        "block-outside-dns",
        "route-method",
        "route-delay",
        "script-security",
        "show-net-up",
        "win-sys",
        "ip-win32",
        "dhcp-option-dns",
        "management",
        "management-query-passwords",
        "management-hold",
        "daemon"
    )

    fun looksLikeOpenVpnConfig(raw: String): Boolean {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        if (normalized.contains("[Interface]", ignoreCase = true) &&
            normalized.contains("[Peer]", ignoreCase = true)
        ) {
            return false
        }
        val lines = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
        val hasRemote = lines.any { it.startsWith("remote ", ignoreCase = true) }
        val hasOvpnMarker = lines.any { line ->
            line.equals("client", ignoreCase = true) ||
                line.startsWith("dev ", ignoreCase = true) ||
                line.startsWith("proto ", ignoreCase = true) ||
                line.equals("<ca>", ignoreCase = true) ||
                line.startsWith("cipher ", ignoreCase = true) ||
                line.startsWith("data-ciphers", ignoreCase = true)
        }
        return hasRemote && hasOvpnMarker
    }

    fun requiresAuthUserPass(rawConfig: String): Boolean {
        return rawConfig
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
            .any { line ->
                line.equals("auth-user-pass", ignoreCase = true) ||
                    line.startsWith("auth-user-pass ", ignoreCase = true)
            }
    }

    fun extractCredentials(rawConfig: String): Pair<String, String> {
        var user = ""
        var pass = ""
        val lines = rawConfig.replace("\r\n", "\n").replace("\r", "\n").lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(META_USER_PREFIX)) {
                user = decodeB64(trimmed.removePrefix(META_USER_PREFIX).trim()).orEmpty()
            } else if (trimmed.startsWith(META_PASS_PREFIX)) {
                pass = decodeB64(trimmed.removePrefix(META_PASS_PREFIX).trim()).orEmpty()
            }
        }
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            return user to pass
        }

        // Also support inline <auth-user-pass> blocks if present in custom configs
        val startIdx = lines.indexOfFirst { it.trim().equals("<auth-user-pass>", ignoreCase = true) }
        val endIdx = lines.indexOfFirst { it.trim().equals("</auth-user-pass>", ignoreCase = true) }
        if (startIdx >= 0 && endIdx > startIdx + 1) {
            val blockLines = lines.subList(startIdx + 1, endIdx).map { it.trim() }.filter { it.isNotEmpty() }
            val blockUser = blockLines.getOrNull(0).orEmpty()
            val blockPass = blockLines.getOrNull(1).orEmpty()
            return blockUser to blockPass
        }
        return "" to ""
    }

    fun embedCredentialsMetadata(
        rawConfig: String,
        username: String?,
        password: String?
    ): String {
        val stripped = stripMetadataAndInlineAuthBlock(rawConfig)
        val cleanUser = username?.trim().orEmpty()
        val cleanPass = password.orEmpty()
        if (cleanUser.isEmpty() && cleanPass.isEmpty()) {
            return stripped
        }
        val header = buildString {
            if (cleanUser.isNotEmpty()) {
                append(META_USER_PREFIX).append(encodeB64(cleanUser)).append('\n')
            }
            if (cleanPass.isNotEmpty()) {
                append(META_PASS_PREFIX).append(encodeB64(cleanPass)).append('\n')
            }
        }
        return (header + stripped).trim()
    }

    /**
     * Produces a clean OpenVPN3-compatible config string without NetGate credential metadata lines
     * or Windows-specific directives that cause `eval_config` / `connect` warnings or failures.
     */
    fun prepareRuntimeConfig(rawConfig: String): String {
        val stripped = stripMetadataAndInlineAuthBlock(rawConfig)
        val out = mutableListOf<String>()
        var inBlock = false
        for (rawLine in stripped.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("<") && !trimmed.startsWith("</")) {
                inBlock = true
                out.add(trimmed)
                continue
            }
            if (trimmed.startsWith("</")) {
                inBlock = false
                out.add(trimmed)
                continue
            }
            if (inBlock) {
                out.add(trimmed)
                continue
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue
            }
            val directive = trimmed.substringBefore(' ').substringBefore('\t').lowercase()
            if (directive in UNSUPPORTED_OR_WINDOWS_DIRECTIVES) {
                continue
            }
            if (directive == "up" || directive == "down" || directive == "route-up") {
                continue
            }
            if (directive == "auth-user-pass") {
                // OpenVPN3 cannot open local filesystem password files; credentials come from provide_creds().
                out.add("auth-user-pass")
                continue
            }
            out.add(trimmed)
        }
        return out.joinToString("\n").trim() + "\n"
    }

    fun parseToProfile(
        rawConfig: String,
        nameHint: String = "",
        providerHint: VpnGuardProviderType? = null,
        idOverride: String? = null,
        routingModeOverride: VpnGuardRoutingMode? = null,
        targetPackagesOverride: Set<String>? = null,
        username: String? = null,
        password: String? = null
    ): Result<VpnGuardProfile> = runCatching {
        val withCreds = if (!username.isNullOrBlank() || !password.isNullOrEmpty()) {
            embedCredentialsMetadata(rawConfig, username, password)
        } else {
            rawConfig.replace("\r\n", "\n").replace("\r", "\n").trim()
        }
        val cleanForParse = stripMetadataAndInlineAuthBlock(withCreds)
        val activeLines = cleanForParse.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }

        val remotes = activeLines.filter { it.startsWith("remote ", ignoreCase = true) }
        require(remotes.isNotEmpty()) {
            "OpenVPN .ovpn config is missing a 'remote <host> <port>' directive"
        }

        val firstRemoteParts = remotes.first().split(Regex("\\s+"))
        val host = firstRemoteParts.getOrNull(1).orEmpty().trim()
        require(host.isNotEmpty()) { "OpenVPN 'remote' directive has no server host" }

        val globalPort = activeLines
            .firstOrNull { it.startsWith("port ", ignoreCase = true) }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.trim()
        val remotePort = firstRemoteParts.getOrNull(2)?.trim()?.takeIf { it.all(Char::isDigit) }
            ?: globalPort
            ?: "1194"

        val proto = firstRemoteParts.getOrNull(3)?.trim()
            ?: activeLines.firstOrNull { it.startsWith("proto ", ignoreCase = true) }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.trim()
            ?: "udp"

        val dnsServers = activeLines
            .filter { it.startsWith("dhcp-option DNS ", ignoreCase = true) }
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(2)?.trim()?.takeIf { ip -> ip.isNotEmpty() } }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Tunnel DNS" }

        val mtu = activeLines
            .firstOrNull { it.startsWith("tun-mtu ", ignoreCase = true) }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(1200, 1500)
            ?: 1500

        val lowerText = cleanForParse.lowercase()
        val lowerName = nameHint.lowercase()
        val detectedProvider = providerHint ?: when {
            lowerText.contains("vpngate") || lowerName.contains("vpngate") || lowerText.contains("public-vpn") ->
                VpnGuardProviderType.VPNGATE
            else -> VpnGuardProviderType.CUSTOM_OPENVPN
        }

        val endpointHost = "$host:$remotePort ($proto)"
        val cleanName = nameHint.trim()
            .removeSuffix(".ovpn")
            .removeSuffix(".conf")
            .removeSuffix(".txt")
            .ifBlank {
                if (detectedProvider == VpnGuardProviderType.VPNGATE) {
                    "VPNGate $host"
                } else {
                    "OpenVPN $host"
                }
            }

        VpnGuardProfile(
            id = idOverride ?: UUID.randomUUID().toString(),
            name = cleanName,
            providerType = detectedProvider,
            rawConfig = withCreds,
            endpointHost = endpointHost,
            interfaceAddresses = "Dynamic OpenVPN TUN",
            dnsServers = dnsServers,
            mtu = mtu,
            routingMode = routingModeOverride ?: VpnGuardRoutingMode.WHOLE_SYSTEM,
            targetPackages = targetPackagesOverride ?: emptySet()
        )
    }

    fun fromVpnGateServer(
        server: VpnGateServer,
        routingMode: VpnGuardRoutingMode = VpnGuardRoutingMode.WHOLE_SYSTEM,
        targetPackages: Set<String> = emptySet()
    ): VpnGuardProfile {
        val safeKey = server.ip.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val deterministicId = "vpngate_$safeKey"
        val profileName = "${server.countryLong} (${server.ip})"
        return parseToProfile(
            rawConfig = server.ovpnConfig,
            nameHint = profileName,
            providerHint = VpnGuardProviderType.VPNGATE,
            idOverride = deterministicId,
            routingModeOverride = routingMode,
            targetPackagesOverride = targetPackages
        ).getOrElse {
            VpnGuardProfile(
                id = deterministicId,
                name = profileName,
                providerType = VpnGuardProviderType.VPNGATE,
                rawConfig = server.ovpnConfig,
                endpointHost = server.ip,
                interfaceAddresses = "Dynamic OpenVPN TUN",
                dnsServers = "Tunnel DNS",
                mtu = 1500,
                routingMode = routingMode,
                targetPackages = targetPackages
            )
        }
    }

    private fun stripMetadataAndInlineAuthBlock(raw: String): String {
        val lines = raw.replace("\r\n", "\n").replace("\r", "\n").lines()
        val out = mutableListOf<String>()
        var skippingAuthBlock = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(META_USER_PREFIX) || trimmed.startsWith(META_PASS_PREFIX)) {
                continue
            }
            if (trimmed.equals("<auth-user-pass>", ignoreCase = true)) {
                skippingAuthBlock = true
                continue
            }
            if (skippingAuthBlock) {
                if (trimmed.equals("</auth-user-pass>", ignoreCase = true)) {
                    skippingAuthBlock = false
                }
                continue
            }
            out.add(line)
        }
        return out.joinToString("\n").trim()
    }

    private fun encodeB64(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeB64(encoded: String): String? {
        return runCatching {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
