package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.model.AppDestination
import com.example.data.model.BlurEngineStyle
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalIsDarkTheme

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

    val outerBorderBrush = if (isDark) {
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
                MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
            )
        )
    }

    val glassSheenBrush = Brush.verticalGradient(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(glassSheenBrush)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingNavItem(
                    icon = Icons.Rounded.Shield,
                    contentDescription = "Firewall",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.FIREWALL,
                    onClick = onSelectFirewall,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_firewall")
                )

                FloatingNavItem(
                    icon = Icons.Rounded.Public,
                    contentDescription = "Website Blocker",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.WEBSITES,
                    onClick = onSelectWebsites,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_websites")
                )

                FloatingNavItem(
                    icon = Icons.Rounded.BarChart,
                    contentDescription = "Data Usage",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.DATA_USAGE,
                    onClick = onSelectDataUsage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_data_usage")
                )

                FloatingNavItem(
                    icon = Icons.Rounded.Speed,
                    contentDescription = "Speed Test",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.SPEED_TEST,
                    onClick = onSelectSpeedTest,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("floating_nav_speed")
                )

                FloatingNavItem(
                    icon = Icons.Rounded.Dns,
                    contentDescription = "1.1.1.1 DNS Protection",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.DNS,
                    onClick = onSelectDnsProtection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("floating_nav_dns")
                )

                FloatingNavItem(
                    icon = androidx.compose.material.icons.Icons.Rounded.WifiTethering,
                    contentDescription = "Hotspot & Wi-Fi",
                    selected = !isConnectionInfoOpen && currentDestination == AppDestination.HOTSPOT,
                    onClick = onSelectHotspot,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("floating_nav_hotspot")
                )
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
    val isDark = LocalIsDarkTheme.current
    val activeAccent = MaterialTheme.colorScheme.primary
    val itemShape = RoundedCornerShape(28.dp)

    val pillFill by animateColorAsState(
        targetValue = if (selected) {
            activeAccent.copy(alpha = if (isDark) 0.24f else 0.16f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "floatingNavFill"
    )

    val pillBorder by animateColorAsState(
        targetValue = if (selected) {
            activeAccent.copy(alpha = if (isDark) 0.55f else 0.45f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "floatingNavBorder"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            activeAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "floatingNavTint"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(itemShape)
            .background(pillFill, itemShape)
            .border(1.dp, pillBorder, itemShape)
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}
