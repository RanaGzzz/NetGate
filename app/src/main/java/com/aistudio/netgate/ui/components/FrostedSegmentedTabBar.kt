package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.ui.theme.LocalBlurEngineConfig
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.LocalThemeStyle

data class FrostedSegmentedTabItem(
    val label: String,
    val testTag: String,
    val activeIcon: ImageVector? = null,
    val inactiveIcon: ImageVector? = activeIcon,
    val badgeText: String? = null,
    val badgeColor: Color? = null,
    val contentDescription: String = label
)

/**
 * Unified frosted-glass segmented tab bar matching NetGate's elevated glossy pill aesthetic.
 * Integrates with [LocalBlurEngineConfig] and [MaterialTheme.colorScheme] (including Material You
 * dynamic colors) so active highlights, inner specular sheen, and outer glow automatically reflect
 * the user's selected theme and blur engine settings.
 */
@Composable
fun FrostedSegmentedTabBar(
    items: List<FrostedSegmentedTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minScrollableTabWidth: Dp = 104.dp,
    forceEqualWidth: Boolean = false
) {
    if (items.isEmpty()) return
    val clampedSelected = selectedIndex.coerceIn(0, items.lastIndex)

    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val motionSpec = LocalExpressiveMotion.current
    val isBlurActive = blurConfig.style != BlurEngineStyle.OFF &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE
    val intensityScale = blurConfig.intensity.coerceIn(0.4f, 1.5f)

    val trackShape = RoundedCornerShape(20.dp)
    val pillShape = RoundedCornerShape(15.dp)

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary

    // Derive a rich, vibrant active gradient base from MaterialTheme (works for both
    // Material You dynamic palettes on dark/light mode and built-in NetGate color themes).
    val pillMid = remember(primary, primaryContainer, isDark) {
        if (primary.luminance() > 0.44f) {
            lerp(primaryContainer, primary, 0.38f)
        } else {
            primary
        }
    }
    val pillTop = remember(pillMid, isBlurActive) {
        lerp(pillMid, Color.White, if (isBlurActive) 0.22f else 0.12f)
    }
    val pillBottom = remember(pillMid, secondary, isDark) {
        val shifted = lerp(pillMid, secondary, 0.14f)
        lerp(shifted, Color.Black, if (isDark) 0.22f else 0.12f)
    }
    val activeFillBrush = remember(pillTop, pillMid, pillBottom) {
        Brush.verticalGradient(colors = listOf(pillTop, pillMid, pillBottom))
    }
    val activeBorderBrush = remember(pillMid, primary, isDark, isBlurActive) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isBlurActive) (if (isDark) 0.62f else 0.84f) else 0.42f),
                lerp(primary, Color.White, 0.28f).copy(alpha = 0.52f),
                pillMid.copy(alpha = 0.88f)
            )
        )
    }
    val activeContentColor = remember(pillMid) {
        if (pillMid.luminance() > 0.46f) Color(0xFF0A1018) else Color.White
    }
    val glowColor = remember(primary, pillMid) {
        lerp(pillMid, primary, 0.45f)
    }

    FrostedSurface(
        shape = trackShape,
        elevation = if (isBlurActive) 6.dp else 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val gap = 4.dp
            val minRequiredForEqual = if (items.size <= 2) {
                0.dp
            } else {
                minScrollableTabWidth * items.size + gap * (items.size - 1)
            }
            val useScrollable = !forceEqualWidth && items.size > 2 && maxWidth < minRequiredForEqual

            if (!useScrollable) {
                val segmentWidth = ((maxWidth - gap * (items.size - 1)) / items.size)
                    .coerceAtLeast(48.dp)
                val targetOffset = (segmentWidth + gap) * clampedSelected
                val indicatorOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = when (themeStyle) {
                            NetGateThemeStyle.LIQUID_GLASS -> 0.66f
                            NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.70f
                            NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.92f
                            NetGateThemeStyle.NETGATE_DEFAULT -> 0.74f
                        },
                        stiffness = motionSpec.spatialStiffness
                    ),
                    label = "frostedSegmentedPillOffset"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle vertical hairlines between inactive segments
                    if (items.size >= 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until items.lastIndex) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(22.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = if (isDark) 0.24f else 0.34f
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Sliding glowing glass pill indicator
                    ActiveFrostedSegmentCapsule(
                        pillShape = pillShape,
                        fillBrush = activeFillBrush,
                        borderBrush = activeBorderBrush,
                        glowColor = glowColor,
                        isBlurActive = isBlurActive,
                        intensityScale = intensityScale,
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(segmentWidth)
                            .height(48.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            val isSelected = index == clampedSelected
                            FrostedSegmentedTabLabel(
                                item = item,
                                selected = isSelected,
                                activeContentColor = activeContentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(pillShape)
                                    .semantics { selected = isSelected }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { onSelect(index) }
                                    )
                                    .testTag(item.testTag)
                            )
                        }
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                val density = LocalDensity.current
                LaunchedEffect(clampedSelected, items.size) {
                    if (scrollState.maxValue > 0) {
                        val approxWidthPx = with(density) { (minScrollableTabWidth + gap).toPx() }
                        val viewportPx = with(density) { maxWidth.toPx() }
                        val targetScroll = (approxWidthPx * clampedSelected - viewportPx / 2f + approxWidthPx / 2f)
                            .toInt()
                            .coerceIn(0, scrollState.maxValue)
                        scrollState.animateScrollTo(targetScroll)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == clampedSelected
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = minScrollableTabWidth)
                                .heightIn(min = 48.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.activeFrostedPillDecor(
                                            pillShape = pillShape,
                                            fillBrush = activeFillBrush,
                                            borderBrush = activeBorderBrush,
                                            glowColor = glowColor,
                                            isBlurActive = isBlurActive,
                                            intensityScale = intensityScale
                                        )
                                    } else {
                                        Modifier.clip(pillShape)
                                    }
                                )
                                .semantics { selected = isSelected }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(index) }
                                )
                                .testTag(item.testTag)
                        ) {
                            FrostedSegmentedTabLabel(
                                item = item,
                                selected = isSelected,
                                activeContentColor = activeContentColor,
                                horizontalPadding = 14.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFrostedSegmentCapsule(
    pillShape: RoundedCornerShape,
    fillBrush: Brush,
    borderBrush: Brush,
    glowColor: Color,
    isBlurActive: Boolean,
    intensityScale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.activeFrostedPillDecor(
            pillShape = pillShape,
            fillBrush = fillBrush,
            borderBrush = borderBrush,
            glowColor = glowColor,
            isBlurActive = isBlurActive,
            intensityScale = intensityScale
        )
    )
}

private fun Modifier.activeFrostedPillDecor(
    pillShape: RoundedCornerShape,
    fillBrush: Brush,
    borderBrush: Brush,
    glowColor: Color,
    isBlurActive: Boolean,
    intensityScale: Float
): Modifier = this
    .shadow(
        elevation = if (isBlurActive) (10f * intensityScale).coerceIn(4f, 16f).dp else 3.dp,
        shape = pillShape,
        ambientColor = glowColor.copy(alpha = 0.72f),
        spotColor = glowColor.copy(alpha = 0.85f)
    )
    .clip(pillShape)
    .background(fillBrush)
    .border(width = 1.15.dp, brush = borderBrush, shape = pillShape)
    .drawWithContent {
        drawContent()
        if (isBlurActive) {
            val inset = 14.dp.toPx().coerceAtMost(size.width * 0.18f)
            val endX = (size.width - inset).coerceAtLeast(inset)
            if (endX > inset) {
                val edgeY = 2.dp.toPx()
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.56f),
                            Color.Transparent
                        ),
                        startX = inset,
                        endX = endX
                    ),
                    start = Offset(inset, edgeY),
                    end = Offset(endX, edgeY),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }

@Composable
private fun FrostedSegmentedTabLabel(
    item: FrostedSegmentedTabItem,
    selected: Boolean,
    activeContentColor: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "frostedSegmentTabColor"
    )
    val displayIcon = if (selected) item.activeIcon else item.inactiveIcon

    Row(
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (displayIcon != null) {
            Icon(
                imageVector = displayIcon,
                contentDescription = item.contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(5.dp))
            val resolvedBadgeBg = if (selected) {
                activeContentColor.copy(alpha = 0.22f)
            } else {
                (item.badgeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.18f)
            }
            val resolvedBadgeText = if (selected) {
                activeContentColor
            } else {
                item.badgeColor ?: MaterialTheme.colorScheme.primary
            }
            Surface(
                shape = CircleShape,
                color = resolvedBadgeBg
            ) {
                Text(
                    text = item.badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = resolvedBadgeText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}
