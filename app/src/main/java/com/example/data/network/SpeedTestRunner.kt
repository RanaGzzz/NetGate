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
import kotlin.math.roundToInt

enum class SpeedTestPhase(val label: String) {
    IDLE("Ready to test"),
    PING("Measuring Ping & Jitter…"),
    DOWNLOAD("Testing Download…"),
    UPLOAD("Testing Upload…"),
    VIDEO_PLAYBACK("Testing Video Playback (4K–420p)…"),
    COMPLETED("Test Complete"),
    ERROR("Test Failed")
}

enum class VideoResolutionTier(
    val shortLabel: String,
    val detailLabel: String,
    val requiredMbps: Double,
    val maxSmoothJitterMs: Int
) {
    ULTRA_HD_4K(
        shortLabel = "4K",
        detailLabel = "2160p Ultra HD",
        requiredMbps = 25.0,
        maxSmoothJitterMs = 45
    ),
    QHD_2K(
        shortLabel = "2K",
        detailLabel = "1440p QHD",
        requiredMbps = 12.0,
        maxSmoothJitterMs = 60
    ),
    FHD_1080P(
        shortLabel = "1080p",
        detailLabel = "Full HD",
        requiredMbps = 5.5,
        maxSmoothJitterMs = 80
    ),
    HD_720P(
        shortLabel = "720p",
        detailLabel = "HD Ready",
        requiredMbps = 2.8,
        maxSmoothJitterMs = 110
    ),
    SD_420P(
        shortLabel = "420p",
        detailLabel = "Standard Definition",
        requiredMbps = 1.2,
        maxSmoothJitterMs = 150
    )
}

enum class VideoPlaybackVerdict(val label: String) {
    SMOOTH("Smooth"),
    BORDERLINE("May Buffer"),
    BUFFERING("Buffering")
}

data class VideoTierResult(
    val tier: VideoResolutionTier,
    val verdict: VideoPlaybackVerdict,
    val bandwidthRatio: Float,
    val estimatedBufferMs: Int,
    val note: String
)

data class VideoPlaybackReport(
    val maxSmoothTier: VideoResolutionTier?,
    val effectiveMbps: Double,
    val sustainedFloorMbps: Double,
    val videoStartupMs: Int,
    val stabilityPercent: Int,
    val summary: String,
    val tiers: List<VideoTierResult>
)

