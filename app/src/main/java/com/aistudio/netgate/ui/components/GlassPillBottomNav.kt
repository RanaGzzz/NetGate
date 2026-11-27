package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.ui.theme.BlurEngine.blurEngineBackdrop
import com.aistudio.netgate.ui.theme.BlurEngine.frostedChromaticEdge
import com.aistudio.netgate.ui.theme.BlurEngine.liquidGlassRefractionEdge
import com.aistudio.netgate.ui.theme.EmeraldBright
import com.aistudio.netgate.ui.theme.LiquidGlassAqua
import com.aistudio.netgate.ui.theme.LiquidGlassCyan
import com.aistudio.netgate.ui.theme.LiquidGlassPrismStops
import com.aistudio.netgate.ui.theme.LocalBackdropLiquidState
import com.aistudio.netgate.ui.theme.LocalBlurEngineConfig
import com.aistudio.netgate.ui.theme.LocalDynamicColorEnabled
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.LocalOverlayLiquidState
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.navLiquidFrostDp
import io.github.fletchmckee.liquid.liquid

data class GlassPillNavItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * Floating morphing glass pill navigation bar inspired by the Morph aesthetic:
 * - Translucent blurred glass capsule sitting above navigation/gesture insets with horizontal margins.
 * - Soft dark/light ambient glow gradient backing + thin light rim border.
 * - Stacked icon + concise label inside every tab.
 * - Spring-morphing highlighted capsule indicator for the active tab, respecting Material You
 *   dynamic colors when enabled and the selected [BlurEngineStyle] across all themes.
 */
