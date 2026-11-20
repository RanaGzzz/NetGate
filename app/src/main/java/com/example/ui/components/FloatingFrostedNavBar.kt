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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDestination
import com.example.data.model.BlurEngineStyle
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalIsDarkTheme

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
    onSelectSpeedTest: () -> Unit,
    onSelectDnsProtection: () -> Unit,
    onSelectHotspot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val pillShape = RoundedCornerShape(36.dp)
    val indicatorShape = RoundedCornerShape(28.dp)

    val navAlpha = when (blurConfig.style) {
        BlurEngineStyle.OFF -> if (isDark) 0.94f else 0.96f
        BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.84f else 0.88f
        BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.74f else 0.80f
        BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.62f else 0.70f
    }

    val containerColor by animateColorAsState(
        targetValue = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = navAlpha)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = navAlpha)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "floatingNavContainerColor"
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val outerBorderBrush = remember(isDark, outlineColor) {
        if (isDark) {
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

    val glassSheenBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.03f),
                    Color.Transparent
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent
                )
            }
        )
    }

    val tabs = listOf(
        NavTabSpec(AppDestination.FIREWALL, Icons.Rounded.Shield, "Firewall", "nav_tab_firewall", onSelectFirewall),
        NavTabSpec(AppDestination.WEBSITES, Icons.Rounded.Public, "Website Blocker", "nav_tab_websites", onSelectWebsites),
        NavTabSpec(AppDestination.DATA_USAGE, Icons.Rounded.BarChart, "Data Usage", "nav_tab_data_usage", onSelectDataUsage),
        NavTabSpec(AppDestination.SPEED_TEST, Icons.Rounded.Speed, "Speed Test", "floating_nav_speed", onSelectSpeedTest),
        NavTabSpec(AppDestination.DNS, Icons.Rounded.Dns, "1.1.1.1 DNS Protection", "floating_nav_dns", onSelectDnsProtection),
        NavTabSpec(AppDestination.HOTSPOT, Icons.Rounded.WifiTethering, "Hotspot & Wi-Fi", "floating_nav_hotspot", onSelectHotspot)
    )
    val selectedIndex = tabs.indexOfFirst { it.destination == currentDestination }.coerceAtLeast(0)
    val activeAccent = MaterialTheme.colorScheme.primary
    val activeSecondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = containerColor,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, outerBorderBrush),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .testTag("floating_frosted_nav_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(glassSheenBrush)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val gap = 2.dp
                val tabWidth = ((maxWidth - gap * (tabs.size - 1)) / tabs.size).coerceAtLeast(48.dp)
                val targetIndicatorOffset = (tabWidth + gap) * selectedIndex
                val indicatorOffset by animateDpAsState(
                    targetValue = targetIndicatorOffset,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 440f
                    ),
                    label = "floatingNavGlideOffset"
                )
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (isConnectionInfoOpen) 0.25f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "floatingNavIndicatorAlpha"
                )

                // Gliding neon-glass active pill indicator
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .height(48.dp)
                        .graphicsLayer { alpha = indicatorAlpha }
                        .clip(indicatorShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    activeAccent.copy(alpha = if (isDark) 0.28f else 0.18f),
                                    activeSecondary.copy(alpha = if (isDark) 0.18f else 0.11f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    activeAccent.copy(alpha = if (isDark) 0.65f else 0.50f),
                                    activeSecondary.copy(alpha = if (isDark) 0.35f else 0.25f)
                                )
                            ),
                            shape = indicatorShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        FloatingNavItem(
                            icon = tab.icon,
                            contentDescription = tab.contentDescription,
                            selected = !isConnectionInfoOpen && currentDestination == tab.destination,
                            onClick = tab.onClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tab.testTag)
                        )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val activeAccent = MaterialTheme.colorScheme.primary

    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            activeAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "floatingNavTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.88f
            selected -> 1.14f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 520f
        ),
        label = "floatingNavIconScale"
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
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
