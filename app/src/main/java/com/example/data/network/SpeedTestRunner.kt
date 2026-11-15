package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

enum class SpeedTestPhase(val label: String) {
    IDLE("Ready to test"),
    PING("Measuring Ping & Jitter…"),
    DOWNLOAD("Testing Download…"),
    UPLOAD("Testing Upload…"),
    COMPLETED("Test Complete"),
    ERROR("Test Failed")
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val progress: Float = 0f,
    val liveSpeedMbps: Double = 0.0,
    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val testedNetworkLabel: String = "",
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = phase == SpeedTestPhase.PING ||
            phase == SpeedTestPhase.DOWNLOAD ||
            phase == SpeedTestPhase.UPLOAD
}

class SpeedTestRunner {

    fun runTest(networkLabel: String): Flow<SpeedTestState> = flow {
        emit(
            SpeedTestState(
                phase = SpeedTestPhase.PING,
                progress = 0.05f,
                testedNetworkLabel = networkLabel
            )
        )

        // 1. Ping & Jitter via TCP RTT probes
        val pingResult = measurePingAndJitter()
        if (pingResult == null) {
            emit(
                SpeedTestState(
                    phase = SpeedTestPhase.ERROR,
                    testedNetworkLabel = networkLabel,
                    errorMessage = "Unable to reach test server. Check your internet connection."
                )
            )
            return@flow
        }

        val (pingMs, jitterMs) = pingResult
        emit(
            SpeedTestState(
                phase = SpeedTestPhase.DOWNLOAD,
                progress = 0.22f,
                pingMs = pingMs,
                jitterMs = jitterMs,
                testedNetworkLabel = networkLabel
            )
        )

        // 2. Download Speed Test (up to 15 MB or 6 seconds)
        var lastState = SpeedTestState(
            phase = SpeedTestPhase.DOWNLOAD,
            progress = 0.22f,
            pingMs = pingMs,
            jitterMs = jitterMs,
            testedNetworkLabel = networkLabel
        )

        val finalDownloadMbps = measureDownloadSpeed { liveMbps, fraction ->
            lastState = lastState.copy(
                phase = SpeedTestPhase.DOWNLOAD,
                progress = 0.22f + (fraction * 0.38f),
                liveSpeedMbps = liveMbps,
                downloadMbps = liveMbps
            )
            emit(lastState)
        }

        if (finalDownloadMbps == null) {
            emit(
                lastState.copy(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = "Download test interrupted or timed out."
                )
            )
            return@flow
        }

        emit(
            lastState.copy(
                phase = SpeedTestPhase.UPLOAD,
                progress = 0.62f,
                liveSpeedMbps = 0.0,
                downloadMbps = finalDownloadMbps
            )
        )

        // 3. Upload Speed Test (up to 4 MB or 5 seconds)
        var uploadState = lastState.copy(
            phase = SpeedTestPhase.UPLOAD,
            progress = 0.62f,
            liveSpeedMbps = 0.0,
            downloadMbps = finalDownloadMbps
        )

        val finalUploadMbps = measureUploadSpeed { liveMbps, fraction ->
            uploadState = uploadState.copy(
                phase = SpeedTestPhase.UPLOAD,
                progress = 0.62f + (fraction * 0.36f),
                liveSpeedMbps = liveMbps,
                uploadMbps = liveMbps
            )
            emit(uploadState)
        }

        if (finalUploadMbps == null) {
            emit(
                uploadState.copy(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = "Upload test interrupted or timed out."
                )
            )
            return@flow
        }

        emit(
            SpeedTestState(
                phase = SpeedTestPhase.COMPLETED,
                progress = 1f,
                liveSpeedMbps = finalDownloadMbps,
                pingMs = pingMs,
                jitterMs = jitterMs,
                downloadMbps = finalDownloadMbps,
                uploadMbps = finalUploadMbps,
                testedNetworkLabel = networkLabel
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun measurePingAndJitter(): Pair<Int, Int>? {
        val samples = mutableListOf<Double>()
        val endpoints = listOf(
            InetSocketAddress("1.1.1.1", 443),
            InetSocketAddress("8.8.8.8", 443)
        )

        for (attempt in 0 until 5) {
            if (!currentCoroutineContext().isActive) return null
            var measuredMs: Double? = null
            for (addr in endpoints) {
                val socket = Socket()
                try {
                    val startNs = System.nanoTime()
                    socket.connect(addr, 2500)
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                    measuredMs = elapsedMs
                    break
                } catch (_: Exception) {
                    // Try fallback endpoint
                } finally {
                    runCatching { socket.close() }
                }
            }
            if (measuredMs != null) {
                samples.add(measuredMs)
            }
        }

        if (samples.isEmpty()) return null
        val avgPing = samples.average().toInt().coerceAtLeast(1)
        val jitter = if (samples.size > 1) {
            samples.zipWithNext { a, b -> abs(a - b) }.average().toInt()
        } else {
            0
        }
        return avgPing to jitter
    }

    private suspend fun measureDownloadSpeed(
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): Double? {
        val url = URL("https://speed.cloudflare.com/__down?bytes=15000000")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 6000
            useCaches = false
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            val buffer = ByteArray(32 * 1024)
            var totalBytes = 0L
            val startNs = System.nanoTime()
            var lastEmitNs = startNs
            val maxDurationNs = 5_500_000_000L

            connection.inputStream.use { input ->
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    val nowNs = System.nanoTime()
                    val elapsedNs = nowNs - startNs
                    if (nowNs - lastEmitNs >= 150_000_000L && elapsedNs > 50_000_000L) {
                        val mbps = bytesPerNsToMbps(totalBytes, elapsedNs)
                        val fraction = (elapsedNs.toFloat() / maxDurationNs.toFloat()).coerceIn(0f, 1f)
                        onProgress(mbps, fraction)
                        lastEmitNs = nowNs
                    }
                    if (elapsedNs >= maxDurationNs) break
                }
            }

            val totalElapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
            if (totalBytes <= 0L) null else bytesPerNsToMbps(totalBytes, totalElapsedNs)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun measureUploadSpeed(
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): Double? {
        val totalTargetBytes = 4 * 1024 * 1024
        val url = URL("https://speed.cloudflare.com/__up")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 8000
            useCaches = false
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "application/octet-stream")
        }

        return try {
            val payloadChunk = ByteArray(32 * 1024) { 0x5A }
            var totalSent = 0L
            val startNs = System.nanoTime()
            var lastEmitNs = startNs
            val maxDurationNs = 5_000_000_000L

            connection.outputStream.use { output ->
                while (currentCoroutineContext().isActive && totalSent < totalTargetBytes) {
                    output.write(payloadChunk)
                    totalSent += payloadChunk.size
                    val nowNs = System.nanoTime()
                    val elapsedNs = nowNs - startNs
                    if (nowNs - lastEmitNs >= 150_000_000L && elapsedNs > 50_000_000L) {
                        output.flush()
                        val mbps = bytesPerNsToMbps(totalSent, elapsedNs)
                        val fraction = maxOf(
                            totalSent.toFloat() / totalTargetBytes.toFloat(),
                            elapsedNs.toFloat() / maxDurationNs.toFloat()
                        ).coerceIn(0f, 1f)
                        onProgress(mbps, fraction)
                        lastEmitNs = nowNs
                    }
                    if (elapsedNs >= maxDurationNs) break
                }
                output.flush()
            }

            val code = connection.responseCode
            val totalElapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
            if (code in 200..299 && totalSent > 0L) {
                bytesPerNsToMbps(totalSent, totalElapsedNs)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun bytesPerNsToMbps(bytes: Long, elapsedNs: Long): Double {
        val bits = bytes * 8.0
        val seconds = elapsedNs / 1_000_000_000.0
        return (bits / seconds) / 1_000_000.0
    }
}
