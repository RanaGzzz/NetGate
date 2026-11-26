package com.aistudio.netgate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WifiAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionInfoSheet(
    details: ConnectionDetails,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        FrostedSheetContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .testTag("connection_info_sheet")
            ) {
            FrostedSheetDragHandle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Connection & Wi-Fi Info",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (details.activeNetworkType) {
                            ActiveNetworkType.WIFI -> "Active transport: Wi-Fi"
                            ActiveNetworkType.MOBILE_DATA -> "Active transport: Mobile Data"
                            ActiveNetworkType.OTHER -> "Active transport: Ethernet / Other"
                            ActiveNetworkType.NONE -> "No active internet transport"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_connection_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh connection info"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Radio ON/OFF Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioStateCard(
                    title = "Wi-Fi",
                    status = when {
                        details.isWifiConnected -> "ON • Connected"
                        details.isWifiRadioEnabled -> "ON • Not Connected"
                        else -> "OFF"
                    },
                    isOn = details.isWifiRadioEnabled,
                    isConnected = details.isWifiConnected,
                    icon = if (details.isWifiRadioEnabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    accent = WifiAccent,
                    modifier = Modifier.weight(1f)
                )

                RadioStateCard(
                    title = "Mobile Data",
                    status = when {
                        details.isMobileDataConnected && details.activeNetworkType == ActiveNetworkType.MOBILE_DATA ->
                            "ON • Active"
                        details.isMobileDataConnected -> "ON • Standby"
                        details.isMobileDataEnabled -> "ON • Idle"
                        else -> "OFF"
                    },
                    isOn = details.isMobileDataEnabled || details.isMobileDataConnected,
                    isConnected = details.isMobileDataConnected,
                    icon = if (details.isMobileDataEnabled || details.isMobileDataConnected) {
                        Icons.Rounded.SignalCellularAlt
                    } else {
                        Icons.Rounded.SignalCellularOff
                    },
                    accent = DataAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            // Wi-Fi Telemetry Section
            if (details.isWifiConnected) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle(icon = Icons.Rounded.Wifi, title = "Wi-Fi Details")
                Spacer(modifier = Modifier.height(8.dp))
                FrostedSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        details.wifiStandardLabel?.let {
                            InfoRow("Standard", it)
                        }
                        details.wifiBandLabel?.let { band ->
                            val freqText = details.wifiFrequencyMhz?.let { "$band ($it MHz)" } ?: band
                            InfoRow("Frequency Band", freqText)
                        }
                        if (details.wifiRssiDbm != null) {
                            val sigText = "${details.wifiRssiDbm} dBm" +
                                (details.wifiSignalLabel?.let { " ($it)" } ?: "")
                            InfoRow("Signal Strength", sigText)
                        }
                        if (details.wifiLinkSpeedMbps != null) {
                            val speedText = buildString {
                                append("Tx ${details.wifiLinkSpeedMbps} Mbps")
                                details.wifiRxLinkSpeedMbps?.let { append(" / Rx $it Mbps") }
                            }
                            InfoRow("Link Speed", speedText)
                        }
                    }
                }
            }

            // Current IP & Routing Info
            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle(icon = Icons.Rounded.Router, title = "Current Network Details")
            Spacer(modifier = Modifier.height(8.dp))
            FrostedSurface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    InfoRow("Interface", details.interfaceName ?: "Unavailable")
                    InfoRow("IPv4 Address", details.ipv4Address ?: "Unavailable")
                    details.ipv6Address?.let { InfoRow("IPv6 Address", it) }
                    InfoRow("Gateway / Router", details.gatewayAddress ?: "Unavailable")
                    InfoRow(
                        "DNS Servers",
                        if (details.dnsServers.isNotEmpty()) details.dnsServers.joinToString(", ") else "Unavailable"
                    )
                    InfoRow(
                        "Private DNS",
                        when {
                            details.isCloudflarePrivateDns ->
                                "Cloudflare (${details.privateDnsHostname ?: "1.1.1.1"})"
                            details.isPrivateDnsActive && !details.privateDnsHostname.isNullOrBlank() ->
                                details.privateDnsHostname
                            details.isPrivateDnsActive -> "Automatic (Encrypted)"
                            else -> "Off / ISP Default"
                        }
                    )
                    if (details.downstreamKbps > 0 || details.upstreamKbps > 0) {
                        InfoRow(
                            "Est. Bandwidth",
                            "Down ${formatKbps(details.downstreamKbps)} / Up ${formatKbps(details.upstreamKbps)}"
                        )
                    }
                    InfoRow("Metered", if (details.isMetered) "Yes (Metered)" else "No (Unmetered)")
                    InfoRow(
                        "Internet Validated",
                        if (details.isValidated) "Reachable" else "Pending / Offline"
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RadioStateCard(
    title: String,
    status: String,
    isOn: Boolean,
    isConnected: Boolean,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val activeAccent = when {
        isConnected -> EmeraldPrimary
        isOn -> accent
        else -> MaterialTheme.colorScheme.outline
    }
    FrostedSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tintColor = if (isOn) activeAccent.copy(alpha = 0.14f) else null,
        borderAccent = if (isOn) activeAccent else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isOn) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOn) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) {
        "${kbps / 1000} Mbps"
    } else {
        "$kbps Kbps"
    }
}
