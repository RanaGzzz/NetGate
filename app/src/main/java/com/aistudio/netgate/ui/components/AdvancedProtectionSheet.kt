package com.aistudio.netgate.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.BackupUiState
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        FrostedSheetContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FrostedSheetDragHandle()
                Text(
                    text = "Kill Switch, Backup & Automation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "VPN kill switch, auto-reconnect, trusted Wi-Fi & AES-256 encrypted backup.",
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
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
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
                        imageVector = Icons.Rounded.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Column {
                        Text(
                            text = "Encrypted Backup & Restore",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            createBackupLauncher.launch("netgate-backup-$stamp.ngeb")
                        },
                        enabled = password.length >= 8 && !backupState.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("advanced_backup_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                        enabled = !backupState.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("advanced_backup_choose_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Choose File",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
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
        }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
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

private fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()
