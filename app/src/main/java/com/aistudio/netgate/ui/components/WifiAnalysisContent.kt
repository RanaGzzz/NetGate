package com.aistudio.netgate.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.SavedWifiNetworkInfo
import com.aistudio.netgate.data.model.WifiDiagnosticsUiState
import com.aistudio.netgate.data.model.WifiSavedAccessStatus
import com.aistudio.netgate.data.model.WifiSignalQuality
import com.aistudio.netgate.data.model.isGoodWifiSignal
import com.aistudio.netgate.data.model.rssiToSignalPercent
import com.aistudio.netgate.data.network.WifiConnector
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun WifiAnalysisContent(
    state: WifiDiagnosticsUiState,
    onRefreshWifi: () -> Unit,
    onClearObservedHistory: () -> Unit,
    bottomPadding: Dp = 104.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefreshWifi()
    }

    var pendingConnectNetwork by remember { mutableStateOf<SavedWifiNetworkInfo?>(null) }
    var connectingSsid by remember { mutableStateOf<String?>(null) }

    fun connectToNetwork(network: SavedWifiNetworkInfo, passphrase: String?) {
        connectingSsid = network.ssid
        WifiConnector.connect(
            context = context,
            ssid = network.ssid,
            securityLabel = network.securityLabel,
            passphrase = passphrase
        ) { success, message ->
            connectingSsid = null
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (success) onRefreshWifi()
        }
    }

    fun requestConnect(network: SavedWifiNetworkInfo) {
        if (!WifiConnector.canJoinFromApp(network.securityLabel)) {
            Toast.makeText(context, WifiConnector.WEP_UNSUPPORTED_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        if (WifiConnector.requiresPassphrase(network.securityLabel)) {
            pendingConnectNetwork = network
        } else {
            connectToNetwork(network, null)
        }
    }

    pendingConnectNetwork?.let { network ->
        WifiPasswordDialog(
            ssid = network.ssid,
            onDismiss = { pendingConnectNetwork = null },
            onConnect = { passphrase ->
                pendingConnectNetwork = null
                connectToNetwork(network, passphrase)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 820.dp)
            .testTag("wifi_analysis_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "wifi_connected_hero") {
            ConnectedWifiHeroCard(
                state = state,
                onGrantLocation = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        }

        item(key = "wifi_signal_quality_guide") {
            WifiSignalQualityGuideCard(
                activeQuality = state.connectedWifi?.quality,
                activeRssiDbm = state.connectedWifi?.rssiDbm
            )
        }

        item(key = "wifi_saved_header") {
            SavedWifiAccessCard(
                state = state,
                onGrantLocation = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onOpenSystemWifiSettings = { openSystemWifiSettings(context) },
                onClearObserved = onClearObservedHistory
            )
        }

        if (state.savedNetworks.isEmpty()) {
            item(key = "wifi_saved_empty") {
                EmptyWifiListCard(
                    hasLocationPermission = state.hasLocationPermission,
                    onGrantLocation = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onOpenSystemWifiSettings = { openSystemWifiSettings(context) }
                )
            }
        } else {
            items(
                items = state.savedNetworks,
                key = { "saved_${it.ssid.lowercase()}" }
            ) { network ->
                WifiNetworkTelemetryCard(
                    network = network,
                    isConnecting = connectingSsid == network.ssid,
                    onConnect = { requestConnect(network) },
                    modifier = Modifier.testTag("saved_wifi_card_${network.ssid.lowercase().replace(" ", "_")}")
                )
            }
        }

        if (state.nearbyNetworks.isNotEmpty()) {
            item(key = "wifi_nearby_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WifiFind,
                            contentDescription = null,
                            tint = WifiAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Nearby Wi-Fi Networks (${state.nearbyNetworks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Live Scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = state.nearbyNetworks.take(20),
                key = { "nearby_${it.ssid.lowercase()}_${it.bssid}" }
            ) { network ->
                WifiNetworkTelemetryCard(
                    network = network,
                    isConnecting = connectingSsid == network.ssid,
                    onConnect = { requestConnect(network) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedWifiHeroCard(
    state: WifiDiagnosticsUiState,
    onGrantLocation: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val connected = state.connectedWifi
    val quality = connected?.quality ?: WifiSignalQuality.UNAVAILABLE
    val accent = qualityAccentColor(quality)

    FrostedSurface(
        shape = RoundedCornerShape(24.dp),
        borderAccent = if (connected != null) accent else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connected_wifi_hero_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        color = accent.copy(alpha = if (isDark) 0.22f else 0.14f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    connected != null && quality.isGoodOrBetter -> Icons.Rounded.SignalWifi4Bar
                                    connected != null -> Icons.Rounded.Wifi
                                    state.isWifiEnabled -> Icons.Rounded.WifiFind
                                    else -> Icons.Rounded.WifiOff
                                },
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = connected?.ssid ?: if (state.isWifiEnabled) {
                                "Wi-Fi On • Not Connected"
                            } else {
                                "Wi-Fi Radio Off"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (connected != null) {
                                listOfNotNull(
                                    connected.standardLabel,
                                    connected.bandLabel,
                                    connected.channel?.let { "Ch $it" },
                                    connected.securityLabel.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                            } else {
                                "Connect to Wi-Fi to inspect standard, band, RSSI & link speed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (connected != null) {
                Spacer(modifier = Modifier.height(14.dp))
                val rssi = connected.rssiDbm
                val percent = rssiToSignalPercent(rssi)
                val goodSignal = isGoodWifiSignal(rssi)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = if (isDark) 0.14f else 0.09f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
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
                                    text = "Wi-Fi Quality: ${quality.label}" +
                                        (rssi?.let { " ($it dBm • $percent%)" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = quality.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = if (goodSignal) "Good Signal ✓" else "Weak Signal",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (percent / 100f).coerceIn(0f, 1f) },
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "-100 dBm Weak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "-67 dBm+ Good",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "-30 dBm Max",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricCell(
                        label = "Standard",
                        value = connected.standardLabel,
                        icon = Icons.Rounded.Router,
                        accent = WifiAccent,
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricCell(
                        label = "Frequency & Band",
                        value = buildFrequencyBandLabel(connected),
                        icon = Icons.Rounded.NetworkCheck,
                        accent = DataAccent,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricCell(
                        label = "Signal Strength",
                        value = rssi?.let { "$it dBm (${quality.label})" } ?: "Unavailable",
                        icon = Icons.Rounded.SignalWifi4Bar,
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricCell(
                        label = "Link Speed",
                        value = buildLinkSpeedLabel(connected),
                        icon = Icons.Rounded.Speed,
                        accent = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!state.hasLocationPermission && connected.ssid.contains("Hidden", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onGrantLocation,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Reveal Wi-Fi Name (SSID) & Scan Nearby")
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiMetricCell(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
        },
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WifiSignalQualityGuideCard(
    activeQuality: WifiSignalQuality?,
    activeRssiDbm: Int?
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_quality_guide_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Wi-Fi Signal Quality Guide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPrimary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Good: -67 dBm or stronger",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Signal closer to 0 dBm is stronger. For fast, stable video calls & low-ping browsing, stay at -67 dBm (Good) or better.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            val tiers = listOf(
                WifiSignalQuality.EXCELLENT,
                WifiSignalQuality.GOOD,
                WifiSignalQuality.FAIR,
                WifiSignalQuality.WEAK
            )
            tiers.forEachIndexed { index, tier ->
                val tierColor = qualityAccentColor(tier)
                val isCurrent = activeQuality == tier
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) {
                        tierColor.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                    border = if (isCurrent) {
                        BorderStroke(1.dp, tierColor.copy(alpha = 0.45f))
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(tierColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${tier.label} (${tier.rangeLabel})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isCurrent || tier == WifiSignalQuality.GOOD) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.SemiBold
                                    },
                                    color = if (isCurrent) tierColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (tier == WifiSignalQuality.GOOD) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Good Threshold",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = tier.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent && activeRssiDbm != null) {
                            Text(
                                text = "$activeRssiDbm dBm",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tierColor
                            )
                        }
                    }
                }
                if (index < tiers.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedWifiAccessCard(
    state: WifiDiagnosticsUiState,
    onGrantLocation: () -> Unit,
    onOpenSystemWifiSettings: () -> Unit,
    onClearObserved: () -> Unit
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_wifi_header_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Wi-Fi on Device (${state.savedNetworks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (state.accessStatus) {
                            WifiSavedAccessStatus.FULL_OS_LIST -> "System configured & live signal profiles"
                            WifiSavedAccessStatus.SCOPED_ANDROID_10_PLUS -> "Connected, device-saved & NetGate observed profiles"
                            WifiSavedAccessStatus.PERMISSION_NEEDED -> "Location permission needed to reveal SSIDs & scans"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.savedNetworks.any { !it.isCurrentlyConnected }) {
                    IconButton(onClick = onClearObserved) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear observed Wi-Fi history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.statusNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.statusNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.hasLocationPermission) {
                    Button(
                        onClick = onGrantLocation,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wifi_grant_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiFind,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Wi-Fi Location")
                    }
                }
                OutlinedButton(
                    onClick = onOpenSystemWifiSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wifi_open_system_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Android Saved Wi-Fi")
                }
            }
        }
    }
}

@Composable
private fun EmptyWifiListCard(
    hasLocationPermission: Boolean,
    onGrantLocation: () -> Unit,
    onOpenSystemWifiSettings: () -> Unit
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.SignalWifiBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No Saved Wi-Fi Profiles Captured Yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!hasLocationPermission) {
                    "Grant Wi-Fi Location access so NetGate can inspect your connected network's SSID, band, standard, and record real device Wi-Fi profiles."
                } else {
                    "Connect to a Wi-Fi network to capture its Standard, Frequency Band, Signal Strength, Link Speed, and Quality rating."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = if (!hasLocationPermission) onGrantLocation else onOpenSystemWifiSettings,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(if (!hasLocationPermission) "Grant Location Access" else "Open Wi-Fi Settings")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiNetworkTelemetryCard(
    network: SavedWifiNetworkInfo,
    isConnecting: Boolean = false,
    onConnect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = qualityAccentColor(network.quality)
    val rssi = network.rssiDbm
    val goodSignal = isGoodWifiSignal(rssi)

    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = if (network.isCurrentlyConnected) accent else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        imageVector = if (network.isCurrentlyConnected) Icons.Rounded.SignalWifi4Bar else Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (rssi != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = network.ssid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildList {
                                if (network.sourceLabel.isNotBlank()) add(network.sourceLabel)
                                if (network.securityLabel.isNotBlank()) add(network.securityLabel)
                            }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (goodSignal) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (rssi != null) {
                                "${network.quality.label} • $rssi dBm"
                            } else {
                                "Saved"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WifiSpecChip(label = "Standard", value = network.standardLabel)
                WifiSpecChip(label = "Band", value = buildFrequencyBandLabel(network))
                WifiSpecChip(
                    label = "Signal",
                    value = rssi?.let { "$it dBm (${rssiToSignalPercent(it)}%)" } ?: "Out of range"
                )
                WifiSpecChip(label = "Link Speed", value = buildLinkSpeedLabel(network))
                if (network.securityLabel.isNotBlank()) {
                    WifiSpecChip(label = "Security", value = network.securityLabel, icon = Icons.Rounded.Lock)
                }
                when {
                    network.isCurrentlyConnected -> WifiActionChip(
                        label = "Connected",
                        icon = Icons.Rounded.CheckCircle,
                        accent = accent,
                        enabled = false,
                        testTag = "wifi_connected_chip_${network.ssid.lowercase().replace(" ", "_")}",
                        onClick = {}
                    )
                    onConnect != null -> WifiActionChip(
                        label = if (isConnecting) "Connecting…" else "Connect",
                        icon = Icons.Rounded.Wifi,
                        accent = WifiAccent,
                        enabled = !isConnecting,
                        testTag = "wifi_connect_${network.ssid.lowercase().replace(" ", "_")}",
                        onClick = onConnect
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiActionChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun WifiPasswordDialog(
    ssid: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = WifiAccent
            )
        },
        title = { Text("Connect to $ssid") },
        text = {
            Column {
                Text(
                    text = "Enter this network's Wi-Fi password. Android keeps the credential in its own Wi-Fi settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Wi-Fi password") },
                    visualTransformation = if (isVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_password_field")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(password) },
                enabled = password.isNotBlank(),
                modifier = Modifier.testTag("wifi_connect_confirm_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WifiSpecChip(
    label: String,
    value: String,
    icon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun buildFrequencyBandLabel(network: SavedWifiNetworkInfo): String {
    val band = network.bandLabel
    val freq = network.frequencyMhz?.let { "$it MHz" }
    val ch = network.channel?.let { "Ch $it" }
    return listOfNotNull(band, freq, ch).joinToString(" • ").ifBlank { "Unavailable" }
}

private fun buildLinkSpeedLabel(network: SavedWifiNetworkInfo): String {
    val tx = network.linkSpeedMbps
    val rx = network.rxLinkSpeedMbps
    return when {
        tx != null && rx != null -> "Tx $tx / Rx $rx Mbps"
        tx != null -> "$tx Mbps"
        rx != null -> "Rx $rx Mbps"
        else -> "N/A"
    }
}

private fun qualityAccentColor(quality: WifiSignalQuality): Color = when (quality) {
    WifiSignalQuality.EXCELLENT -> EmeraldPrimary
    WifiSignalQuality.GOOD -> WifiAccent
    WifiSignalQuality.FAIR -> WarningAmber
    WifiSignalQuality.WEAK -> BlockedRed
    WifiSignalQuality.UNAVAILABLE -> DataAccent
}

private fun openSystemWifiSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
