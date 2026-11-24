package com.aistudio.netgate.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiLock
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.HotspotClientInfo
import com.aistudio.netgate.data.model.HotspotProxyRuntimeState
import com.aistudio.netgate.data.model.WifiIdleStatus
import com.aistudio.netgate.data.model.formatCountdownTimer
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.service.WifiAutomationManager
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent

@Composable
fun HotspotAndWifiContent(
    preferences: FirewallPreferencesState,
    hotspotState: HotspotProxyRuntimeState,
    connectionDetails: ConnectionDetails,
    idleStatus: WifiIdleStatus,
    nowMillis: Long,
    onToggleHotspotProxy: (Boolean) -> Unit,
    onSelectHotspotPort: (Int) -> Unit,
    onToggleApplyNetGateFilters: (Boolean) -> Unit,
    onToggleClientBlocked: (clientIp: String, blocked: Boolean) -> Unit,
    onSetClientDataCap: (clientIp: String, capBytes: Long) -> Unit,
    onSetClientLabel: (clientIp: String, label: String) -> Unit,
    onResetClientStats: (clientIp: String) -> Unit,
    onClearAllHotspotStats: () -> Unit,
    onRefreshHotspot: () -> Unit,
    onCopiedEndpoint: (String) -> Unit,
    onToggleGlobalWifiLock: (Boolean) -> Unit,
    onScheduleWifiTimer: (minutes: Int) -> Unit,
    onCancelWifiTimer: () -> Unit,
    onToggleAutoIdleLock: (Boolean) -> Unit,
    onSelectIdleMinutes: (Int) -> Unit,
    onToggleOpenPanelNotify: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingClient by remember { mutableStateOf<HotspotClientInfo?>(null) }
    var showCustomPortDialog by remember { mutableStateOf(false) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("hotspot_wifi_screen")
    ) {
        val isWide = maxWidth >= 820.dp
        val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HotspotProxyCard(
                        preferences = preferences,
                        hotspotState = hotspotState,
                        onToggleHotspotProxy = onToggleHotspotProxy,
                        onSelectHotspotPort = onSelectHotspotPort,
                        onOpenCustomPortDialog = { showCustomPortDialog = true },
                        onToggleApplyNetGateFilters = onToggleApplyNetGateFilters,
                        onRefreshHotspot = onRefreshHotspot,
                        onCopyEndpoint = { endpoint ->
                            copyText(context, "NetGate Hotspot Proxy", endpoint)
                            onCopiedEndpoint(endpoint)
                        },
                        onOpenHotspotSettings = {
                            runCatching { context.startActivity(WifiAutomationManager.buildHotspotSettingsIntent()) }
                        }
                    )

                    HotspotClientsCard(
                        hotspotState = hotspotState,
                        onToggleClientBlocked = onToggleClientBlocked,
                        onEditClient = { editingClient = it },
                        onClearAllStats = onClearAllHotspotStats
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WifiAutomationCard(
                        preferences = preferences,
                        connectionDetails = connectionDetails,
                        idleStatus = idleStatus,
                        nowMillis = nowMillis,
                        onToggleGlobalWifiLock = onToggleGlobalWifiLock,
                        onOpenWifiPanel = {
                            runCatching { context.startActivity(WifiAutomationManager.buildWifiPanelIntent(context)) }
                        },
                        onScheduleWifiTimer = onScheduleWifiTimer,
                        onOpenCustomTimerDialog = { showCustomTimerDialog = true },
                        onCancelWifiTimer = onCancelWifiTimer,
                        onToggleAutoIdleLock = onToggleAutoIdleLock,
                        onSelectIdleMinutes = onSelectIdleMinutes,
                        onToggleOpenPanelNotify = onToggleOpenPanelNotify
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = navInsetBottom + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HotspotProxyCard(
                        preferences = preferences,
                        hotspotState = hotspotState,
                        onToggleHotspotProxy = onToggleHotspotProxy,
                        onSelectHotspotPort = onSelectHotspotPort,
                        onOpenCustomPortDialog = { showCustomPortDialog = true },
                        onToggleApplyNetGateFilters = onToggleApplyNetGateFilters,
                        onRefreshHotspot = onRefreshHotspot,
                        onCopyEndpoint = { endpoint ->
                            copyText(context, "NetGate Hotspot Proxy", endpoint)
                            onCopiedEndpoint(endpoint)
                        },
                        onOpenHotspotSettings = {
                            runCatching { context.startActivity(WifiAutomationManager.buildHotspotSettingsIntent()) }
                        }
                    )

                    HotspotClientsCard(
                        hotspotState = hotspotState,
                        onToggleClientBlocked = onToggleClientBlocked,
                        onEditClient = { editingClient = it },
                        onClearAllStats = onClearAllHotspotStats
                    )

                    WifiAutomationCard(
                        preferences = preferences,
                        connectionDetails = connectionDetails,
                        idleStatus = idleStatus,
                        nowMillis = nowMillis,
                        onToggleGlobalWifiLock = onToggleGlobalWifiLock,
                        onOpenWifiPanel = {
                            runCatching { context.startActivity(WifiAutomationManager.buildWifiPanelIntent(context)) }
                        },
                        onScheduleWifiTimer = onScheduleWifiTimer,
                        onOpenCustomTimerDialog = { showCustomTimerDialog = true },
                        onCancelWifiTimer = onCancelWifiTimer,
                        onToggleAutoIdleLock = onToggleAutoIdleLock,
                        onSelectIdleMinutes = onSelectIdleMinutes,
                        onToggleOpenPanelNotify = onToggleOpenPanelNotify
                    )
                }
            }
        }
    }

    editingClient?.let { client ->
        HotspotClientConfigDialog(
            client = client,
            onSave = { label, capBytes ->
                onSetClientLabel(client.clientIp, label)
                onSetClientDataCap(client.clientIp, capBytes)
                editingClient = null
            },
            onResetStats = {
                onResetClientStats(client.clientIp)
                editingClient = null
            },
            onDismiss = { editingClient = null }
        )
    }

    if (showCustomPortDialog) {
        CustomNumberDialog(
            title = "Hotspot Proxy Port",
            label = "Port (1024–65535)",
            initialValue = preferences.hotspotProxyPort.toString(),
            onConfirm = { value ->
                value.toIntOrNull()?.let { onSelectHotspotPort(it) }
                showCustomPortDialog = false
            },
            onDismiss = { showCustomPortDialog = false }
        )
    }

    if (showCustomTimerDialog) {
        CustomNumberDialog(
            title = "Wi-Fi Lock Timer",
            label = "Minutes (1–720)",
            initialValue = "45",
            onConfirm = { value ->
                value.toIntOrNull()?.let { onScheduleWifiTimer(it) }
                showCustomTimerDialog = false
            },
            onDismiss = { showCustomTimerDialog = false }
        )
    }
}

