package com.aistudio.netgate.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.AppUpdateStatus
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.BackupUiState
import com.aistudio.netgate.service.BackgroundPowerHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Advanced protection settings: VPN kill switch, auto-reconnect, trusted Wi-Fi automation,
 * Android 15-17 battery-saver diagnostics, encrypted backup/restore and in-app OTA updates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedProtectionSheet(
    advanced: AdvancedPreferencesState,
    backupState: BackupUiState,
    appUpdateState: AppUpdateUiState,
    currentSsid: String?,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onToggleAutoVpnUnknownWifi: (Boolean) -> Unit,
    onSetWifiTrusted: (String, Boolean) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onExportBackup: (String) -> Unit,
    onImportBackup: (String, String) -> Unit,
    onClearBackupState: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var password by remember { mutableStateOf("") }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportPayload by remember { mutableStateOf<String?>(null) }
    var pendingImportName by remember { mutableStateOf("") }
    var batteryRefresh by remember { mutableIntStateOf(0) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            onExportBackup(password)
        }
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportPayload = readTextFromUri(context, uri)
            pendingImportName = uri.lastPathSegment?.substringAfterLast('/') ?: "backup"
        }
    }

    LaunchedEffect(backupState.exportPayload, pendingExportUri) {
        val payload = backupState.exportPayload
        val uri = pendingExportUri
        if (payload != null && uri != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            pendingExportUri = null
            onClearBackupState()
            if (!written) {
                // The sheet keeps working; the ViewModel surfaces the message on retry.
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Advanced Protection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kill switch, auto-reconnect, trusted Wi-Fi, battery-saver fixes, backup and OTA updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionCard {
                SwitchRow(
                    title = "VPN Kill Switch",
                    subtitle = "If the WireGuard/OpenVPN tunnel drops, block all traffic until it is back",
                    checked = advanced.killSwitchEnabled,
                    onCheckedChange = onToggleKillSwitch,
                    testTag = "advanced_kill_switch"
                )
                if (advanced.killSwitchEngaged) {
                    Text(
                        text = "Kill switch is engaged: traffic is blocked while the tunnel recovers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SwitchRow(
                    title = "Auto-Reconnect",
                    subtitle = "Retry the last profile after a drop or a Wi-Fi ↔ mobile switch",
                    checked = advanced.autoReconnectEnabled,
                    onCheckedChange = onToggleAutoReconnect,
                    testTag = "advanced_auto_reconnect"
                )
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Column {
                        Text(
                            text = "Trusted Wi-Fi Auto-VPN",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = currentSsid ?: "Not connected to Wi-Fi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                SwitchRow(
                    title = "Connect on unknown Wi-Fi",
                    subtitle = "Automatically start the last VPN Guard profile on untrusted SSIDs",
                    checked = advanced.autoVpnOnUnknownWifi,
                    onCheckedChange = onToggleAutoVpnUnknownWifi,
                    testTag = "advanced_auto_vpn_unknown"
                )
                if (currentSsid != null) {
                    val isTrusted = advanced.trustedWifiSsids.any {
                        it.equals(currentSsid, ignoreCase = true)
                    }
                    OutlinedButton(
                        onClick = { onSetWifiTrusted(currentSsid, !isTrusted) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("advanced_trust_current_wifi")
                    ) {
                        Text(
                            if (isTrusted) {
                                "Mark \"$currentSsid\" as untrusted"
                            } else {
                                "Trust \"$currentSsid\" (pause VPN)"
                            }
                        )
                    }
                }
                if (advanced.trustedWifiSsids.isNotEmpty()) {
                    Text(
                        text = "Trusted: " + advanced.trustedWifiSsids.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.BatterySaver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Column {
                        Text(
                            text = "Background & Battery Saver (Android 16/17)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = batterySummary(context, batteryRefresh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onRequestBatteryExemption()
                            batteryRefresh++
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("advanced_battery_exemption")
                    ) {
                        Text("Allow background")
                    }
                    OutlinedButton(
                        onClick = {
                            onOpenNotificationSettings()
                            batteryRefresh++
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Notifications")
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Column {
                        Text(
                            text = "Encrypted Backup & Restore",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Rules, site lists, DNS settings and VPN profiles in one AES-256 file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Backup password (min 8 characters)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advanced_backup_password")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            createBackupLauncher.launch("netgate-backup-$stamp.ngeb")
                        },
                        enabled = password.length >= 8 && !backupState.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                        enabled = !backupState.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Choose file")
                    }
                }
                if (pendingImportPayload != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Selected: $pendingImportName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            pendingImportPayload?.let { payload ->
                                onImportBackup(payload, password)
                            }
                        },
                        enabled = password.isNotEmpty() && !backupState.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Decrypt & Restore")
                    }
                }
                backupState.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                backupState.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (backupState.isWorking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Column {
                        Text(
                            text = "In-App Updates (OTA)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = appUpdateState.otaReason.ifBlank {
                                "Detects new versions and new builds of the same version"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        enabled = appUpdateState.status != AppUpdateStatus.CHECKING,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text(if (appUpdateState.status == AppUpdateStatus.CHECKING) "Checking…" else "Check")
                    }
                    Button(
                        onClick = onDownloadUpdate,
                        enabled = appUpdateState.canInstallDirectly && !appUpdateState.isDownloadingUpdate,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("advanced_download_update")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text("Install")
                    }
                }
                if (appUpdateState.isDownloadingUpdate) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { appUpdateState.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading ${appUpdateState.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                appUpdateState.otaBuild?.let { build ->
                    Text(
                        text = "Latest build: ${build.versionName} (code ${build.versionCode}) • ${build.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilterChip(
                selected = false,
                onClick = onDismiss,
                label = { Text("Close") },
                modifier = Modifier.testTag("advanced_close")
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun batterySummary(context: Context, refreshKey: Int): String {
    // refreshKey forces recomposition with fresh system values.
    val unusedRefresh = refreshKey
    @Suppress("UNUSED_VARIABLE") val refreshToken = unusedRefresh
    val exempt = BackgroundPowerHelper.isIgnoringBatteryOptimizations(context)
    val powerSave = BackgroundPowerHelper.isPowerSaveMode(context)
    val idle = BackgroundPowerHelper.isDeviceIdleMode(context)
    val restricted = BackgroundPowerHelper.isBackgroundRestricted(context)
    val notifications = BackgroundPowerHelper.areNotificationsEnabled(context)
    return buildString {
        append(if (exempt) "Battery optimization exempt" else "Battery optimized (may pause VPN)")
        if (powerSave) append(" • Saver ON")
        if (idle) append(" • Doze")
        if (restricted) append(" • Background restricted")
        if (!notifications) append(" • Notifications OFF")
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()
