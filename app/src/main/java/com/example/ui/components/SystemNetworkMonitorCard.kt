package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveNetworkType
import com.example.data.model.LiveNetworkTelemetry
import com.example.data.model.StatusBarSpeedStyle
import com.example.data.model.ThroughputSample
import com.example.data.model.formatDataBytes
import com.example.data.model.formatSpeedBytesPerSec
import com.example.service.NetworkMonitorService
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WarningAmber
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.ui.theme.WifiAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemNetworkMonitorCard(
    telemetry: LiveNetworkTelemetry,
    isStatusBarSpeedEnabled: Boolean,
    statusBarSpeedStyle: StatusBarSpeedStyle,
    showStatusBarDailyUsage: Boolean,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onSelectStatusBarStyle: (StatusBarSpeedStyle) -> Unit,
    onToggleShowDailyUsage: (Boolean) -> Unit,
    onResetSessionStats: () -> Unit,
    onOpenSitesAndSecurity: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    var notificationsBlocked by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsBlocked = NetworkMonitorService.areNotificationsBlocked(context)
    }
    LaunchedEffect(isStatusBarSpeedEnabled) {
        notificationsBlocked = NetworkMonitorService.areNotificationsBlocked(context)
    }
    val isOnline = telemetry.activeNetworkType != ActiveNetworkType.NONE
    val networkLabel = when (telemetry.activeNetworkType) {
        ActiveNetworkType.WIFI -> "Wi-Fi"
        ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
        ActiveNetworkType.OTHER -> "Ethernet"
        ActiveNetworkType.NONE -> "Offline"
    }
    val ifaceSuffix = telemetry.activeInterfaceName?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()

    FrostedSurface(
        shape = RoundedCornerShape(24.dp),
        borderAccent = if (isOnline) EmeraldPrimary.copy(alpha = 0.55f) else null,
        tintColor = if (isOnline) {
            EmeraldPrimary.copy(alpha = if (isDark) 0.12f else 0.08f)
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_network_monitor_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = (if (isOnline) EmeraldPrimary else MaterialTheme.colorScheme.outline)
                            .copy(alpha = 0.18f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MonitorHeart,
                                contentDescription = "Live system network monitor",
                                tint = if (isOnline) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "System Network Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // The LIVE badge lives on the interface line, not next to the title: on narrow
                        // screens it was squeezed beside the long title and rendered as vertical text.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$networkLabel$ifaceSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isOnline) {
                                    EmeraldPrimary.copy(alpha = 0.20f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isOnline) EmeraldPrimary else MaterialTheme.colorScheme.outline
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isOnline) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onResetSessionStats,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_live_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset live session counters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Incoming (Download) & Outgoing (Upload) Speed Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiveDirectionTile(
                    label = "Incoming (DL)",
                    speedBytesPerSec = telemetry.downloadBytesPerSec,
                    peakBytesPerSec = telemetry.peakDownloadBytesPerSec,
                    sessionBytes = telemetry.sessionRxBytes,
                    accent = EmeraldPrimary,
                    icon = Icons.Rounded.ArrowDownward,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("live_incoming_tile")
                )
                LiveDirectionTile(
                    label = "Outgoing (UL)",
                    speedBytesPerSec = telemetry.uploadBytesPerSec,
                    peakBytesPerSec = telemetry.peakUploadBytesPerSec,
                    sessionBytes = telemetry.sessionTxBytes,
                    accent = WarningAmber,
                    icon = Icons.Rounded.ArrowUpward,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("live_outgoing_tile")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Incoming vs Outgoing Throughput Waveform Chart
            LiveThroughputSparkline(
                samples = telemetry.historySamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_throughput_chart")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Today's System-Wide Wi-Fi & Mobile Usage (TrafficStats, works without Usage Access)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TodayTransportPill(
                    title = "Today Wi-Fi",
                    rxBytes = telemetry.todayWifiRxBytes,
                    txBytes = telemetry.todayWifiTxBytes,
                    accent = WifiAccent,
                    icon = Icons.Rounded.Wifi,
                    modifier = Modifier.weight(1f)
                )
                TodayTransportPill(
                    title = "Today Mobile",
                    rxBytes = telemetry.todayMobileRxBytes,
                    txBytes = telemetry.todayMobileTxBytes,
                    accent = DataAccent,
                    icon = Icons.Rounded.CellTower,
                    modifier = Modifier.weight(1f)
                )
            }

            if (onOpenSitesAndSecurity != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onOpenSitesAndSecurity,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_sites_and_regions_quick_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Live Sites, Countries & Security")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Bar Live Speed Icon Panel
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.62f),
                border = BorderStroke(
                    1.dp,
                    if (isStatusBarSpeedEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                    } else if (isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.85f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = if (isStatusBarSpeedEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Status Bar Speed Icon",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isStatusBarSpeedEnabled) {
                                        "Live ↑/↓ speed & data usage pinned in status bar"
                                    } else {
                                        "Show live DL/UL speed & usage in status bar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isStatusBarSpeedEnabled,
                            onCheckedChange = onToggleStatusBarSpeed,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("status_bar_speed_switch")
                        )
                    }

                    if (isStatusBarSpeedEnabled && notificationsBlocked) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarningAmber.copy(alpha = if (isDark) 0.16f else 0.22f))
                                .clickable { openNotificationSettings(context) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("status_bar_notifications_blocked")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Speed icon is hidden by Android",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Notifications are blocked. Tap to allow NetGate notifications.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Status Bar Icon Style",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusBarSpeedStyle.entries.forEach { style ->
                            val selected = statusBarSpeedStyle == style
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectStatusBarStyle(style) },
                                label = {
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("status_bar_style_${style.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleShowDailyUsage(!showStatusBarDailyUsage) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Today's Total in Notification",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (showStatusBarDailyUsage) {
                                    "Displays today's Wi-Fi + Mobile Rx/Tx totals"
                                } else {
                                    "Displays current session Rx/Tx totals"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showStatusBarDailyUsage,
                            onCheckedChange = onToggleShowDailyUsage,
                            modifier = Modifier.testTag("status_bar_daily_usage_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDirectionTile(
    label: String,
    speedBytesPerSec: Long,
    peakBytesPerSec: Long,
    sessionBytes: Long,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatSpeedBytesPerSec(speedBytesPerSec),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Peak ${formatSpeedBytesPerSec(peakBytesPerSec)} • ${formatDataBytes(sessionBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TodayTransportPill(
    title: String,
    rxBytes: Long,
    txBytes: Long,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "↓ ${formatDataBytes(rxBytes)}  •  ↑ ${formatDataBytes(txBytes)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiveThroughputSparkline(
    samples: List<ThroughputSample>,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val maxScale = remember(samples) {
        val peak = samples.maxOfOrNull { maxOf(it.rxBytesPerSec, it.txBytesPerSec) } ?: 0L
        maxOf(peak, 16_384L)
    }
    val gridColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color.Black.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, gridColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDotLabel(color = EmeraldPrimary, text = "↓ Incoming")
                    LegendDotLabel(color = WarningAmber, text = "↑ Outgoing")
                }
                Text(
                    text = "Scale: ${formatSpeedBytesPerSec(maxScale)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                val width = size.width
                val height = size.height
                val topPad = 4.dp.toPx()
                val bottomPad = 4.dp.toPx()
                val usableHeight = (height - topPad - bottomPad).coerceAtLeast(1f)

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = topPad + usableHeight * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (frac == 1f) null else dash
                    )
                }

                if (samples.size < 2) {
                    return@Canvas
                }

                val count = samples.size
                val stepX = width / (count - 1).coerceAtLeast(1).toFloat()

                val rxPath = Path()
                val rxFillPath = Path()
                val txPath = Path()

                samples.forEachIndexed { index, sample ->
                    val x = index * stepX
                    val rxRatio = (sample.rxBytesPerSec.toFloat() / maxScale.toFloat()).coerceIn(0f, 1f)
                    val txRatio = (sample.txBytesPerSec.toFloat() / maxScale.toFloat()).coerceIn(0f, 1f)
                    val rxY = topPad + usableHeight * (1f - rxRatio)
                    val txY = topPad + usableHeight * (1f - txRatio)

                    if (index == 0) {
                        rxPath.moveTo(x, rxY)
                        rxFillPath.moveTo(x, topPad + usableHeight)
                        rxFillPath.lineTo(x, rxY)
                        txPath.moveTo(x, txY)
                    } else {
                        rxPath.lineTo(x, rxY)
                        rxFillPath.lineTo(x, rxY)
                        txPath.lineTo(x, txY)
                    }
                    if (index == count - 1) {
                        rxFillPath.lineTo(x, topPad + usableHeight)
                        rxFillPath.close()
                    }
                }

                drawPath(
                    path = rxFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.28f),
                            EmeraldPrimary.copy(alpha = 0.02f)
                        )
                    )
                )
                drawPath(
                    path = rxPath,
                    color = EmeraldPrimary,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawPath(
                    path = txPath,
                    color = WarningAmber,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LegendDotLabel(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
