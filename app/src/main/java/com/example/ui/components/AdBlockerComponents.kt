package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdFilterListItem
import com.example.data.model.DnsQueryLogEntry
import com.example.data.model.DnsQueryStatus
import com.example.ui.WebsiteBlockerUiState
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WifiAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DnsLogFilter(val label: String) {
    ALL("All"),
    ADS_BLOCKED("Ads Blocked"),
    ALLOWED("Allowed")
}

@Composable
fun AdBlockerHeroCard(
    state: WebsiteBlockerUiState,
    isFirewallActive: Boolean,
    onToggleAdBlocker: (Boolean) -> Unit,
    onSyncFilterLists: () -> Unit,
    onToggleEssentialPassthrough: (Boolean) -> Unit,
    onToggleUseDohUpstream: (Boolean) -> Unit,
    onRequestEnableFirewall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = if (state.isAdBlockerEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (state.isAdBlockerEnabled) EmeraldPrimary.copy(alpha = 0.55f) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("ad_blocker_hero_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = activeAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isAdBlockerEnabled) {
                                    Icons.Rounded.GppGood
                                } else {
                                    Icons.Rounded.Security
                                },
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ad & Tracker Blocker",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !state.isAdBlockerEnabled -> "Off • Block ads, trackers & malware system-wide"
                                isFirewallActive -> "Active • Blocking ads across all apps & browsers"
                                else -> "Ready • Enable NetGate Firewall to start filtering"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !state.isAdBlockerEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                isFirewallActive -> EmeraldPrimary
                                else -> WarningAmber
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Switch(
                    checked = state.isAdBlockerEnabled,
                    onCheckedChange = { enabled ->
                        onToggleAdBlocker(enabled)
                        if (enabled && !isFirewallActive) {
                            onRequestEnableFirewall()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("ad_blocker_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdStatPill(
                    label = "Session Ads",
                    value = state.blockedAdsCount.toString(),
                    accent = BlockedRed,
                    modifier = Modifier.weight(1f)
                )
                AdStatPill(
                    label = "Lifetime",
                    value = formatCompactNumber(state.lifetimeAdsBlockedCount),
                    accent = WifiAccent,
                    modifier = Modifier.weight(1f)
                )
                AdStatPill(
                    label = "Domains",
                    value = formatCompactNumber(state.loadedAdDomainsCount.toLong()),
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isAdBlockerEnabled && !isFirewallActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestEnableFirewall,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("ad_blocker_enable_firewall_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activate Firewall & Ad Blocker")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FilledTonalButton(
                onClick = onSyncFilterLists,
                enabled = !state.isSyncingAdLists,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("sync_ad_lists_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (state.isSyncingAdLists) {
                        "Downloading Filter Lists…"
                    } else {
                        "Sync Enabled Filter Lists"
                    }
                )
            }

            if (state.isSyncingAdLists) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(10.dp))

            AdOptionToggleRow(
                title = "${state.selectedUpstreamDns.title} DoH Upstream",
                subtitle = "Encrypt allowed DNS lookups via HTTPS (automatic UDP/53 fallback)",
                checked = state.useDohUpstream,
                onCheckedChange = onToggleUseDohUpstream,
                testTag = "ad_doh_upstream_switch"
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdOptionToggleRow(
                title = "Essential OS & Push Passthrough",
                subtitle = "Always allow connectivity checks, payment gateways & FCM push services",
                checked = state.isEssentialPassthroughEnabled,
                onCheckedChange = onToggleEssentialPassthrough,
                testTag = "ad_essential_passthrough_switch"
            )
        }
    }
}

@Composable
fun HttpsFilteringProxyCard(
    state: WebsiteBlockerUiState,
    isFirewallActive: Boolean,
    onToggleHttpsProxy: (Boolean) -> Unit,
    onSelectProxyPort: (Int) -> Unit,
    onToggleStripTracking: (Boolean) -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onCopiedEndpoint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val endpoint = "127.0.0.1:${state.httpsProxyPort}"
    val activeAccent = when {
        state.isHttpsProxyEnabled && isFirewallActive && state.isHttpsProxyActive -> EmeraldPrimary
        state.isHttpsProxyEnabled -> WifiAccent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (state.isHttpsProxyEnabled) activeAccent.copy(alpha = 0.55f) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("https_filtering_proxy_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = activeAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.VpnLock,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "HTTPS Filtering Proxy & VPN",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !state.isHttpsProxyEnabled ->
                                    "Inspects CONNECT hosts, TLS SNI & ad URLs root-free"
                                isFirewallActive && state.isHttpsProxyActive ->
                                    "Listening on $endpoint • VPN Proxy attached"
                                else ->
                                    "Ready on $endpoint — activate Firewall to start"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isHttpsProxyEnabled) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Switch(
                    checked = state.isHttpsProxyEnabled,
                    onCheckedChange = { enabled ->
                        onToggleHttpsProxy(enabled)
                        if (enabled && !isFirewallActive) {
                            onRequestEnableFirewall()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("https_proxy_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdStatPill(
                    label = "Proxy Blocked",
                    value = state.blockedProxyRequestsCount.toString(),
                    accent = BlockedRed,
                    modifier = Modifier.weight(1f)
                )
                AdStatPill(
                    label = "Relayed",
                    value = state.allowedProxyRequestsCount.toString(),
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WifiAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WifiAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = endpoint,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = WifiAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Local Proxy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("NetGate Proxy", endpoint))
                                onCopiedEndpoint(endpoint)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("copy_https_proxy_endpoint")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy $endpoint",
                                tint = WifiAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Port:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(8899, 8080, 8888).forEach { presetPort ->
                    FilterChip(
                        selected = state.httpsProxyPort == presetPort,
                        onClick = { onSelectProxyPort(presetPort) },
                        label = { Text(presetPort.toString(), style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("proxy_port_chip_$presetPort")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AdOptionToggleRow(
                title = "Strip URL Tracking Parameters",
                subtitle = "Removes utm_*, fbclid, gclid, igshid & ref params on HTTP requests",
                checked = state.isProxyStripTrackingParams,
                onCheckedChange = onToggleStripTracking,
                testTag = "proxy_strip_tracking_switch"
            )
        }
    }
}

@Composable
private fun AdOptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
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
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun AdStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AdFilterListRow(
    item: AdFilterListItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDeleteCustomList: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = when {
        !item.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        item.category.contains("Malware", ignoreCase = true) ||
            item.category.contains("Phishing", ignoreCase = true) -> BlockedRed
        else -> EmeraldPrimary
    }

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = if (item.isEnabled) accent.copy(alpha = 0.45f) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("ad_filter_list_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val syncLabel = if (item.lastSyncedAt > 0L) {
                    "${formatCompactNumber(item.domainCount.toLong())} domains • Synced ${formatTimestampShort(item.lastSyncedAt)}"
                } else {
                    "Not synced yet — tap Sync to download remote hosts"
                }
                Text(
                    text = syncLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.lastSyncedAt > 0L) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDeleteCustomList != null) {
                IconButton(
                    onClick = onDeleteCustomList,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_custom_filter_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${item.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = item.isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("ad_filter_switch_${item.id}")
            )
        }
    }
}

@Composable
fun DnsLogsFilterCard(
    searchQuery: String,
    selectedFilter: DnsLogFilter,
    totalLogsCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (DnsLogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = WifiAccent.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = WifiAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live DNS Query Log ($totalLogsCount)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Block or Allow on any lookup to customize your Website Rules instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                                text = "Search domains in DNS log…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dns_log_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear DNS log search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnsLogFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dns_log_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
fun DnsQueryLogRow(
    entry: DnsQueryLogEntry,
    onBlockDomain: (String) -> Unit,
    onAllowDomain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (entry.status) {
        DnsQueryStatus.BLOCKED_AD -> BlockedRed
        DnsQueryStatus.BLOCKED_WEBSITE -> BlockedRed
        DnsQueryStatus.BLOCKED_HTTPS -> BlockedRed
        DnsQueryStatus.BLOCKED_APP -> WarningAmber
        DnsQueryStatus.PASSTHROUGH -> DataAccent
        DnsQueryStatus.ALLOWED -> EmeraldPrimary
    }
    val isBlocked = entry.status == DnsQueryStatus.BLOCKED_AD ||
        entry.status == DnsQueryStatus.BLOCKED_WEBSITE ||
        entry.status == DnsQueryStatus.BLOCKED_HTTPS ||
        entry.status == DnsQueryStatus.BLOCKED_APP

    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = accent.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dns_log_row_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (entry.status) {
                            DnsQueryStatus.BLOCKED_AD, DnsQueryStatus.BLOCKED_WEBSITE -> Icons.Rounded.Block
                            DnsQueryStatus.BLOCKED_HTTPS -> Icons.Rounded.Http
                            DnsQueryStatus.BLOCKED_APP -> Icons.Rounded.Shield
                            DnsQueryStatus.PASSTHROUGH -> Icons.Rounded.VerifiedUser
                            DnsQueryStatus.ALLOWED -> Icons.Rounded.CheckCircle
                        },
                        contentDescription = entry.status.label,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.domain,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.status.label} • ${entry.reason} • ${formatTimeHms(entry.timestampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (isBlocked) {
                OutlinedButton(
                    onClick = { onAllowDomain(entry.domain) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dns_log_allow_${entry.domain}")
                ) {
                    Text("Allow", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = { onBlockDomain(entry.domain) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dns_log_block_${entry.domain}")
                ) {
                    Text("Block", style = MaterialTheme.typography.labelSmall, color = BlockedRed)
                }
            }
        }
    }
}

private fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatTimestampShort(millis: Long): String {
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun formatTimeHms(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
