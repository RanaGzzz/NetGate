package com.aistudio.netgate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent

private val CHUNK_SIZE_OPTIONS = listOf(2, 3, 4, 8, 16)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DpiBypassShieldCard(
    dpiBypassMode: DpiBypassMode,
    dpiFragmentChunkSize: Int,
    dpiIdleKeepAliveEnabled: Boolean,
    blockQuicForDpiBypass: Boolean,
    antiDnsPoisonEnabled: Boolean,
    dnsTransportMode: DnsTransportMode,
    dpiBypassedConnectionsCount: Long,
    dpiAutopilotLearnedCount: Int,
    antiDnsPoisonBlockedCount: Long,
    quicStrippedCount: Long,
    isFirewallActive: Boolean,
    onSelectDpiMode: (DpiBypassMode) -> Unit,
    onSelectChunkSize: (Int) -> Unit,
    onToggleIdleKeepAlive: (Boolean) -> Unit,
    onToggleBlockQuic: (Boolean) -> Unit,
    onToggleAntiDnsPoison: (Boolean) -> Unit,
    onSelectDnsTransportMode: (DnsTransportMode) -> Unit,
    onFlushCache: () -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onOpenVpnDpiLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isEnabled = dpiBypassMode != DpiBypassMode.OFF
    val targetAccent = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isFirewallActive -> WifiAccent
        else -> WarningAmber
    }
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "dpi_bypass_accent"
    )

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        tintColor = if (isEnabled) accent.copy(alpha = 0.08f) else null,
        borderAccent = if (isEnabled) accent.copy(alpha = 0.56f) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dpi_bypass_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (dpiBypassMode == DpiBypassMode.AUTOPILOT) {
                                    Icons.Rounded.AutoFixHigh
                                } else {
                                    ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield)
                                },
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "DPI Bypass Shield",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isEnabled) dpiBypassMode.title.uppercase() else "OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = when {
                                !isEnabled -> "Rootless TLS SNI fragmentation & anti-DPI engine"
                                isFirewallActive -> dpiBypassMode.subtitle
                                else -> "Ready • Activate NetGate Firewall to intercept DPI"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled && !isFirewallActive) {
                                WarningAmber
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        val nextMode = if (checked) DpiBypassMode.AUTOPILOT else DpiBypassMode.OFF
                        onSelectDpiMode(nextMode)
                        if (checked && !isFirewallActive) {
                            onRequestEnableFirewall()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = WifiAccent
                    ),
                    modifier = Modifier.testTag("dpi_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Metric Live Telemetry Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DpiMetricPill(
                    label = "DPI Bypassed",
                    value = dpiBypassedConnectionsCount.toString(),
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                DpiMetricPill(
                    label = "Learned Hosts",
                    value = dpiAutopilotLearnedCount.toString(),
                    accent = WifiAccent,
                    modifier = Modifier.weight(1f)
                )
                DpiMetricPill(
                    label = "Spoof / QUIC",
                    value = "${antiDnsPoisonBlockedCount + quicStrippedCount}",
                    accent = if (antiDnsPoisonBlockedCount > 0L) BlockedRed else WarningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isEnabled && !isFirewallActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestEnableFirewall,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dpi_enable_firewall_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activate Firewall & DPI Bypass")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DPI Reshaping Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DpiBypassMode.entries.forEach { mode ->
                    val selected = dpiBypassMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelectDpiMode(mode)
                            if (mode != DpiBypassMode.OFF && !isFirewallActive) {
                                onRequestEnableFirewall()
                            }
                        },
                        label = {
                            Text(
                                text = mode.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WifiAccent.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dpi_mode_${mode.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TLS Fragment Chunk Size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CHUNK_SIZE_OPTIONS.forEach { size ->
                            val selected = dpiFragmentChunkSize == size
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectChunkSize(size) },
                                label = { Text("${size}B") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("dpi_chunk_$size")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Resilient DNS Transport",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dnsTransportMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DnsTransportMode.entries.forEach { mode ->
                    val selected = dnsTransportMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDnsTransportMode(mode) },
                        label = { Text(mode.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dns_transport_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DpiToggleRow(
                    title = "Block QUIC (HTTP/3 Downgrade)",
                    subtitle = "Strips HTTPS/SVCB DNS hints (${quicStrippedCount} downgraded) so browsers fall back to TCP SNI split",
                    checked = blockQuicForDpiBypass,
                    onCheckedChange = onToggleBlockQuic,
                    switchTestTag = "block_quic_switch"
                )
                DpiToggleRow(
                    title = "Anti-DNS Poisoning Guard",
                    subtitle = "Rejects forged loopback/sinkhole DNS IPs (${antiDnsPoisonBlockedCount} caught) & switches to encrypted/TCP DNS",
                    checked = antiDnsPoisonEnabled,
                    onCheckedChange = onToggleAntiDnsPoison,
                    switchTestTag = "anti_dns_poison_switch"
                )
                DpiToggleRow(
                    title = "Messaging Idle Keep-Alive",
                    subtitle = "Keeps chat & social sockets alive (SO_KEEPALIVE) during long idle pauses",
                    checked = dpiIdleKeepAliveEnabled,
                    onCheckedChange = onToggleIdleKeepAlive,
                    switchTestTag = "dpi_idle_keep_alive_switch"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onFlushCache,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("flush_dpi_cache_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flush DNS & Reset Autopilot")
            }

            if (onOpenVpnDpiLogs != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenVpnDpiLogs,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dpi_open_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View DPI Bypass & VPN Logs")
                }
            }
        }
    }
}

@Composable
private fun DpiMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
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
private fun DpiToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (checked) WifiAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(switchTestTag)
            )
        }
    }
}
