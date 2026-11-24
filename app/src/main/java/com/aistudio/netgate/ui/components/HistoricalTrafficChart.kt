package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netgate.data.model.DailyTrafficHistoryPoint
import com.aistudio.netgate.data.model.DataUsageSummary
import com.aistudio.netgate.data.model.HistoricalUsageTotals
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SentReceivedSummaryCard(
    summary: DataUsageSummary,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    val totalTraffic = (summary.totalRxBytes + summary.totalTxBytes).coerceAtLeast(1L)
    val hasData = (summary.totalRxBytes + summary.totalTxBytes) > 0L
    val rxFraction = if (hasData) {
        (summary.totalRxBytes.toFloat() / totalTraffic.toFloat()).coerceIn(0.05f, 0.95f)
    } else {
        0.5f
    }
    val txFraction = (1f - rxFraction).coerceAtLeast(0.05f)

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sent_received_summary_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sent vs. Received Traffic",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$periodLabel • Wi-Fi + Mobile Data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = formatDataBytes(summary.totalRxBytes + summary.totalTxBytes),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectionMetricTile(
                    title = "Received (Rx)",
                    totalBytes = summary.totalRxBytes,
                    wifiBytes = summary.totalWifiRxBytes,
                    mobileBytes = summary.totalMobileRxBytes,
                    accent = EmeraldPrimary,
                    icon = Icons.Rounded.ArrowDownward,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("total_received_tile")
                )
                DirectionMetricTile(
                    title = "Sent (Tx)",
                    totalBytes = summary.totalTxBytes,
                    wifiBytes = summary.totalWifiTxBytes,
                    mobileBytes = summary.totalMobileTxBytes,
                    accent = WarningAmber,
                    icon = Icons.Rounded.ArrowUpward,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("total_sent_tile")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Proportional Rx vs Tx bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                if (hasData) {
                    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .weight(rxFraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(EmeraldPrimary, WifiAccent)
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .weight(txFraction)
                                .fillMaxHeight()
                                .background(WarningAmber)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Wi-Fi Total: ${formatDataBytes(summary.totalWifiBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WifiAccent
                )
                Text(
                    text = "Mobile Total: ${formatDataBytes(summary.totalMobileBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DataAccent
                )
            }
        }
    }
}

