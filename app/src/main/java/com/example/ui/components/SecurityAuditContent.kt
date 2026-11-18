package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveSocketInfo
import com.example.data.model.DeviceSecurityAuditReport
import com.example.data.model.SecurityAuditAction
import com.example.data.model.SecurityAuditCheckItem
import com.example.data.model.SecurityAuditStatus
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WarningAmber
import java.text.DateFormat
import java.util.Date

@Composable
fun SecurityAuditContent(
    report: DeviceSecurityAuditReport,
    flaggedSitesCount: Int,
    onRunAudit: () -> Unit,
    onSecurityAction: (SecurityAuditAction) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val heroAccent = when (report.overallStatus) {
        SecurityAuditStatus.SECURE -> EmeraldPrimary
        SecurityAuditStatus.NOTICE -> WarningAmber
        SecurityAuditStatus.WARNING -> BlockedRed
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
                .testTag("security_audit_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "security_hero_card") {
                FrostedSurface(
                    shape = RoundedCornerShape(24.dp),
                    borderAccent = heroAccent.copy(alpha = 0.6f),
                    tintColor = heroAccent.copy(alpha = if (isDark) 0.14f else 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_audit_hero_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = heroAccent.copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (report.overallStatus) {
                                                SecurityAuditStatus.SECURE -> Icons.Rounded.VerifiedUser
                                                SecurityAuditStatus.NOTICE -> Icons.Rounded.Security
                                                SecurityAuditStatus.WARNING -> Icons.Rounded.GppMaybe
                                            },
                                            contentDescription = report.verdictLabel,
                                            tint = heroAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = report.verdictLabel,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (report.lastScannedAt > 0L) {
                                            val timeText = DateFormat.getTimeInstance(DateFormat.SHORT)
                                                .format(Date(report.lastScannedAt))
                                            "Last audited at $timeText"
                                        } else {
                                            "Intrusion & Remote Access Audit"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = heroAccent.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, heroAccent.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${report.securityScore}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = heroAccent
                                    )
                                    Text(
                                        text = "SCORE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = heroAccent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = report.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = (if (flaggedSitesCount > 0) BlockedRed else EmeraldPrimary).copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    (if (flaggedSitesCount > 0) BlockedRed else EmeraldPrimary).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$flaggedSitesCount suspicious endpoint${if (flaggedSitesCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (flaggedSitesCount > 0) BlockedRed else EmeraldPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${report.checks.size} security checks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (report.isScanning) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onRunAudit,
                            enabled = !report.isScanning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = heroAccent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_security_audit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radar,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (report.isScanning) "Scanning Device…" else "Scan Device Now")
                        }
                    }
                }
            }

            if (report.checks.isNotEmpty()) {
                item(key = "checks_header") {
                    Text(
                        text = "System & Intrusion Checks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                items(
                    items = report.checks,
                    key = { it.id }
                ) { check ->
                    SecurityCheckCard(
                        item = check,
                        onAction = onSecurityAction
                    )
                }
            }

            if (report.activeSockets.isNotEmpty()) {
                item(key = "sockets_header") {
                    Text(
                        text = "Kernel Active & Listening Sockets (${report.activeSockets.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(
                    items = report.activeSockets.take(30),
                    key = { "${it.protocol}_${it.localAddress}_${it.localPort}_${it.remoteAddress}_${it.remotePort}_${it.uid}" }
                ) { socket ->
                    ActiveSocketRowCard(socket = socket)
                }
            }
        }
    }
}

@Composable
private fun SecurityCheckCard(
    item: SecurityAuditCheckItem,
    onAction: (SecurityAuditAction) -> Unit
) {
    val accent = when (item.status) {
        SecurityAuditStatus.SECURE -> EmeraldPrimary
        SecurityAuditStatus.NOTICE -> WarningAmber
        SecurityAuditStatus.WARNING -> BlockedRed
    }
    val icon = when (item.status) {
        SecurityAuditStatus.SECURE -> Icons.Rounded.CheckCircle
        SecurityAuditStatus.NOTICE -> Icons.Rounded.Security
        SecurityAuditStatus.WARNING -> Icons.Rounded.Warning
    }

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = when (item.status) {
            SecurityAuditStatus.WARNING -> BlockedRed.copy(alpha = 0.55f)
            SecurityAuditStatus.NOTICE -> WarningAmber.copy(alpha = 0.45f)
            SecurityAuditStatus.SECURE -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_check_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.status.name,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = accent
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.actionLabel != null && item.actionType != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onAction(item.actionType) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("audit_action_${item.id}")
                ) {
                    Text(item.actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ActiveSocketRowCard(socket: ActiveSocketInfo) {
    val accent = if (socket.isSuspicious) BlockedRed else EmeraldPrimary
    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = if (socket.isSuspicious) BlockedRed.copy(alpha = 0.55f) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lan,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = socket.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${socket.protocol} • ${socket.stateLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Text(
                    text = if (socket.isListening) {
                        "Listening on ${socket.localAddress}:${socket.localPort}"
                    } else {
                        "${socket.countryFlag} ${socket.remoteAddress}:${socket.remotePort} (${socket.countryName})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = socket.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (socket.isSuspicious) BlockedRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