object VideoPlaybackEvaluator {
    fun evaluate(
        downloadMbps: Double,
        sustainedFloorMbps: Double,
        pingMs: Int?,
        jitterMs: Int?,
        videoStartupMs: Int?
    ): VideoPlaybackReport {
        val safeDownload = downloadMbps.coerceAtLeast(0.0)
        val safeFloor = sustainedFloorMbps.coerceIn(0.0, safeDownload.coerceAtLeast(0.0))
        // Blend overall average (45%) and sustained P20 floor (55%) so dips affect streaming scores honestly
        val effectiveMbps = if (safeFloor > 0.0) {
            (safeDownload * 0.45) + (safeFloor * 0.55)
        } else {
            safeDownload * 0.85
        }
        val effectivePing = (pingMs ?: 35).coerceAtLeast(1)
        val effectiveJitter = (jitterMs ?: 8).coerceAtLeast(0)
        val startupDelayMs = (videoStartupMs ?: (effectivePing * 2 + 40)).coerceAtLeast(15)

        val stabilityPercent = if (safeDownload <= 0.05) {
            0
        } else {
            val floorRatio = (effectiveMbps / safeDownload).coerceIn(0.25, 1.0)
            val jitterPenalty = (effectiveJitter / 180.0).coerceIn(0.0, 0.35)
            ((floorRatio - jitterPenalty) * 100.0).roundToInt().coerceIn(25, 99)
        }

        val tierResults = VideoResolutionTier.entries.map { tier ->
            val smoothThreshold = tier.requiredMbps * 1.15
            val borderlineThreshold = tier.requiredMbps * 0.85
            val ratio = (effectiveMbps / tier.requiredMbps).toFloat().coerceIn(0f, 3f)

            val verdict = when {
                effectiveMbps >= smoothThreshold && effectiveJitter <= tier.maxSmoothJitterMs ->
                    VideoPlaybackVerdict.SMOOTH
                effectiveMbps >= borderlineThreshold ->
                    VideoPlaybackVerdict.BORDERLINE
                else ->
                    VideoPlaybackVerdict.BUFFERING
            }

            // Approximate startup time for a 2-second pre-buffer segment
            val segmentFillMs = if (effectiveMbps > 0.05) {
                ((tier.requiredMbps * 2.0 / effectiveMbps) * 1000.0).roundToInt().coerceIn(80, 12_000)
            } else {
                12_000
            }
            val estimatedBufferMs = startupDelayMs + segmentFillMs

            val note = when (verdict) {
                VideoPlaybackVerdict.SMOOTH ->
                    String.format(java.util.Locale.US, "%.1fx headroom • Instant playback", ratio)
                VideoPlaybackVerdict.BORDERLINE -> if (effectiveMbps >= smoothThreshold) {
                    "Speed ok, high jitter (${effectiveJitter} ms) may stall"
                } else {
                    val gap = (smoothThreshold - effectiveMbps).coerceAtLeast(0.2)
                    String.format(java.util.Locale.US, "Tight margin • +%.1f Mbps ideal", gap)
                }
                VideoPlaybackVerdict.BUFFERING -> {
                    val deficit = (tier.requiredMbps - effectiveMbps).coerceAtLeast(0.2)
                    String.format(java.util.Locale.US, "Needs %.1f Mbps (+%.1f Mbps more)", tier.requiredMbps, deficit)
                }
            }

            VideoTierResult(
                tier = tier,
                verdict = verdict,
                bandwidthRatio = ratio,
                estimatedBufferMs = estimatedBufferMs,
                note = note
            )
        }

        val maxSmooth = tierResults.firstOrNull { it.verdict == VideoPlaybackVerdict.SMOOTH }?.tier
        val summary = when (maxSmooth) {
            VideoResolutionTier.ULTRA_HD_4K ->
                "Streams up to 4K Ultra HD smoothly with zero buffering expected."
            VideoResolutionTier.QHD_2K ->
                "Streams up to 2K QHD smoothly; 4K may pause under load."
            VideoResolutionTier.FHD_1080P ->
                "Streams 1080p Full HD cleanly; higher tiers may rebuffer."
            VideoResolutionTier.HD_720P ->
                "Best suited for 720p HD and 420p SD playback."
            VideoResolutionTier.SD_420P ->
                "Best suited for 420p SD playback on this connection."
            null ->
                "Connection too slow or unstable for smooth 420p video playback."
        }

        return VideoPlaybackReport(
            maxSmoothTier = maxSmooth,
            effectiveMbps = effectiveMbps,
            sustainedFloorMbps = safeFloor,
            videoStartupMs = startupDelayMs,
            stabilityPercent = stabilityPercent,
            summary = summary,
            tiers = tierResults
        )
    }
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val progress: Float = 0f,
    val liveSpeedMbps: Double = 0.0,
    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val videoPlaybackReport: VideoPlaybackReport? = null,
    val testedNetworkLabel: String = "",
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = phase == SpeedTestPhase.PING ||
            phase == SpeedTestPhase.DOWNLOAD ||
            phase == SpeedTestPhase.UPLOAD ||
            phase == SpeedTestPhase.VIDEO_PLAYBACK
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
                progress = 0.20f,
                pingMs = pingMs,
                jitterMs = jitterMs,
                testedNetworkLabel = networkLabel
            )
        )

        // 2. Download Speed Test with multi-mirror fallback & interval sampling
        var downloadState = SpeedTestState(
            phase = SpeedTestPhase.DOWNLOAD,
            progress = 0.20f,
            pingMs = pingMs,
            jitterMs = jitterMs,
            testedNetworkLabel = networkLabel
        )

        val downloadOutcome = measureDownloadSpeed(targetBytes = 10_000_000L, maxDurationNs = 5_000_000_000L) { liveMbps, fraction ->
            downloadState = downloadState.copy(
                phase = SpeedTestPhase.DOWNLOAD,
                progress = 0.20f + (fraction * 0.34f),
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
                progress = 0.56f,
                liveSpeedMbps = 0.0,
                downloadMbps = finalDownloadMbps
            )
        )

        // 3. Upload Speed Test with fixed-length streaming & fallback
        var uploadState = downloadState.copy(
            phase = SpeedTestPhase.UPLOAD,
            progress = 0.56f,
            liveSpeedMbps = 0.0,
            downloadMbps = finalDownloadMbps
        )

        val uploadOutcome = measureUploadSpeed { liveMbps, fraction ->
            uploadState = uploadState.copy(
                phase = SpeedTestPhase.UPLOAD,
                progress = 0.56f + (fraction * 0.28f),
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

        // 4. Adaptive Video Playback Streaming Probe (4K / 2K / 1080p / 720p / 420p)
        emit(
            uploadState.copy(
                phase = SpeedTestPhase.VIDEO_PLAYBACK,
                progress = 0.86f,
                liveSpeedMbps = finalDownloadMbps,
                uploadMbps = finalUploadMbps
            )
        )

        val videoSegmentOutcome = measureDownloadSpeed(
            targetBytes = 2_000_000L,
            maxDurationNs = 2_000_000_000L
        ) { liveMbps, fraction ->
            emit(
                uploadState.copy(
                    phase = SpeedTestPhase.VIDEO_PLAYBACK,
                    progress = 0.86f + (fraction * 0.12f),
                    liveSpeedMbps = liveMbps,
                    downloadMbps = finalDownloadMbps,
                    uploadMbps = finalUploadMbps
                )
            )
        }

        val combinedDownload = videoSegmentOutcome.mbps?.let { seg ->
            (finalDownloadMbps * 0.70) + (seg * 0.30)
        } ?: finalDownloadMbps
        val combinedFloor = listOfNotNull(
            downloadOutcome.sustainedFloorMbps,
            videoSegmentOutcome.sustainedFloorMbps
        ).minOrNull() ?: (combinedDownload * 0.85)

        val videoReport = VideoPlaybackEvaluator.evaluate(
            downloadMbps = combinedDownload,
            sustainedFloorMbps = combinedFloor,
            pingMs = pingMs,
            jitterMs = jitterMs,
            videoStartupMs = videoSegmentOutcome.firstByteMs ?: downloadOutcome.firstByteMs
        )

        emit(
            SpeedTestState(
                phase = SpeedTestPhase.COMPLETED,
                progress = 1f,
                liveSpeedMbps = finalDownloadMbps,
                pingMs = pingMs,
                jitterMs = jitterMs,
                downloadMbps = finalDownloadMbps,
                uploadMbps = finalUploadMbps,
                videoPlaybackReport = videoReport,
                testedNetworkLabel = networkLabel
            )
        )
    }.flowOn(Dispatchers.IO)

    fun runVideoPlaybackTest(
        networkLabel: String,
        previousState: SpeedTestState
    ): Flow<SpeedTestState> = flow {
        emit(
            previousState.copy(
                phase = SpeedTestPhase.VIDEO_PLAYBACK,
                progress = 0.10f,
                liveSpeedMbps = previousState.downloadMbps ?: 0.0,
                testedNetworkLabel = networkLabel,
                errorMessage = null
            )
        )

        val pingResult = if (previousState.pingMs == null || previousState.jitterMs == null) {
            measurePingAndJitter()
        } else {
            previousState.pingMs to previousState.jitterMs
        }
        val pingMs = pingResult?.first
        val jitterMs = pingResult?.second

        var latestState = previousState.copy(
            phase = SpeedTestPhase.VIDEO_PLAYBACK,
            progress = 0.25f,
            pingMs = pingMs,
            jitterMs = jitterMs,
            testedNetworkLabel = networkLabel,
            errorMessage = null
        )
        emit(latestState)

        val segmentOutcome = measureDownloadSpeed(
            targetBytes = 3_500_000L,
            maxDurationNs = 3_200_000_000L
        ) { liveMbps, fraction ->
            latestState = latestState.copy(
                phase = SpeedTestPhase.VIDEO_PLAYBACK,
                progress = 0.25f + (fraction * 0.70f),
                liveSpeedMbps = liveMbps,
                downloadMbps = liveMbps
            )
            emit(latestState)
        }

        val measuredMbps = segmentOutcome.mbps
        if (measuredMbps == null) {
            emit(
                latestState.copy(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = segmentOutcome.error ?: "Video streaming probe timed out."
                )
            )
            return@flow
        }

        val floorMbps = segmentOutcome.sustainedFloorMbps ?: (measuredMbps * 0.85)
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = measuredMbps,
            sustainedFloorMbps = floorMbps,
            pingMs = pingMs,
            jitterMs = jitterMs,
            videoStartupMs = segmentOutcome.firstByteMs
        )

        emit(
            latestState.copy(
                phase = SpeedTestPhase.COMPLETED,
                progress = 1f,
                liveSpeedMbps = measuredMbps,
                downloadMbps = measuredMbps,
                pingMs = pingMs,
                jitterMs = jitterMs,
                videoPlaybackReport = report,
                testedNetworkLabel = networkLabel,
                errorMessage = null
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
        targetBytes: Long = 10_000_000L,
        maxDurationNs: Long = 5_500_000_000L,
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): MeasurementOutcome {
        val endpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=$targetBytes",
            "https://cachefly.cachefly.net/10mb.test",
            "https://proof.ovh.net/files/10Mb.dat",
            "https://nbg1-speed.hetzner.com/100MB.bin"
        )

        var lastError: String? = null
        for (endpoint in endpoints) {
            if (!currentCoroutineContext().isActive) {
                return MeasurementOutcome(null, error = "Speed test cancelled.")
            }
            val result = tryDownloadFrom(endpoint, maxDurationNs, onProgress)
            if (result.mbps != null) {
                return result
            }
            lastError = result.error
        }
        return MeasurementOutcome(null, error = lastError ?: "All download mirrors timed out.")
    }

    private suspend fun tryDownloadFrom(
        urlString: String,
        maxDurationNs: Long,
        onProgress: suspend (liveMbps: Double, fraction: Float) -> Unit
    ): MeasurementOutcome {
        var connection: HttpsURLConnection? = null
        var totalBytes = 0L
        var requestStartNs = 0L
        var payloadStartNs = 0L
        var firstByteMs: Int? = null
        val intervalMbpsSamples = mutableListOf<Double>()

        return try {
            val url = URL(urlString)
            requestStartNs = System.nanoTime()
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
                return MeasurementOutcome(null, error = "Download rejected (HTTP $code)")
            }

            val buffer = ByteArray(16 * 1024)
            payloadStartNs = System.nanoTime()
            var lastEmitNs = payloadStartNs
            var bytesSinceLastEmit = 0L

            connection.inputStream.use { input ->
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val nowNs = System.nanoTime()
                    if (totalBytes == 0L) {
                        payloadStartNs = nowNs
                        lastEmitNs = nowNs
                        firstByteMs = ((nowNs - requestStartNs) / 1_000_000L).toInt().coerceAtLeast(5)
                    }
                    totalBytes += read
                    bytesSinceLastEmit += read
                    val elapsedNs = nowNs - payloadStartNs
                    val intervalNs = nowNs - lastEmitNs
                    if (intervalNs >= 180_000_000L && elapsedNs > 80_000_000L) {
                        val instantMbps = bytesPerNsToMbps(bytesSinceLastEmit, intervalNs)
                        intervalMbpsSamples.add(instantMbps)
                        val avgMbps = bytesPerNsToMbps(totalBytes, elapsedNs)
                        val fraction = (elapsedNs.toFloat() / maxDurationNs.toFloat()).coerceIn(0f, 1f)
                        onProgress(avgMbps, fraction)
                        lastEmitNs = nowNs
                        bytesSinceLastEmit = 0L
                    }
                    if (elapsedNs >= maxDurationNs) break
                }
            }

            val totalElapsedNs = (System.nanoTime() - payloadStartNs).coerceAtLeast(1L)
            if (totalBytes <= 0L) {
                MeasurementOutcome(null, error = "Empty response from download server")
            } else {
                val finalMbps = bytesPerNsToMbps(totalBytes, totalElapsedNs)
                val p20Floor = computeSustainedFloor(intervalMbpsSamples, finalMbps)
                MeasurementOutcome(
                    mbps = finalMbps,
                    sustainedFloorMbps = p20Floor,
                    firstByteMs = firstByteMs
                )
            }
        } catch (e: Exception) {
            if (totalBytes >= 64 * 1024 && payloadStartNs > 0L) {
                val totalElapsedNs = (System.nanoTime() - payloadStartNs).coerceAtLeast(1L)
                val finalMbps = bytesPerNsToMbps(totalBytes, totalElapsedNs)
                MeasurementOutcome(
                    mbps = finalMbps,
                    sustainedFloorMbps = computeSustainedFloor(intervalMbpsSamples, finalMbps),
                    firstByteMs = firstByteMs
                )
            } else {
                MeasurementOutcome(null, error = "Download error: ${e.javaClass.simpleName} (${e.message ?: "timeout"})")
            }
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun computeSustainedFloor(samples: List<Double>, fallbackAvg: Double): Double {
        if (samples.size < 3) return fallbackAvg * 0.88
        val sorted = samples.sorted()
        val p20Index = ((sorted.size - 1) * 0.20).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[p20Index].coerceAtMost(fallbackAvg)
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
                return MeasurementOutcome(null, error = "Speed test cancelled.")
            }
            val result = tryUploadTo(endpoint, payloadBytes, onProgress)
            if (result.mbps != null) {
                return result
            }
            lastError = result.error
        }
        return MeasurementOutcome(null, error = lastError ?: "Upload test failed.")
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
                MeasurementOutcome(bytesPerNsToMbps(totalSent, totalElapsedNs))
            } else {
                MeasurementOutcome(null, error = "Upload rejected (HTTP $code)")
            }
        } catch (e: Exception) {
            MeasurementOutcome(null, error = "Upload error: ${e.javaClass.simpleName} (${e.message ?: "timeout"})")
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
        val sustainedFloorMbps: Double? = null,
        val firstByteMs: Int? = null,
        val error: String? = null
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
