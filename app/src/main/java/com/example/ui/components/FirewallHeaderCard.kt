package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiLock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveNetworkType
import com.example.data.model.ConnectionDetails
import com.example.data.model.LiveNetworkTelemetry
import com.example.data.model.formatDataBytes
import com.example.data.model.formatSpeedBytesPerSec
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WifiAccent

@Composable
fun FirewallHeaderCard(
    isFirewallActive: Boolean,
    connectionDetails: ConnectionDetails,
    totalAppsCount: Int,
    wifiBlockedCount: Int,
    dataBlockedCount: Int,
    activeBlockedNowCount: Int,
    isAdBlockerEnabled: Boolean = false,
    blockedAdsCount: Long = 0L,
    liveTelemetry: LiveNetworkTelemetry = LiveNetworkTelemetry(),
    isGlobalWifiBlocked: Boolean = false,
    isHotspotProxyActive: Boolean = false,
    onToggleFirewall: (Boolean) -> Unit,
    onOpenConnectionInfo: () -> Unit,
    onOpenDnsProtection: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenAdBlocker: () -> Unit = {},
    onOpenNetworkMonitor: () -> Unit = {},
    onOpenHotspot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor by animateColorAsState(
        targetValue = if (isFirewallActive) primaryColor else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "firewallAccent"
    )
    val glassTint by animateColorAsState(
        targetValue = when {
            isFirewallActive && isDark -> primaryColor.copy(alpha = 0.16f)
            isFirewallActive -> primaryColor.copy(alpha = 0.12f)
            isDark -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
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
        borderAccent = if (isFirewallActive) primaryColor else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
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
                    val infiniteOrbit = rememberInfiniteTransition(label = "shieldReactorOrbit")
                    val orbitSweepState = infiniteOrbit.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3600, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shieldOrbitSweep"
                    )
                    val shieldSecondary = MaterialTheme.colorScheme.secondary

                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                        modifier = Modifier
                            .size(48.dp)
                            .drawBehind {
                                if (isFirewallActive) {
                                    val angle = orbitSweepState.value
                                    val pad = 4.dp.toPx()
                                    val arcSize = Size(size.width + pad * 2f, size.height + pad * 2f)
                                    val topLeft = Offset(-pad, -pad)
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.85f),
                                                shieldSecondary.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        ),
                                        startAngle = angle,
                                        sweepAngle = 125f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
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

            // Live Wi-Fi ON/OFF & Mobile Data ON/OFF radio pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioStatusChip(
                    label = "Wi-Fi",
                    stateText = when {
                        connectionDetails.isWifiConnected -> "ON • Active"
                        connectionDetails.isWifiRadioEnabled -> "ON • Idle"
                        else -> "OFF"
                    },
                    isOn = connectionDetails.isWifiRadioEnabled,
                    isActive = connectionDetails.isWifiConnected,
                    icon = if (connectionDetails.isWifiRadioEnabled) {
                        Icons.Rounded.Wifi
                    } else {
                        Icons.Rounded.WifiOff
                    },
                    accent = WifiAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wifi_radio_status_chip")
                )

                RadioStatusChip(
                    label = "Data",
                    stateText = when {
                        connectionDetails.isMobileDataConnected &&
                            connectionDetails.activeNetworkType == ActiveNetworkType.MOBILE_DATA -> "ON • Active"
                        connectionDetails.isMobileDataConnected || connectionDetails.isMobileDataEnabled -> "ON"
                        else -> "OFF"
                    },
                    isOn = connectionDetails.isMobileDataEnabled || connectionDetails.isMobileDataConnected,
                    isActive = connectionDetails.isMobileDataConnected &&
                        connectionDetails.activeNetworkType == ActiveNetworkType.MOBILE_DATA,
                    icon = if (connectionDetails.isMobileDataEnabled || connectionDetails.isMobileDataConnected) {
                        Icons.Rounded.SignalCellularAlt
                    } else {
                        Icons.Rounded.SignalCellularOff
                    },
                    accent = DataAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_radio_status_chip")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Blocked App & Ad Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                StatMetricPill(
                    label = if (isAdBlockerEnabled) "Ads Blk" else "Ad Guard",
                    count = blockedAdsCount.toInt(),
                    accent = if (isAdBlockerEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenAdBlocker)
                        .testTag("header_ad_guard_pill")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val quickConnectionSummary = buildString {
                when (connectionDetails.activeNetworkType) {
                    ActiveNetworkType.WIFI -> {
                        val parts = mutableListOf<String>()
                        connectionDetails.wifiBandLabel?.let { parts.add(it) }
                        connectionDetails.wifiLinkSpeedMbps?.let { parts.add("$it Mbps") }
                        connectionDetails.wifiRssiDbm?.let { parts.add("$it dBm") }
                        connectionDetails.ipv4Address?.let { parts.add(it) }
                        if (parts.isEmpty()) append("Wi-Fi connected • Tap for details")
                        else append(parts.joinToString(" • "))
                    }
                    ActiveNetworkType.MOBILE_DATA -> {
                        val parts = mutableListOf("Mobile Data")
                        connectionDetails.ipv4Address?.let { parts.add(it) }
                        if (connectionDetails.isMetered) parts.add("Metered")
                        append(parts.joinToString(" • "))
                    }
                    ActiveNetworkType.OTHER -> append("Active network • Tap for IP & DNS info")
                    ActiveNetworkType.NONE -> append("Offline • Tap for connection status")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenConnectionInfo)
                        .testTag("connection_info_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = quickConnectionSummary,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open Wi-Fi and connection info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                val isCfDnsActive = connectionDetails.isCloudflarePrivateDns
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCfDnsActive) {
                        EmeraldPrimary.copy(alpha = if (isDark) 0.22f else 0.18f)
                    } else if (isDark) {
                        Color.White.copy(alpha = 0.08f)
                    } else {
                        Color.White.copy(alpha = 0.65f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCfDnsActive) {
                            EmeraldPrimary.copy(alpha = 0.55f)
                        } else if (isDark) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.White.copy(alpha = 0.85f)
                        }
                    ),
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenDnsProtection)
                        .testTag("dns_protection_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = if (isCfDnsActive) EmeraldPrimary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCfDnsActive) "1.1.1.1 On" else "1.1.1.1 DNS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.52f else 0.78f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                    ),
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenSpeedTest)
                        .testTag("speed_test_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live System-Wide Network Monitor Quick Strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EmeraldPrimary.copy(alpha = if (isDark) 0.14f else 0.10f),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.36f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenNetworkMonitor)
                    .testTag("header_live_monitor_strip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MonitorHeart,
                            contentDescription = "Open live network monitor",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "↓ ${formatSpeedBytesPerSec(liveTelemetry.downloadBytesPerSec)}  •  ↑ ${formatSpeedBytesPerSec(liveTelemetry.uploadBytesPerSec)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today ${formatDataBytes(liveTelemetry.todayTotalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        maxLines = 1
                    )
                }
            }

            if (isGlobalWifiBlocked || isHotspotProxyActive) {
                Spacer(modifier = Modifier.height(8.dp))
                val alertColor = if (isGlobalWifiBlocked) BlockedRed else WifiAccent
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = alertColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenHotspot)
                        .testTag("header_hotspot_wifi_status_strip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isGlobalWifiBlocked) {
                                    Icons.Rounded.WifiLock
                                } else {
                                    Icons.Rounded.WifiTethering
                                },
                                contentDescription = null,
                                tint = alertColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isGlobalWifiBlocked && isHotspotProxyActive ->
                                        "Global Wi-Fi Lock & Hotspot Proxy Active"
                                    isGlobalWifiBlocked ->
                                        "Global Wi-Fi Lock Active • All app Wi-Fi blocked"
                                    else ->
                                        "Hotspot Proxy Sharing Active"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = alertColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open Hotspot and Wi-Fi automation",
                            tint = alertColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
private fun RadioStatusChip(
    label: String,
    stateText: String,
    isOn: Boolean,
    isActive: Boolean,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val highlight = if (isActive) EmeraldPrimary else accent
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isOn) {
            highlight.copy(alpha = if (isDark) 0.16f else 0.14f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.06f)
        } else {
            Color.White.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            1.dp,
            if (isOn) {
                highlight.copy(alpha = 0.45f)
            } else if (isDark) {
                Color.White.copy(alpha = 0.12f)
            } else {
                Color.White.copy(alpha = 0.80f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$label $stateText",
                tint = if (isOn) highlight else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $stateText",
                style = MaterialTheme.typography.labelMedium,
                color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val isDark = LocalIsDarkTheme.current
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
