package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataUsageSummary
import com.example.data.model.UsageChartMode
import com.example.data.model.UsageFilterCategory
import com.example.data.model.formatDataBytes
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.BlockedRedBright
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WifiAccent
import kotlin.math.max

@Composable
fun BlockedVsAllowedBarChart(
    summary: DataUsageSummary,
    chartMode: UsageChartMode,
    selectedCategory: UsageFilterCategory,
    periodLabel: String,
    onChartModeChange: (UsageChartMode) -> Unit,
    onSelectCategory: (UsageFilterCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val textMeasurer = rememberTextMeasurer()

    val maxBarBytes = when (chartMode) {
        UsageChartMode.TOTAL -> max(summary.blockedTotalBytes, summary.allowedTotalBytes)
        UsageChartMode.SPLIT -> maxOf(
            summary.blockedWifiBytes,
            summary.blockedMobileBytes,
            summary.allowedWifiBytes,
            summary.allowedMobileBytes
        )
    }.coerceAtLeast(1L)

    val blockedTotalTarget = if (summary.grandTotalBytes > 0L) {
        (summary.blockedTotalBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val allowedTotalTarget = if (summary.grandTotalBytes > 0L) {
        (summary.allowedTotalBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val blockedWifiTarget = if (summary.grandTotalBytes > 0L) {
        (summary.blockedWifiBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val blockedMobileTarget = if (summary.grandTotalBytes > 0L) {
        (summary.blockedMobileBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val allowedWifiTarget = if (summary.grandTotalBytes > 0L) {
        (summary.allowedWifiBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val allowedMobileTarget = if (summary.grandTotalBytes > 0L) {
        (summary.allowedMobileBytes.toFloat() / maxBarBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animBlockedTotal by animateFloatAsState(
        targetValue = blockedTotalTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "blockedTotalBar"
    )
    val animAllowedTotal by animateFloatAsState(
        targetValue = allowedTotalTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "allowedTotalBar"
    )
    val animBlockedWifi by animateFloatAsState(
        targetValue = blockedWifiTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "blockedWifiBar"
    )
    val animBlockedMobile by animateFloatAsState(
        targetValue = blockedMobileTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "blockedMobileBar"
    )
    val animAllowedWifi by animateFloatAsState(
        targetValue = allowedWifiTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "allowedWifiBar"
    )
    val animAllowedMobile by animateFloatAsState(
        targetValue = allowedMobileTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.78f),
        label = "allowedMobileBar"
    )

    val gridColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryLabelColor = MaterialTheme.colorScheme.onSurface
    val axisLabelStyle = TextStyle(
        color = labelColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
    val valueLabelStyle = TextStyle(
        color = primaryLabelColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("blocked_vs_allowed_bar_chart_card"),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Blocked vs. Allowed Data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$periodLabel • Combined ${formatDataBytes(summary.grandTotalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chart Mode Pill Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UsageChartMode.entries.forEach { mode ->
                        val selected = chartMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                            } else if (isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.60f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onChartModeChange(mode) }
                                .testTag("chart_mode_${mode.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (summary.grandTotalBytes == 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No network traffic recorded in this window",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try switching to 7 Days or 30 Days, or tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .testTag("blocked_vs_allowed_canvas")
                        .pointerInput(selectedCategory) {
                            detectTapGestures { offset ->
                                val tappedCategory = if (offset.x < size.width / 2f) {
                                    UsageFilterCategory.BLOCKED
                                } else {
                                    UsageFilterCategory.ALLOWED
                                }
                                val next = if (selectedCategory == tappedCategory) {
                                    UsageFilterCategory.ALL
                                } else {
                                    tappedCategory
                                }
                                onSelectCategory(next)
                            }
                        }
                ) {
                    val leftAxisWidth = 58.dp.toPx()
                    val topPadding = 28.dp.toPx()
                    val bottomLabelSpace = 44.dp.toPx()
                    val rightPadding = 12.dp.toPx()

                    val chartLeft = leftAxisWidth
                    val chartRight = (size.width - rightPadding).coerceAtLeast(chartLeft + 40f)
                    val chartTop = topPadding
                    val chartBottom = (size.height - bottomLabelSpace).coerceAtLeast(chartTop + 40f)
                    val chartHeight = chartBottom - chartTop
                    val chartWidth = chartRight - chartLeft

                    // Reference gridlines (0%, 50%, 100%)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    val levels = listOf(0f to 0L, 0.5f to (maxBarBytes / 2L), 1f to maxBarBytes)
                    levels.forEach { (fraction, valueBytes) ->
                        val y = chartBottom - (chartHeight * fraction)
                        drawLine(
                            color = gridColor,
                            start = Offset(chartLeft, y),
                            end = Offset(chartRight, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (fraction == 0f) null else dashEffect
                        )
                        val tickLabel = if (fraction == 0f) "0 B" else formatDataBytes(valueBytes)
                        val measuredTick = textMeasurer.measure(tickLabel, axisLabelStyle)
                        drawText(
                            textLayoutResult = measuredTick,
                            topLeft = Offset(
                                x = (chartLeft - measuredTick.size.width - 8.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measuredTick.size.height / 2f).coerceIn(0f, size.height - measuredTick.size.height)
                            )
                        )
                    }

                    val groupWidth = chartWidth / 2f
                    val blockedCenterX = chartLeft + groupWidth * 0.5f
                    val allowedCenterX = chartLeft + groupWidth * 1.5f
                    val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())

                    val blockedAlpha = if (selectedCategory == UsageFilterCategory.ALLOWED) 0.42f else 1f
                    val allowedAlpha = if (selectedCategory == UsageFilterCategory.BLOCKED) 0.42f else 1f

                    if (chartMode == UsageChartMode.TOTAL) {
                        val barWidth = (groupWidth * 0.44f).coerceIn(36.dp.toPx(), 78.dp.toPx())

                        // Blocked Bar
                        val blockedBarH = (chartHeight * animBlockedTotal)
                            .let { if (summary.blockedTotalBytes > 0L) it.coerceAtLeast(6.dp.toPx()) else 0f }
                        if (blockedBarH > 0f) {
                            val topLeft = Offset(blockedCenterX - barWidth / 2f, chartBottom - blockedBarH)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        BlockedRedBright.copy(alpha = blockedAlpha),
                                        BlockedRed.copy(alpha = blockedAlpha)
                                    )
                                ),
                                topLeft = topLeft,
                                size = Size(barWidth, blockedBarH),
                                cornerRadius = corner
                            )
                        }
                        val blockedPct = ((summary.blockedTotalBytes * 100.0) / summary.grandTotalBytes.toDouble())
                        val blockedValText = "${formatDataBytes(summary.blockedTotalBytes)} (${String.format(java.util.Locale.US, "%.0f", blockedPct)}%)"
                        val measuredBlockedVal = textMeasurer.measure(blockedValText, valueLabelStyle)
                        drawText(
                            textLayoutResult = measuredBlockedVal,
                            topLeft = Offset(
                                x = (blockedCenterX - measuredBlockedVal.size.width / 2f)
                                    .coerceIn(chartLeft, chartRight - measuredBlockedVal.size.width),
                                y = (chartBottom - blockedBarH - measuredBlockedVal.size.height - 6.dp.toPx())
                                    .coerceAtLeast(2.dp.toPx())
                            )
                        )

                        // Allowed Bar
                        val allowedBarH = (chartHeight * animAllowedTotal)
                            .let { if (summary.allowedTotalBytes > 0L) it.coerceAtLeast(6.dp.toPx()) else 0f }
                        if (allowedBarH > 0f) {
                            val topLeft = Offset(allowedCenterX - barWidth / 2f, chartBottom - allowedBarH)
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        EmeraldBright.copy(alpha = allowedAlpha),
                                        EmeraldPrimary.copy(alpha = allowedAlpha)
                                    )
                                ),
                                topLeft = topLeft,
                                size = Size(barWidth, allowedBarH),
                                cornerRadius = corner
                            )
                        }
                        val allowedPct = ((summary.allowedTotalBytes * 100.0) / summary.grandTotalBytes.toDouble())
                        val allowedValText = "${formatDataBytes(summary.allowedTotalBytes)} (${String.format(java.util.Locale.US, "%.0f", allowedPct)}%)"
                        val measuredAllowedVal = textMeasurer.measure(allowedValText, valueLabelStyle)
                        drawText(
                            textLayoutResult = measuredAllowedVal,
                            topLeft = Offset(
                                x = (allowedCenterX - measuredAllowedVal.size.width / 2f)
                                    .coerceIn(chartLeft, chartRight - measuredAllowedVal.size.width),
                                y = (chartBottom - allowedBarH - measuredAllowedVal.size.height - 6.dp.toPx())
                                    .coerceAtLeast(2.dp.toPx())
                            )
                        )
                    } else {
                        // SPLIT MODE: Grouped Wi-Fi vs Mobile Data bars under Blocked & Allowed
                        val subBarWidth = (groupWidth * 0.24f).coerceIn(18.dp.toPx(), 40.dp.toPx())
                        val pairGap = 8.dp.toPx()
                        val subCorner = CornerRadius(8.dp.toPx(), 8.dp.toPx())

                        fun drawSubBar(
                            centerX: Float,
                            bytes: Long,
                            fraction: Float,
                            color: Color,
                            alpha: Float
                        ) {
                            val barH = (chartHeight * fraction)
                                .let { if (bytes > 0L) it.coerceAtLeast(5.dp.toPx()) else 0f }
                            if (barH > 0f) {
                                drawRoundRect(
                                    color = color.copy(alpha = alpha),
                                    topLeft = Offset(centerX - subBarWidth / 2f, chartBottom - barH),
                                    size = Size(subBarWidth, barH),
                                    cornerRadius = subCorner
                                )
                            }
                            val textRes = textMeasurer.measure(formatDataBytes(bytes), axisLabelStyle)
                            drawText(
                                textLayoutResult = textRes,
                                topLeft = Offset(
                                    x = (centerX - textRes.size.width / 2f)
                                        .coerceIn(chartLeft, chartRight - textRes.size.width),
                                    y = (chartBottom - barH - textRes.size.height - 4.dp.toPx())
                                        .coerceAtLeast(2.dp.toPx())
                                )
                            )
                        }

                        val offsetDelta = (subBarWidth + pairGap) / 2f
                        drawSubBar(
                            centerX = blockedCenterX - offsetDelta,
                            bytes = summary.blockedWifiBytes,
                            fraction = animBlockedWifi,
                            color = WifiAccent,
                            alpha = blockedAlpha
                        )
                        drawSubBar(
                            centerX = blockedCenterX + offsetDelta,
                            bytes = summary.blockedMobileBytes,
                            fraction = animBlockedMobile,
                            color = DataAccent,
                            alpha = blockedAlpha
                        )
                        drawSubBar(
                            centerX = allowedCenterX - offsetDelta,
                            bytes = summary.allowedWifiBytes,
                            fraction = animAllowedWifi,
                            color = WifiAccent,
                            alpha = allowedAlpha
                        )
                        drawSubBar(
                            centerX = allowedCenterX + offsetDelta,
                            bytes = summary.allowedMobileBytes,
                            fraction = animAllowedMobile,
                            color = DataAccent,
                            alpha = allowedAlpha
                        )
                    }

                    // X-axis group titles + counts
                    val blockedAxisTitle = textMeasurer.measure(
                        "Blocked (${summary.blockedAppsCount})",
                        valueLabelStyle.copy(color = BlockedRed)
                    )
                    drawText(
                        textLayoutResult = blockedAxisTitle,
                        topLeft = Offset(
                            x = blockedCenterX - blockedAxisTitle.size.width / 2f,
                            y = chartBottom + 10.dp.toPx()
                        )
                    )

                    val allowedAxisTitle = textMeasurer.measure(
                        "Allowed (${summary.allowedAppsCount})",
                        valueLabelStyle.copy(color = EmeraldPrimary)
                    )
                    drawText(
                        textLayoutResult = allowedAxisTitle,
                        topLeft = Offset(
                            x = allowedCenterX - allowedAxisTitle.size.width / 2f,
                            y = chartBottom + 10.dp.toPx()
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Legend + Interactive Filter Hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chartMode == UsageChartMode.TOTAL) {
                        LegendDot(color = BlockedRed, label = "Blocked Apps")
                        Spacer(modifier = Modifier.width(18.dp))
                        LegendDot(color = EmeraldPrimary, label = "Allowed Apps")
                    } else {
                        LegendDot(color = WifiAccent, label = "Wi-Fi")
                        Spacer(modifier = Modifier.width(18.dp))
                        LegendDot(color = DataAccent, label = "Mobile Data")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(10.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
