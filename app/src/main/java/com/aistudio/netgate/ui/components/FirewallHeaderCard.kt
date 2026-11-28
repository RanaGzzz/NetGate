package com.aistudio.netgate.ui.components

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.data.model.formatSpeedBytesPerSec
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalBlurEngineConfig
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.WifiAccent

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
    isVpnGuardActive: Boolean = false,
    vpnGuardProfileLabel: String = "",
    blockedWebsitesCount: Int = 0,
    websiteRulesBadgeText: String = "$blockedWebsitesCount Blocked",
    dnsSummaryLabel: String = "1.1.1.1",
    /** Short self-healing reason when the tunnel failed to establish; null while healthy. */
    tunnelError: String? = null,
    onToggleFirewall: (Boolean) -> Unit,
    onOpenConnectionInfo: () -> Unit,
    onOpenDnsProtection: () -> Unit,
    onOpenSpeedTest: () -> Unit = {},
    onOpenAdBlocker: () -> Unit = {},
    onOpenWebsitesTab: () -> Unit = {},
    onOpenNetworkMonitor: () -> Unit = {},
    onOpenHotspot: () -> Unit = {},
    onOpenVpnGuard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val themeStyle = LocalThemeStyle.current
    val blurConfig = LocalBlurEngineConfig.current
    val motionSpec = LocalExpressiveMotion.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor by animateColorAsState(
        targetValue = if (isFirewallActive) primaryColor else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "firewallAccent"
    )
    val baseSurface = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val activeAlpha = when {
        themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> if (isDark) 0.92f else 0.95f
        themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isDark) 0.84f else 0.90f
        blurConfig.style == BlurEngineStyle.OFF -> if (isDark) 0.88f else 0.92f
        else -> if (isDark) 0.54f else 0.70f
    }
    val pausedAlpha = when {
        themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> if (isDark) 0.90f else 0.94f
        themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isDark) 0.84f else 0.90f
        blurConfig.style == BlurEngineStyle.OFF -> if (isDark) 0.88f else 0.92f
        else -> if (isDark) 0.62f else 0.74f
    }
    val glassTint by animateColorAsState(
        targetValue = if (isFirewallActive) {
            lerp(baseSurface, primaryColor, if (isDark) 0.24f else 0.14f).copy(alpha = activeAlpha)
        } else {
            baseSurface.copy(alpha = pausedAlpha)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "firewallGlassTint"
    )

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("firewall_header_card"),
        shape = RoundedCornerShape((24 + themeStyle.cornerRadiusBoostDp).dp),
        tintColor = glassTint,
        borderAccent = if (isFirewallActive) primaryColor.copy(alpha = 0.45f) else null,
        enableLiquidTouch = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = motionSpec.spatialDampingRatio,
                        stiffness = motionSpec.spatialStiffness
                    )
                )
                .padding(16.dp)
        ) {
            // 1. Header: Shield + Firewall Active + Blocked Apps + Master Switch
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
                    val statusColor = if (isFirewallActive) EmeraldPrimary else BlockedRed

                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.38f)),
                        modifier = Modifier
                            .size(44.dp)
                            .drawBehind {
                                val angle = orbitSweepState.value
                                val pad = 3.dp.toPx()
                                val arcSize = Size(size.width + pad * 2f, size.height + pad * 2f)
                                val topLeft = Offset(-pad, -pad)
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            statusColor.copy(alpha = if (isFirewallActive) 0.90f else 0.75f),
                                            statusColor.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    ),
                                    startAngle = angle,
                                    sweepAngle = if (isFirewallActive) 125f else 95f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
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
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isFirewallActive) "Firewall Active" else "Firewall Paused",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = when {
                                !isFirewallActive -> "Enable to enforce rules"
                                else -> "$activeBlockedNowCount ${if (activeBlockedNowCount == 1) "app" else "apps"} blocked"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        checkedTrackColor = EmeraldPrimary,
                        uncheckedThumbColor = BlockedRed,
                        uncheckedTrackColor = BlockedRed.copy(alpha = 0.28f),
                        uncheckedBorderColor = BlockedRed.copy(alpha = 0.50f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Radio Status Pills (Wi-Fi ON / Data OFF)
            val isWifiOn = connectionDetails.isWifiRadioEnabled || connectionDetails.isWifiConnected
            val isDataOn = connectionDetails.isMobileDataEnabled || connectionDetails.isMobileDataConnected
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioStatusChip(
                    label = if (isWifiOn) "Wi-Fi ON" else "Wi-Fi OFF",
                    isOn = isWifiOn,
                    icon = if (isWifiOn) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    activeAccent = primaryColor,
                    inactiveAccent = BlockedRed,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wifi_radio_status_chip")
                )

                RadioStatusChip(
                    label = if (isDataOn) "Data ON" else "Data OFF",
                    isOn = isDataOn,
                    icon = if (isDataOn) Icons.Rounded.SignalCellularAlt else Icons.Rounded.SignalCellularOff,
                    activeAccent = primaryColor,
                    inactiveAccent = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_radio_status_chip")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Three Stacked Metric Tiles (WI-FI OFF / DATA OFF / ADS BLK)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMetricTile(
                    label = "WI-FI OFF",
                    count = wifiBlockedCount,
                    accent = if (wifiBlockedCount > 0) BlockedRed else primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatMetricTile(
                    label = "DATA OFF",
                    count = dataBlockedCount,
                    accent = if (dataBlockedCount > 0) BlockedRed else primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatMetricTile(
                    label = if (isAdBlockerEnabled) "ADS BLK" else "AD GUARD",
                    count = blockedAdsCount.toInt(),
                    accent = if (isAdBlockerEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenAdBlocker)
                        .testTag("header_ad_guard_pill")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Live Network Speed & Today Usage Strip
            val innerTileColor = if (isDark) {
                Color.Black.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f)
            }
            val innerTileBorder = if (isDark) {
                Color.White.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = innerTileColor,
                border = BorderStroke(1.dp, innerTileBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenNetworkMonitor)
                    .testTag("header_live_monitor_strip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val speedText =
                        "↓ ${formatSpeedBytesPerSec(liveTelemetry.downloadBytesPerSec)}  ↑ ${formatSpeedBytesPerSec(liveTelemetry.uploadBytesPerSec)}"
                    val usageText = "${formatDataBytes(liveTelemetry.todayTotalBytes)} Today"
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = usageText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primaryColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Three Equal Quick Action Pills: Connection Band | DNS | Website Rules (Blocked)
            val conciseConnectionLabel = when (connectionDetails.activeNetworkType) {
                ActiveNetworkType.WIFI -> connectionDetails.wifiBandLabel ?: "Wi-Fi"
                ActiveNetworkType.MOBILE_DATA -> "Mobile"
                ActiveNetworkType.OTHER -> "Active"
                ActiveNetworkType.NONE -> "Offline"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickInfoPill(
                    text = conciseConnectionLabel,
                    showChevron = true,
                    highlighted = false,
                    accent = primaryColor,
                    onClick = onOpenConnectionInfo,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("connection_info_button")
                )

                QuickInfoPill(
                    text = dnsSummaryLabel,
                    showChevron = true,
                    highlighted = connectionDetails.isCloudflarePrivateDns,
                    accent = primaryColor,
                    onClick = onOpenDnsProtection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dns_protection_button")
                )

                QuickInfoPill(
                    text = websiteRulesBadgeText,
                    showChevron = false,
                    highlighted = true,
                    accent = primaryColor,
                    onClick = onOpenWebsitesTab,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("header_websites_blocked_pill")
                )
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

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Compact Inline VPN Guard Status Link
            val vpnAccent = if (isVpnGuardActive) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenVpnGuard)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("header_vpn_guard_strip"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield),
                    contentDescription = "Open VPN Guard",
                    tint = vpnAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isVpnGuardActive) {
                        "VPN Guard • ${vpnGuardProfileLabel.ifBlank { "WireGuard & WARP" }} Active"
                    } else {
                        "VPN Guard • WireGuard & WARP"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isVpnGuardActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = vpnAccent,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (!tunnelError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tunnelError,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("header_tunnel_error")
                )
            }

            if (!isFirewallActive && (wifiBlockedCount > 0 || dataBlockedCount > 0)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${wifiBlockedCount + dataBlockedCount} rule(s) queued across $totalAppsCount apps — turn on Firewall to apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryColor
                )
            }
        }
    }
}

@Composable
private fun RadioStatusChip(
    label: String,
    isOn: Boolean,
    icon: ImageVector,
    activeAccent: Color,
    inactiveAccent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isOn) {
            activeAccent.copy(alpha = if (isDark) 0.16f else 0.14f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.05f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f)
        },
        border = BorderStroke(
            1.dp,
            if (isOn) {
                activeAccent.copy(alpha = 0.55f)
            } else if (isDark) {
                Color.White.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
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
                contentDescription = label,
                tint = if (isOn) activeAccent else inactiveAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatMetricTile(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) {
            Color.Black.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f)
        },
        border = BorderStroke(
            1.dp,
            if (isDark) {
                Color.White.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickInfoPill(
    text: String,
    showChevron: Boolean,
    highlighted: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            highlighted -> accent.copy(alpha = if (isDark) 0.14f else 0.12f)
            isDark -> Color.Black.copy(alpha = 0.20f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f)
        },
        border = BorderStroke(
            1.dp,
            when {
                highlighted -> accent.copy(alpha = 0.55f)
                isDark -> Color.White.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
        ),
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (highlighted) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showChevron) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
