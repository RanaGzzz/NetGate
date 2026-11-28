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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
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
import com.aistudio.netgate.ui.components.GlassTabItem
import com.aistudio.netgate.ui.components.GlassTabVariant
import com.aistudio.netgate.ui.components.GlassTabs
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.components.rememberBottomNavClearancePadding
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
    val portraitBottomPadding = rememberBottomNavClearancePadding()

    BackHandler(enabled = state.selectedSubTab != DataUsageSubTab.OVERVIEW) {
        onSelectSubTab(DataUsageSubTab.OVERVIEW)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("data_usage_screen")
    ) {
        val isWide = maxWidth >= 800.dp

        val isStandaloneTool = state.selectedSubTab == DataUsageSubTab.WIFI ||
            state.selectedSubTab == DataUsageSubTab.SPEED_TEST

        Column(modifier = Modifier.fillMaxSize()) {
            if (!isStandaloneTool) {
                DataUsageSubTabBar(
                    selectedSubTab = state.selectedSubTab,
                    sitesCount = state.liveConnections.size.coerceAtLeast(state.historicalConnections.size),
                    flaggedCount = state.flaggedSitesCount,
                    onSelectSubTab = onSelectSubTab,
                    modifier = Modifier
                        .padding(horizontal = if (isWide) 20.dp else 16.dp, vertical = 4.dp)
                )
            }

            when (state.selectedSubTab) {
                DataUsageSubTab.WIFI -> {
                    WifiAnalysisContent(
                        state = state.wifiDiagnostics,
                        onRefreshWifi = onRefreshWifi,
                        onClearObservedHistory = onClearObservedWifi,
                        bottomPadding = if (isWide) 24.dp else portraitBottomPadding,
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
                            onRunVideoTest = onRunVideoTest,
                            bottomPadding = if (isWide) 24.dp else portraitBottomPadding
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
                        bottomPadding = if (isWide) 24.dp else portraitBottomPadding,
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
                        bottomPadding = if (isWide) 24.dp else portraitBottomPadding,
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
                        bottom = portraitBottomPadding
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

private val USAGE_SUB_TABS: List<DataUsageSubTab> = listOf(
    DataUsageSubTab.OVERVIEW,
    DataUsageSubTab.SITES_REGIONS,
    DataUsageSubTab.SECURITY_AUDIT
)

@Composable
private fun DataUsageSubTabBar(
    selectedSubTab: DataUsageSubTab,
    sitesCount: Int,
    flaggedCount: Int,
    onSelectSubTab: (DataUsageSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultBadgeColor = MaterialTheme.colorScheme.primary
    val tabItems = USAGE_SUB_TABS.map { tab ->
        val (activeIcon, inactiveIcon) = when (tab) {
            DataUsageSubTab.OVERVIEW -> Icons.Rounded.BarChart to Icons.Outlined.BarChart
            DataUsageSubTab.SITES_REGIONS -> Icons.Rounded.Public to Icons.Outlined.Public
            DataUsageSubTab.SECURITY_AUDIT -> Icons.Rounded.Security to Icons.Outlined.Security
            else -> Icons.Rounded.BarChart to Icons.Outlined.BarChart
        }
        val badgeText = when (tab) {
            DataUsageSubTab.OVERVIEW -> null
            DataUsageSubTab.SITES_REGIONS -> if (sitesCount > 0) "$sitesCount" else null
            DataUsageSubTab.SECURITY_AUDIT -> if (flaggedCount > 0) "$flaggedCount!" else null
            else -> null
        }
        val badgeColor = if (tab == DataUsageSubTab.SECURITY_AUDIT && flaggedCount > 0) {
            BlockedRed
        } else {
            defaultBadgeColor
        }
        GlassTabItem(
            label = tab.label,
            testTag = "data_usage_subtab_${tab.name.lowercase()}",
            activeIcon = activeIcon,
            inactiveIcon = inactiveIcon,
            badgeText = badgeText,
            badgeColor = badgeColor
        )
    }

    GlassTabs(
        items = tabItems,
        selectedIndex = USAGE_SUB_TABS.indexOf(selectedSubTab).coerceAtLeast(0),
        onSelect = { index -> onSelectSubTab(USAGE_SUB_TABS[index]) },
        variant = GlassTabVariant.TOP_SEGMENTED,
        minScrollableTabWidth = 108.dp,
        modifier = modifier
    )
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
            val rangeEntries = UsageTimeRange.entries
            GlassTabs(
                items = rangeEntries.map { range ->
                    GlassTabItem(
                        label = range.label,
                        testTag = "usage_range_${range.name.lowercase()}"
                    )
                },
                selectedIndex = rangeEntries.indexOf(selectedRange).coerceAtLeast(0),
                onSelect = { index -> onSelectRange(rangeEntries[index]) },
                variant = GlassTabVariant.CHIP,
                minScrollableTabWidth = 68.dp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))

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
        val categories = UsageFilterCategory.entries
        GlassTabs(
            items = categories.map { category ->
                GlassTabItem(
                    label = category.label,
                    testTag = "usage_filter_${category.name.lowercase()}"
                )
            },
            selectedIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            onSelect = { index -> onSelectCategory(categories[index]) },
            variant = GlassTabVariant.CHIP,
            minScrollableTabWidth = 72.dp,
            modifier = Modifier.fillMaxWidth()
        )
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
