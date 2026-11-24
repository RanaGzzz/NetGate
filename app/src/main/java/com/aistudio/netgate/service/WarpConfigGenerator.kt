package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.VpnGuardProfile
import com.aistudio.netgate.data.model.VpnGuardProviderType
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

object WarpConfigGenerator {

    private const val WARP_REG_URL = "https://api.cloudflareclient.com/v0a2158/reg"
    private const val DEFAULT_WARP_PEER_PUBKEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    private const val DEFAULT_WARP_ENDPOINT = "engage.cloudflareclient.com:2408"

    private val dohFallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = runCatching { Dns.SYSTEM.lookup(hostname) }
                .getOrNull()
                .orEmpty()
                .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress || it.isSiteLocalAddress }
            if (system.isNotEmpty()) return system
            val dohIps = AdBlockFilterEngine.resolveViaDoh(
                domain = hostname,
                dohUrl = "https://1.1.1.1/dns-query"
            )
            if (dohIps.isNotEmpty()) {
                val parsed = dohIps.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                if (parsed.isNotEmpty()) return parsed
            }
            return runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
        }
    }

    /**
     * Resolves a WireGuard peer host using system DNS first, falling back to 1.1.1.1 DoH
     * (and known Cloudflare WARP anycast IPs if DNS is blocked locally).
     */
    fun resolveHostForWireGuard(host: String): String? {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.isEmpty()) return null
        val system = runCatching { InetAddress.getAllByName(clean).toList() }
            .getOrNull()
            .orEmpty()
            .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress || it.isSiteLocalAddress }
        if (system.isNotEmpty()) {
            return null // System DNS works natively; GoBackend can keep the hostname directly
        }
        val dohIps = AdBlockFilterEngine.resolveViaDoh(
            domain = clean,
            dohUrl = "https://1.1.1.1/dns-query"
        )
        if (dohIps.isNotEmpty()) {
            return dohIps.first()
        }
        if (clean.equals("engage.cloudflareclient.com", ignoreCase = true)) {
            return "162.159.192.1"
        }
        return null
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dohFallbackDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun generateLiveWarpProfile(
        profileName: String = "Cloudflare WARP",
        routingMode: VpnGuardRoutingMode = VpnGuardRoutingMode.WHOLE_SYSTEM,
        targetPackages: Set<String> = emptySet()
    ): Result<VpnGuardProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val (privateKey, publicKey) = WireGuardConfigHelper.generateKeyPair()
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val payload = JSONObject().apply {
                put("key", publicKey)
                put("install_id", "")
                put("fcm_token", "")
                put("tos", timestamp)
                put("model", "NetGate Android")
                put("type", "Android")
                put("locale", "en_US")
                put("warp_enabled", true)
            }.toString()

            val request = Request.Builder()
                .url(WARP_REG_URL)
                .header("User-Agent", "okhttp/3.12.1")
                .header("CF-Client-Version", "a-6.30-3596")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                check(response.isSuccessful && bodyText.isNotBlank()) {
                    "WARP registration failed (HTTP ${response.code}). Try connecting again or import an external .conf."
                }
                parseWarpRegistrationJson(
                    jsonText = bodyText,
                    privateKeyBase64 = privateKey,
                    profileName = profileName,
                    routingMode = routingMode,
                    targetPackages = targetPackages
                ).getOrThrow()
            }
        }
    }

    fun parseWarpRegistrationJson(
        jsonText: String,
        privateKeyBase64: String,
        profileName: String = "Cloudflare WARP",
        routingMode: VpnGuardRoutingMode = VpnGuardRoutingMode.WHOLE_SYSTEM,
        targetPackages: Set<String> = emptySet()
    ): Result<VpnGuardProfile> = runCatching {
        val root = JSONObject(jsonText)
        val config = root.getJSONObject("config")
        val iface = config.getJSONObject("interface")
        val addresses = iface.getJSONObject("addresses")
        val v4Raw = addresses.optString("v4", "172.16.0.2").trim().ifEmpty { "172.16.0.2" }
        val v4Cidr = if (v4Raw.contains('/')) v4Raw else "$v4Raw/32"
        val v6Raw = addresses.optString("v6", "").trim()
        val addressLine = if (v6Raw.isNotEmpty()) {
            val v6Cidr = if (v6Raw.contains('/')) v6Raw else "$v6Raw/128"
            "$v4Cidr, $v6Cidr"
        } else {
            v4Cidr
        }

        val peersArray = config.getJSONArray("peers")
        require(peersArray.length() > 0) { "WARP API returned no peers" }
        val peerObj = peersArray.getJSONObject(0)
        val peerPublicKey = peerObj.optString("public_key", DEFAULT_WARP_PEER_PUBKEY)
            .ifBlank { DEFAULT_WARP_PEER_PUBKEY }
        val endpointObj = peerObj.optJSONObject("endpoint")
        val endpointHostRaw = endpointObj?.optString("host", "").orEmpty().trim()
        val endpointV4Raw = endpointObj?.optString("v4", "").orEmpty().trim()
        val endpoint = when {
            endpointHostRaw.isNotBlank() && !endpointHostRaw.endsWith(":0") && endpointHostRaw.contains(':') -> endpointHostRaw
            endpointHostRaw.isNotBlank() && endpointHostRaw.endsWith(":0") -> "${endpointHostRaw.removeSuffix(":0")}:2408"
            endpointHostRaw.isNotBlank() -> "$endpointHostRaw:2408"
            endpointV4Raw.isNotBlank() && !endpointV4Raw.endsWith(":0") && endpointV4Raw.contains(':') -> endpointV4Raw
            endpointV4Raw.isNotBlank() -> "${endpointV4Raw.substringBefore(':')}:2408"
            else -> DEFAULT_WARP_ENDPOINT
        }

        val conf = """
            [Interface]
            PrivateKey = $privateKeyBase64
            Address = $addressLine
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1280

            [Peer]
            PublicKey = $peerPublicKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $endpoint
            PersistentKeepalive = 25
        """.trimIndent()

        WireGuardConfigHelper.parseToProfile(
            rawConfig = conf,
            nameHint = profileName,
            providerHint = VpnGuardProviderType.CLOUDFLARE_WARP,
            idOverride = UUID.randomUUID().toString(),
            routingModeOverride = routingMode,
            targetPackagesOverride = targetPackages
        ).getOrThrow()
    }
}
