package com.aistudio.netgate.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.VpnDpiLogCategory
import com.aistudio.netgate.data.model.VpnDpiLogEntry
import com.aistudio.netgate.data.model.VpnDpiLogSeverity
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardRuntimeState
import com.aistudio.netgate.service.AppLogCollector
import com.aistudio.netgate.service.AppLogEntry
import com.aistudio.netgate.service.AppLogLevel
import com.aistudio.netgate.service.VpnRuntimeState
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogStreamTab(val label: String) {
    LIVE_APP("Live App & Crash"),
    VPN_DPI("VPN & DPI Events")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VpnDpiLogsSheet(
    logs: List<VpnDpiLogEntry>,
    appLogs: List<AppLogEntry> = emptyList(),
    initialCategory: VpnDpiLogCategory = VpnDpiLogCategory.ALL,
    vpnRuntimeState: VpnRuntimeState = VpnRuntimeState(),
    vpnGuardRuntimeState: VpnGuardRuntimeState = VpnGuardRuntimeState(),
    onClearLogs: () -> Unit,
    onCopyLogs: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val defaultTab = remember(initialCategory, appLogs.isNotEmpty(), logs.isNotEmpty()) {
        when {
            initialCategory == VpnDpiLogCategory.VPN_GUARD ||
                initialCategory == VpnDpiLogCategory.DPI_BYPASS ||
                initialCategory == VpnDpiLogCategory.VPN_TUNNEL -> LogStreamTab.VPN_DPI
            appLogs.isNotEmpty() -> LogStreamTab.LIVE_APP
            logs.isNotEmpty() -> LogStreamTab.VPN_DPI
            else -> LogStreamTab.LIVE_APP
        }
    }
    var selectedTab by rememberSaveable { mutableStateOf(defaultTab) }

    var selectedCategory by rememberSaveable(initialCategory) {
        mutableStateOf(initialCategory)
    }
    var selectedLevel by rememberSaveable { mutableStateOf<AppLogLevel?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val availableTags = remember(appLogs) {
        appLogs.map { it.tag }
            .distinct()
            .sorted()
    }

    val filteredLogs = remember(logs, selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        logs.filter { entry ->
            val matchesCat = selectedCategory == VpnDpiLogCategory.ALL || entry.category == selectedCategory
            val matchesQuery = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.detail.lowercase().contains(q) ||
                entry.category.label.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    val filteredAppLogs = remember(appLogs, selectedLevel, selectedTag, searchQuery) {
        val q = searchQuery.trim().lowercase()
        appLogs.filter { entry ->
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel
            val matchesTag = selectedTag == null || entry.tag.equals(selectedTag, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.message.lowercase().contains(q) ||
                entry.tag.lowercase().contains(q) ||
                (entry.stackTrace?.lowercase()?.contains(q) == true)
            matchesLevel && matchesTag && matchesQuery
        }
    }

    val activeVisibleCount = if (selectedTab == LogStreamTab.LIVE_APP) {
        filteredAppLogs.size
    } else {
        filteredLogs.size
    }
    val activeTotalCount = if (selectedTab == LogStreamTab.LIVE_APP) {
        appLogs.size
    } else {
        logs.size
    }

    val liveListState = rememberLazyListState()
    val vpnListState = rememberLazyListState()

    val isLiveAtBottom by remember {
        derivedStateOf {
            val info = liveListState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) {
                true
            } else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(isLiveAtBottom, liveListState.isScrollInProgress) {
        if (liveListState.isScrollInProgress && !isLiveAtBottom) {
            autoScrollEnabled = false
        } else if (isLiveAtBottom) {
            autoScrollEnabled = true
        }
    }

    LaunchedEffect(selectedTab, filteredAppLogs.size, autoScrollEnabled) {
        if (selectedTab == LogStreamTab.LIVE_APP && autoScrollEnabled && filteredAppLogs.isNotEmpty()) {
            liveListState.animateScrollToItem(filteredAppLogs.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = Modifier.testTag("vpn_dpi_logs_sheet")
    ) {
        FrostedSheetContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )

                // Header + non-overlapping aligned actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Diagnostics & Logs",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$activeVisibleCount of $activeTotalCount • Live stream",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val formatted = if (selectedTab == LogStreamTab.LIVE_APP) {
                                    AppLogCollector.formatForExport(filteredAppLogs)
                                } else {
                                    formatLogEntriesForClipboard(filteredLogs)
                                }
                                copyToClipboard(context, "NetGate Diagnostics Logs", formatted)
                                onCopyLogs("Copied $activeVisibleCount log entries")
                            },
                            enabled = activeVisibleCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("vpn_dpi_logs_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy logs",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        IconButton(
                            onClick = {
                                val exportText = if (selectedTab == LogStreamTab.LIVE_APP) {
                                    AppLogCollector.formatForExport(filteredAppLogs)
                                } else {
                                    buildString {
                                        appendLine(AppLogCollector.deviceAndAppMetadata())
                                        appendLine(formatLogEntriesForClipboard(filteredLogs))
                                    }
                                }
                                val exportedFile = AppLogCollector.exportToCacheFile(context, exportText)
                                if (exportedFile != null) {
                                    val launched = runCatching {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            exportedFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "NetGate Diagnostics Log")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(shareIntent, "Export NetGate Logs").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    }.isSuccess
                                    if (launched) {
                                        onCopyLogs("Exported ${exportedFile.name}")
                                    } else {
                                        copyToClipboard(context, "NetGate Diagnostics Logs", exportText)
                                        onCopyLogs("Saved ${exportedFile.name} & copied to clipboard")
                                    }
                                } else {
                                    onCopyLogs("Unable to write log file")
                                }
                            },
                            enabled = activeVisibleCount > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("logs_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share or export logs"
                            )
                        }
                        IconButton(
                            onClick = onClearLogs,
                            enabled = logs.isNotEmpty() || appLogs.isNotEmpty(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vpn_dpi_logs_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear logs"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stream Mode Switcher (Live App & Crash vs VPN & DPI Events)
                FrostedSurface(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LogStreamTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val count = if (tab == LogStreamTab.LIVE_APP) appLogs.size else logs.size
                            val tag = if (tab == LogStreamTab.LIVE_APP) {
                                "logs_tab_live_app"
                            } else {
                                "logs_tab_vpn_dpi"
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                } else {
                                    Color.Transparent
                                },
                                border = if (selected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTab = tab }
                                    .testTag(tag)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${tab.label} ($count)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Live Runtime Snapshot Strip
                FrostedSurface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val guardAccent = when (vpnGuardRuntimeState.connectionState) {
                            VpnGuardConnectionState.CONNECTED -> EmeraldPrimary
                            VpnGuardConnectionState.ERROR -> BlockedRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val dpiAccent = if (vpnRuntimeState.dpiBypassMode != DpiBypassMode.OFF) {
                            WifiAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val fwAccent = if (vpnRuntimeState.isServiceRunning) {
                            EmeraldPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        MiniStatusPill(
                            label = "VPN Guard",
                            value = vpnGuardRuntimeState.connectionState.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            accent = guardAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatusPill(
                            label = "DPI Mode",
                            value = "${vpnRuntimeState.dpiBypassMode.title} (${vpnRuntimeState.dpiBypassedConnectionsCount})",
                            accent = dpiAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatusPill(
                            label = "Firewall TUN",
                            value = if (vpnRuntimeState.isServiceRunning) "Running" else "Idle",
                            accent = fwAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Input
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                    text = if (selectedTab == LogStreamTab.LIVE_APP) {
                                        "Search logs, stack traces, tags…"
                                    } else {
                                        "Search VPN, DPI bypass, crash, QUIC…"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vpn_dpi_log_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == LogStreamTab.LIVE_APP) {
                    // Level Filters
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedLevel == null,
                            onClick = { selectedLevel = null },
                            label = {
                                Text("All (${appLogs.size})", style = MaterialTheme.typography.labelMedium)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("log_level_filter_all")
                        )
                        AppLogLevel.entries.forEach { level ->
                            val count = appLogs.count { it.level == level }
                            FilterChip(
                                selected = selectedLevel == level,
                                onClick = {
                                    selectedLevel = if (selectedLevel == level) null else level
                                },
                                label = {
                                    Text("${level.label} ($count)", style = MaterialTheme.typography.labelMedium)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("log_level_filter_${level.name.lowercase()}")
                            )
                        }
                    }

                    if (availableTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedTag == null,
                                    onClick = { selectedTag = null },
                                    label = {
                                        Text("All Tags", style = MaterialTheme.typography.labelSmall)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("log_tag_filter_all")
                                )
                            }
                            items(availableTags) { tag ->
                                FilterChip(
                                    selected = selectedTag == tag,
                                    onClick = {
                                        selectedTag = if (selectedTag == tag) null else tag
                                    },
                                    label = {
                                        Text(tag, style = MaterialTheme.typography.labelSmall)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("log_tag_filter_${tag.lowercase().replace(" ", "_")}")
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(VpnDpiLogCategory.entries) { category ->
                            val count = if (category == VpnDpiLogCategory.ALL) {
                                logs.size
                            } else {
                                logs.count { it.category == category }
                            }
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = "${category.label} ($count)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("vpn_dpi_log_filter_${category.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == LogStreamTab.LIVE_APP) {
                    if (filteredAppLogs.isEmpty()) {
                        EmptyLogsCard(
                            isCompletelyEmpty = appLogs.isEmpty(),
                            message = if (appLogs.isEmpty()) {
                                "Events from NetGate VPN, firewall rules, DNS blocker, Logcat, and crashes will stream here in real time."
                            } else {
                                "Try clearing your search query or selecting All log levels/tags."
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            LazyColumn(
                                state = liveListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vpn_dpi_logs_list")
                            ) {
                                items(filteredAppLogs, key = { it.id }) { entry ->
                                    LiveAppLogRow(entry = entry)
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !autoScrollEnabled && filteredAppLogs.size > 4,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            autoScrollEnabled = true
                                            scope.launch {
                                                liveListState.animateScrollToItem(filteredAppLogs.lastIndex)
                                            }
                                        }
                                        .testTag("logs_jump_to_latest")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = "Jump to latest log",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Latest",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (filteredLogs.isEmpty()) {
                        EmptyLogsCard(
                            isCompletelyEmpty = logs.isEmpty(),
                            message = if (logs.isEmpty()) {
                                "Connect VPN Guard, activate NetGate Firewall, or switch DPI Bypass modes to stream live events here."
                            } else {
                                "Try clearing your search query or selecting All categories."
                            }
                        )
                    } else {
                        LazyColumn(
                            state = vpnListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .testTag("vpn_dpi_logs_list")
                        ) {
                            items(filteredLogs, key = { it.id }) { entry ->
                                VpnDpiLogRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsCard(
    isCompletelyEmpty: Boolean,
    message: String
) {
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_dpi_logs_empty")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isCompletelyEmpty) "No app, VPN or crash events yet" else "No matching log entries",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveAppLogRow(entry: AppLogEntry) {
    var showStackTrace by remember(entry.id) { mutableStateOf(entry.level == AppLogLevel.CRASH) }
    val accent = when (entry.level) {
        AppLogLevel.INFO -> WifiAccent
        AppLogLevel.WARN -> WarningAmber
        AppLogLevel.ERROR -> BlockedRed
        AppLogLevel.CRASH -> BlockedRed
    }
    val icon = when (entry.level) {
        AppLogLevel.INFO -> Icons.Rounded.Info
        AppLogLevel.WARN -> Icons.Rounded.WarningAmber
        AppLogLevel.ERROR -> Icons.Rounded.ErrorOutline
        AppLogLevel.CRASH -> Icons.Rounded.BugReport
    }

    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = accent.copy(alpha = if (entry.level == AppLogLevel.CRASH) 0.55f else 0.34f),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!entry.stackTrace.isNullOrBlank()) {
                    Modifier.clickable { showStackTrace = !showStackTrace }
                } else {
                    Modifier
                }
            )
            .testTag("app_log_row_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = entry.level.label,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = entry.level.shortCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DataAccent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = entry.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = DataAccent,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showStackTrace) 6 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (showStackTrace && !entry.stackTrace.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BlockedRed.copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, BlockedRed.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry.stackTrace,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 14,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatLiveTimestamp(entry.timestampMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = accent
                    )
                    if (!entry.stackTrace.isNullOrBlank()) {
                        Text(
                            text = if (showStackTrace) "Tap to collapse trace" else "Tap to view stack trace",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnDpiLogRow(entry: VpnDpiLogEntry) {
    val accent = when (entry.severity) {
        VpnDpiLogSeverity.SUCCESS -> EmeraldPrimary
        VpnDpiLogSeverity.WARNING -> WarningAmber
        VpnDpiLogSeverity.ERROR -> BlockedRed
        VpnDpiLogSeverity.INFO -> WifiAccent
    }
    val vpnTunnelIcon = ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield)
    val icon = when (entry.category) {
        VpnDpiLogCategory.VPN_GUARD -> vpnTunnelIcon
        VpnDpiLogCategory.DPI_BYPASS -> Icons.Rounded.EnhancedEncryption
        VpnDpiLogCategory.VPN_TUNNEL, VpnDpiLogCategory.ALL -> Icons.Rounded.Shield
        VpnDpiLogCategory.APP -> Icons.Rounded.BugReport
    }

    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        borderAccent = accent.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_dpi_log_row_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = entry.category.label,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DataAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = DataAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${entry.severity.label} • ${formatLogTimestamp(entry.timestampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun MiniStatusPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatLogTimestamp(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

private fun formatLiveTimestamp(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
}

private fun formatLogEntriesForClipboard(entries: List<VpnDpiLogEntry>): String {
    if (entries.isEmpty()) return "No logs"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return entries.joinToString("\n") {
        val safeTitle = AppLogCollector.sanitizeBrowsingHostnames(it.title)
        val safeDetail = AppLogCollector.sanitizeBrowsingHostnames(it.detail)
        "[${fmt.format(Date(it.timestampMs))}] [${it.category.label}] [${it.severity.label}] $safeTitle — $safeDetail"
    }
}
