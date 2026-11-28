package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aistudio.netgate.ui.theme.BlurEngine.blurEngineBackdrop
import com.aistudio.netgate.ui.theme.GlassBlurStyle
import com.aistudio.netgate.ui.theme.resolveWcagAccessibleContentColor
import com.aistudio.netgate.ui.theme.useBlurStyle
import io.github.fletchmckee.liquid.liquid
import kotlin.math.roundToInt

enum class GlassTabVariant {
    TOP_SEGMENTED,
    BOTTOM_NAV,
    CHIP,
    MENU
}

data class GlassTabItem(
    val label: String,
    val testTag: String = "",
    val activeIcon: ImageVector? = null,
    val inactiveIcon: ImageVector? = activeIcon,
    val badgeText: String? = null,
    val badgeColor: Color? = null,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val accentColor: Color? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * Shared portrait bottom clearance so floating bottom navigation never overlaps the tail
 * of scrollable lists across all user-selected bar heights and system gesture insets.
 */
@Composable
fun rememberBottomNavClearancePadding(extraPadding: Dp = 0.dp): Dp {
    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = LocalBottomNavConfig.current.heightOption.heightDp
    // Floating nav chrome: top(6) + bottom(8) + inner padding(8) + dots(9) + barHeight + breathing gap(40)
    return navInsetBottom + barHeight + 72.dp + extraPadding
}

/**
 * Unified glass tab component covering top segmented tabs, floating bottom navigation,
 * selectable chip strips, and popup menus. All blur, tint, opacity, border, contrast, and
 * transition values come exclusively from [useBlurStyle].
 */
@Composable
fun GlassTabs(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassTabVariant = GlassTabVariant.TOP_SEGMENTED,
    minScrollableTabWidth: Dp = 104.dp,
    forceEqualWidth: Boolean = false,
    isDimmed: Boolean = false,
    showPageIndicatorDots: Boolean = true
) {
    if (items.isEmpty()) return

    val selectedAccent = items.getOrNull(selectedIndex)?.accentColor ?: MaterialTheme.colorScheme.primary
    val blur = useBlurStyle(
        accentColor = selectedAccent,
        useOverlayLiquid = variant == GlassTabVariant.BOTTOM_NAV
    )

    when (variant) {
        GlassTabVariant.BOTTOM_NAV -> GlassBottomNavTabs(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            blur = blur,
            isDimmed = isDimmed,
            showPageIndicatorDots = showPageIndicatorDots,
            modifier = modifier
        )
        GlassTabVariant.TOP_SEGMENTED,
        GlassTabVariant.CHIP -> GlassPillSegmentTabs(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            blur = blur,
            variant = variant,
            minScrollableTabWidth = minScrollableTabWidth,
            forceEqualWidth = forceEqualWidth,
            modifier = modifier
        )
        GlassTabVariant.MENU -> GlassMenuList(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            blur = blur,
            modifier = modifier
        )
    }
}

@Composable
internal fun GlassContainerSurface(
    blur: GlassBlurStyle,
    shape: Shape,
    isMenu: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = if (isMenu) blur.menuColor else blur.containerColor
    val borderColor = if (isMenu) blur.menuBorderColor else blur.containerBorderColor
    val blurDp = when {
        !blur.isBlurActive -> 0.dp
        isMenu -> blur.containerBlurDp
        else -> blur.navBlurDp
    }

    val liquidModifier = if (blur.useFletchLiquid && blur.liquidState != null && !isMenu) {
        Modifier.liquid(blur.liquidState) {
            this.shape = shape
            this.frost = blur.liquidFrostDp
            this.refraction = blur.liquidRefraction
            this.curve = blur.liquidCurve
            this.edge = blur.liquidEdge
            this.dispersion = blur.liquidDispersion
            this.saturation = blur.saturation
            this.tint = bgColor
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(liquidModifier)
            .clip(shape)
            .background(if (blur.useFletchLiquid && !isMenu) Color.Transparent else bgColor)
            .border(width = blur.containerBorderWidth, color = borderColor, shape = shape)
    ) {
        if (!blur.useFletchLiquid && blurDp > 0.5.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .blurEngineBackdrop(
                        radius = blurDp,
                        saturation = blur.saturation
                    )
                    .background(bgColor)
            )
        }
        content()
    }
}

@Composable
private fun GlassPillSegmentTabs(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    blur: GlassBlurStyle,
    variant: GlassTabVariant,
    minScrollableTabWidth: Dp,
    forceEqualWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val hasSelection = selectedIndex in items.indices
    val clampedSelected = if (hasSelection) selectedIndex else 0
    val sharedShape = CircleShape
    val outerPadding = 4.dp
    val gap = if (variant == GlassTabVariant.CHIP) 4.dp else 4.dp
    val segmentHeight = 48.dp

    val motionSpec = if (blur.isReducedMotion) {
        snap<Dp>()
    } else {
        tween<Dp>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }
    val alphaSpec = if (blur.isReducedMotion) {
        snap<Float>()
    } else {
        tween<Float>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (hasSelection) 1f else 0f,
        animationSpec = alphaSpec,
        label = "glassTabsIndicatorAlpha"
    )

    GlassContainerSurface(
        blur = blur,
        shape = sharedShape,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(outerPadding)
        ) {
            val minRequiredForEqual = if (items.size <= 2 && variant == GlassTabVariant.TOP_SEGMENTED) {
                0.dp
            } else {
                minScrollableTabWidth * items.size + gap * (items.size - 1)
            }
            val useScrollable = !forceEqualWidth && maxWidth < minRequiredForEqual

            if (!useScrollable) {
                val segmentWidth = ((maxWidth - gap * (items.size - 1)) / items.size)
                    .coerceAtLeast(48.dp)
                val targetOffset = (segmentWidth + gap) * clampedSelected
                val indicatorOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = motionSpec,
                    label = "glassTabsEqualOffset"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Single sliding active pill indicator (zero shadow glow, zero double outlines)
                    if (indicatorAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(segmentWidth)
                                .height(segmentHeight)
                                .graphicsLayer { alpha = indicatorAlpha }
                                .clip(sharedShape)
                                .background(blur.indicatorColor)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            val isSelected = hasSelection && index == clampedSelected
                            GlassSegmentLabel(
                                item = item,
                                selected = isSelected,
                                blur = blur,
                                compact = variant == GlassTabVariant.CHIP,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = segmentHeight)
                                    .clip(sharedShape)
                                    .semantics { selected = isSelected }
                                    .clickable(
                                        enabled = item.enabled,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = {
                                            item.onClick?.invoke()
                                            onSelect(index)
                                        }
                                    )
                                    .then(
                                        if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
                                    )
                            )
                        }
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                val density = LocalDensity.current
                val itemLeftsPx = remember(items.size) { mutableStateListOf(*Array(items.size) { 0f }) }
                val itemWidthsPx = remember(items.size) { mutableStateListOf(*Array(items.size) { 0f }) }

                val rawLeftDp = with(density) { itemLeftsPx.getOrElse(clampedSelected) { 0f }.toDp() }
                val rawWidthDp = with(density) { itemWidthsPx.getOrElse(clampedSelected) { 0f }.toDp() }

                val indicatorOffset by animateDpAsState(
                    targetValue = rawLeftDp,
                    animationSpec = motionSpec,
                    label = "glassTabsScrollOffset"
                )
                val indicatorWidth by animateDpAsState(
                    targetValue = rawWidthDp,
                    animationSpec = motionSpec,
                    label = "glassTabsScrollWidth"
                )

                LaunchedEffect(clampedSelected, hasSelection, items.size, rawLeftDp, rawWidthDp) {
                    if (hasSelection && scrollState.maxValue > 0 && rawWidthDp > 0.dp) {
                        val centerPx = with(density) { (rawLeftDp + rawWidthDp / 2f).toPx() }
                        val viewportPx = with(density) { maxWidth.toPx() }
                        val targetScroll = (centerPx - viewportPx / 2f)
                            .roundToInt()
                            .coerceIn(0, scrollState.maxValue)
                        scrollState.animateScrollTo(targetScroll)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                ) {
                    Box {
                        if (indicatorAlpha > 0.01f && indicatorWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorOffset)
                                    .width(indicatorWidth)
                                    .height(segmentHeight)
                                    .graphicsLayer { alpha = indicatorAlpha }
                                    .clip(sharedShape)
                                    .background(blur.indicatorColor)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items.forEachIndexed { index, item ->
                                val isSelected = hasSelection && index == clampedSelected
                                val chipMinWidth = if (variant == GlassTabVariant.CHIP) 56.dp else minScrollableTabWidth
                                GlassSegmentLabel(
                                    item = item,
                                    selected = isSelected,
                                    blur = blur,
                                    compact = variant == GlassTabVariant.CHIP,
                                    horizontalPadding = if (variant == GlassTabVariant.CHIP) 14.dp else 14.dp,
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = chipMinWidth)
                                        .heightIn(min = segmentHeight)
                                        .onGloballyPositioned { coords ->
                                            if (index < itemLeftsPx.size) {
                                                val left = coords.positionInParent().x
                                                val w = coords.size.width.toFloat()
                                                if (itemLeftsPx[index] != left) itemLeftsPx[index] = left
                                                if (itemWidthsPx[index] != w) itemWidthsPx[index] = w
                                            }
                                        }
                                        .clip(sharedShape)
                                        .semantics { selected = isSelected }
                                        .clickable(
                                            enabled = item.enabled,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Tab,
                                            onClick = {
                                                item.onClick?.invoke()
                                                onSelect(index)
                                            }
                                        )
                                        .then(
                                            if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSegmentLabel(
    item: GlassTabItem,
    selected: Boolean,
    blur: GlassBlurStyle,
    compact: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 10.dp
) {
    val colorSpec = if (blur.isReducedMotion) {
        snap<Color>()
    } else {
        tween<Color>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) blur.activeContentColor else blur.inactiveContentColor,
        animationSpec = colorSpec,
        label = "glassSegmentContentColor"
    )
    val displayIcon = if (selected) item.activeIcon else (item.inactiveIcon ?: item.activeIcon)

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
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = item.label,
            style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge).copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(5.dp))
            val resolvedBadgeBg = if (selected) blur.activeBadgeBgColor else blur.inactiveBadgeBgColor
            val resolvedBadgeText = if (selected) {
                blur.activeBadgeTextColor
            } else {
                item.badgeColor ?: blur.inactiveBadgeTextColor
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

@Composable
private fun GlassBottomNavTabs(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    blur: GlassBlurStyle,
    isDimmed: Boolean,
    showPageIndicatorDots: Boolean,
    modifier: Modifier = Modifier
) {
    val hasSelection = selectedIndex in items.indices
    val clampedSelected = if (hasSelection) selectedIndex else 0
    val sharedShape = CircleShape

    val navConfig = LocalBottomNavConfig.current
    val barHeight = navConfig.heightOption.heightDp.coerceAtLeast(48.dp)
    val maxContainerWidth = navConfig.widthOption.maxDp
    val configMinTabWidth = navConfig.iconScale.minTabWidth.coerceAtLeast(48.dp)
    val iconSize = navConfig.iconScale.iconSize
    val fontSize = navConfig.iconScale.fontSize
    val scrollState = rememberScrollState()

    val offsetSpec = if (blur.isReducedMotion) {
        snap<Dp>()
    } else {
        tween<Dp>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }
    val alphaSpec = if (blur.isReducedMotion) {
        snap<Float>()
    } else {
        tween<Float>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassContainerSurface(
            blur = blur,
            shape = sharedShape,
            modifier = Modifier
                .widthIn(max = maxContainerWidth)
                .fillMaxWidth()
                .testTag("floating_frosted_nav_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val isDenseBar = items.size >= 7
                val gap = if (isDenseBar) 1.dp else 2.dp
                val minRequiredWidth = configMinTabWidth * items.size + gap * (items.size - 1)
                val needsScroll = maxWidth < minRequiredWidth
                val tabWidth = if (needsScroll) {
                    configMinTabWidth
                } else {
                    (maxWidth - gap * (items.size - 1)) / items.size
                }
                val contentWidth = if (needsScroll) minRequiredWidth else maxWidth
                val targetIndicatorOffset = (tabWidth + gap) * clampedSelected

                val indicatorOffset by animateDpAsState(
                    targetValue = targetIndicatorOffset,
                    animationSpec = offsetSpec,
                    label = "glassBottomNavOffset"
                )
                val indicatorAlpha by animateFloatAsState(
                    targetValue = when {
                        !hasSelection -> 0f
                        isDimmed -> 0.28f
                        else -> 1f
                    },
                    animationSpec = alphaSpec,
                    label = "glassBottomNavIndicatorAlpha"
                )

                val density = LocalDensity.current
                LaunchedEffect(clampedSelected, hasSelection, needsScroll, contentWidth) {
                    if (hasSelection && needsScroll && scrollState.maxValue > 0) {
                        val targetPx = with(density) {
                            ((tabWidth + gap) * clampedSelected - maxWidth / 2 + tabWidth / 2).toPx()
                        }
                        scrollState.animateScrollTo(targetPx.toInt().coerceIn(0, scrollState.maxValue))
                    }
                }

                Box(
                    modifier = if (needsScroll) {
                        Modifier.horizontalScroll(scrollState)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    Box(modifier = Modifier.width(contentWidth)) {
                        val orbDiameter = minOf(tabWidth, barHeight)
                        val orbOffsetX = indicatorOffset + (tabWidth - orbDiameter) / 2f
                        val orbStrength = if (blur.isBlurActive) 1f else 0.72f
                        val orbTopTint = lerp(blur.accentColor, Color.White, if (blur.isDark) 0.34f else 0.45f)
                            .copy(alpha = (if (blur.isDark) 0.32f else 0.38f) * orbStrength)
                        val orbMidTint = blur.accentColor
                            .copy(alpha = (if (blur.isDark) 0.24f else 0.20f) * orbStrength)
                        val orbBottomTint = blur.accentColor
                            .copy(alpha = (if (blur.isDark) 0.14f else 0.10f) * orbStrength)
                        val orbBorderBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (blur.isDark) 0.62f else 0.88f),
                                blur.accentColor.copy(alpha = if (blur.isDark) 0.38f else 0.34f),
                                Color.White.copy(alpha = if (blur.isDark) 0.22f else 0.44f)
                            )
                        )
                        val effectiveContainerBg = blur.containerColor.compositeOver(MaterialTheme.colorScheme.background)
                        val effectiveOrbBg = orbMidTint.compositeOver(effectiveContainerBg)
                        val activeOrbContentColor = resolveWcagAccessibleContentColor(
                            background = effectiveOrbBg,
                            preferred = if (blur.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            secondaryFallback = blur.activeContentColor,
                            minRatio = 4.5f
                        )

                        // Previous translucent circular frosted glass orb indicator centered on active tab
                        if (indicatorAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .offset(x = orbOffsetX)
                                    .size(orbDiameter)
                                    .align(Alignment.CenterStart)
                                    .graphicsLayer { alpha = indicatorAlpha }
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(orbTopTint, orbMidTint, orbBottomTint)
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.15.dp,
                                        brush = orbBorderBrush,
                                        shape = CircleShape
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier.width(contentWidth),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items.forEachIndexed { index, item ->
                                val isSelected = !isDimmed && hasSelection && index == clampedSelected
                                val colorSpec = if (blur.isReducedMotion) {
                                    snap<Color>()
                                } else {
                                    tween<Color>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
                                }
                                val contentTint by animateColorAsState(
                                    targetValue = if (isSelected) activeOrbContentColor else blur.inactiveContentColor,
                                    animationSpec = colorSpec,
                                    label = "glassBottomNavTabTint"
                                )
                                val iconVector = if (isSelected) {
                                    item.activeIcon
                                } else {
                                    item.inactiveIcon ?: item.activeIcon
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .width(tabWidth)
                                        .minimumInteractiveComponentSize()
                                        .heightIn(min = barHeight)
                                        .clip(sharedShape)
                                        .semantics { selected = isSelected }
                                        .clickable(
                                            enabled = item.enabled,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Tab,
                                            onClick = {
                                                item.onClick?.invoke()
                                                onSelect(index)
                                            }
                                        )
                                        .padding(horizontal = if (isDenseBar) 2.dp else 4.dp, vertical = 4.dp)
                                        .then(
                                            if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
                                        )
                                ) {
                                    if (iconVector != null) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = item.contentDescription,
                                            tint = contentTint,
                                            modifier = Modifier.size(iconSize)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = item.label,
                                        color = contentTint,
                                        fontSize = fontSize,
                                        lineHeight = (fontSize.value + 2f).sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPageIndicatorDots && items.size > 1) {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("glass_pill_page_dots")
            ) {
                items.forEachIndexed { index, _ ->
                    val isCurrent = !isDimmed && hasSelection && index == clampedSelected
                    val dotWidth by animateDpAsState(
                        targetValue = if (isCurrent) 14.dp else 4.dp,
                        animationSpec = offsetSpec,
                        label = "navPageDotWidth_$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) {
                                    blur.accentColor.copy(alpha = 0.90f)
                                } else {
                                    blur.inactiveContentColor.copy(alpha = 0.34f)
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Multi-row / FlowRow container matching [GlassTabs] for wrapped chip groups and 2-column grids.
 * Unselected chips sit directly on a completely transparent background with zero stray boxes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassChipFlowGroup(
    items: List<GlassTabItem>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showContainerTrack: Boolean = true,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp
) {
    if (items.isEmpty()) return
    val selectedAccent = selectedIndex?.let { items.getOrNull(it)?.accentColor }
        ?: MaterialTheme.colorScheme.primary
    val blur = useBlurStyle(accentColor = selectedAccent)
    val trackShape = RoundedCornerShape(22.dp)

    val rowContent: @Composable () -> Unit = {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showContainerTrack) Modifier.padding(4.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                GlassChipPill(
                    item = item,
                    selected = isSelected,
                    blur = blur,
                    onClick = {
                        item.onClick?.invoke()
                        onSelect(index)
                    }
                )
            }
        }
    }

    if (showContainerTrack) {
        GlassContainerSurface(
            blur = blur,
            shape = trackShape,
            modifier = modifier.fillMaxWidth()
        ) {
            rowContent()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            rowContent()
        }
    }
}

/**
 * Single chip / option pill powered by [useBlurStyle].
 * Unselected state always uses [Color.Transparent] with zero ripple/hover/focus fill clutter.
 */
@Composable
fun GlassChipPill(
    item: GlassTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blur: GlassBlurStyle = useBlurStyle(accentColor = item.accentColor ?: MaterialTheme.colorScheme.primary),
    fillWidth: Boolean = false
) {
    val pillShape = CircleShape
    val colorSpec = if (blur.isReducedMotion) {
        snap<Color>()
    } else {
        tween<Color>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }
    val containerFill by animateColorAsState(
        targetValue = if (selected) blur.indicatorColor else Color.Transparent,
        animationSpec = colorSpec,
        label = "glassChipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) blur.activeContentColor else blur.inactiveContentColor,
        animationSpec = colorSpec,
        label = "glassChipFg"
    )
    val displayIcon = if (selected) item.activeIcon else (item.inactiveIcon ?: item.activeIcon)

    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .clip(pillShape)
            .background(containerFill)
            .semantics { this.selected = selected }
            .clickable(
                enabled = item.enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .then(
                if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (displayIcon != null) {
            Icon(
                imageVector = displayIcon,
                contentDescription = item.contentDescription,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Shared glass container tray for 2-column grids or custom chip layouts.
 */
@Composable
fun GlassGroupTrack(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val blur = useBlurStyle()
    GlassContainerSurface(
        blur = blur,
        shape = shape,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

/**
 * Unified glass popup menu replacing Material DropdownMenu / DropdownMenuItem.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val blur = useBlurStyle(useOverlayLiquid = true)
    val menuShape = RoundedCornerShape(18.dp)
    val density = LocalDensity.current
    val yOffsetPx = with(density) { 4.dp.roundToPx() }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, yOffsetPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        GlassContainerSurface(
            blur = blur,
            shape = menuShape,
            isMenu = true,
            modifier = modifier
                .widthIn(min = 180.dp, max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
fun GlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val blur = useBlurStyle(useOverlayLiquid = true)
    val itemShape = RoundedCornerShape(14.dp)
    val colorSpec = if (blur.isReducedMotion) {
        snap<Color>()
    } else {
        tween<Color>(durationMillis = blur.indicatorTransitionMillis, easing = FastOutSlowInEasing)
    }
    val itemBg by animateColorAsState(
        targetValue = if (selected) blur.indicatorColor else Color.Transparent,
        animationSpec = colorSpec,
        label = "glassMenuItemBg"
    )
    val itemFg by animateColorAsState(
        targetValue = if (selected) blur.activeContentColor else blur.inactiveContentColor,
        animationSpec = colorSpec,
        label = "glassMenuItemFg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .clip(itemShape)
            .background(itemBg)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) itemFg else (leadingIconTint ?: itemFg),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = itemFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassMenuList(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    blur: GlassBlurStyle,
    modifier: Modifier = Modifier
) {
    GlassContainerSurface(
        blur = blur,
        shape = RoundedCornerShape(18.dp),
        isMenu = true,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val icon = if (isSelected) item.activeIcon else (item.inactiveIcon ?: item.activeIcon)
                GlassDropdownMenuItem(
                    text = item.label,
                    leadingIcon = icon,
                    selected = isSelected,
                    enabled = item.enabled,
                    onClick = {
                        item.onClick?.invoke()
                        onSelect(index)
                    },
                    modifier = if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
                )
            }
        }
    }
}
