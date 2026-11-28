package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.HotspotClientInfo
import com.aistudio.netgate.data.model.HotspotFilterProfile
import com.aistudio.netgate.data.model.HotspotProxyRuntimeState
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber

/**
 * Hotspot security controls: PAC/QR setup, tunnel sharing, new-device approval and per-device
 * filtering profiles plus speed limits.
 */
@Composable
fun HotspotSecurityCard(
    hotspotState: HotspotProxyRuntimeState,
    advanced: AdvancedPreferencesState,
    onToggleShareActiveTunnel: (Boolean) -> Unit,
    onToggleRequireApproval: (Boolean) -> Unit,
    onApproveClient: (String) -> Unit,
    onBlockClient: (String) -> Unit,
    onSetClientProfile: (String, HotspotFilterProfile) -> Unit,
    onSetClientSpeedLimit: (String, Long) -> Unit
) {
    val pacUrl = "http://${hotspotState.suggestedProxyHost}:${hotspotState.activePort}/proxy.pac"

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_security_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hotspot Security & Device Rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            QrCodeCard(
                title = "Quick setup QR",
                subtitle = "Scan from a phone or add the PAC URL on a laptop/TV",
                content = pacUrl
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
                icon = Icons.Rounded.WifiTethering,
                title = "Share NetGate VPN Tunnel",
                subtitle = if (hotspotState.isNetGateVpnActive) {
                    "Tethered traffic exits through the active VPN Guard tunnel"
                } else {
                    "Connect a VPN Guard profile to route tethered traffic through it"
                },
                checked = advanced.hotspotShareActiveTunnel,
                onCheckedChange = onToggleShareActiveTunnel,
                testTag = "hotspot_share_tunnel_toggle"
            )

            ToggleRow(
                icon = Icons.Rounded.Devices,
                title = "New-Device Approval",
                subtitle = "Hold unknown devices until you allow them, and get an alert",
                checked = advanced.hotspotRequireApproval,
                onCheckedChange = onToggleRequireApproval,
                testTag = "hotspot_require_approval_toggle"
            )

            if (hotspotState.clients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Per-device rules",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                hotspotState.clients.forEach { client ->
                    HotspotClientRuleRow(
                        client = client,
                        requireApproval = advanced.hotspotRequireApproval,
                        isApproved = client.clientIp in advanced.hotspotApprovedClientIps,
                        onApprove = { onApproveClient(client.clientIp) },
                        onBlock = { onBlockClient(client.clientIp) },
                        onProfileChange = { profile -> onSetClientProfile(client.clientIp, profile) },
                        onSpeedChange = { limit -> onSetClientSpeedLimit(client.clientIp, limit) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Connected devices appear here once they use the proxy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun HotspotClientRuleRow(
    client: HotspotClientInfo,
    requireApproval: Boolean,
    isApproved: Boolean,
    onApprove: () -> Unit,
    onBlock: () -> Unit,
    onProfileChange: (HotspotFilterProfile) -> Unit,
    onSpeedChange: (Long) -> Unit
) {
    val pending = requireApproval && !isApproved
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            when {
                pending -> WarningAmber.copy(alpha = 0.6f)
                client.isManuallyBlocked -> BlockedRed.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(client.clientIp)
                            append(" • ")
                            append(client.profile.label)
                            append(" • ")
                            append(client.speedLimitLabel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (pending) {
                    FilledTonalButton(onClick = onApprove) {
                        Text("Allow")
                    }
                } else if (client.isManuallyBlocked) {
                    FilledTonalButton(onClick = onApprove) {
                        Text("Unblock")
                    }
                } else {
                    FilledTonalButton(onClick = onBlock) {
                        Text("Block")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val profiles = HotspotFilterProfile.entries
            GlassChipFlowGroup(
                items = profiles.map { profile ->
                    GlassTabItem(label = profile.label)
                },
                selectedIndex = profiles.indexOf(client.profile).takeIf { it >= 0 },
                onSelect = { index -> onProfileChange(profiles[index]) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            val speedOptions = listOf(0L, 2L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024)
            GlassChipFlowGroup(
                items = speedOptions.map { limit ->
                    GlassTabItem(
                        label = if (limit <= 0L) "Unlimited" else formatDataBytes(limit) + "/s"
                    )
                },
                selectedIndex = speedOptions.indexOf(client.speedLimitBytesPerSec).takeIf { it >= 0 },
                onSelect = { index -> onSpeedChange(speedOptions[index]) }
            )
        }
    }
}
