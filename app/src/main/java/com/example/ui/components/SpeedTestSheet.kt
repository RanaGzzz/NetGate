package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.network.SpeedTestPhase
import com.example.data.network.SpeedTestState
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.WifiAccent
import java.util.Locale

@Composable
fun SpeedTestContent(
    state: SpeedTestState,
    activeNetworkLabel: String,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speedTestProgress"
    )

    val displayedMbps = when (state.phase) {
        SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD -> state.liveSpeedMbps
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
                state.phase == SpeedTestPhase.COMPLETED -> "Run Again"
                else -> "Start Speed Test"
            },
            style = MaterialTheme.typography.labelLarge
        )
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
