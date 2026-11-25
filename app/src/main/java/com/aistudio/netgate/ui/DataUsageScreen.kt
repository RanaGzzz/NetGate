package com.aistudio.netgate.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import com.aistudio.netgate.data.network.SpeedTestState
import com.aistudio.netgate.ui.components.SpeedTestContent
import com.aistudio.netgate.ui.components.WifiAnalysisContent
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AppUsageBreakdownItem
import com.aistudio.netgate.data.model.DataUsageSubTab
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.SecurityAuditAction
import com.aistudio.netgate.data.model.SiteTrafficFilter
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.data.model.UsageChartMode
import com.aistudio.netgate.data.model.UsageFilterCategory
import com.aistudio.netgate.data.model.UsageTimeRange
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.ui.components.BlockedVsAllowedBarChart
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.components.HistoricalTrafficChartCard
import com.aistudio.netgate.ui.components.SecurityAuditContent
import com.aistudio.netgate.ui.components.SentReceivedSummaryCard
import com.aistudio.netgate.ui.components.SitesAndRegionsContent
import com.aistudio.netgate.ui.components.SystemNetworkMonitorCard
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WifiAccent

@Composable
fun DataUsageContent(
    state: DataUsageUiState,
    liveTelemetry: LiveNetworkTelemetry,
    isFirewallActive: Boolean,
    isDeepMonitorEnabled: Boolean,
    isHttpsProxyActive: Boolean,
    isStatusBarSpeedEnabled: Boolean,
    statusBarSpeedStyle: StatusBarSpeedStyle,
    showStatusBarDailyUsage: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onSelectSubTab: (DataUsageSubTab) -> Unit,
    onTimeRangeChange: (UsageTimeRange) -> Unit,
    onChartModeChange: (UsageChartMode) -> Unit,
    onFilterCategoryChange: (UsageFilterCategory) -> Unit,
    onRefreshUsage: () -> Unit,
    onClearHistory: () -> Unit,
    onInspectApp: (String) -> Unit,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onSelectStatusBarStyle: (StatusBarSpeedStyle) -> Unit,
    onToggleShowDailyUsage: (Boolean) -> Unit,
    onResetLiveSession: () -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit,
    onSetShowLiveSitesFeed: (Boolean) -> Unit,
    onSiteFilterChange: (SiteTrafficFilter) -> Unit,
    onSiteSearchQueryChange: (String) -> Unit,
    onToggleCountryFilter: (String) -> Unit,
    onClearConnectionHistory: () -> Unit,
    onQuickBlockSite: (String) -> Unit,
    onQuickAllowSite: (String) -> Unit,
    onQuickBlockApp: (String) -> Unit = {},
    onQuickAllowApp: (String) -> Unit = {},
    onRunSecurityAudit: () -> Unit,
    onOpenFlaggedSitesFromAudit: () -> Unit,
    speedTestState: SpeedTestState,
    activeNetworkLabel: String,
    onStartSpeedTest: () -> Unit,
    onStopSpeedTest: () -> Unit,
    onRunVideoTest: () -> Unit,
    onRefreshWifi: () -> Unit = {},
    onClearObservedWifi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxAppBytes = (state.appItems.maxOfOrNull { it.totalBytes } ?: 1L).coerceAtLeast(1L)
    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = state.selectedSubTab != DataUsageSubTab.OVERVIEW) {
        onSelectSubTab(DataUsageSubTab.OVERVIEW)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("data_usage_screen")
    ) {
        val isWide = maxWidth >= 800.dp

        Column(modifier = Modifier.fillMaxSize()) {
            DataUsageSubTabBar(
                selectedSubTab = state.selectedSubTab,
                wifiCount = state.wifiDiagnostics.savedNetworks.size,
                sitesCount = state.liveConnections.size.coerceAtLeast(state.historicalConnections.size),
                flaggedCount = state.flaggedSitesCount,
                onSelectSubTab = onSelectSubTab,
                modifier = Modifier
                    .padding(horizontal = if (isWide) 20.dp else 16.dp, vertical = 4.dp)
            )

            when (state.selectedSubTab) {
                DataUsageSubTab.WIFI -> {
                    WifiAnalysisContent(
                        state = state.wifiDiagnostics,
                        onRefreshWifi = onRefreshWifi,
                        onClearObservedHistory = onClearObservedWifi,
                        bottomPadding = if (isWide) 24.dp else navInsetBottom + 96.dp,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                DataUsageSubTab.SPEED_TEST -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = if (isWide) 20.dp else 16.dp, vertical = 8.dp)
                    ) {
                        SpeedTestContent(
                            state = speedTestState,
                            activeNetworkLabel = activeNetworkLabel,
                            onStartTest = onStartSpeedTest,
                            onStopTest = onStopSpeedTest,
                            onRunVideoTest = onRunVideoTest
                        )
                    }
                }

                DataUsageSubTab.SITES_REGIONS -> {
                    SitesAndRegionsContent(
                        state = state,
                        isFirewallActive = isFirewallActive,
                        isDeepMonitorEnabled = isDeepMonitorEnabled,
                        isHttpsProxyActive = isHttpsProxyActive,
                        loadIcon = loadIcon,
                        onRequestEnableFirewall = onRequestEnableFirewall,
                        onToggleDeepMonitor = onToggleDeepMonitor,
                        onSetShowLiveFeed = onSetShowLiveSitesFeed,
                        onSiteFilterChange = onSiteFilterChange,
                        onSearchQueryChange = onSiteSearchQueryChange,
                        onToggleCountryFilter = onToggleCountryFilter,
                        onClearHistory = onClearConnectionHistory,
                        onQuickBlockSite = onQuickBlockSite,
                        onQuickAllowSite = onQuickAllowSite,
                        onQuickBlockApp = onQuickBlockApp,
                        onQuickAllowApp = onQuickAllowApp,
                        bottomPadding = if (isWide) 24.dp else navInsetBottom + 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                }

                DataUsageSubTab.SECURITY_AUDIT -> {
                    SecurityAuditContent(
                        report = state.securityAuditReport,
                        flaggedSitesCount = state.flaggedSitesCount,
                        onRunAudit = onRunSecurityAudit,
                        onSecurityAction = { action ->
                            when (action) {
                                SecurityAuditAction.OPEN_ACCESSIBILITY_SETTINGS ->
                                    openSafeSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                SecurityAuditAction.OPEN_DEVICE_ADMIN_SETTINGS ->
                                    openSafeSettings(context, Settings.ACTION_SECURITY_SETTINGS)
                                SecurityAuditAction.OPEN_DEVELOPER_SETTINGS ->
                                    openSafeSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                SecurityAuditAction.OPEN_USAGE_ACCESS ->
                                    openUsageAccessSettings(context)
                                SecurityAuditAction.FILTER_FLAGGED_SITES ->
                                    onOpenFlaggedSitesFromAudit()
                            }
                        },
                        bottomPadding = if (isWide) 24.dp else navInsetBottom + 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                }

                DataUsageSubTab.OVERVIEW -> {
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
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "live_system_monitor") {
                        SystemNetworkMonitorCard(
                            telemetry = liveTelemetry,
                            isStatusBarSpeedEnabled = isStatusBarSpeedEnabled,
                            statusBarSpeedStyle = statusBarSpeedStyle,
                            showStatusBarDailyUsage = showStatusBarDailyUsage,
                            onToggleStatusBarSpeed = onToggleStatusBarSpeed,
                            onSelectStatusBarStyle = onSelectStatusBarStyle,
                            onToggleShowDailyUsage = onToggleShowDailyUsage,
                            onResetSessionStats = onResetLiveSession,
                            onOpenSitesAndSecurity = { onSelectSubTab(DataUsageSubTab.SITES_REGIONS) }
                        )
                    }
                    item(key = "time_range") {
                        TimeRangeBar(
                            selectedRange = state.timeRange,
                            isSyncing = state.isSyncing,
                            hasUsageAccess = state.hasUsageAccess,
                            onSelectRange = onTimeRangeChange,
                            onRefresh = onRefreshUsage
                        )
                    }
                    if (!state.hasUsageAccess) {
                        item(key = "usage_access_card") {
                            UsageAccessPermissionCard(
                                onGrantClick = { openUsageAccessSettings(context) }
                            )
                        }
                    }
                    item(key = "sent_received_card") {
                        SentReceivedSummaryCard(
                            summary = state.summary,
                            periodLabel = state.timeRange.label
                        )
                    }
                    item(key = "historical_chart") {
                        HistoricalTrafficChartCard(
                            dailyHistory = state.dailyHistory,
                            historicalTotals = state.historicalTotals,
                            onClearHistory = onClearHistory
                        )
                    }
                    item(key = "bar_chart") {
                        BlockedVsAllowedBarChart(
                            summary = state.summary,
                            chartMode = state.chartMode,
                            selectedCategory = state.filterCategory,
                            periodLabel = state.timeRange.label,
                            onChartModeChange = onChartModeChange,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }
                    item(key = "summary_cards") {
                        BlockedVsAllowedSummaryCards(
                            state = state,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "breakdown_header") {
                        BreakdownHeaderRow(
                            selectedCategory = state.filterCategory,
                            itemCount = state.appItems.size,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }
                    if (state.appItems.isEmpty()) {
                        item(key = "empty_breakdown") {
                            EmptyUsageListCard(hasUsageAccess = state.hasUsageAccess)
                        }
                    } else {
                        items(
                            items = state.appItems,
                            key = { it.packageName }
                        ) { item ->
                            AppUsageBreakdownRow(
                                item = item,
                                maxBytes = maxAppBytes,
                                loadIcon = loadIcon,
                                onClick = { onInspectApp(item.packageName) }
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = navInsetBottom + 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "live_system_monitor") {
                        SystemNetworkMonitorCard(
                            telemetry = liveTelemetry,
                            isStatusBarSpeedEnabled = isStatusBarSpeedEnabled,
                            statusBarSpeedStyle = statusBarSpeedStyle,
                            showStatusBarDailyUsage = showStatusBarDailyUsage,
                            onToggleStatusBarSpeed = onToggleStatusBarSpeed,
                            onSelectStatusBarStyle = onSelectStatusBarStyle,
                            onToggleShowDailyUsage = onToggleShowDailyUsage,
                            onResetSessionStats = onResetLiveSession,
                            onOpenSitesAndSecurity = { onSelectSubTab(DataUsageSubTab.SITES_REGIONS) }
                        )
                    }
                    item(key = "time_range") {
                        TimeRangeBar(
                            selectedRange = state.timeRange,
                            isSyncing = state.isSyncing,
                            hasUsageAccess = state.hasUsageAccess,
                            onSelectRange = onTimeRangeChange,
                            onRefresh = onRefreshUsage
                        )
                    }

                    if (!state.hasUsageAccess) {
                        item(key = "usage_access_card") {
                            UsageAccessPermissionCard(
                                onGrantClick = { openUsageAccessSettings(context) }
                            )
                        }
                    }

                    state.errorMessage?.let { err ->
                        item(key = "usage_error") {
                            FrostedSurface(
                                shape = RoundedCornerShape(16.dp),
                                borderAccent = BlockedRed,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BlockedRed,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    item(key = "sent_received_card") {
                        SentReceivedSummaryCard(
                            summary = state.summary,
                            periodLabel = state.timeRange.label
                        )
                    }

                    item(key = "historical_chart") {
                        HistoricalTrafficChartCard(
                            dailyHistory = state.dailyHistory,
                            historicalTotals = state.historicalTotals,
                            onClearHistory = onClearHistory
                        )
                    }

                    item(key = "bar_chart") {
                        BlockedVsAllowedBarChart(
                            summary = state.summary,
                            chartMode = state.chartMode,
                            selectedCategory = state.filterCategory,
                            periodLabel = state.timeRange.label,
                            onChartModeChange = onChartModeChange,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }

                    item(key = "summary_cards") {
                        BlockedVsAllowedSummaryCards(
                            state = state,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }

                    item(key = "breakdown_header") {
                        BreakdownHeaderRow(
                            selectedCategory = state.filterCategory,
                            itemCount = state.appItems.size,
                            onSelectCategory = onFilterCategoryChange
                        )
                    }

                    if (state.appItems.isEmpty()) {
                        item(key = "empty_breakdown") {
                            EmptyUsageListCard(hasUsageAccess = state.hasUsageAccess)
                        }
                    } else {
                        items(
                            items = state.appItems,
                            key = { it.packageName }
                        ) { item ->
                            AppUsageBreakdownRow(
                                item = item,
                                maxBytes = maxAppBytes,
                                loadIcon = loadIcon,
                                onClick = { onInspectApp(item.packageName) }
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
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
private fun DataUsageSubTabBar(
    selectedSubTab: DataUsageSubTab,
    wifiCount: Int,
    sitesCount: Int,
    flaggedCount: Int,
    onSelectSubTab: (DataUsageSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useWeight = maxWidth >= 640.dp
        val rowModifier = if (useWeight) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        }
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DataUsageSubTab.entries.forEach { tab ->
                val selected = selectedSubTab == tab
                val icon = when (tab) {
                    DataUsageSubTab.OVERVIEW -> Icons.Rounded.BarChart
                    DataUsageSubTab.WIFI -> Icons.Rounded.Wifi
                    DataUsageSubTab.SITES_REGIONS -> Icons.Rounded.Public
                    DataUsageSubTab.SECURITY_AUDIT -> Icons.Rounded.Security
                    DataUsageSubTab.SPEED_TEST -> Icons.Rounded.Speed
                }
                val badgeText = when (tab) {
                    DataUsageSubTab.OVERVIEW -> null
                    DataUsageSubTab.WIFI -> if (wifiCount > 0) "$wifiCount" else null
                    DataUsageSubTab.SITES_REGIONS -> if (sitesCount > 0) "$sitesCount" else null
                    DataUsageSubTab.SECURITY_AUDIT -> if (flaggedCount > 0) "$flaggedCount!" else null
                    DataUsageSubTab.SPEED_TEST -> null
                }
                val badgeColor = if (tab == DataUsageSubTab.SECURITY_AUDIT && flaggedCount > 0) {
                    BlockedRed
                } else {
                    MaterialTheme.colorScheme.primary
                }

                val pillModifier = if (useWeight) {
                    Modifier.weight(1f)
                } else {
                    Modifier.defaultMinSize(minWidth = 104.dp)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    } else if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        }
                    ),
                    modifier = pillModifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSubTab(tab) }
                        .testTag("data_usage_subtab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .then(if (useWeight) Modifier.fillMaxSize() else Modifier.fillMaxHeight())
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TimeRangeBar(
    selectedRange: UsageTimeRange,
    isSyncing: Boolean,
    hasUsageAccess: Boolean,
    onSelectRange: (UsageTimeRange) -> Unit,
    onRefresh: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UsageTimeRange.entries.forEach { range ->
                    val selected = selectedRange == range
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectRange(range) },
                        label = {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (isDark) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                            },
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("usage_range_${range.name.lowercase()}")
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.55f else 0.78f),
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !isSyncing, onClick = onRefresh)
                    .testTag("sync_usage_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Sync data usage",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasUsageAccess) "Sync" else "Check",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (isSyncing) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun UsageAccessPermissionCard(
    onGrantClick: () -> Unit
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = MaterialTheme.colorScheme.primary,
        tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usage_access_permission_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Usage Access Required for Live Stats",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To read real Wi-Fi and Mobile Data consumption per app from Android's NetworkStatsManager and compare Blocked vs. Allowed apps in Room, enable Usage Access for NetGate. No Accessibility permissions or root are needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_usage_access_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Usage Access")
            }
        }
    }
}

@Composable
private fun BlockedVsAllowedSummaryCards(
    state: DataUsageUiState,
    onSelectCategory: (UsageFilterCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CategorySummaryCard(
            title = "Blocked Apps",
            totalBytes = state.summary.blockedTotalBytes,
            wifiBytes = state.summary.blockedWifiBytes,
            mobileBytes = state.summary.blockedMobileBytes,
            appsCount = state.summary.blockedAppsCount,
            accent = BlockedRed,
            isSelected = state.filterCategory == UsageFilterCategory.BLOCKED,
            icon = Icons.Rounded.Block,
            onClick = {
                onSelectCategory(
                    if (state.filterCategory == UsageFilterCategory.BLOCKED) {
                        UsageFilterCategory.ALL
                    } else {
                        UsageFilterCategory.BLOCKED
                    }
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag("blocked_summary_card")
        )

        CategorySummaryCard(
            title = "Allowed Apps",
            totalBytes = state.summary.allowedTotalBytes,
            wifiBytes = state.summary.allowedWifiBytes,
            mobileBytes = state.summary.allowedMobileBytes,
            appsCount = state.summary.allowedAppsCount,
            accent = EmeraldPrimary,
            isSelected = state.filterCategory == UsageFilterCategory.ALLOWED,
            icon = Icons.Rounded.CheckCircle,
            onClick = {
                onSelectCategory(
                    if (state.filterCategory == UsageFilterCategory.ALLOWED) {
                        UsageFilterCategory.ALL
                    } else {
                        UsageFilterCategory.ALLOWED
                    }
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag("allowed_summary_card")
        )
    }
}

@Composable
private fun CategorySummaryCard(
    title: String,
    totalBytes: Long,
    wifiBytes: Long,
    mobileBytes: Long,
    appsCount: Int,
    accent: Color,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        tintColor = if (isSelected) accent.copy(alpha = 0.16f) else null,
        borderAccent = if (isSelected) accent else accent.copy(alpha = 0.35f),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDataBytes(totalBytes),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$appsCount app${if (appsCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Wi-Fi ${formatDataBytes(wifiBytes)} • Data ${formatDataBytes(mobileBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BreakdownHeaderRow(
    selectedCategory: UsageFilterCategory,
    itemCount: Int,
    onSelectCategory: (UsageFilterCategory) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Per-App Room Breakdown ($itemCount)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsageFilterCategory.entries.forEach { category ->
                val selected = selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                        },
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                    ),
                    modifier = Modifier.testTag("usage_filter_${category.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun AppUsageBreakdownRow(
    item: AppUsageBreakdownItem,
    maxBytes: Long,
    loadIcon: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit
) {
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.packageName) {
        value = loadIcon(item.packageName)
    }
    val accent = if (item.isBlocked) BlockedRed else EmeraldPrimary
    val progress = (item.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = if (item.isBlocked) BlockedRed.copy(alpha = 0.45f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("usage_app_row_${item.packageName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap!!,
                                contentDescription = item.appName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = item.appName,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = when {
                                    item.blockWifi && item.blockMobileData -> "Blocked"
                                    item.blockWifi -> "Wi-Fi Off"
                                    item.blockMobileData -> "Data Off"
                                    else -> "Allowed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Wi-Fi ${formatDataBytes(item.wifiBytes)} • Data ${formatDataBytes(item.mobileBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "↓ ${formatDataBytes(item.totalRxBytes)} received • ↑ ${formatDataBytes(item.totalTxBytes)} sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatDataBytes(item.totalBytes),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }

            if (item.totalBytes > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    val wifiFraction = (item.wifiBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                    ) {
                        if (wifiFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(wifiFraction.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(if (item.isBlocked) BlockedRed else WifiAccent)
                            )
                        }
                        val mobileFraction = (1f - wifiFraction).coerceAtLeast(0f)
                        if (mobileFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(mobileFraction.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(if (item.isBlocked) BlockedRed.copy(alpha = 0.65f) else DataAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyUsageListCard(hasUsageAccess: Boolean) {
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.DataUsage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasUsageAccess) "No matching app usage yet" else "Grant Usage Access above",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasUsageAccess) {
                    "Switch filter or time window, or tap Sync to refresh."
                } else {
                    "Once enabled, NetGate syncs real per-app usage into the Room database."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openSafeSettings(context: Context, action: String) {
    val candidates = listOf(
        Intent(action),
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

private fun openUsageAccessSettings(context: Context) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val candidates = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = pkgUri },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
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
