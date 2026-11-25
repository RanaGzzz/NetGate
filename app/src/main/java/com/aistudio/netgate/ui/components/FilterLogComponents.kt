package com.aistudio.netgate.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AdBlockReason
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.TempAllowDuration
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilterLogEntryCard(
    entry: DnsQueryLogEntry,
    onAllowTemporarily: (domain: String, duration: TempAllowDuration) -> Unit,
    onAllowPermanently: (domain: String) -> Unit,
    onBlockPermanently: (domain: String) -> Unit,
    onReportFalsePositive: (domain: String) -> Unit,
    onNotifyCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var isAllowMenuOpen by remember { mutableStateOf(false) }
    var showFalsePositiveDialog by remember { mutableStateOf(false) }

    val isBlocked = entry.status == DnsQueryStatus.BLOCKED_AD ||
        entry.status == DnsQueryStatus.BLOCKED_WEBSITE ||
        entry.status == DnsQueryStatus.BLOCKED_HTTPS ||
        entry.status == DnsQueryStatus.BLOCKED_APP

    val accent = when {
        entry.blockReason == AdBlockReason.MALWARE || entry.blockReason == AdBlockReason.PHISHING -> BlockedRed
        isBlocked -> BlockedRed
        entry.status == DnsQueryStatus.SAFE_REWRITE -> WifiAccent
        entry.status == DnsQueryStatus.PASSTHROUGH -> DataAccent
        else -> EmeraldPrimary
    }

    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = accent.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("filter_log_entry_${entry.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isBlocked -> Icons.Rounded.Block
                                entry.status == DnsQueryStatus.SAFE_REWRITE -> Icons.Rounded.Security
                                else -> Icons.Rounded.CheckCircle
                            },
                            contentDescription = if (isBlocked) "Blocked" else "Allowed",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.domain,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entry.hitCount > 1) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "×${entry.hitCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (isBlocked) "BLOCKED" else "ALLOWED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }

                        if (entry.blockReason != AdBlockReason.NONE) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = entry.blockReason.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = formatTimeHms(entry.timestampMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded metadata details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // Full URL
                            if (entry.fullUrl.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Rounded.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = entry.fullUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // App Name
                            val appLabel = entry.appName.ifBlank { entry.appPackage.ifBlank { "System / Network" } }
                            Text(
                                text = "App: $appLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Filter list responsible
                            val listLabel = entry.filterListName.ifBlank { entry.reason }
                            Text(
                                text = "Filter: $listLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (entry.latencyMicros > 0L) {
                                val latMs = if (entry.latencyMicros < 1000L) "< 1 ms" else "${entry.latencyMicros / 1000L} ms"
                                Text(
                                    text = "Lookup Latency: $latMs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6 Quick Actions Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Action 1: Allow (Temporary / Permanent)
                        Box {
                            OutlinedButton(
                                onClick = { isAllowMenuOpen = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("filter_log_action_allow_${entry.domain}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = EmeraldPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Allow", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                            }

                            DropdownMenu(
                                expanded = isAllowMenuOpen,
                                onDismissRequest = { isAllowMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Allow for 1 Hour") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = WifiAccent)
                                    },
                                    onClick = {
                                        isAllowMenuOpen = false
                                        onAllowTemporarily(entry.domain, TempAllowDuration.ONE_HOUR)
                                    },
                                    modifier = Modifier.testTag("allow_1_hour_${entry.domain}")
                                )
                                DropdownMenuItem(
                                    text = { Text("Allow for 24 Hours") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = WifiAccent)
                                    },
                                    onClick = {
                                        isAllowMenuOpen = false
                                        onAllowTemporarily(entry.domain, TempAllowDuration.TWENTY_FOUR_HOURS)
                                    },
                                    modifier = Modifier.testTag("allow_24_hours_${entry.domain}")
                                )
                                DropdownMenuItem(
                                    text = { Text("Allow for 7 Days") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = WifiAccent)
                                    },
                                    onClick = {
                                        isAllowMenuOpen = false
                                        onAllowTemporarily(entry.domain, TempAllowDuration.SEVEN_DAYS)
                                    },
                                    modifier = Modifier.testTag("allow_7_days_${entry.domain}")
                                )
                                DropdownMenuItem(
                                    text = { Text("Permanent Allow") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldPrimary)
                                    },
                                    onClick = {
                                        isAllowMenuOpen = false
                                        onAllowPermanently(entry.domain)
                                    },
                                    modifier = Modifier.testTag("allow_permanent_${entry.domain}")
                                )
                            }
                        }

                        // Action 2: Permanent Block
                        OutlinedButton(
                            onClick = { onBlockPermanently(entry.domain) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("filter_log_action_block_${entry.domain}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = BlockedRed
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block", style = MaterialTheme.typography.labelSmall, color = BlockedRed)
                        }

                        // Action 3: Copy Domain
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(entry.domain))
                                onNotifyCopied("Copied ${entry.domain} to clipboard")
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("filter_log_action_copy_${entry.domain}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy domain",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Action 4: Whois Lookup
                        IconButton(
                            onClick = {
                                openBrowserSafe(context, "https://who.is/whois/${entry.domain}")
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("filter_log_action_whois_${entry.domain}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = "Whois Lookup",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Action 5: VirusTotal Scan
                        IconButton(
                            onClick = {
                                openBrowserSafe(context, "https://www.virustotal.com/gui/domain/${entry.domain}")
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("filter_log_action_virustotal_${entry.domain}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "VirusTotal Scan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Action 6: Report False Positive
                        IconButton(
                            onClick = { showFalsePositiveDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("filter_log_action_false_positive_${entry.domain}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Feedback,
                                contentDescription = "Report False Positive",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFalsePositiveDialog) {
        AlertDialog(
            onDismissRequest = { showFalsePositiveDialog = false },
            title = { Text("Report False Positive?") },
            text = {
                Text(
                    "Report ${entry.domain} as incorrectly blocked? This will automatically add a permanent allow rule so the site functions normally."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFalsePositiveDialog = false
                        onReportFalsePositive(entry.domain)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Report & Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFalsePositiveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun openBrowserSafe(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Unable to open web browser", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTimeHms(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
