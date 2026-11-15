package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppFilterMode
import com.example.data.model.ThemeMode
import com.example.ui.components.AppDetailBottomSheet
import com.example.ui.components.AppRuleRow
import com.example.ui.components.BatchSelectionBar
import com.example.ui.components.ConnectionInfoSheet
import com.example.ui.components.FirewallHeaderCard
import com.example.ui.components.FirewallSettingsSheet
import com.example.ui.components.FrostedBackdrop
import com.example.ui.components.FrostedSurface
import com.example.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    viewModel: FirewallViewModel,
    onRequestEnableFirewall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    val isFirewallActive = uiState.preferences.isFirewallEnabled &&
        uiState.vpnRuntimeState.isServiceRunning

    FrostedBackdrop(
        isFirewallActive = isFirewallActive,
        modifier = modifier
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Wi-Fi & Data Controller",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.exitSelectionMode()
                                } else {
                                    viewModel.enterSelectionMode()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .testTag("batch_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSelectionMode) {
                                    Icons.Rounded.Close
                                } else {
                                    Icons.Rounded.Checklist
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isSelectionMode) "Done" else "Select",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        IconButton(
                            onClick = { viewModel.cycleThemeMode() },
                            modifier = Modifier.testTag("theme_cycle_button")
                        ) {
                            Icon(
                                imageVector = when (uiState.preferences.themeMode) {
                                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                },
                                contentDescription = "Switch Day or Night theme"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshInstalledApps() },
                            modifier = Modifier.testTag("refresh_apps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh installed apps"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.openSettings() },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.action_settings)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideLayout = maxWidth >= 800.dp

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(340.dp)
                                    .fillMaxHeight()
                            ) {
                                FirewallHeaderCard(
                                    isFirewallActive = isFirewallActive,
                                    connectionDetails = uiState.connectionDetails,
                                    totalAppsCount = uiState.allManagedApps.size,
                                    wifiBlockedCount = uiState.wifiBlockedCount,
                                    dataBlockedCount = uiState.dataBlockedCount,
                                    activeBlockedNowCount = uiState.vpnRuntimeState.blockedActivePackagesCount,
                                    onToggleFirewall = { enable ->
                                        if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                    },
                                    onOpenConnectionInfo = viewModel::openConnectionInfo
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                SearchAndFiltersBlock(
                                    uiState = uiState,
                                    onSearchQueryChange = viewModel::onSearchQueryChange,
                                    onFilterModeChange = viewModel::onFilterModeChange
                                )
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AppsCollectionContent(
                                    uiState = uiState,
                                    isGrid = true,
                                    viewModel = viewModel,
                                    bottomPadding = if (uiState.isSelectionMode) 168.dp else 24.dp
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            AppsCollectionContent(
                                uiState = uiState,
                                isGrid = false,
                                viewModel = viewModel,
                                bottomPadding = if (uiState.isSelectionMode) 172.dp else 24.dp,
                                headerContent = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        FirewallHeaderCard(
                                            isFirewallActive = isFirewallActive,
                                            connectionDetails = uiState.connectionDetails,
                                            totalAppsCount = uiState.allManagedApps.size,
                                            wifiBlockedCount = uiState.wifiBlockedCount,
                                            dataBlockedCount = uiState.dataBlockedCount,
                                            activeBlockedNowCount = uiState.vpnRuntimeState.blockedActivePackagesCount,
                                            onToggleFirewall = { enable ->
                                                if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                            },
                                            onOpenConnectionInfo = viewModel::openConnectionInfo
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        SearchAndFiltersBlock(
                                            uiState = uiState,
                                            onSearchQueryChange = viewModel::onSearchQueryChange,
                                            onFilterModeChange = viewModel::onFilterModeChange
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = uiState.isSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val visiblePackages = uiState.filteredApps.map { it.packageName }.toSet()
                    val allVisibleSelected = visiblePackages.isNotEmpty() &&
                        visiblePackages.all { it in uiState.selectedPackages }

                    BatchSelectionBar(
                        selectedCount = uiState.selectedPackages.size,
                        allVisibleSelected = allVisibleSelected,
                        onSelectAll = viewModel::selectAllVisible,
                        onClearSelection = viewModel::clearSelection,
                        onExitSelection = viewModel::exitSelectionMode,
                        onApplyBatchAction = viewModel::applyBatchAction
                    )
                }
            }
        }
    }

    uiState.inspectedApp?.let { inspected ->
        AppDetailBottomSheet(
            app = inspected,
            activeNetworkType = uiState.effectiveNetworkType,
            isFirewallActive = isFirewallActive,
            loadIcon = viewModel::getAppIconBitmap,
            onUpdateRule = { blockWifi, blockData ->
                viewModel.setAppRules(inspected.packageName, blockWifi, blockData)
            },
            onDismiss = viewModel::closeAppDetails
        )
    }

    if (uiState.isConnectionInfoOpen) {
        ConnectionInfoSheet(
            details = uiState.connectionDetails,
            onRefresh = viewModel::refreshConnectionDetails,
            onDismiss = viewModel::closeConnectionInfo
        )
    }

    if (uiState.isSettingsOpen) {
        FirewallSettingsSheet(
            preferences = uiState.preferences,
            onThemeModeChange = viewModel::setThemeMode,
            onToggleDynamicColor = viewModel::setUseDynamicColor,
            onToggleShowSystemApps = viewModel::setShowSystemApps,
            onToggleShowOfflineApps = viewModel::setShowOfflineApps,
            onClearAllRules = viewModel::clearAllRules,
            onDismiss = viewModel::closeSettings
        )
    }
}

@Composable
private fun SearchAndFiltersBlock(
    uiState: FirewallUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        FrostedSurface(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (uiState.searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_apps_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_input")
                    )
                }
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AppFilterMode.entries) { mode ->
                val count = when (mode) {
                    AppFilterMode.ALL -> uiState.allManagedApps.size
                    AppFilterMode.BLOCKED -> uiState.anyRestrictedCount
                    AppFilterMode.WIFI_BLOCKED -> uiState.wifiBlockedCount
                    AppFilterMode.DATA_BLOCKED -> uiState.dataBlockedCount
                    AppFilterMode.ALLOWED -> uiState.allManagedApps.size - uiState.anyRestrictedCount
                }
                val selected = uiState.filterMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onFilterModeChange(mode) },
                    label = {
                        Text(
                            text = "${mode.label} ($count)",
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
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = if (isDark) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.90f)
                        },
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.testTag("filter_chip_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (uiState.isSelectionMode) {
                "Tap apps to select, then apply Wi-Fi or Data rules below."
            } else {
                "Tap Wi-Fi / Data to toggle, or long-press an app for batch select."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppsCollectionContent(
    uiState: FirewallUiState,
    isGrid: Boolean,
    viewModel: FirewallViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    headerContent: (@Composable () -> Unit)? = null
) {
    val isFirewallActive = uiState.preferences.isFirewallEnabled &&
        uiState.vpnRuntimeState.isServiceRunning

    if (uiState.isLoading) {
        Column(modifier = Modifier.fillMaxSize()) {
            headerContent?.let {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { it() }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scanning installed apps…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    if (isGrid) {
        if (uiState.filteredApps.isEmpty()) {
            EmptyAppsPlaceholder(
                hasFilters = uiState.searchQuery.isNotEmpty() || uiState.filterMode != AppFilterMode.ALL,
                onResetFilters = {
                    viewModel.onSearchQueryChange("")
                    viewModel.onFilterModeChange(AppFilterMode.ALL)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("apps_grid")
            ) {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName }
                ) { app ->
                    AppRuleRow(
                        app = app,
                        activeNetworkType = uiState.effectiveNetworkType,
                        isFirewallActive = isFirewallActive,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = app.packageName in uiState.selectedPackages,
                        loadIcon = viewModel::getAppIconBitmap,
                        onToggleWifi = { viewModel.toggleWifiBlock(app) },
                        onToggleData = { viewModel.toggleDataBlock(app) },
                        onRowClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.togglePackageSelected(app.packageName)
                            } else {
                                viewModel.openAppDetails(app.packageName)
                            }
                        },
                        onRowLongClick = {
                            if (!uiState.isSelectionMode) {
                                viewModel.enterSelectionMode(app.packageName)
                            } else {
                                viewModel.togglePackageSelected(app.packageName)
                            }
                        }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("apps_list")
        ) {
            if (headerContent != null) {
                item(key = "header_controls") {
                    headerContent()
                }
            }

            if (uiState.filteredApps.isEmpty()) {
                item(key = "empty_placeholder") {
                    EmptyAppsPlaceholder(
                        hasFilters = uiState.searchQuery.isNotEmpty() || uiState.filterMode != AppFilterMode.ALL,
                        onResetFilters = {
                            viewModel.onSearchQueryChange("")
                            viewModel.onFilterModeChange(AppFilterMode.ALL)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    )
                }
            } else {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName }
                ) { app ->
                    AppRuleRow(
                        app = app,
                        activeNetworkType = uiState.effectiveNetworkType,
                        isFirewallActive = isFirewallActive,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = app.packageName in uiState.selectedPackages,
                        loadIcon = viewModel::getAppIconBitmap,
                        onToggleWifi = { viewModel.toggleWifiBlock(app) },
                        onToggleData = { viewModel.toggleDataBlock(app) },
                        onRowClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.togglePackageSelected(app.packageName)
                            } else {
                                viewModel.openAppDetails(app.packageName)
                            }
                        },
                        onRowLongClick = {
                            if (!uiState.isSelectionMode) {
                                viewModel.enterSelectionMode(app.packageName)
                            } else {
                                viewModel.togglePackageSelected(app.packageName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAppsPlaceholder(
    hasFilters: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterListOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasFilters) "No matching apps" else "No apps found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasFilters) {
                    "Try clearing your search or switching back to All Apps."
                } else {
                    "Use Batch Select or tap Wi-Fi / Data toggles on any app."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasFilters) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("reset_filters_button")
                ) {
                    Text("Show All Apps")
                }
            }
        }
    }
}
