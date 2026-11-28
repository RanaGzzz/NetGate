package com.aistudio.netgate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WifiAccent

@Composable
fun ImportRulesDialog(
    onDismiss: () -> Unit,
    onImport: (rawText: String, defaultIsWhitelist: Boolean) -> Unit
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    var defaultIsWhitelist by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Rules", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste domains, hosts files (0.0.0.0 domain), or Adblock rules (||domain^, @@||domain^).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassTabs(
                    items = listOf(
                        GlassTabItem(
                            label = "Target: Blacklist",
                            testTag = "import_target_blacklist"
                        ),
                        GlassTabItem(
                            label = "Target: Whitelist",
                            testTag = "import_target_whitelist"
                        )
                    ),
                    selectedIndex = if (defaultIsWhitelist) 1 else 0,
                    onSelect = { index -> defaultIsWhitelist = index == 1 },
                    variant = GlassTabVariant.CHIP,
                    forceEqualWidth = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            "example.com\n||tracker.com^\n@@||allowed.com^\n0.0.0.0 ads.sample.org",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("import_rules_text_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawText.isNotBlank()) {
                        onImport(rawText, defaultIsWhitelist)
                        onDismiss()
                    }
                },
                enabled = rawText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("import_rules_confirm_button")
            ) {
                Text("Import")
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
fun ExportRulesDialog(
    exportedText: String,
    onDismiss: () -> Unit,
    onNotifyCopied: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = WifiAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Rules", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Standard Adblock format compatible with uBlock Origin, AdGuard, and Pi-hole.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = exportedText.ifBlank { "# No rules to export yet" },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(exportedText))
                    onNotifyCopied("Copied exported rules to clipboard")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WifiAccent),
                modifier = Modifier.testTag("export_rules_copy_button")
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Rules")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BackupAndRestoreRulesDialog(
    backupJson: String,
    onDismiss: () -> Unit,
    onRestore: (rawJson: String) -> Unit,
    onNotifyCopied: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var restoreJsonInput by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SettingsBackupRestore,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup & Restore Rules", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTabs(
                    items = listOf(
                        GlassTabItem(
                            label = "Backup",
                            testTag = "backup_restore_tab_backup",
                            activeIcon = Icons.Rounded.Download,
                            inactiveIcon = Icons.Outlined.Download
                        ),
                        GlassTabItem(
                            label = "Restore",
                            testTag = "backup_restore_tab_restore",
                            activeIcon = Icons.Rounded.Restore,
                            inactiveIcon = Icons.Rounded.Restore
                        )
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    variant = GlassTabVariant.TOP_SEGMENTED
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Text(
                        text = "Complete backup of your custom domain rules, enabled filter subscriptions, and Smart Protection profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = backupJson,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Paste a JSON backup payload to restore rules and filter subscriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        placeholder = { Text("Paste JSON backup here…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("restore_backup_json_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(backupJson))
                        onNotifyCopied("Copied backup JSON to clipboard")
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("copy_backup_json_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Backup")
                }
            } else {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            onRestore(restoreJsonInput)
                            onDismiss()
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("restore_backup_confirm_button")
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Backup")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
