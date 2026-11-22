package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDestination
import com.example.data.model.BlurEngineStyle
import com.example.data.model.NetGateThemeStyle
import com.example.ui.theme.BlurEngine.liquidGlassRefractionEdge
import com.example.ui.theme.LiquidGlassPrismStops
import com.example.ui.theme.LocalBackdropLiquidState
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalExpressiveMotion
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalOverlayLiquidState
import com.example.ui.theme.LocalThemeStyle
import com.example.ui.theme.navLiquidFrostDp
import io.github.fletchmckee.liquid.liquid

private data class NavTabSpec(
    val destination: AppDestination,
    val icon: ImageVector,
    val contentDescription: String,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun FloatingFrostedNavBar(
    currentDestination: AppDestination,
    isConnectionInfoOpen: Boolean,
    onSelectFirewall: () -> Unit,
    onSelectWebsites: () -> Unit,
    onSelectDataUsage: () -> Unit,
    onSelectDnsProtection: () -> Unit,
    onSelectHotspot: () -> Unit = {},
    onSelectVpnGuard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val motionSpec = LocalExpressiveMotion.current

    val pillShape = RoundedCornerShape(
        when (themeStyle) {
            NetGateThemeStyle.LIQUID_GLASS -> 38.dp
            NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 34.dp
            NetGateThemeStyle.MATERIAL_YOU_LITE -> 28.dp
            NetGateThemeStyle.NETGATE_DEFAULT -> 36.dp
        }
    )
    val indicatorShape = RoundedCornerShape(
        when (themeStyle) {
            NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 22.dp
            NetGateThemeStyle.MATERIAL_YOU_LITE -> 20.dp
            else -> 28.dp
        }
    )

    val overlayLiquidState = LocalOverlayLiquidState.current ?: LocalBackdropLiquidState.current
    val useFletchLiquid = themeStyle == NetGateThemeStyle.LIQUID_GLASS && overlayLiquidState != null

    val navAlpha = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> if (isDark) 0.94f else 0.97f
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isDark) 0.88f else 0.92f
        NetGateThemeStyle.LIQUID_GLASS -> if (useFletchLiquid) {
            when (blurConfig.style) {
                BlurEngineStyle.OFF -> if (isDark) 0.68f else 0.74f
                BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.52f else 0.58f
                BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.44f else 0.52f
                BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.36f else 0.44f
            }
        } else {
            when (blurConfig.style) {
                BlurEngineStyle.OFF -> if (isDark) 0.88f else 0.90f
                BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.72f else 0.78f
                BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.62f else 0.70f
                BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.50f else 0.60f
            }
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> if (isDark) 0.94f else 0.96f
            BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.84f else 0.88f
            BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.74f else 0.80f
            BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.62f else 0.70f
        }
    }

    val containerColor by animateColorAsState(
        targetValue = when (themeStyle) {
            NetGateThemeStyle.MATERIAL_YOU_LITE -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = navAlpha)
            else -> if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = navAlpha)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = navAlpha)
            }
        },
        animationSpec = spring(stiffness = motionSpec.spatialStiffness),
        label = "floatingNavContainerColor"
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val outerBorderBrush = remember(isDark, outlineColor, themeStyle) {
        when (themeStyle) {
            NetGateThemeStyle.LIQUID_GLASS -> Brush.linearGradient(
                colors = LiquidGlassPrismStops.map { it.copy(alpha = if (isDark) 0.50f else 0.80f) }
            )
            NetGateThemeStyle.MATERIAL_YOU_LITE -> Brush.linearGradient(
                colors = listOf(outlineColor.copy(alpha = 0.35f), outlineColor.copy(alpha = 0.25f))
            )
            else -> if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        outlineColor.copy(alpha = 0.36f)
                    )
                )
            }
        }
    }

    val glassSheenBrush = remember(isDark, themeStyle) {
        if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE) {
            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 0.20f else 0.14f),
                        Color.White.copy(alpha = 0.03f),
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

    val tabs = listOf(
        NavTabSpec(AppDestination.FIREWALL, Icons.Rounded.Shield, "Firewall", "nav_tab_firewall", onSelectFirewall),
        NavTabSpec(AppDestination.WEBSITES, Icons.Rounded.Public, "Website Blocker", "nav_tab_websites", onSelectWebsites),
        NavTabSpec(AppDestination.VPN_GUARD, Icons.Rounded.VpnLock, "VPN Guard", "floating_nav_vpn_guard", onSelectVpnGuard),
        NavTabSpec(AppDestination.DATA_USAGE, Icons.Rounded.BarChart, "Data Usage", "nav_tab_data_usage", onSelectDataUsage),
        NavTabSpec(AppDestination.DNS, Icons.Rounded.Dns, "1.1.1.1 DNS Protection", "floating_nav_dns", onSelectDnsProtection),
        NavTabSpec(AppDestination.HOTSPOT, Icons.Rounded.WifiTethering, "Hotspot & Wi-Fi", "floating_nav_hotspot", onSelectHotspot)
    )
    val selectedIndex = tabs.indexOfFirst { it.destination == currentDestination }.coerceAtLeast(0)
    val activeAccent = MaterialTheme.colorScheme.primary
    val activeSecondary = MaterialTheme.colorScheme.secondary
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val navFrostDp = blurConfig.style.navLiquidFrostDp()
        Surface(
            shape = pillShape,
            color = if (useFletchLiquid) Color.Transparent else containerColor,
            shadowElevation = if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE) 6.dp else 14.dp,
            border = BorderStroke(
                width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 1.3.dp else 1.dp,
                brush = outerBorderBrush
            ),
            modifier = Modifier
                .widthIn(max = 560.dp)
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
                            this.saturation = 1.24f
                            this.tint = containerColor
                        }
                    } else {
                        Modifier
                    }
                )
                .testTag("floating_frosted_nav_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!useFletchLiquid) {
                            Modifier.liquidGlassRefractionEdge(
                                enabled = motionSpec.enableRefractionShader,
                                shape = pillShape,
                                strength = 0.018f
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(glassSheenBrush)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val gap = 2.dp
                val minRequiredWidth = 48.dp * tabs.size + gap * (tabs.size - 1)
                val needsScroll = maxWidth < minRequiredWidth
                val tabWidth = if (needsScroll) {
                    48.dp
                } else {
                    (maxWidth - gap * (tabs.size - 1)) / tabs.size
                }
                val contentWidth = if (needsScroll) minRequiredWidth else maxWidth
                val targetIndicatorOffset = (tabWidth + gap) * selectedIndex

                val indicatorDamping = when (themeStyle) {
                    NetGateThemeStyle.LIQUID_GLASS -> 0.58f
                    NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.66f
                    NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.96f
                    NetGateThemeStyle.NETGATE_DEFAULT -> 0.78f
                }
                val indicatorStiffness = when (themeStyle) {
                    NetGateThemeStyle.LIQUID_GLASS -> 420f
                    NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 360f
                    NetGateThemeStyle.MATERIAL_YOU_LITE -> 640f
                    NetGateThemeStyle.NETGATE_DEFAULT -> 440f
                }
                val indicatorOffset by animateDpAsState(
                    targetValue = targetIndicatorOffset,
                    animationSpec = spring(
                        dampingRatio = indicatorDamping,
                        stiffness = indicatorStiffness
                    ),
                    label = "floatingNavGlideOffset"
                )
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (isConnectionInfoOpen) 0.25f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "floatingNavIndicatorAlpha"
                )

                val density = LocalDensity.current
                LaunchedEffect(selectedIndex, needsScroll, contentWidth) {
                    if (needsScroll && scrollState.maxValue > 0) {
                        val targetPx = with(density) { ((tabWidth + gap) * selectedIndex - maxWidth / 2 + tabWidth / 2).toPx() }
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
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .height(48.dp)
                                .graphicsLayer { alpha = indicatorAlpha }
                                .clip(indicatorShape)
                                .background(
                                    when (themeStyle) {
                                        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                            )
                                        )
                                        NetGateThemeStyle.MATERIAL_YOU_LITE -> Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                            )
                                        )
                                        else -> Brush.linearGradient(
                                            colors = listOf(
                                                activeAccent.copy(alpha = if (isDark) 0.28f else 0.18f),
                                                activeSecondary.copy(alpha = if (isDark) 0.18f else 0.11f)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 1.2.dp else 1.dp,
                                    brush = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) {
                                        Brush.linearGradient(LiquidGlassPrismStops.map { it.copy(alpha = 0.65f) })
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                activeAccent.copy(alpha = if (isDark) 0.65f else 0.50f),
                                                activeSecondary.copy(alpha = if (isDark) 0.35f else 0.25f)
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
                            tabs.forEach { tab ->
                                FloatingNavItem(
                                    icon = tab.icon,
                                    contentDescription = tab.contentDescription,
                                    selected = !isConnectionInfoOpen && currentDestination == tab.destination,
                                    themeStyle = themeStyle,
                                    onClick = tab.onClick,
                                    modifier = Modifier
                                        .width(tabWidth)
                                        .testTag(tab.testTag)
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
private fun FloatingNavItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    themeStyle: NetGateThemeStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val activeAccent = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE,
        NetGateThemeStyle.MATERIAL_YOU_LITE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primary
    }

    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            activeAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = themeStyle.springStiffness),
        label = "floatingNavTint"
    )
    val targetScaleX = when {
        isPressed && themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 1.14f
        isPressed -> 0.86f
        selected && themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 1.20f
        selected -> 1.14f
        else -> 1.0f
    }
    val targetScaleY = when {
        isPressed && themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 0.82f
        isPressed -> 0.86f
        selected && themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 1.20f
        selected -> 1.14f
        else -> 1.0f
    }
    val scaleSpring = spring<Float>(
        dampingRatio = themeStyle.springDampingRatio,
        stiffness = themeStyle.springStiffness
    )
    val iconScaleX by animateFloatAsState(
        targetValue = targetScaleX,
        animationSpec = scaleSpring,
        label = "floatingNavIconScaleX"
    )
    val iconScaleY by animateFloatAsState(
        targetValue = targetScaleY,
        animationSpec = scaleSpring,
        label = "floatingNavIconScaleY"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScaleX
                    scaleY = iconScaleY
                }
        )
    }
}
