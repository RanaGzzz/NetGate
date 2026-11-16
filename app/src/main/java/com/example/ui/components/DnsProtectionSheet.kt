package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsProbeState
import com.example.data.model.DnsProviderPreset
import com.example.ui.theme.EmeraldPrimary

@Composable
fun DnsProtectionContent(
    connectionDetails: ConnectionDetails,
    probeState: DnsProbeState,
    onTestCloudflareDoh: () -> Unit,
    onCopiedHostname: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPreset by remember { mutableStateOf(DnsProviderPreset.CLOUDFLARE_FAST) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("dns_protection_sheet")
    ) {
        val isWide = maxWidth >= 800.dp
        val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DnsStatusAndSetupCards(
                        connectionDetails = connectionDetails,
                        selectedPreset = selectedPreset,
                        probeState = probeState,
                        onTestCloudflareDoh = onTestCloudflareDoh,
                        onOpenSettings = {
                            copyToClipboard(context, selectedPreset.hostname)
                            onCopiedHostname(selectedPreset.hostname)
                            openNetworkOrPrivateDnsSettings(context)
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DnsPresetsList(
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it },
                        onCopyPreset = { host ->
                            copyToClipboard(context, host)
                            onCopiedHostname(host)
                        }
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
                    DnsStatusAndSetupCards(
                        connectionDetails = connectionDetails,
                        selectedPreset = selectedPreset,
                        probeState = probeState,
                        onTestCloudflareDoh = onTestCloudflareDoh,
                        onOpenSettings = {
                            copyToClipboard(context, selectedPreset.hostname)
                            onCopiedHostname(selectedPreset.hostname)
                            openNetworkOrPrivateDnsSettings(context)
                        }
                    )

                    DnsPresetsList(
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it },
                        onCopyPreset = { host ->
                            copyToClipboard(context, host)
                            onCopiedHostname(host)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DnsStatusAndSetupCards(
    connectionDetails: ConnectionDetails,
    selectedPreset: DnsProviderPreset,
    probeState: DnsProbeState,
    onTestCloudflareDoh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isCfActive = connectionDetails.isCloudflarePrivateDns
    val isAnyPrivateActive = connectionDetails.isPrivateDnsActive
    val statusBorder = when {
        isCfActive -> EmeraldPrimary
        isAnyPrivateActive -> MaterialTheme.colorScheme.primary
        else -> null
    }

    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        tintColor = if (isCfActive) EmeraldPrimary.copy(alpha = 0.14f) else null,
        borderAccent = statusBorder,
        modifier = Modifier.fillMaxWidth()
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
                    Icon(
                        imageVector = if (isCfActive) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        contentDescription = null,
                        tint = if (isCfActive) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                isCfActive -> "Cloudflare Private DNS Active"
                                isAnyPrivateActive && !connectionDetails.privateDnsHostname.isNullOrBlank() ->
                                    "Custom Private DNS Active"
                                isAnyPrivateActive -> "Private DNS Active (Automatic)"
                                else -> "Using Default / ISP DNS"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !connectionDetails.privateDnsHostname.isNullOrBlank() ->
                                    "Hostname: ${connectionDetails.privateDnsHostname}"
                                connectionDetails.dnsServers.isNotEmpty() ->
                                    "Active DNS: ${connectionDetails.dnsServers.joinToString(", ")}"
                                else -> "Set Private DNS to one.one.one.one below"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "How to enable system-wide (works with NetGate Firewall):",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "1. Tap Copy & Open Private DNS Settings below.\n" +
                    "2. Tap Private DNS → Private DNS provider hostname.\n" +
                    "3. Paste ${selectedPreset.hostname} and tap Save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("copy_and_open_dns_settings_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Copy ${selectedPreset.hostname} & Open Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onTestCloudflareDoh,
                enabled = !probeState.isTesting,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_cloudflare_doh_button")
            ) {
                if (probeState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Cloudflare 1.1.1.1…")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            probeState.latencyMs != null ->
                                "Cloudflare DoH Reachable (${probeState.latencyMs} ms) — Retest"
                            probeState.errorMessage != null ->
                                "Retry Cloudflare 1.1.1.1 Check"
                            else -> "Test Cloudflare 1.1.1.1 Reachability"
                        }
                    )
                }
            }

            probeState.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DnsPresetsList(
    selectedPreset: DnsProviderPreset,
    onSelectPreset: (DnsProviderPreset) -> Unit,
    onCopyPreset: (String) -> Unit
) {
    Text(
        text = "Select Encrypted DNS Hostname",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DnsProviderPreset.entries.forEach { preset ->
            val selected = selectedPreset == preset
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                tintColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                } else {
                    null
                },
                borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectPreset(preset) }
                    .testTag("dns_preset_${preset.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelectPreset(preset) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = preset.hostname,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onCopyPreset(preset.hostname) },
                        modifier = Modifier.testTag("copy_dns_${preset.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy ${preset.hostname}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Private DNS Hostname", text))
}

private fun openNetworkOrPrivateDnsSettings(context: Context) {
    val candidates = listOf(
        Intent("android.settings.PRIVATE_DNS_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
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
