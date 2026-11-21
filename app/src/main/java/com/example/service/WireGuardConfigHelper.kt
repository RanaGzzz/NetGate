package com.example.service

import com.example.data.model.VpnGuardProfile
import com.example.data.model.VpnGuardProviderType
import com.example.data.model.VpnGuardRoutingMode
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

object WireGuardConfigHelper {

    val WHATSAPP_PACKAGES: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val EXTENSION_INTERFACE_KEYS = setOf(
        "jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5"
    )

    fun sanitizeWireGuardText(raw: String): String {
        val normalizedLines = raw.replace("\r\n", "\n").replace("\r", "\n").lines()
        val out = mutableListOf<String>()
        var inInterface = false
        for (line in normalizedLines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inInterface = trimmed.equals("[Interface]", ignoreCase = true)
                out.add(trimmed)
                continue
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue
            }
            val eqIdx = trimmed.indexOf('=')
            if (inInterface && eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim().lowercase()
                if (key in EXTENSION_INTERFACE_KEYS) {
                    continue
                }
            }
            out.add(trimmed)
        }
        return out.joinToString("\n").trim()
    }

    fun parseToProfile(
        rawConfig: String,
        nameHint: String = "",
        providerHint: VpnGuardProviderType? = null,
        idOverride: String? = null,
        routingModeOverride: VpnGuardRoutingMode? = null,
        targetPackagesOverride: Set<String>? = null
    ): Result<VpnGuardProfile> = runCatching {
        val cleaned = sanitizeWireGuardText(rawConfig)
        require(cleaned.contains("[Interface]", ignoreCase = true)) {
            "Config is missing [Interface] section"
        }
        require(cleaned.contains("[Peer]", ignoreCase = true)) {
            "Config is missing [Peer] section"
        }

        val parsed = Config.parse(ByteArrayInputStream(cleaned.toByteArray(StandardCharsets.UTF_8)))
        val iface = parsed.`interface`
        val peers = parsed.peers
        require(peers.isNotEmpty()) { "Config must include at least one [Peer]" }

        val firstEndpoint = peers.firstNotNullOfOrNull { peer ->
            peer.endpoint.orElse(null)?.toString()
        }.orEmpty()
        val addresses = iface.addresses.joinToString(", ") { it.toString() }
        val dnsList = iface.dnsServers.joinToString(", ") { it.hostAddress ?: it.toString() }
            .ifBlank { "1.1.1.1" }
        val mtu = iface.mtu.orElse(1280)

        val embeddedIncluded = iface.includedApplications.toSet()
        val embeddedExcluded = iface.excludedApplications.toSet()

        val resolvedRoutingMode = routingModeOverride ?: when {
            embeddedIncluded.isNotEmpty() -> VpnGuardRoutingMode.SELECTED_APPS
            embeddedExcluded.isNotEmpty() -> VpnGuardRoutingMode.EXCLUDE_APPS
            else -> VpnGuardRoutingMode.WHOLE_SYSTEM
        }
        val resolvedPackages = targetPackagesOverride ?: when (resolvedRoutingMode) {
            VpnGuardRoutingMode.SELECTED_APPS -> embeddedIncluded
            VpnGuardRoutingMode.EXCLUDE_APPS -> embeddedExcluded
            VpnGuardRoutingMode.WHOLE_SYSTEM -> emptySet()
        }

        val detectedProvider = providerHint ?: detectProviderType(cleaned, firstEndpoint)
        val cleanName = nameHint.trim()
            .removeSuffix(".conf")
            .removeSuffix(".txt")
            .ifBlank {
                when (detectedProvider) {
                    VpnGuardProviderType.CLOUDFLARE_WARP -> "Cloudflare WARP"
                    VpnGuardProviderType.PROTON_VPN -> "Proton VPN"
                    VpnGuardProviderType.WINDSCRIBE -> "Windscribe WireGuard"
                    else -> firstEndpoint.substringBefore(':').ifBlank { "WireGuard Tunnel" }
                }
            }

        VpnGuardProfile(
            id = idOverride ?: UUID.randomUUID().toString(),
            name = cleanName,
            providerType = detectedProvider,
            rawConfig = stripPerAppLines(cleaned),
            endpointHost = firstEndpoint,
            interfaceAddresses = addresses,
            dnsServers = dnsList,
            mtu = mtu,
            routingMode = resolvedRoutingMode,
            targetPackages = resolvedPackages
        )
    }

    fun stripPerAppLines(rawConfig: String): String {
        return rawConfig
            .replace("\r\n", "\n")
            .lines()
            .filterNot { line ->
                val lower = line.trim().lowercase()
                lower.startsWith("includedapplications") || lower.startsWith("excludedapplications")
            }
            .joinToString("\n")
            .trim()
    }

    fun buildEffectiveConfigText(
        rawConfig: String,
        routingMode: VpnGuardRoutingMode,
        targetPackages: Set<String>
    ): String {
        val baseLines = stripPerAppLines(sanitizeWireGuardText(rawConfig)).lines().toMutableList()
        val sanitizedPackages = targetPackages
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val appDirectiveLine: String? = when (routingMode) {
            VpnGuardRoutingMode.WHOLE_SYSTEM -> null
            VpnGuardRoutingMode.SELECTED_APPS -> {
                if (sanitizedPackages.isNotEmpty()) {
                    "IncludedApplications = ${sanitizedPackages.joinToString(", ")}"
                } else {
                    null
                }
            }
            VpnGuardRoutingMode.EXCLUDE_APPS -> {
                if (sanitizedPackages.isNotEmpty()) {
                    "ExcludedApplications = ${sanitizedPackages.joinToString(", ")}"
                } else {
                    null
                }
            }
        }

        if (appDirectiveLine != null) {
            val ifaceIdx = baseLines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
            if (ifaceIdx >= 0) {
                var insertAt = ifaceIdx + 1
                while (insertAt < baseLines.size && !baseLines[insertAt].trim().startsWith("[")) {
                    insertAt++
                }
                baseLines.add(insertAt, appDirectiveLine)
            }
        }
        return baseLines.joinToString("\n").trim() + "\n"
    }

    fun buildWireGuardConfig(
        profile: VpnGuardProfile
    ): Result<Config> = runCatching {
        val effectiveText = buildEffectiveConfigText(
            rawConfig = profile.rawConfig,
            routingMode = profile.routingMode,
            targetPackages = profile.targetPackages
        )
        Config.parse(ByteArrayInputStream(effectiveText.toByteArray(StandardCharsets.UTF_8)))
    }

    fun generateKeyPair(): Pair<String, String> {
        val kp = KeyPair()
        return kp.privateKey.toBase64() to kp.publicKey.toBase64()
    }

    fun generateTemplateConfig(
        endpoint: String = "your-server.example.com:51820",
        dns: String = "1.1.1.1, 1.0.0.1"
    ): Pair<String, String> {
        val (privateKey, publicKey) = generateKeyPair()
        val peerPlaceholderKey = KeyPair().publicKey.toBase64()
        val conf = """
            [Interface]
            PrivateKey = $privateKey
            Address = 10.66.66.2/32
            DNS = $dns
            MTU = 1280

            [Peer]
            PublicKey = $peerPlaceholderKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $endpoint
            PersistentKeepalive = 25
        """.trimIndent()
        return conf to publicKey
    }

    private fun detectProviderType(configText: String, endpoint: String): VpnGuardProviderType {
        val lowerEndpoint = endpoint.lowercase()
        val lowerText = configText.lowercase()
        return when {
            lowerEndpoint.contains("cloudflareclient.com") ||
                lowerText.contains("bmxoc+f1fxemf9dyik2h5/1sutzh0juvo51h2wpfgyo=") ->
                VpnGuardProviderType.CLOUDFLARE_WARP
            lowerEndpoint.contains("proton") || lowerText.contains("proton") ->
                VpnGuardProviderType.PROTON_VPN
            lowerEndpoint.contains("windscribe") || lowerText.contains("windscribe") ->
                VpnGuardProviderType.WINDSCRIBE
            lowerEndpoint.contains("mullvad") || lowerText.contains("mullvad") ->
                VpnGuardProviderType.MULLVAD_OR_OTHER
            else -> VpnGuardProviderType.CUSTOM_WIREGUARD
        }
    }
}
