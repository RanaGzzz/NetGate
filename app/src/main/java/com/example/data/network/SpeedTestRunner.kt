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

        // 2. Download Speed Test with multi-mirror fallback
        var downloadState = SpeedTestState(
            phase = SpeedTestPhase.DOWNLOAD,
            progress = 0.22f,
            pingMs = pingMs,
            jitterMs = jitterMs,
            testedNetworkLabel = networkLabel
        )

        val downloadOutcome = measureDownloadSpeed { liveMbps, fraction ->
            downloadState = downloadState.copy(
                phase = SpeedTestPhase.DOWNLOAD,
                progress = 0.22f + (fraction * 0.38f),
                liveSpeedMbps = liveMbps,
                downloadMbps = liveMbps
            )
            emit(downloadState)
        }

        val finalDownloadMbps = downloadOutcome.mbps
        if (finalDownloadMbps == null) {
            emit(
                downloadState.copy(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = downloadOutcome.error ?: "Download test failed or timed out."
                )
            )
            return@flow
        }

        emit(
            downloadState.copy(
                phase = SpeedTestPhase.UPLOAD,
                progress = 0.62f,
                liveSpeedMbps = 0.0,
                downloadMbps = finalDownloadMbps
            )
        )

        // 3. Upload Speed Test with fixed-length streaming & fallback
        var uploadState = downloadState.copy(
            phase = SpeedTestPhase.UPLOAD,
            progress = 0.62f,
            liveSpeedMbps = 0.0,
            downloadMbps = finalDownloadMbps
        )

        val uploadOutcome = measureUploadSpeed { liveMbps, fraction ->
            uploadState = uploadState.copy(
                phase = SpeedTestPhase.UPLOAD,
                progress = 0.62f + (fraction * 0.36f),
                liveSpeedMbps = liveMbps,
                uploadMbps = liveMbps
            )
            emit(uploadState)
        }

        val finalUploadMbps = uploadOutcome.mbps
        if (finalUploadMbps == null) {
            emit(
                uploadState.copy(
                    phase = SpeedTestPhase.ERROR,
                    downloadMbps = finalDownloadMbps,
                    errorMessage = uploadOutcome.error ?: "Upload test failed or timed out."
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
                    socket.connect(addr, 3000)
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
    ): MeasurementOutcome {
        val endpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=10000000",
            "https://cachefly.cachefly.net/10mb.test",
            "https://proof.ovh.net/files/10Mb.dat",
            "https://nbg1-speed.hetzner.com/100MB.bin"
        )

        var lastError: String? = null
        for (endpoint in endpoints) {
            if (!currentCoroutineContext().isActive) {
                return MeasurementOutcome(null, "Speed test cancelled.")
            }
            val result = tryDownloadFrom(endpoint, onProgress)
            if (result.mbps != null) {
                return result
            }
            lastError = result.error
        }
        return MeasurementOutcome(null, lastError ?: "All download mirrors timed out.")
    }

    private suspend fun tryDownloadFrom(
        urlString: String,
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): MeasurementOutcome {
        var connection: HttpsURLConnection? = null
        var totalBytes = 0L
        var startNs = 0L

        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 12000
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return MeasurementOutcome(null, "Download rejected (HTTP $code)")
            }

            val buffer = ByteArray(16 * 1024)
            startNs = System.nanoTime()
            var lastEmitNs = startNs
            val maxDurationNs = 5_500_000_000L

            connection.inputStream.use { input ->
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (totalBytes == 0L) {
                        // Start throughput timer once first payload bytes arrive
                        startNs = System.nanoTime()
                        lastEmitNs = startNs
                    }
                    totalBytes += read
                    val nowNs = System.nanoTime()
                    val elapsedNs = nowNs - startNs
                    if (nowNs - lastEmitNs >= 150_000_000L && elapsedNs > 80_000_000L) {
                        val mbps = bytesPerNsToMbps(totalBytes, elapsedNs)
                        val fraction = (elapsedNs.toFloat() / maxDurationNs.toFloat()).coerceIn(0f, 1f)
                        onProgress(mbps, fraction)
                        lastEmitNs = nowNs
                    }
                    if (elapsedNs >= maxDurationNs) break
                }
            }

            val totalElapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
            if (totalBytes <= 0L) {
                MeasurementOutcome(null, "Empty response from download server")
            } else {
                MeasurementOutcome(bytesPerNsToMbps(totalBytes, totalElapsedNs), null)
            }
        } catch (e: Exception) {
            if (totalBytes >= 64 * 1024 && startNs > 0L) {
                val totalElapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
                MeasurementOutcome(bytesPerNsToMbps(totalBytes, totalElapsedNs), null)
            } else {
                MeasurementOutcome(null, "Download error: ${e.javaClass.simpleName} (${e.message ?: "timeout"})")
            }
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private suspend fun measureUploadSpeed(
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): MeasurementOutcome {
        val endpoints = listOf(
            "https://speed.cloudflare.com/__up" to (2 * 1024 * 1024),
            "https://httpbin.org/post" to (1 * 1024 * 1024)
        )

        var lastError: String? = null
        for ((endpoint, payloadBytes) in endpoints) {
            if (!currentCoroutineContext().isActive) {
                return MeasurementOutcome(null, "Speed test cancelled.")
            }
            val result = tryUploadTo(endpoint, payloadBytes, onProgress)
            if (result.mbps != null) {
                return result
            }
            lastError = result.error
        }
        return MeasurementOutcome(null, lastError ?: "Upload test failed.")
    }

    private suspend fun tryUploadTo(
        urlString: String,
        totalTargetBytes: Int,
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): MeasurementOutcome {
        var connection: HttpsURLConnection? = null
        var totalSent = 0L
        var startNs = 0L

        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 15000
                useCaches = false
                setFixedLengthStreamingMode(totalTargetBytes)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Connection", "close")
            }

            val payloadChunk = ByteArray(16 * 1024) { 0x5A }
            startNs = System.nanoTime()
            var lastEmitNs = startNs

            connection.outputStream.use { output ->
                while (currentCoroutineContext().isActive && totalSent < totalTargetBytes) {
                    val remaining = (totalTargetBytes - totalSent).toInt().coerceAtMost(payloadChunk.size)
                    output.write(payloadChunk, 0, remaining)
                    totalSent += remaining
                    val nowNs = System.nanoTime()
                    val elapsedNs = nowNs - startNs
                    if (nowNs - lastEmitNs >= 150_000_000L && elapsedNs > 80_000_000L) {
                        val mbps = bytesPerNsToMbps(totalSent, elapsedNs)
                        val fraction = (totalSent.toFloat() / totalTargetBytes.toFloat()).coerceIn(0f, 1f)
                        onProgress(mbps, fraction)
                        lastEmitNs = nowNs
                    }
                }
                output.flush()
            }

            val code = connection.responseCode
            val totalElapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
            if (code in 200..299 && totalSent > 0L) {
                MeasurementOutcome(bytesPerNsToMbps(totalSent, totalElapsedNs), null)
            } else {
                MeasurementOutcome(null, "Upload rejected (HTTP $code)")
            }
        } catch (e: Exception) {
            MeasurementOutcome(null, "Upload error: ${e.javaClass.simpleName} (${e.message ?: "timeout"})")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun bytesPerNsToMbps(bytes: Long, elapsedNs: Long): Double {
        val bits = bytes * 8.0
        val seconds = elapsedNs / 1_000_000_000.0
        return (bits / seconds) / 1_000_000.0
    }

    private data class MeasurementOutcome(
        val mbps: Double?,
        val error: String?
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
