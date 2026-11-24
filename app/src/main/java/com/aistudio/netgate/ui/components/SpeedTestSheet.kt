package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.network.SpeedTestPhase
import com.aistudio.netgate.data.network.SpeedTestState
import com.aistudio.netgate.data.network.VideoPlaybackReport
import com.aistudio.netgate.data.network.VideoPlaybackVerdict
import com.aistudio.netgate.data.network.VideoResolutionTier
import com.aistudio.netgate.data.network.VideoTierResult
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.util.Locale

@Composable
fun SpeedTestContent(
    state: SpeedTestState,
    activeNetworkLabel: String,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onRunVideoTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speedTestProgress"
    )

    val displayedMbps = when (state.phase) {
        SpeedTestPhase.DOWNLOAD,
        SpeedTestPhase.UPLOAD,
        SpeedTestPhase.VIDEO_PLAYBACK -> state.liveSpeedMbps
        SpeedTestPhase.COMPLETED -> state.downloadMbps ?: 0.0
        else -> 0.0
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("speed_test_sheet")
    ) {
        val isWide = maxWidth >= 800.dp
        val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                SpeedTestGaugeCard(
                    state = state,
                    activeNetworkLabel = activeNetworkLabel,
                    displayedMbps = displayedMbps,
                    animatedProgress = animatedProgress,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpeedTestStatsAndAction(
                        state = state,
                        onStartTest = onStartTest,
                        onStopTest = onStopTest
                    )
                    VideoPlaybackTestCard(
                        state = state,
                        onRunVideoTest = onRunVideoTest,
                        onStopTest = onStopTest
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = navInsetBottom + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SpeedTestGaugeCard(
                        state = state,
                        activeNetworkLabel = activeNetworkLabel,
                        displayedMbps = displayedMbps,
                        animatedProgress = animatedProgress,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SpeedTestStatsAndAction(
                        state = state,
                        onStartTest = onStartTest,
                        onStopTest = onStopTest
                    )

                    VideoPlaybackTestCard(
                        state = state,
                        onRunVideoTest = onRunVideoTest,
                        onStopTest = onStopTest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTestGaugeCard(
    state: SpeedTestState,
    activeNetworkLabel: String,
    displayedMbps: Double,
    animatedProgress: Float,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(24.dp),
        borderAccent = if (state.isRunning) MaterialTheme.colorScheme.primary else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Network Speed Test",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.testedNetworkLabel.isNotEmpty() && state.phase != SpeedTestPhase.IDLE) {
                            "Testing on ${state.testedNetworkLabel}"
                        } else {
                            "Current connection: $activeNetworkLabel"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            val activeGradient = when (state.phase) {
                SpeedTestPhase.UPLOAD -> listOf(DataAccent, MaterialTheme.colorScheme.primary)
                SpeedTestPhase.VIDEO_PLAYBACK -> listOf(EmeraldPrimary, WifiAccent)
                else -> listOf(WifiAccent, EmeraldPrimary)
            }

            Box(
                modifier = Modifier.size(204.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth
                    )
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    drawArc(
                        color = trackColor,
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    drawArc(
                        brush = Brush.sweepGradient(activeGradient),
                        startAngle = 150f,
                        sweepAngle = 240f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatMbps(displayedMbps),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mbps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.phase.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.phase == SpeedTestPhase.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTestStatsAndAction(
    state: SpeedTestState,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpeedStatCard(
            label = "Ping",
            value = state.pingMs?.let { "$it ms" } ?: "—",
            icon = Icons.Rounded.NetworkPing,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SpeedStatCard(
            label = "Jitter",
            value = state.jitterMs?.let { "$it ms" } ?: "—",
            icon = Icons.Rounded.Speed,
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpeedStatCard(
            label = "Download",
            value = state.downloadMbps?.let { "${formatMbps(it)} Mbps" } ?: "—",
            icon = Icons.Rounded.ArrowDownward,
            accent = WifiAccent,
            modifier = Modifier.weight(1f)
        )
        SpeedStatCard(
            label = "Upload",
            value = state.uploadMbps?.let { "${formatMbps(it)} Mbps" } ?: "—",
            icon = Icons.Rounded.ArrowUpward,
            accent = DataAccent,
            modifier = Modifier.weight(1f)
        )
    }

    state.errorMessage?.let { err ->
        FrostedSurface(
            shape = RoundedCornerShape(14.dp),
            tintColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
            borderAccent = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    Button(
        onClick = if (state.isRunning) onStopTest else onStartTest,
        shape = RoundedCornerShape(16.dp),
        colors = if (state.isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("speed_test_action_button")
    ) {
        Icon(
            imageVector = if (state.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when {
                state.isRunning -> "Stop Speed Test"
                state.phase == SpeedTestPhase.COMPLETED -> "Run Full Speed & Video Test"
                else -> "Start Speed Test"
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun VideoPlaybackTestCard(
    state: SpeedTestState,
    onRunVideoTest: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report = state.videoPlaybackReport
    val isTestingVideo = state.phase == SpeedTestPhase.VIDEO_PLAYBACK
    var selectedTier by remember(report) { mutableStateOf<VideoResolutionTier?>(report?.maxSmoothTier) }

    val heroAccent = when {
        report == null -> MaterialTheme.colorScheme.primary
        report.maxSmoothTier == VideoResolutionTier.ULTRA_HD_4K ||
            report.maxSmoothTier == VideoResolutionTier.QHD_2K ||
            report.maxSmoothTier == VideoResolutionTier.FHD_1080P -> EmeraldPrimary
        report.maxSmoothTier != null -> WarningAmber
        else -> BlockedRed
    }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (report != null || isTestingVideo) heroAccent.copy(alpha = 0.55f) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_playback_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = heroAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isTestingVideo) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = heroAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.OndemandVideo,
                                    contentDescription = null,
                                    tint = heroAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Video Playback Test",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Checks 4K • 2K • 1080p • 720p • 420p streaming",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (report != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = heroAccent.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, heroAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = report.maxSmoothTier?.let { "Up to ${it.shortLabel}" } ?: "Low Speed",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = heroAccent,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("video_max_resolution_badge")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (report != null) {
                VideoPlaybackSummaryBanner(report = report, accent = heroAccent)
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.tiers.forEach { tierResult ->
                        VideoTierRow(
                            result = tierResult,
                            isSelected = selectedTier == tierResult.tier,
                            onClick = { selectedTier = tierResult.tier }
                        )
                    }
                }
            } else {
                // Default reference preview before running test
                Text(
                    text = if (isTestingVideo) {
                        "Streaming live adaptive video chunks to check startup delay and buffering stability…"
                    } else {
                        "Run a quick video streaming check to verify which resolutions play smoothly without buffering on your active internet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VideoResolutionTier.entries.forEach { tier ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_tier_${tier.shortLabel.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tier.shortLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${formatMbps(tier.requiredMbps)}M",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = if (isTestingVideo) onStopTest else onRunVideoTest,
                enabled = !state.isRunning || isTestingVideo,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("video_test_action_button")
            ) {
                Icon(
                    imageVector = if (isTestingVideo) Icons.Rounded.Stop else Icons.Rounded.OndemandVideo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isTestingVideo -> "Stop Video Check"
                        report != null -> "Re-test Video Playback"
                        else -> "Test Video Playback (4K – 420p)"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun VideoPlaybackSummaryBanner(
    report: VideoPlaybackReport,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniMetricPill(
                    label = "Effective",
                    value = "${formatMbps(report.effectiveMbps)} Mbps",
                    modifier = Modifier.weight(1f)
                )
                MiniMetricPill(
                    label = "Start Delay",
                    value = "${report.videoStartupMs} ms",
                    modifier = Modifier.weight(1f)
                )
                MiniMetricPill(
                    label = "Stability",
                    value = "${report.stabilityPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun VideoTierRow(
    result: VideoTierResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tierColor = when (result.verdict) {
        VideoPlaybackVerdict.SMOOTH -> EmeraldPrimary
        VideoPlaybackVerdict.BORDERLINE -> WarningAmber
        VideoPlaybackVerdict.BUFFERING -> BlockedRed
    }
    val verdictIcon = when (result.verdict) {
        VideoPlaybackVerdict.SMOOTH -> Icons.Rounded.CheckCircle
        VideoPlaybackVerdict.BORDERLINE -> Icons.Rounded.WarningAmber
        VideoPlaybackVerdict.BUFFERING -> Icons.Rounded.Cancel
    }
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) tierColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "videoTierBorder"
    )
    val animatedFill by animateFloatAsState(
        targetValue = (result.bandwidthRatio / 1.5f).coerceIn(0.06f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "videoTierFill"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isSelected) 0.78f else 0.52f),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_tier_${result.tier.shortLabel.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tierColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = result.tier.shortLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = tierColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${result.tier.detailLabel} • ≥${formatMbps(result.tier.requiredMbps)} Mbps",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = result.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = verdictIcon,
                        contentDescription = result.verdict.label,
                        tint = tierColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = result.verdict.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = tierColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFill)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tierColor)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Buffer ~${formatBufferTime(result.estimatedBufferMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpeedStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatMbps(value: Double): String {
    return if (value >= 100.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun formatBufferTime(ms: Int): String {
    return if (ms >= 1000) {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    } else {
        "${ms}ms"
    }
}
