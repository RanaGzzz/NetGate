package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUsageBreakdownItem
import com.example.data.model.UsageChartMode
import com.example.data.model.UsageFilterCategory
import com.example.data.model.UsageTimeRange
import com.example.data.model.formatDataBytes
import com.example.ui.components.BlockedVsAllowedBarChart
import com.example.ui.components.FrostedSurface
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WifiAccent

@Composable
fun DataUsageContent(
    state: DataUsageUiState,
    loadIcon: suspend (String) -> ImageBitmap?,
    onTimeRangeChange: (UsageTimeRange) -> Unit,
    onChartModeChange: (UsageChartMode) -> Unit,
    onFilterCategoryChange: (UsageFilterCategory) -> Unit,
    onRefreshUsage: () -> Unit,
    onInspectApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxAppBytes = (state.appItems.maxOfOrNull { it.totalBytes } ?: 1L).coerceAtLeast(1L)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("data_usage_screen")
    ) {
        val isWide = maxWidth >= 800.dp

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
            val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                        maxLines = 1
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
