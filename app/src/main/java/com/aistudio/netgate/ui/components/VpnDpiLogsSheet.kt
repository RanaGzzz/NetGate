package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.VpnDpiLogCategory
import com.aistudio.netgate.data.model.VpnDpiLogEntry
import com.aistudio.netgate.data.model.VpnDpiLogSeverity
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardRuntimeState
import com.aistudio.netgate.service.VpnRuntimeState
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnDpiLogsSheet(
    logs: List<VpnDpiLogEntry>,
    initialCategory: VpnDpiLogCategory = VpnDpiLogCategory.ALL,
    vpnRuntimeState: VpnRuntimeState = VpnRuntimeState(),
    vpnGuardRuntimeState: VpnGuardRuntimeState = VpnGuardRuntimeState(),
    onClearLogs: () -> Unit,
    onCopyLogs: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themeStyle = LocalThemeStyle.current

    var selectedCategory by rememberSaveable(initialCategory) {
        mutableStateOf(initialCategory)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        logs.filter { entry ->
            val matchesCat = selectedCategory == VpnDpiLogCategory.ALL || entry.category == selectedCategory
            val matchesQuery = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.detail.lowercase().contains(q) ||
                entry.category.label.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(
            alpha = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 0.96f else 0.94f
        ),
        modifier = Modifier.testTag("vpn_dpi_logs_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VPN & DPI Bypass Logs",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${logs.size} event${if (logs.size == 1) "" else "s"} recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val formatted = formatLogEntriesForClipboard(filteredLogs)
                            copyToClipboard(context, "NetGate VPN & DPI Logs", formatted)
                            onCopyLogs("Copied ${filteredLogs.size} VPN & DPI log entries")
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("vpn_dpi_logs_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy logs",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onClearLogs,
                        enabled = logs.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("vpn_dpi_logs_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear logs",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact Live Runtime Snapshot Strip
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val guardAccent = when (vpnGuardRuntimeState.connectionState) {
                        VpnGuardConnectionState.CONNECTED -> EmeraldPrimary
                        VpnGuardConnectionState.ERROR -> BlockedRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val dpiAccent = if (vpnRuntimeState.dpiBypassMode != DpiBypassMode.OFF) {
                        WifiAccent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val fwAccent = if (vpnRuntimeState.isServiceRunning) {
                        EmeraldPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    MiniStatusPill(
                        label = "VPN Guard",
                        value = vpnGuardRuntimeState.connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
                        accent = guardAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatusPill(
                        label = "DPI Mode",
                        value = "${vpnRuntimeState.dpiBypassMode.title} (${vpnRuntimeState.dpiBypassedConnectionsCount})",
                        accent = dpiAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatusPill(
                        label = "Firewall TUN",
                        value = if (vpnRuntimeState.isServiceRunning) "Running" else "Idle",
                        accent = fwAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Input
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search VPN Guard, DPI bypass, handshake, QUIC…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vpn_dpi_log_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VpnDpiLogCategory.entries) { category ->
                    val count = if (category == VpnDpiLogCategory.ALL) {
                        logs.size
                    } else {
                        logs.count { it.category == category }
                    }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = "${category.label} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("vpn_dpi_log_filter_${category.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredLogs.isEmpty()) {
                FrostedSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vpn_dpi_logs_empty")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (logs.isEmpty()) "No VPN or DPI events yet" else "No matching log entries",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (logs.isEmpty()) {
                                "Connect VPN Guard, activate NetGate Firewall, or switch DPI Bypass modes to stream live events here."
                            } else {
                                "Try clearing your search query or selecting All categories."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .testTag("vpn_dpi_logs_list")
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        VpnDpiLogRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnDpiLogRow(entry: VpnDpiLogEntry) {
    val accent = when (entry.severity) {
        VpnDpiLogSeverity.SUCCESS -> EmeraldPrimary
        VpnDpiLogSeverity.WARNING -> WarningAmber
        VpnDpiLogSeverity.ERROR -> BlockedRed
        VpnDpiLogSeverity.INFO -> WifiAccent
    }
    val vpnTunnelIcon = ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield)
    val icon = when (entry.category) {
        VpnDpiLogCategory.VPN_GUARD -> vpnTunnelIcon
        VpnDpiLogCategory.DPI_BYPASS -> Icons.Rounded.EnhancedEncryption
        VpnDpiLogCategory.VPN_TUNNEL, VpnDpiLogCategory.ALL -> Icons.Rounded.Shield
    }

    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = accent.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_dpi_log_row_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = entry.category.label,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DataAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = DataAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${entry.severity.label} • ${formatLogTimestamp(entry.timestampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun MiniStatusPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatLogTimestamp(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

private fun formatLogEntriesForClipboard(entries: List<VpnDpiLogEntry>): String {
    if (entries.isEmpty()) return "No logs"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return entries.joinToString("\n") {
        "[${fmt.format(Date(it.timestampMs))}] [${it.category.label}] [${it.severity.label}] ${it.title} — ${it.detail}"
    }
}
