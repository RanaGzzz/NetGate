package com.example.service

import com.example.data.model.VpnGateServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

object VpnGateClient {

    private val VPNGATE_URLS = listOf(
        "https://www.vpngate.net/api/iphone/",
        "https://download.vpngate.jp/api/iphone/"
    )

    private val dohFallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
            if (system.isNotEmpty()) return system
            val dohIps = AdBlockFilterEngine.resolveViaDoh(
                domain = hostname,
                dohUrl = "https://1.1.1.1/dns-query"
            )
            if (dohIps.isNotEmpty()) {
                return dohIps.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
            }
            return system
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dohFallbackDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchLiveServers(): Result<List<VpnGateServer>> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in VPNGATE_URLS) {
            val attempt = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NetGate/1.0 Android")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val csv = response.body?.string().orEmpty()
                    check(response.isSuccessful && csv.isNotBlank()) {
                        "VPNGate feed unavailable (HTTP ${response.code})"
                    }
                    val parsed = parseCsv(csv)
                    check(parsed.isNotEmpty()) {
                        "VPNGate feed returned 0 valid OpenVPN servers"
                    }
                    parsed
                }
            }
            if (attempt.isSuccess) return@withContext attempt
            lastError = attempt.exceptionOrNull()
        }
        Result.failure(
            lastError ?: IllegalStateException("Unable to reach VPNGate server list")
        )
    }

    fun parseCsv(csvText: String): List<VpnGateServer> {
        val servers = mutableListOf<VpnGateServer>()
        val lines = csvText.replace("\r\n", "\n").replace("\r", "\n").lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("#")
            ) {
                continue
            }
            val cols = parseCsvRow(trimmed)
            if (cols.size < 15) continue

            val hostName = cols[0].trim()
            val ip = cols[1].trim()
            val score = cols[2].trim().toLongOrNull() ?: 0L
            val pingMs = cols[3].trim().toIntOrNull() ?: -1
            val speedBps = cols[4].trim().toLongOrNull() ?: 0L
            val countryLong = cols[5].trim()
            val countryShort = cols[6].trim().uppercase()
            val numSessions = cols[7].trim().toIntOrNull() ?: 0
            val uptimeMs = cols[8].trim().toLongOrNull() ?: 0L
            val totalUsers = cols[9].trim().toLongOrNull() ?: 0L
            val totalTraffic = cols[10].trim().toLongOrNull() ?: 0L
            val logType = cols[11].trim()
            val operator = cols[12].trim()
            val message = cols[13].trim()
            val ovpnBase64 = cols[14].trim()

            if (ip.isBlank() || ovpnBase64.isBlank()) continue
            val ovpnDecoded = decodeBase64Safely(ovpnBase64) ?: continue
            if (!ovpnDecoded.contains("remote ", ignoreCase = true) &&
                !ovpnDecoded.contains("dev ", ignoreCase = true)
            ) {
                continue
            }

            servers.add(
                VpnGateServer(
                    hostName = hostName.ifBlank { ip },
                    ip = ip,
                    score = score,
                    pingMs = pingMs,
                    speedBps = speedBps,
                    countryLong = countryLong.ifBlank { countryShort.ifBlank { "Global" } },
                    countryShort = countryShort,
                    numVpnSessions = numSessions,
                    uptimeMs = uptimeMs,
                    totalUsers = totalUsers,
                    totalTrafficBytes = totalTraffic,
                    logType = logType,
                    operatorName = operator,
                    message = message,
                    ovpnConfig = ovpnDecoded
                )
            )
        }
        return servers
    }

    private fun decodeBase64Safely(raw: String): String? {
        return runCatching {
            val clean = raw.replace("\\s".toRegex(), "")
            val bytes = Base64.getDecoder().decode(clean)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun parseCsvRow(line: String): List<String> {
        val result = ArrayList<String>(16)
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
