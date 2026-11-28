package com.aistudio.netgate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.TempAllowDuration
import com.aistudio.netgate.ui.components.DnsLogFilter
import com.aistudio.netgate.ui.components.DnsLogsFilterCard
import com.aistudio.netgate.ui.components.DpiBypassShieldCard
import com.aistudio.netgate.ui.components.DpiNetworkGuardsCard
import com.aistudio.netgate.ui.components.EmptyWebsiteRulesCard
import com.aistudio.netgate.ui.components.FilterLogEntryCard
import com.aistudio.netgate.ui.components.GlassTabItem
import com.aistudio.netgate.ui.components.GlassTabVariant
import com.aistudio.netgate.ui.components.GlassTabs
import com.aistudio.netgate.ui.components.rememberBottomNavClearancePadding
import com.aistudio.netgate.ui.components.HttpsFilteringProxyCard
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme

enum class SniDpiTab(val label: String) {
    SETTINGS("Settings"),
    LIVE_LOGS("Live Logs")
}

@Composable
fun SniDpiContent(
    state: WebsiteBlockerUiState,
    isFirewallActive: Boolean,
    onToggleHttpsProxy: (Boolean) -> Unit,
    onSelectProxyPort: (Int) -> Unit,
    onToggleProxyStripTracking: (Boolean) -> Unit,
    onSelectSniPreset: (SniProxyPreset) -> Unit,
    onSaveCustomSniProxy: (host: String, port: Int) -> Unit,
    onSelectDpiMode: (DpiBypassMode) -> Unit,
    onSelectDpiChunkSize: (Int) -> Unit,
    onToggleDpiIdleKeepAlive: (Boolean) -> Unit,
    onToggleBlockQuic: (Boolean) -> Unit,
    onToggleAntiDnsPoison: (Boolean) -> Unit,
    onSelectDnsTransportMode: (DnsTransportMode) -> Unit,
    onFlushDpiCache: () -> Unit,
    onCopiedProxyEndpoint: (String) -> Unit,
    onClearDnsLogs: () -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onOpenDpiLogs: () -> Unit,
    onAllowDomainTemporarily: (domain: String, duration: TempAllowDuration) -> Unit,
    onAllowDomainPermanently: (domain: String) -> Unit,
    onBlockDomainPermanently: (domain: String) -> Unit,
    onReportFalsePositive: (domain: String) -> Unit,
    onNotifyCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(SniDpiTab.SETTINGS) }
    var logSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLogFilter by rememberSaveable { mutableStateOf(DnsLogFilter.ALL) }

    BackHandler(enabled = selectedTab != SniDpiTab.SETTINGS) {
        selectedTab = SniDpiTab.SETTINGS
    }

    val filteredLogs = remember(state.dnsQueryLogs, logSearchQuery, selectedLogFilter) {
        val q = logSearchQuery.trim().lowercase()
        state.dnsQueryLogs.filter { entry ->
            val matchesFilter = when (selectedLogFilter) {
                DnsLogFilter.ALL -> true
                DnsLogFilter.ADS_BLOCKED -> entry.status != DnsQueryStatus.ALLOWED &&
                    entry.status != DnsQueryStatus.PASSTHROUGH &&
                    entry.status != DnsQueryStatus.SAFE_REWRITE
                DnsLogFilter.ALLOWED -> entry.status == DnsQueryStatus.ALLOWED ||
                    entry.status == DnsQueryStatus.PASSTHROUGH ||
                    entry.status == DnsQueryStatus.SAFE_REWRITE
            }
            val matchesQuery = q.isEmpty() ||
                entry.domain.lowercase().contains(q) ||
                entry.reason.lowercase().contains(q) ||
                (entry.appName?.lowercase()?.contains(q) == true)
            matchesFilter && matchesQuery
        }
    }

    val blockedLogCount = remember(state.dnsQueryLogs) {
        state.dnsQueryLogs.count {
            it.status == DnsQueryStatus.BLOCKED_AD ||
                it.status == DnsQueryStatus.BLOCKED_WEBSITE ||
                it.status == DnsQueryStatus.BLOCKED_HTTPS ||
                it.status == DnsQueryStatus.BLOCKED_APP
        }
    }
    val allowedLogCount = remember(state.dnsQueryLogs, blockedLogCount) {
        (state.dnsQueryLogs.size - blockedLogCount).coerceAtLeast(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("sni_dpi_screen")
    ) {
        val isWide = maxWidth >= 800.dp
        val listBottomPadding = if (isWide) 24.dp else rememberBottomNavClearancePadding()
        val sidePadding = if (isWide) 20.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isWide) 1280.dp else 680.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 4.dp)
            ) {
                Text(
                    text = "SNI & DPI",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Advanced network routing, bypass strategies & logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                SniDpiSegmentedTabToggle(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = SniDpiTab.entries.indexOf(targetState) >=
                        SniDpiTab.entries.indexOf(initialState)
                    val enter = slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> if (forward) fullWidth / 6 else -fullWidth / 6 }
                    ) + fadeIn(tween(180))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 8 else fullWidth / 8 }
                    ) + fadeOut(tween(140))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "sni_dpi_tab_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { tab ->
                when (tab) {
                    SniDpiTab.SETTINGS -> {
                        if (isWide) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("sni_dpi_settings_list"),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    item(key = "https_filtering_proxy_card") {
                                        HttpsFilteringProxyCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleHttpsProxy = onToggleHttpsProxy,
                                            onSelectProxyPort = onSelectProxyPort,
                                            onToggleStripTracking = onToggleProxyStripTracking,
                                            onSelectSniPreset = onSelectSniPreset,
                                            onSaveCustomSniProxy = onSaveCustomSniProxy,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onCopiedEndpoint = onCopiedProxyEndpoint,
                                            collapsibleDetails = true,
                                            initiallyExpanded = false
                                        )
                                    }
                                    item(key = "dpi_bypass_card") {
                                        DpiBypassShieldCard(
                                            dpiBypassMode = state.dpiBypassMode,
                                            dpiFragmentChunkSize = state.dpiFragmentChunkSize,
                                            dpiIdleKeepAliveEnabled = state.dpiIdleKeepAliveEnabled,
                                            blockQuicForDpiBypass = state.blockQuicForDpiBypass,
                                            antiDnsPoisonEnabled = state.antiDnsPoisonEnabled,
                                            dnsTransportMode = state.dnsTransportMode,
                                            dpiBypassedConnectionsCount = state.dpiBypassedConnectionsCount,
                                            dpiAutopilotLearnedCount = state.dpiAutopilotLearnedCount,
                                            antiDnsPoisonBlockedCount = state.antiDnsPoisonBlockedCount,
                                            quicStrippedCount = state.quicStrippedCount,
                                            isFirewallActive = isFirewallActive,
                                            onSelectDpiMode = onSelectDpiMode,
                                            onSelectChunkSize = onSelectDpiChunkSize,
                                            onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                                            onToggleBlockQuic = onToggleBlockQuic,
                                            onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                                            onSelectDnsTransportMode = onSelectDnsTransportMode,
                                            onFlushCache = onFlushDpiCache,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onOpenVpnDpiLogs = onOpenDpiLogs,
                                            collapsibleTuning = true,
                                            initiallyTuningExpanded = false,
                                            showBottomGuardsInCard = false
                                        )
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    item(key = "dpi_network_guards_card") {
                                        DpiNetworkGuardsCard(
                                            blockQuicForDpiBypass = state.blockQuicForDpiBypass,
                                            antiDnsPoisonEnabled = state.antiDnsPoisonEnabled,
                                            dpiIdleKeepAliveEnabled = state.dpiIdleKeepAliveEnabled,
                                            antiDnsPoisonBlockedCount = state.antiDnsPoisonBlockedCount,
                                            quicStrippedCount = state.quicStrippedCount,
                                            onToggleBlockQuic = onToggleBlockQuic,
                                            onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                                            onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                                            onFlushCache = onFlushDpiCache,
                                            onOpenVpnDpiLogs = onOpenDpiLogs
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("sni_dpi_settings_list"),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = listBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "https_filtering_proxy_card") {
                                    HttpsFilteringProxyCard(
                                        state = state,
                                        isFirewallActive = isFirewallActive,
                                        onToggleHttpsProxy = onToggleHttpsProxy,
                                        onSelectProxyPort = onSelectProxyPort,
                                        onToggleStripTracking = onToggleProxyStripTracking,
                                        onSelectSniPreset = onSelectSniPreset,
                                        onSaveCustomSniProxy = onSaveCustomSniProxy,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onCopiedEndpoint = onCopiedProxyEndpoint,
                                        collapsibleDetails = true,
                                        initiallyExpanded = false
                                    )
                                }
                                item(key = "dpi_bypass_card") {
                                    DpiBypassShieldCard(
                                        dpiBypassMode = state.dpiBypassMode,
                                        dpiFragmentChunkSize = state.dpiFragmentChunkSize,
                                        dpiIdleKeepAliveEnabled = state.dpiIdleKeepAliveEnabled,
                                        blockQuicForDpiBypass = state.blockQuicForDpiBypass,
                                        antiDnsPoisonEnabled = state.antiDnsPoisonEnabled,
                                        dnsTransportMode = state.dnsTransportMode,
                                        dpiBypassedConnectionsCount = state.dpiBypassedConnectionsCount,
                                        dpiAutopilotLearnedCount = state.dpiAutopilotLearnedCount,
                                        antiDnsPoisonBlockedCount = state.antiDnsPoisonBlockedCount,
                                        quicStrippedCount = state.quicStrippedCount,
                                        isFirewallActive = isFirewallActive,
                                        onSelectDpiMode = onSelectDpiMode,
                                        onSelectChunkSize = onSelectDpiChunkSize,
                                        onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                                        onToggleBlockQuic = onToggleBlockQuic,
                                        onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                                        onSelectDnsTransportMode = onSelectDnsTransportMode,
                                        onFlushCache = onFlushDpiCache,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onOpenVpnDpiLogs = onOpenDpiLogs,
                                        collapsibleTuning = true,
                                        initiallyTuningExpanded = false,
                                        showBottomGuardsInCard = false
                                    )
                                }
                                item(key = "dpi_network_guards_card") {
                                    DpiNetworkGuardsCard(
                                        blockQuicForDpiBypass = state.blockQuicForDpiBypass,
                                        antiDnsPoisonEnabled = state.antiDnsPoisonEnabled,
                                        dpiIdleKeepAliveEnabled = state.dpiIdleKeepAliveEnabled,
                                        antiDnsPoisonBlockedCount = state.antiDnsPoisonBlockedCount,
                                        quicStrippedCount = state.quicStrippedCount,
                                        onToggleBlockQuic = onToggleBlockQuic,
                                        onToggleAntiDnsPoison = onToggleAntiDnsPoison,
                                        onToggleIdleKeepAlive = onToggleDpiIdleKeepAlive,
                                        onFlushCache = onFlushDpiCache,
                                        onOpenVpnDpiLogs = onOpenDpiLogs
                                    )
                                }
                            }
                        }
                    }

                    SniDpiTab.LIVE_LOGS -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("sni_dpi_live_logs_list"),
                            contentPadding = PaddingValues(
                                start = sidePadding,
                                end = sidePadding,
                                top = 8.dp,
                                bottom = listBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "live_logs_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filter Log (Live Feed)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = onOpenDpiLogs,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("sni_dpi_open_vpn_logs_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Terminal,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("VPN & DPI", maxLines = 1, softWrap = false)
                                        }
                                        if (state.dnsQueryLogs.isNotEmpty()) {
                                            TextButton(
                                                onClick = onClearDnsLogs,
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("clear_dns_logs_button")
                                            ) {
                                                Text("Clear (${state.dnsQueryLogs.size})")
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "dns_logs_filter") {
                                DnsLogsFilterCard(
                                    searchQuery = logSearchQuery,
                                    selectedFilter = selectedLogFilter,
                                    totalLogsCount = state.dnsQueryLogs.size,
                                    onSearchQueryChange = { logSearchQuery = it },
                                    onSelectFilter = { selectedLogFilter = it },
                                    blockedCount = blockedLogCount,
                                    allowedCount = allowedLogCount
                                )
                            }
                            if (filteredLogs.isEmpty()) {
                                item(key = "empty_dns_logs") {
                                    EmptyWebsiteRulesCard(
                                        title = if (state.dnsQueryLogs.isEmpty()) {
                                            "No DNS or proxy activity yet"
                                        } else {
                                            "No logs match current filter"
                                        },
                                        subtitle = "Enable NetGate Firewall and browse to inspect live ad blocking, DPI bypass, and DNS lookups here.",
                                        icon = Icons.Rounded.Dns
                                    )
                                }
                            } else {
                                items(items = filteredLogs, key = { "dnslog_${it.id}" }) { entry ->
                                    FilterLogEntryCard(
                                        entry = entry,
                                        onAllowTemporarily = onAllowDomainTemporarily,
                                        onAllowPermanently = onAllowDomainPermanently,
                                        onBlockPermanently = onBlockDomainPermanently,
                                        onReportFalsePositive = onReportFalsePositive,
                                        onNotifyCopied = onNotifyCopied
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SniDpiSegmentedTabToggle(
    selectedTab: SniDpiTab,
    onSelectTab: (SniDpiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = SniDpiTab.entries
    val tabItems = tabs.map { tab ->
        val (activeIcon, inactiveIcon) = when (tab) {
            SniDpiTab.SETTINGS -> Icons.Rounded.Tune to Icons.Outlined.Tune
            SniDpiTab.LIVE_LOGS -> Icons.Rounded.Terminal to Icons.Outlined.Terminal
        }
        GlassTabItem(
            label = tab.label,
            testTag = "sni_dpi_tab_${tab.name.lowercase()}",
            activeIcon = activeIcon,
            inactiveIcon = inactiveIcon
        )
    }
    GlassTabs(
        items = tabItems,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        onSelect = { index -> onSelectTab(tabs[index]) },
        variant = GlassTabVariant.TOP_SEGMENTED,
        modifier = modifier.testTag("sni_dpi_segmented_tabs")
    )
}