@Composable
private fun HotspotProxyCard(
    preferences: FirewallPreferencesState,
    hotspotState: HotspotProxyRuntimeState,
    onToggleHotspotProxy: (Boolean) -> Unit,
    onSelectHotspotPort: (Int) -> Unit,
    onOpenCustomPortDialog: () -> Unit,
    onToggleApplyNetGateFilters: (Boolean) -> Unit,
    onRefreshHotspot: () -> Unit,
    onCopyEndpoint: (String) -> Unit,
    onOpenHotspotSettings: () -> Unit
) {
    val active = hotspotState.isProxyRunning
    val accent = when {
        active && hotspotState.isExternalVpnActive -> EmeraldPrimary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    var showGuide by remember { mutableStateOf(false) }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (active) accent.copy(alpha = 0.55f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_proxy_card")
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
                                imageVector = Icons.Rounded.WifiTethering,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hotspot Proxy Sharing",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = hotspotState.shareModeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshHotspot,
                    modifier = Modifier.testTag("refresh_hotspot_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Hotspot interface"
                    )
                }

                Switch(
                    checked = preferences.hotspotProxyEnabled,
                    onCheckedChange = onToggleHotspotProxy,
                    modifier = Modifier.testTag("hotspot_proxy_switch")
                )
            }

            hotspotState.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val endpointText = "${hotspotState.suggestedProxyHost}:${hotspotState.activePort}"
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onCopyEndpoint(endpointText) }
                    .testTag("hotspot_endpoint_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Client Manual Proxy Endpoint (Tap to copy)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endpointText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hotspotState.isHotspotInterfaceActive) {
                                "Hotspot interface detected (${hotspotState.detectedHotspotInterface})"
                            } else {
                                "Turn on Android Hotspot, then set proxy on connected device"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hotspotState.isHotspotInterfaceActive) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy proxy endpoint",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Port Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(8890, 8080, 3128).forEach { port ->
                    FilterChip(
                        selected = preferences.hotspotProxyPort == port,
                        onClick = { onSelectHotspotPort(port) },
                        label = { Text(":$port") },
                        modifier = Modifier.testTag("hotspot_port_$port")
                    )
                }
                OutlinedButton(
                    onClick = onOpenCustomPortDialog,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = if (preferences.hotspotProxyPort !in listOf(8890, 8080, 3128)) {
                            ":${preferences.hotspotProxyPort}"
                        } else {
                            "Custom"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleApplyNetGateFilters(!preferences.hotspotApplyNetGateFilters) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Share NetGate Ad & Site Filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Block ads, blocked sites & enforce SafeSearch for Hotspot clients",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = preferences.hotspotApplyNetGateFilters,
                    onCheckedChange = onToggleApplyNetGateFilters,
                    modifier = Modifier.testTag("hotspot_filter_toggle")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenHotspotSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_hotspot_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hotspot Settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = { showGuide = !showGuide },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hotspot_guide_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showGuide) "Hide Guide" else "Setup Guide")
                }
            }

            AnimatedVisibility(visible = showGuide) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "How VPN & Hotspot Sharing Works (No Root)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1. Turn on Android Portable Hotspot and enable NetGate Hotspot Proxy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "2. On the connected device (laptop/phone/TV), set Wi-Fi Proxy to Manual with Host ${hotspotState.suggestedProxyHost} and Port ${hotspotState.activePort}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3. Want to share a 3rd-party VPN app instead? Pause NetGate Firewall (Android allows 1 VPN at a time), connect your VPN app, and keep Hotspot Proxy ON — sockets relay directly through the active VPN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotspotClientsCard(
    hotspotState: HotspotProxyRuntimeState,
    onToggleClientBlocked: (clientIp: String, blocked: Boolean) -> Unit,
    onEditClient: (HotspotClientInfo) -> Unit,
    onClearAllStats: () -> Unit
) {
    val context = LocalContext.current
    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_clients_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hotspot Data & Connected Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${hotspotState.clients.size} tracked proxy device(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hotspotState.clients.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAllStats,
                        modifier = Modifier.testTag("clear_hotspot_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Reset Hotspot session stats"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HotspotMetricTile(
                    label = "Proxy Shared",
                    value = formatDataBytes(hotspotState.totalRelayedBytes),
                    sub = "↓ ${formatDataBytes(hotspotState.totalRelayedRecvBytes)} • ↑ ${formatDataBytes(hotspotState.totalRelayedSentBytes)}",
                    accent = DataAccent,
                    modifier = Modifier.weight(1f)
                )
                HotspotMetricTile(
                    label = "OS Tethering (24h)",
                    value = if (hotspotState.osTetheringBytes >= 0L) {
                        formatDataBytes(hotspotState.osTetheringBytes)
                    } else {
                        "Usage Perm"
                    },
                    sub = if (hotspotState.osTetheringBytes >= 0L) "System hotspot total" else "Grant Usage Access",
                    accent = WifiAccent,
                    onClick = if (hotspotState.osTetheringBytes < 0L) {
                        { openUsageAccessSettings(context) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hotspotState.clients.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsEthernet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No proxy-routed devices yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Devices connected to your Hotspot using the proxy IP & port appear here with live Rx/Tx usage, pause toggles, and data caps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hotspotState.clients.forEach { client ->
                        HotspotClientRow(
                            client = client,
                            onToggleBlocked = { blocked -> onToggleClientBlocked(client.clientIp, blocked) },
                            onEdit = { onEditClient(client) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotspotMetricTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HotspotClientRow(
    client: HotspotClientInfo,
    onToggleBlocked: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val statusColor = when {
        client.isManuallyBlocked -> BlockedRed
        client.isCapExceeded -> WarningAmber
        else -> EmeraldPrimary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_client_row_${client.clientIp}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (client.isEffectivelyBlocked) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = client.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = buildString {
                            append(client.clientIp)
                            if (client.lastHost.isNotBlank()) append(" • ${client.lastHost}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "↓ ${formatDataBytes(client.bytesReceived)}  •  ↑ ${formatDataBytes(client.bytesSent)}  •  ${client.requestCount} reqs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("hotspot_client_edit_${client.clientIp}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Configure client cap or label",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Switch(
                    checked = !client.isManuallyBlocked,
                    onCheckedChange = { allowed -> onToggleBlocked(!allowed) },
                    modifier = Modifier.testTag("hotspot_client_toggle_${client.clientIp}")
                )
            }

            if (client.dataCapBytes > 0L) {
                val fraction = (client.totalBytes.toFloat() / client.dataCapBytes.toFloat()).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (client.isCapExceeded) BlockedRed else MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (client.isCapExceeded) {
                        "Data cap reached: ${formatDataBytes(client.totalBytes)} / ${formatDataBytes(client.dataCapBytes)} (Blocked)"
                    } else {
                        "Usage cap: ${formatDataBytes(client.totalBytes)} / ${formatDataBytes(client.dataCapBytes)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (client.isCapExceeded) BlockedRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WifiAutomationCard(
    preferences: FirewallPreferencesState,
    connectionDetails: ConnectionDetails,
    idleStatus: WifiIdleStatus,
    nowMillis: Long,
    onToggleGlobalWifiLock: (Boolean) -> Unit,
    onOpenWifiPanel: () -> Unit,
    onScheduleWifiTimer: (minutes: Int) -> Unit,
    onOpenCustomTimerDialog: () -> Unit,
    onCancelWifiTimer: () -> Unit,
    onToggleAutoIdleLock: (Boolean) -> Unit,
    onSelectIdleMinutes: (Int) -> Unit,
    onToggleOpenPanelNotify: (Boolean) -> Unit
) {
    val isLocked = preferences.globalWifiBlocked
    val timerRemainingMs = (preferences.globalWifiTimerExpiresAtMillis - nowMillis).coerceAtLeast(0L)
    val hasActiveTimer = preferences.globalWifiTimerExpiresAtMillis > 0L && timerRemainingMs > 0L

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (isLocked) BlockedRed.copy(alpha = 0.6f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_automation_card")
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
                    val badgeColor = if (isLocked) BlockedRed else WifiAccent
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLocked) Icons.Rounded.WifiLock else Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Global Wi-Fi Traffic Lock",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isLocked -> "Locked — All app Wi-Fi traffic paused by NetGate"
                                connectionDetails.activeNetworkType == ActiveNetworkType.WIFI ->
                                    "Wi-Fi active • Traffic flowing normally"
                                else -> "Standby — Applies automatically when on Wi-Fi"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLocked) BlockedRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isLocked,
                    onCheckedChange = onToggleGlobalWifiLock,
                    modifier = Modifier.testTag("wifi_global_lock_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FilledTonalButton(
                onClick = onOpenWifiPanel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wifi_open_panel_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Android Wi-Fi Switch Panel")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Disable Wi-Fi on Timer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_timer_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disable Wi-Fi Traffic on Timer",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (hasActiveTimer) {
                            Text(
                                text = formatCountdownTimer(timerRemainingMs),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = WarningAmber,
                                modifier = Modifier.testTag("wifi_timer_countdown_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (hasActiveTimer) {
                        Button(
                            onClick = onCancelWifiTimer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wifi_timer_cancel_button")
                        ) {
                            Text("Cancel Wi-Fi Lock Timer")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5 to "5m", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, label) ->
                                OutlinedButton(
                                    onClick = { onScheduleWifiTimer(mins) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("wifi_timer_preset_$mins")
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenCustomTimerDialog,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("wifi_timer_custom_button")
                            ) {
                                Text("Custom", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-Lock Wi-Fi When Idle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_idle_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Auto-Lock Wi-Fi When Not in Use",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Locks Wi-Fi traffic if throughput stays under 2 KB/s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.autoBlockWifiOnIdleEnabled,
                            onCheckedChange = onToggleAutoIdleLock,
                            modifier = Modifier.testTag("wifi_idle_auto_switch")
                        )
                    }

                    if (preferences.autoBlockWifiOnIdleEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(3, 5, 10, 15, 30).forEach { mins ->
                                FilterChip(
                                    selected = preferences.autoBlockWifiIdleMinutes == mins,
                                    onClick = { onSelectIdleMinutes(mins) },
                                    label = { Text("${mins}m") },
                                    modifier = Modifier.testTag("wifi_idle_minutes_$mins")
                                )
                            }
                        }

                        if (idleStatus.isTracking) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(idleStatus.progressFraction.coerceAtLeast(0.03f))
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(WarningAmber)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Idle timer: ${formatCountdownTimer(idleStatus.idleElapsedMillis)} / ${formatCountdownTimer(idleStatus.thresholdMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1-Tap Wi-Fi Panel Notification on Auto-Lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.autoBlockWifiOpenPanelNotify,
                            onCheckedChange = onToggleOpenPanelNotify,
                            modifier = Modifier.testTag("wifi_panel_notify_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Note: Android 10+ prevents apps from toggling hardware Wi-Fi off silently. NetGate blocks all Wi-Fi app traffic immediately through its local VPN and alerts you to flip the Wi-Fi switch in 1 tap.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HotspotClientConfigDialog(
    client: HotspotClientInfo,
    onSave: (label: String, capBytes: Long) -> Unit,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(client.clientIp) { mutableStateOf(client.label) }
    var selectedCapBytes by remember(client.clientIp) { mutableStateOf(client.dataCapBytes) }

    val capPresets = listOf(
        0L to "Unlimited",
        100L * 1024L * 1024L to "100 MB",
        500L * 1024L * 1024L to "500 MB",
        1024L * 1024L * 1024L to "1 GB",
        2L * 1024L * 1024L * 1024L to "2 GB",
        5L * 1024L * 1024L * 1024L to "5 GB"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage ${client.clientIp}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Device Nickname (optional)") },
                    placeholder = { Text("e.g. Work Laptop") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Data Usage Cap",
                    style = MaterialTheme.typography.labelLarge
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    capPresets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { (bytes, title) ->
                                FilterChip(
                                    selected = selectedCapBytes == bytes,
                                    onClick = { selectedCapBytes = bytes },
                                    label = { Text(title, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = onResetStats) {
                    Text("Reset Session Byte Counter (${formatDataBytes(client.totalBytes)})")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(label, selectedCapBytes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomNumberDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() } },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Opens Android's Usage Access screen so OS tethering stats can be read. */
private fun openUsageAccessSettings(context: Context) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val candidates = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = pkgUri },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(intent)
                return
            }
        }
    }
}
