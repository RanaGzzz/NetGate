package com.aistudio.netgate.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.DnsCheckState
import com.aistudio.netgate.data.model.DnsLeakCheckItem
import com.aistudio.netgate.data.model.DnsLeakCheckStatus
import com.aistudio.netgate.data.model.DnsLeakTestState
import com.aistudio.netgate.data.model.DnsLeakVerdict
import com.aistudio.netgate.data.model.DnsProbeState
import com.aistudio.netgate.data.model.DnsProviderPreset
import com.aistudio.netgate.data.model.DnsServerIdentity
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.UpstreamDnsServer
import com.aistudio.netgate.service.VpnRuntimeState
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent

@Composable
fun DnsProtectionContent(
    connectionDetails: ConnectionDetails,
    preferences: FirewallPreferencesState,
    isFirewallActive: Boolean,
    vpnRuntimeState: VpnRuntimeState,
    probeState: DnsProbeState,
    leakTestState: DnsLeakTestState,
    dnsCheckState: DnsCheckState,
    onSelectUpstreamDns: (UpstreamDnsServer) -> Unit,
    onSaveCustomDns: (dohUrl: String, udpIp: String) -> Unit,
    onToggleDohUpstream: (Boolean) -> Unit,
    onRunDnsCheck: () -> Unit,
    onRunDnsLeakTest: () -> Unit,
    onTestCloudflareDoh: () -> Unit,
    onSelectDpiMode: (DpiBypassMode) -> Unit,
    onSelectDpiChunkSize: (Int) -> Unit,
    onToggleDpiIdleKeepAlive: (Boolean) -> Unit,
    onToggleBlockQuic: (Boolean) -> Unit,
    onToggleAntiDnsPoison: (Boolean) -> Unit,
    onSelectDnsTransportMode: (DnsTransportMode) -> Unit,
    onFlushDpiCache: () -> Unit,
    onRequestEnableFirewall: () -> Unit,
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
                    ActiveDnsCheckCard(
                        dnsCheckState = dnsCheckState,
                        isFirewallActive = isFirewallActive,
                        onRunDnsCheck = onRunDnsCheck
                    )

                    DnsLeakTestCard(
                        leakTestState = leakTestState,
                        preferences = preferences,
                        isFirewallActive = isFirewallActive,
                        vpnRuntimeState = vpnRuntimeState,
                        onRunDnsLeakTest = onRunDnsLeakTest,
                        onRequestEnableFirewall = onRequestEnableFirewall
                    )

                    DnsStatusAndSetupCards(
                        connectionDetails = connectionDetails,
                        preferences = preferences,
                        selectedPreset = selectedPreset,
                        probeState = probeState,
                        onTestCloudflareDoh = onTestCloudflareDoh,
                        onOpenSettings = {
                            copyToClipboard(context, "Private DNS Hostname", selectedPreset.hostname)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VpnUpstreamDnsSelectorCard(
                        preferences = preferences,
                        onSelectUpstreamDns = onSelectUpstreamDns,
                        onSaveCustomDns = onSaveCustomDns,
                        onToggleDohUpstream = onToggleDohUpstream
                    )

                    DpiBypassShieldCard(
                        dpiBypassMode = preferences.dpiBypassMode,
                        dpiFragmentChunkSize = preferences.dpiFragmentChunkSize,
                        dpiIdleKeepAliveEnabled = preferences.dpiIdleKeepAliveEnabled,
                        blockQuicForDpiBypass = preferences.blockQuicForDpiBypass,
                        antiDnsPoisonEnabled = preferences.antiDnsPoisonEnabled,
                        dnsTransportMode = preferences.dnsTransportMode,
                        dpiBypassedConnectionsCount = vpnRuntimeState.dpiBypassedConnectionsCount,
                        dpiAutopilotLearnedCount = vpnRuntimeState.dpiAutopilotLearnedCount,
                        antiDnsPoisonBlockedCount = vpnRuntimeState.antiDnsPoisonBlockedCount,
                        quicStrippedCount = vpnRuntimeState.quicStrippedCount,
                        isFirewallActive = isFirewallActive,
                        onSelectDpiMode = onSelectDpiMode,
                        onSelectChunkSize = onSelectDpiChunkSize,
                        onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                        onToggleBlockQuic = onToggleBlockQuic,
                        onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                        onSelectDnsTransportMode = onSelectDnsTransportMode,
                        onFlushCache = onFlushDpiCache,
                        onRequestEnableFirewall = onRequestEnableFirewall
                    )

                    DnsPresetsList(
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it },
                        onCopyPreset = { host ->
                            copyToClipboard(context, "Private DNS Hostname", host)
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
                        .padding(top = 8.dp, bottom = navInsetBottom + 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActiveDnsCheckCard(
                        dnsCheckState = dnsCheckState,
                        isFirewallActive = isFirewallActive,
                        onRunDnsCheck = onRunDnsCheck
                    )

                    DnsLeakTestCard(
                        leakTestState = leakTestState,
                        preferences = preferences,
                        isFirewallActive = isFirewallActive,
                        vpnRuntimeState = vpnRuntimeState,
                        onRunDnsLeakTest = onRunDnsLeakTest,
                        onRequestEnableFirewall = onRequestEnableFirewall
                    )

                    VpnUpstreamDnsSelectorCard(
                        preferences = preferences,
                        onSelectUpstreamDns = onSelectUpstreamDns,
                        onSaveCustomDns = onSaveCustomDns,
                        onToggleDohUpstream = onToggleDohUpstream
                    )

                    DpiBypassShieldCard(
                        dpiBypassMode = preferences.dpiBypassMode,
                        dpiFragmentChunkSize = preferences.dpiFragmentChunkSize,
                        dpiIdleKeepAliveEnabled = preferences.dpiIdleKeepAliveEnabled,
                        blockQuicForDpiBypass = preferences.blockQuicForDpiBypass,
                        antiDnsPoisonEnabled = preferences.antiDnsPoisonEnabled,
                        dnsTransportMode = preferences.dnsTransportMode,
                        dpiBypassedConnectionsCount = vpnRuntimeState.dpiBypassedConnectionsCount,
                        dpiAutopilotLearnedCount = vpnRuntimeState.dpiAutopilotLearnedCount,
                        antiDnsPoisonBlockedCount = vpnRuntimeState.antiDnsPoisonBlockedCount,
                        quicStrippedCount = vpnRuntimeState.quicStrippedCount,
                        isFirewallActive = isFirewallActive,
                        onSelectDpiMode = onSelectDpiMode,
                        onSelectChunkSize = onSelectDpiChunkSize,
                        onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                        onToggleBlockQuic = onToggleBlockQuic,
                        onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                        onSelectDnsTransportMode = onSelectDnsTransportMode,
                        onFlushCache = onFlushDpiCache,
                        onRequestEnableFirewall = onRequestEnableFirewall
                    )

                    DnsStatusAndSetupCards(
                        connectionDetails = connectionDetails,
                        preferences = preferences,
                        selectedPreset = selectedPreset,
                        probeState = probeState,
                        onTestCloudflareDoh = onTestCloudflareDoh,
                        onOpenSettings = {
                            copyToClipboard(context, "Private DNS Hostname", selectedPreset.hostname)
                            onCopiedHostname(selectedPreset.hostname)
                            openNetworkOrPrivateDnsSettings(context)
                        }
                    )

                    DnsPresetsList(
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it },
                        onCopyPreset = { host ->
                            copyToClipboard(context, "Private DNS Hostname", host)
                            onCopiedHostname(host)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveDnsCheckCard(
    dnsCheckState: DnsCheckState,
    isFirewallActive: Boolean,
    onRunDnsCheck: () -> Unit
) {
    val accent = when {
        dnsCheckState.errorMessage != null -> BlockedRed
        dnsCheckState.isEncrypted -> EmeraldPrimary
        else -> WarningAmber
    }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        tintColor = accent.copy(alpha = 0.09f),
        borderAccent = accent.copy(alpha = 0.58f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_dns_check_card")
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
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Connected DNS Resolver",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dnsCheckState.connectedProvider,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (dnsCheckState.isEncrypted) "ENCRYPTED" else "UNENCRYPTED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (dnsCheckState.transportLabel.isNotBlank() || dnsCheckState.resolutionLatencyMs != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dnsCheckState.transportLabel.isNotBlank()) {
                        LeakTelemetryBadge(
                            label = "Transport",
                            value = dnsCheckState.transportLabel,
                            accent = WifiAccent
                        )
                    }
                    dnsCheckState.resolutionLatencyMs?.let { ms ->
                        LeakTelemetryBadge(
                            label = "Lookup RTT",
                            value = "$ms ms",
                            accent = EmeraldPrimary
                        )
                    }
                    if (isFirewallActive) {
                        LeakTelemetryBadge(
                            label = "Firewall Tunnel",
                            value = "Active",
                            accent = EmeraldPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = dnsCheckState.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (dnsCheckState.errorMessage != null) {
                    BlockedRed
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            dnsCheckState.resolvedExitDnsIp?.let { exitIp ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldPrimary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.32f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_verified_exit_resolver_box")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "LIVE VERIFIED EXIT RESOLVER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (dnsCheckState.resolvedExitDnsFlag.isNotBlank()) {
                                    append(dnsCheckState.resolvedExitDnsFlag)
                                    append(" ")
                                }
                                append(exitIp)
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val orgAndLocation = listOfNotNull(
                            dnsCheckState.resolvedExitDnsOrg?.takeIf { it.isNotBlank() },
                            dnsCheckState.resolvedExitDnsLocation?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (orgAndLocation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = orgAndLocation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (dnsCheckState.systemDnsServers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dnsCheckState.systemDnsServers.forEach { server ->
                        DnsServerIdentityRow(server = server)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRunDnsCheck,
                enabled = !dnsCheckState.isChecking,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("check_active_dns_button")
            ) {
                if (dnsCheckState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking Connected DNS…")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (dnsCheckState.checkedAtMs > 0L) {
                            "Re-check Connected DNS"
                        } else {
                            "Check Connected DNS"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DnsServerIdentityRow(server: DnsServerIdentity) {
    val badgeColor = if (server.isEncrypted) EmeraldPrimary else WarningAmber
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = server.providerName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = server.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = if (server.isEncrypted) "Encrypted" else "Plaintext",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsLeakTestCard(
    leakTestState: DnsLeakTestState,
    preferences: FirewallPreferencesState,
    isFirewallActive: Boolean,
    vpnRuntimeState: VpnRuntimeState,
    onRunDnsLeakTest: () -> Unit,
    onRequestEnableFirewall: () -> Unit
) {
    val accent = when (leakTestState.verdict) {
        DnsLeakVerdict.NO_LEAK -> EmeraldPrimary
        DnsLeakVerdict.PARTIAL_RISK -> WarningAmber
        DnsLeakVerdict.LEAK_DETECTED -> BlockedRed
        DnsLeakVerdict.IDLE -> WifiAccent
    }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        tintColor = accent.copy(alpha = 0.08f),
        borderAccent = accent.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dns_leak_test_card")
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (leakTestState.verdict) {
                                    DnsLeakVerdict.NO_LEAK -> Icons.Rounded.GppGood
                                    DnsLeakVerdict.PARTIAL_RISK -> Icons.Rounded.WarningAmber
                                    DnsLeakVerdict.LEAK_DETECTED -> Icons.Rounded.ErrorOutline
                                    DnsLeakVerdict.IDLE -> Icons.Rounded.Radar
                                },
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DNS Leak & Encryption Audit",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = leakTestState.verdict.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                    }
                }

                if (vpnRuntimeState.isTunnelActive) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "VPN ON",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = leakTestState.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (leakTestState.dohLatencyMs != null ||
                leakTestState.egressIp != null ||
                leakTestState.activeSystemDns.isNotEmpty()
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leakTestState.dohLatencyMs?.let { ms ->
                        LeakTelemetryBadge(
                            label = "DoH Latency",
                            value = "$ms ms",
                            accent = EmeraldPrimary
                        )
                    }
                    leakTestState.egressIp?.let { ip ->
                        LeakTelemetryBadge(
                            label = "Egress IP",
                            value = ip,
                            accent = WifiAccent
                        )
                    }
                    leakTestState.egressColo?.let { colo ->
                        LeakTelemetryBadge(
                            label = "POP / Edge",
                            value = colo,
                            accent = WifiAccent
                        )
                    }
                    if (leakTestState.activeSystemDns.isNotEmpty()) {
                        LeakTelemetryBadge(
                            label = "Active DNS",
                            value = leakTestState.activeSystemDns.joinToString(", "),
                            accent = accent
                        )
                    }
                }
            }

            if (leakTestState.checks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    leakTestState.checks.forEach { item ->
                        LeakCheckRow(item = item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunDnsLeakTest,
                    enabled = !leakTestState.isRunning,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("run_dns_leak_test_button")
                ) {
                    if (leakTestState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auditing DNS…")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (leakTestState.lastTestedAtMs > 0L) "Re-run Leak Test" else "Run DNS Leak Test"
                        )
                    }
                }

                if (!isFirewallActive) {
                    FilledTonalButton(
                        onClick = onRequestEnableFirewall,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("leak_enable_firewall_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Enable Firewall",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable VPN")
                    }
                }
            }

            if (preferences.isFirewallEnabled && !vpnRuntimeState.isTunnelActive) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tip: Enable Ad Blocker, Website Filter, or HTTPS Proxy so DNS routes through NetGate TUN.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun LeakTelemetryBadge(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent
            )
        }
    }
}

@Composable
private fun LeakCheckRow(item: DnsLeakCheckItem) {
    val itemAccent = when (item.status) {
        DnsLeakCheckStatus.PASS -> EmeraldPrimary
        DnsLeakCheckStatus.WARN -> WarningAmber
        DnsLeakCheckStatus.FAIL -> BlockedRed
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = itemAccent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, itemAccent.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (item.status) {
                    DnsLeakCheckStatus.PASS -> Icons.Rounded.CheckCircle
                    DnsLeakCheckStatus.WARN -> Icons.Rounded.WarningAmber
                    DnsLeakCheckStatus.FAIL -> Icons.Rounded.ErrorOutline
                },
                contentDescription = item.status.name,
                tint = itemAccent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VpnUpstreamDnsSelectorCard(
    preferences: FirewallPreferencesState,
    onSelectUpstreamDns: (UpstreamDnsServer) -> Unit,
    onSaveCustomDns: (dohUrl: String, udpIp: String) -> Unit,
    onToggleDohUpstream: (Boolean) -> Unit
) {
    var customDohInput by remember(preferences.customDohUrl) {
        mutableStateOf(preferences.customDohUrl)
    }
    var customUdpInput by remember(preferences.customUdpDnsIp) {
        mutableStateOf(preferences.customUdpDnsIp)
    }

    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = WifiAccent.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_upstream_dns_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VPN Upstream DNS Server",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Used by NetGate VPN DNS Proxy & HTTPS Proxy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = WifiAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Encrypted DoH Transport (RFC 8484)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send allowed queries over HTTPS instead of plaintext UDP/53",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = preferences.useDohUpstream,
                        onCheckedChange = onToggleDohUpstream,
                        modifier = Modifier.testTag("dns_tab_doh_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpstreamDnsServer.entries.forEach { server ->
                    val selected = preferences.selectedUpstreamDns == server
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectUpstreamDns(server) }
                            .testTag("upstream_dns_${server.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectUpstreamDns(server) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = server.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (server != UpstreamDnsServer.CUSTOM_DNS) {
                                    Text(
                                        text = "${server.primaryUdpIp} • ${server.dohUrl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (preferences.selectedUpstreamDns == UpstreamDnsServer.CUSTOM_DNS) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customDohInput,
                    onValueChange = { customDohInput = it },
                    label = { Text("Custom DoH Endpoint URL") },
                    placeholder = { Text("https://dns.nextdns.io/your-id") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_doh_url_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customUdpInput,
                    onValueChange = { customUdpInput = it },
                    label = { Text("Fallback UDP DNS IP") },
                    placeholder = { Text("1.1.1.1") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_udp_ip_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onSaveCustomDns(customDohInput, customUdpInput) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_custom_dns_button")
                ) {
                    Text("Save Custom Upstream DNS")
                }
            }
        }
    }
}

@Composable
private fun DnsStatusAndSetupCards(
    connectionDetails: ConnectionDetails,
    preferences: FirewallPreferencesState,
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
                                else -> "System Private DNS: Off / Default"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !connectionDetails.privateDnsHostname.isNullOrBlank() ->
                                    "Hostname: ${connectionDetails.privateDnsHostname}"
                                connectionDetails.dnsServers.isNotEmpty() ->
                                    "Interface DNS: ${connectionDetails.dnsServers.joinToString(", ")}"
                                else -> "Set Private DNS to ${selectedPreset.hostname} below"
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
                text = "System Private DNS (DoT) & Reachability",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Note: For Ad Blocker / Website Blocker inside NetGate VPN, keep Android Private DNS on Automatic and pick your upstream resolver above.",
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
                    Text("Testing ${preferences.selectedUpstreamDns.title} DoH…")
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
                                "${preferences.selectedUpstreamDns.title} DoH (${probeState.latencyMs} ms) — Retest"
                            probeState.errorMessage != null ->
                                "Retry DoH Latency Check"
                            else -> "Ping ${preferences.selectedUpstreamDns.title} DoH"
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
        text = "Android Private DNS (DoT) Hostnames",
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