@Composable
fun GlassPillBottomNav(
    items: List<GlassPillNavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    isDimmed: Boolean = false,
    showPageIndicatorDots: Boolean = true
) {
    if (items.isEmpty()) return
    val hasSelection = selectedIndex in items.indices
    val clampedSelected = if (hasSelection) selectedIndex else 0

    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val motionSpec = LocalExpressiveMotion.current
    val useDynamicColor = LocalDynamicColorEnabled.current
    val intensityScale = blurConfig.intensity.coerceIn(0.4f, 1.5f)
    val isMorph = blurConfig.style == BlurEngineStyle.MORPH

    val pillShape = RoundedCornerShape(
        when {
            isMorph -> 38.dp
            themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 38.dp
            themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 34.dp
            themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> 28.dp
            else -> 36.dp
        }
    )
    val indicatorShape = RoundedCornerShape(
        when {
            themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> 22.dp
            else -> 28.dp
        }
    )

    val overlayLiquidState = LocalOverlayLiquidState.current ?: LocalBackdropLiquidState.current
    val useFletchLiquid = themeStyle == NetGateThemeStyle.LIQUID_GLASS && overlayLiquidState != null

    val navAlpha = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> if (isDark) 0.92f else 0.96f
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isDark) 0.84f else 0.90f
        NetGateThemeStyle.LIQUID_GLASS -> if (useFletchLiquid) {
            when (blurConfig.style) {
                BlurEngineStyle.OFF -> if (isDark) 0.68f else 0.74f
                BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.52f else 0.58f
                BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.44f else 0.52f
                BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.36f else 0.44f
                BlurEngineStyle.MORPH -> if (isDark) 0.34f else 0.42f
            }
        } else {
            when (blurConfig.style) {
                BlurEngineStyle.OFF -> if (isDark) 0.88f else 0.90f
                BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.72f else 0.78f
                BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.62f else 0.70f
                BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.52f else 0.62f
                BlurEngineStyle.MORPH -> if (isDark) 0.48f else 0.58f
            }
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> if (isDark) 0.94f else 0.96f
            BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.80f else 0.86f
            BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.70f else 0.78f
            BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.58f else 0.68f
            BlurEngineStyle.MORPH -> if (isDark) 0.54f else 0.64f
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = navAlpha)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = navAlpha)
        },
        animationSpec = spring(stiffness = motionSpec.spatialStiffness),
        label = "glassPillNavContainerColor"
    )

    val dynamicPrimary = MaterialTheme.colorScheme.primary
    val dynamicSecondary = MaterialTheme.colorScheme.secondary
    val dynamicTertiary = MaterialTheme.colorScheme.tertiary

    val accentColor = if (useDynamicColor) dynamicPrimary else MaterialTheme.colorScheme.primary
    val glowTeal = if (useDynamicColor) dynamicSecondary else LiquidGlassAqua
    val glowBlue = if (useDynamicColor) dynamicTertiary else LiquidGlassCyan
    val outlineColor = MaterialTheme.colorScheme.outline

    val outerBorderBrush = remember(isDark, outlineColor, themeStyle, isMorph, accentColor, glowBlue) {
        when {
            themeStyle == NetGateThemeStyle.LIQUID_GLASS -> Brush.linearGradient(
                colors = LiquidGlassPrismStops.map { it.copy(alpha = if (isDark) 0.52f else 0.80f) }
            )
            isMorph -> Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.36f else 0.88f),
                    glowBlue.copy(alpha = if (isDark) 0.28f else 0.38f),
                    accentColor.copy(alpha = if (isDark) 0.22f else 0.30f),
                    Color.White.copy(alpha = if (isDark) 0.12f else 0.38f)
                )
            )
            themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> Brush.linearGradient(
                colors = listOf(outlineColor.copy(alpha = 0.35f), outlineColor.copy(alpha = 0.25f))
            )
            isDark -> Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.30f),
                    glowBlue.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.08f)
                )
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.96f),
                    outlineColor.copy(alpha = 0.36f)
                )
            )
        }
    }

    val glassSheenBrush = remember(isDark, themeStyle, isMorph) {
        if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE && !isMorph) {
            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = if (isMorph || themeStyle == NetGateThemeStyle.LIQUID_GLASS) 0.18f else 0.12f),
                        Color.White.copy(alpha = 0.025f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                }
            )
        }
    }

    val navFrostDp = blurConfig.style.navLiquidFrostDp(intensityScale)
    val backdropBlurDp = when (blurConfig.style) {
        BlurEngineStyle.OFF -> 0f
        BlurEngineStyle.SOFT_GLASS -> 8f
        BlurEngineStyle.ACRYLIC_PRO -> 14f
        BlurEngineStyle.ULTRA_FROST -> 20f
        BlurEngineStyle.MORPH -> 24f
    } * intensityScale
    val scrollState = rememberScrollState()

    val navConfig = LocalBottomNavConfig.current
    val barHeight = navConfig.heightOption.heightDp
    val maxContainerWidth = navConfig.widthOption.maxDp
    val configMinTabWidth = navConfig.iconScale.minTabWidth
    val iconSize = navConfig.iconScale.iconSize
    val fontSize = navConfig.iconScale.fontSize

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = pillShape,
            color = if (useFletchLiquid) Color.Transparent else containerColor,
            shadowElevation = if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE) 6.dp else 16.dp,
            border = BorderStroke(
                width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS || isMorph) 1.25.dp else 1.dp,
                brush = outerBorderBrush
            ),
            modifier = Modifier
                .widthIn(max = maxContainerWidth)
                .fillMaxWidth()
                .then(
                    if (useFletchLiquid) {
                        Modifier.liquid(overlayLiquidState!!) {
                            this.shape = pillShape
                            this.frost = navFrostDp
                            this.refraction = 0.30f
                            this.curve = 0.34f
                            this.edge = 0.09f
                            this.dispersion = 0.04f
                            this.saturation = blurConfig.style.saturationBoost
                            this.tint = containerColor
                        }
                    } else {
                        Modifier
                    }
                )
                .testTag("floating_frosted_nav_bar")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(pillShape)
            ) {
                // Soft blurred teal/blue/dynamic glow layer behind nav tabs (Morph & frosted glass backdrop)
                if (!useFletchLiquid && blurConfig.style != BlurEngineStyle.OFF &&
                    themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .blurEngineBackdrop(
                                radius = backdropBlurDp.dp,
                                saturation = blurConfig.style.saturationBoost
                            )
                            .drawBehind {
                                val baseGlowAlpha = when (blurConfig.style) {
                                    BlurEngineStyle.OFF -> 0f
                                    BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.14f else 0.10f
                                    BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.20f else 0.14f
                                    BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.24f else 0.18f
                                    BlurEngineStyle.MORPH -> if (isDark) 0.32f else 0.22f
                                } * intensityScale.coerceAtMost(1.3f)

                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            glowTeal.copy(alpha = baseGlowAlpha * 0.65f),
                                            accentColor.copy(alpha = baseGlowAlpha * 0.38f),
                                            glowBlue.copy(alpha = baseGlowAlpha * 0.70f)
                                        )
                                    )
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            glowTeal.copy(alpha = baseGlowAlpha),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.22f, size.height * 0.45f),
                                        radius = size.width * 0.38f
                                    ),
                                    center = Offset(size.width * 0.22f, size.height * 0.45f),
                                    radius = size.width * 0.38f
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            glowBlue.copy(alpha = baseGlowAlpha * 0.92f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.80f, size.height * 0.55f),
                                        radius = size.width * 0.36f
                                    ),
                                    center = Offset(size.width * 0.80f, size.height * 0.55f),
                                    radius = size.width * 0.36f
                                )
                            }
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!useFletchLiquid) {
                                if (motionSpec.enableRefractionShader) {
                                    Modifier.liquidGlassRefractionEdge(
                                        enabled = true,
                                        shape = pillShape,
                                        strength = 0.018f
                                    )
                                } else {
                                    Modifier.frostedChromaticEdge(
                                        shape = pillShape,
                                        borderAccent = if (isMorph) glowTeal else null
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .background(glassSheenBrush)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    val isDenseBar = items.size >= 7
                    val gap = if (isDenseBar) 1.dp else 2.dp
                    val minTabWidth = configMinTabWidth
                    val minRequiredWidth = minTabWidth * items.size + gap * (items.size - 1)
                    val needsScroll = maxWidth < minRequiredWidth
                    val tabWidth = if (needsScroll) {
                        minTabWidth
                    } else {
                        (maxWidth - gap * (items.size - 1)) / items.size
                    }
                    val contentWidth = if (needsScroll) minRequiredWidth else maxWidth
                    val targetIndicatorOffset = (tabWidth + gap) * clampedSelected

                    val indicatorDamping = when (themeStyle) {
                        NetGateThemeStyle.LIQUID_GLASS -> 0.62f
                        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.68f
                        NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.92f
                        NetGateThemeStyle.NETGATE_DEFAULT -> 0.72f
                    }
                    val indicatorStiffness = when (themeStyle) {
                        NetGateThemeStyle.LIQUID_GLASS -> 420f
                        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 380f
                        NetGateThemeStyle.MATERIAL_YOU_LITE -> 560f
                        NetGateThemeStyle.NETGATE_DEFAULT -> 400f
                    }
                    val indicatorOffset by animateDpAsState(
                        targetValue = targetIndicatorOffset,
                        animationSpec = spring(
                            dampingRatio = indicatorDamping,
                            stiffness = indicatorStiffness
                        ),
                        label = "glassPillMorphOffset"
                    )
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = when {
                            !hasSelection -> 0f
                            isDimmed -> 0.28f
                            else -> 1f
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "glassPillIndicatorAlpha"
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
                            // Spring-morphing highlighted capsule for the active tab
                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorOffset)
                                    .width(tabWidth)
                                    .height(barHeight)
                                    .graphicsLayer { alpha = indicatorAlpha }
                                    .clip(indicatorShape)
                                    .background(
                                        when (themeStyle) {
                                            NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
                                                )
                                            )
                                            NetGateThemeStyle.MATERIAL_YOU_LITE -> Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f),
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
                                                )
                                            )
                                            else -> Brush.linearGradient(
                                                colors = listOf(
                                                    accentColor.copy(alpha = if (isDark) 0.28f else 0.18f),
                                                    glowTeal.copy(alpha = if (isDark) 0.20f else 0.12f)
                                                )
                                            )
                                        }
                                    )
                                    .border(
                                        width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS || isMorph) 1.15.dp else 1.dp,
                                        brush = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) {
                                            Brush.linearGradient(LiquidGlassPrismStops.map { it.copy(alpha = 0.68f) })
                                        } else {
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    accentColor.copy(alpha = if (isDark) 0.68f else 0.52f),
                                                    glowBlue.copy(alpha = if (isDark) 0.36f else 0.26f)
                                                )
                                            )
                                        },
                                        shape = indicatorShape
                                    )
                            )

                            Row(
                                modifier = Modifier.width(contentWidth),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items.forEachIndexed { index, item ->
                                    GlassPillTab(
                                        item = item,
                                        selected = !isDimmed && hasSelection && index == clampedSelected,
                                        themeStyle = themeStyle,
                                        useDynamicColor = useDynamicColor,
                                        compactLabel = isDenseBar,
                                        tabHeight = barHeight,
                                        iconSize = iconSize,
                                        fontSize = fontSize,
                                        modifier = Modifier
                                            .width(tabWidth)
                                            .testTag(item.testTag)
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
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "navPageDotWidth_$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) {
                                    accentColor.copy(alpha = 0.88f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPillTab(
    item: GlassPillNavItem,
    selected: Boolean,
    themeStyle: NetGateThemeStyle,
    useDynamicColor: Boolean,
    compactLabel: Boolean = false,
    tabHeight: androidx.compose.ui.unit.Dp = 54.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 9.5.sp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val selectedTint = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE,
        NetGateThemeStyle.MATERIAL_YOU_LITE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> if (useDynamicColor) {
            MaterialTheme.colorScheme.primary
        } else {
            EmeraldBright
        }
    }

    val contentTint by animateColorAsState(
        targetValue = if (selected) {
            selectedTint
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = themeStyle.springStiffness),
        label = "glassPillTabTint"
    )

    val targetScaleX = when {
        isPressed && themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 1.08f
        isPressed -> 0.90f
        selected -> 1.06f
        else -> 1.0f
    }
    val targetScaleY = when {
        isPressed && themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 0.90f
        isPressed -> 0.90f
        selected -> 1.06f
        else -> 1.0f
    }
    val scaleSpring = spring<Float>(
        dampingRatio = themeStyle.springDampingRatio,
        stiffness = themeStyle.springStiffness
    )
    val scaleX by animateFloatAsState(
        targetValue = targetScaleX,
        animationSpec = scaleSpring,
        label = "glassPillTabScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = targetScaleY,
        animationSpec = scaleSpring,
        label = "glassPillTabScaleY"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = tabHeight)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = item.onClick
            )
            .padding(horizontal = if (compactLabel) 1.dp else 2.dp, vertical = 4.dp)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = contentTint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            color = contentTint,
            fontSize = fontSize,
            lineHeight = (fontSize.value + 2f).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
