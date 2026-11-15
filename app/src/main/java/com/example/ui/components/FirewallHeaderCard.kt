package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveNetworkType
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.WifiAccent

@Composable
fun FirewallHeaderCard(
    isFirewallActive: Boolean,
    activeNetworkType: ActiveNetworkType,
    totalAppsCount: Int,
    wifiBlockedCount: Int,
    dataBlockedCount: Int,
    activeBlockedNowCount: Int,
    onToggleFirewall: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val accentColor by animateColorAsState(
        targetValue = if (isFirewallActive) EmeraldPrimary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "firewallAccent"
    )
    val glassTint by animateColorAsState(
        targetValue = when {
            isFirewallActive && isDark -> EmeraldPrimary.copy(alpha = 0.16f)
            isFirewallActive -> EmeraldPrimary.copy(alpha = 0.12f)
            isDark -> Color(0xFF162236).copy(alpha = 0.60f)
            else -> Color.White.copy(alpha = 0.72f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "firewallGlassTint"
    )

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("firewall_header_card"),
        shape = RoundedCornerShape(26.dp),
        tintColor = glassTint,
        borderAccent = if (isFirewallActive) EmeraldPrimary else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.40f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFirewallActive) {
                                    Icons.Rounded.GppGood
                                } else {
                                    Icons.Rounded.Shield
                                },
                                contentDescription = if (isFirewallActive) {
                                    "Firewall active"
                                } else {
                                    "Firewall paused"
                                },
                                tint = if (isFirewallActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (isFirewallActive) "Firewall Active" else "Firewall Paused",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                !isFirewallActive -> "Enable to enforce Wi-Fi & Data rules"
                                activeBlockedNowCount > 0 ->
                                    "Blocking $activeBlockedNowCount app${if (activeBlockedNowCount == 1) "" else "s"} right now"
                                else -> "Monitoring • 0 apps blocked on current network"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isFirewallActive,
                    onCheckedChange = onToggleFirewall,
                    modifier = Modifier.testTag("master_firewall_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetworkStatusPill(
                    networkType = activeNetworkType,
                    modifier = Modifier.weight(1.25f)
                )
                StatMetricPill(
                    label = "Wi-Fi Off",
                    count = wifiBlockedCount,
                    accent = if (wifiBlockedCount > 0) BlockedRed else WifiAccent,
                    modifier = Modifier.weight(1f)
                )
                StatMetricPill(
                    label = "Data Off",
                    count = dataBlockedCount,
                    accent = if (dataBlockedCount > 0) BlockedRed else DataAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!isFirewallActive && (wifiBlockedCount > 0 || dataBlockedCount > 0)) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${wifiBlockedCount + dataBlockedCount} rule(s) queued across $totalAppsCount apps — turn on Firewall to apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NetworkStatusPill(
    networkType: ActiveNetworkType,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val icon: ImageVector
    val label: String
    val tint: Color
    when (networkType) {
        ActiveNetworkType.WIFI -> {
            icon = Icons.Rounded.Wifi
            label = "On Wi-Fi"
            tint = WifiAccent
        }
        ActiveNetworkType.MOBILE_DATA -> {
            icon = Icons.Rounded.SignalCellularAlt
            label = "Mobile Data"
            tint = DataAccent
        }
        ActiveNetworkType.OTHER -> {
            icon = Icons.Rounded.PublicOff
            label = "Other Net"
            tint = MaterialTheme.colorScheme.primary
        }
        ActiveNetworkType.NONE -> {
            icon = Icons.Rounded.SignalWifiOff
            label = "Offline"
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatMetricPill(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