@Composable
private fun DirectionMetricTile(
    title: String,
    totalBytes: Long,
    wifiBytes: Long,
    mobileBytes: Long,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
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
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDataBytes(totalBytes),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Wi-Fi ${formatDataBytes(wifiBytes)} • Cell ${formatDataBytes(mobileBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HistoricalTrafficChartCard(
    dailyHistory: List<DailyTrafficHistoryPoint>,
    historicalTotals: HistoricalUsageTotals,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val textMeasurer = rememberTextMeasurer()
    var selectedDayIndex by remember(dailyHistory.size) {
        mutableStateOf(if (dailyHistory.isNotEmpty()) dailyHistory.lastIndex else -1)
    }

    val visiblePoints = remember(dailyHistory) {
        if (dailyHistory.size > 7) dailyHistory.takeLast(7) else dailyHistory
    }
    val maxDayBytes = remember(visiblePoints) {
        (visiblePoints.maxOfOrNull { it.totalBytes } ?: 1L).coerceAtLeast(1L)
    }

    val gridColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val axisLabelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )

    FrostedSurface(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("historical_traffic_chart_card")
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
                        color = WifiAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = WifiAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Room Historical Daily Usage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (historicalTotals.daysTrackedCount > 0) {
                                "${historicalTotals.daysTrackedCount} day${if (historicalTotals.daysTrackedCount == 1) "" else "s"} persisted • ${formatDataBytes(historicalTotals.grandTotalBytes)}"
                            } else {
                                "Persisted across syncs in Room"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (dailyHistory.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_traffic_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear Room history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (visiblePoints.isEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No historical days saved yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Sync after enabling Usage Access to archive daily Wi-Fi and Mobile Rx/Tx totals in Room.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // Totals strip across all Room-persisted days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistorySummaryBadge(
                        label = "All ↓ Rx",
                        value = formatDataBytes(historicalTotals.totalRxBytes),
                        color = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    HistorySummaryBadge(
                        label = "All ↑ Tx",
                        value = formatDataBytes(historicalTotals.totalTxBytes),
                        color = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val safeSelectedIndex = selectedDayIndex.coerceIn(0, visiblePoints.lastIndex)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("historical_daily_canvas")
                        .pointerInput(visiblePoints.size) {
                            detectTapGestures { offset ->
                                val leftAxisWidth = 54.dp.toPx()
                                val usableW = (size.width - leftAxisWidth - 8.dp.toPx()).coerceAtLeast(1f)
                                val relX = ((offset.x - leftAxisWidth) / usableW).coerceIn(0f, 0.999f)
                                val idx = (relX * visiblePoints.size).toInt().coerceIn(0, visiblePoints.lastIndex)
                                selectedDayIndex = idx
                            }
                        }
                ) {
                    val leftAxisWidth = 54.dp.toPx()
                    val topPad = 14.dp.toPx()
                    val bottomPad = 28.dp.toPx()
                    val rightPad = 8.dp.toPx()

                    val chartLeft = leftAxisWidth
                    val chartRight = (size.width - rightPad).coerceAtLeast(chartLeft + 40f)
                    val chartBottom = (size.height - bottomPad).coerceAtLeast(topPad + 30f)
                    val chartHeight = chartBottom - topPad
                    val chartWidth = chartRight - chartLeft

                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    listOf(0f to 0L, 0.5f to maxDayBytes / 2L, 1f to maxDayBytes).forEach { (frac, v) ->
                        val y = chartBottom - chartHeight * frac
                        drawLine(
                            color = gridColor,
                            start = Offset(chartLeft, y),
                            end = Offset(chartRight, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (frac == 0f) null else dash
                        )
                        val lbl = if (frac == 0f) "0" else formatDataBytes(v)
                        val measured = textMeasurer.measure(lbl, axisLabelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (chartLeft - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                            )
                        )
                    }

                    val slotW = chartWidth / visiblePoints.size.toFloat()
                    val barW = (slotW * 0.48f).coerceIn(14.dp.toPx(), 34.dp.toPx())
                    val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    visiblePoints.forEachIndexed { idx, point ->
                        val centerX = chartLeft + slotW * (idx + 0.5f)
                        val isSelected = idx == safeSelectedIndex
                        val barAlpha = if (isSelected) 1f else 0.72f

                        val totalFrac = (point.totalBytes.toFloat() / maxDayBytes.toFloat()).coerceIn(0f, 1f)
                        val totalH = (chartHeight * totalFrac)
                            .let { if (point.totalBytes > 0L) it.coerceAtLeast(6.dp.toPx()) else 0f }

                        if (totalH > 0f) {
                            val mobileRatio = (point.mobileBytes.toFloat() / point.totalBytes.toFloat()).coerceIn(0f, 1f)
                            val mobileH = totalH * mobileRatio
                            val wifiH = (totalH - mobileH).coerceAtLeast(0f)

                            // Bottom segment: Wi-Fi
                            if (wifiH > 0f) {
                                drawRoundRect(
                                    color = WifiAccent.copy(alpha = barAlpha),
                                    topLeft = Offset(centerX - barW / 2f, chartBottom - wifiH),
                                    size = Size(barW, wifiH),
                                    cornerRadius = radius
                                )
                            }
                            // Top segment: Mobile Data
                            if (mobileH > 0f) {
                                drawRoundRect(
                                    color = DataAccent.copy(alpha = barAlpha),
                                    topLeft = Offset(centerX - barW / 2f, chartBottom - totalH),
                                    size = Size(barW, mobileH),
                                    cornerRadius = radius
                                )
                            }
                        }

                        val dayLabel = formatEpochDayShort(point.dateEpochDay)
                        val dayMeasured = textMeasurer.measure(
                            dayLabel,
                            axisLabelStyle.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (isDark) Color.White else Color.Black
                                } else {
                                    axisLabelStyle.color
                                }
                            )
                        )
                        drawText(
                            textLayoutResult = dayMeasured,
                            topLeft = Offset(
                                x = (centerX - dayMeasured.size.width / 2f)
                                    .coerceIn(chartLeft, chartRight - dayMeasured.size.width),
                                y = chartBottom + 8.dp.toPx()
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selected day detail bar
                val selectedPoint = visiblePoints.getOrNull(safeSelectedIndex)
                if (selectedPoint != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedDayIndex = (safeSelectedIndex + 1) % visiblePoints.size
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatEpochDayFull(selectedPoint.dateEpochDay)} (${selectedPoint.appsCount} apps)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatDataBytes(selectedPoint.totalBytes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "↓ Rx ${formatDataBytes(selectedPoint.rxBytes)} • ↑ Tx ${formatDataBytes(selectedPoint.txBytes)}  |  Wi-Fi ${formatDataBytes(selectedPoint.wifiBytes)} • Cell ${formatDataBytes(selectedPoint.mobileBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

private fun formatEpochDayShort(epochDay: Long): String {
    val fmt = SimpleDateFormat("M/d", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(epochDay * 86_400_000L))
}

private fun formatEpochDayFull(epochDay: Long): String {
    val fmt = SimpleDateFormat("EEE, MMM d", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(epochDay * 86_400_000L))
}
