package com.aistudio.netgate.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.aistudio.netgate.data.network.LocalNetworkPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import android.net.VpnService
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.AppFilterMode
import com.aistudio.netgate.data.model.DataUsageSubTab
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.UpstreamDnsServer
import com.aistudio.netgate.data.model.VpnDpiLogCategory
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.WebsiteTabSection
import com.aistudio.netgate.ui.components.AdvancedProtectionSheet
import com.aistudio.netgate.ui.components.AppChangelogSheet
import com.aistudio.netgate.ui.components.AppDetailBottomSheet
import com.aistudio.netgate.ui.components.AppRuleRow
import com.aistudio.netgate.ui.components.BackgroundAnimationSheet
import com.aistudio.netgate.ui.components.BackupAndRestoreRulesDialog
import com.aistudio.netgate.ui.components.BatchSelectionBar
import com.aistudio.netgate.ui.components.ConnectionInfoSheet
import com.aistudio.netgate.ui.components.DnsProtectionContent
import com.aistudio.netgate.ui.components.ExportRulesDialog
import com.aistudio.netgate.ui.components.FirewallHeaderCard
import com.aistudio.netgate.ui.components.FirewallSettingsSheet
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.FrostedBackdrop
import com.aistudio.netgate.ui.components.FrostedSegmentedTabBar
import com.aistudio.netgate.ui.components.FrostedSegmentedTabItem
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.components.GlassToast
import com.aistudio.netgate.ui.components.HotspotAndWifiContent
import com.aistudio.netgate.ui.components.ImportRulesDialog
import com.aistudio.netgate.ui.components.VpnDpiLogsSheet
import com.aistudio.netgate.ui.components.VpnGuardContent
import com.aistudio.netgate.ui.components.websiteManagementItems
import com.aistudio.netgate.service.AppLogCollector
import com.aistudio.netgate.ui.theme.BlurEngine.blurEngineModalDepth
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.LocalOverlayLiquidState
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import io.github.fletchmckee.liquid.liquefiable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    viewModel: FirewallViewModel,
    onRequestEnableFirewall: () -> Unit,
    onRequestToggleStatusBarSpeed: (Boolean) -> Unit,
    onReplayStartupAnimation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val advancedPreferences by viewModel.advancedPreferences.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val appLogs by AppLogCollector.entries.collectAsStateWithLifecycle()
    val settingsImportPending by viewModel.settingsImportPending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBackgroundSheet by rememberSaveable { mutableStateOf(false) }
    var showAdvancedProtection by rememberSaveable { mutableStateOf(false) }
    var isSniDpiSelected by rememberSaveable { mutableStateOf(false) }
    var selectedFirewallTab by rememberSaveable { mutableStateOf(FirewallContentTab.ALL_APPS) }
    var areAppFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedWebsiteSection by rememberSaveable { mutableStateOf(WebsiteTabSection.BLOCKLIST) }
    var websiteDomainInput by rememberSaveable { mutableStateOf("") }
    var bypassAppSearchQuery by rememberSaveable { mutableStateOf("") }
    var isImportRulesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isExportRulesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isBackupRestoreDialogOpen by rememberSaveable { mutableStateOf(false) }
    var exportRulesPayload by rememberSaveable { mutableStateOf("") }
    var exportBackupPayload by rememberSaveable { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val selectAppDestination: (AppDestination) -> Unit = { dest ->
        isSniDpiSelected = false
        viewModel.selectDestination(dest)
    }
    val selectSniDpi: () -> Unit = {
        if (uiState.isSelectionMode) {
            viewModel.exitSelectionMode()
        }
        viewModel.selectDestination(AppDestination.FIREWALL)
        isSniDpiSelected = true
    }

    LaunchedEffect(uiState.currentDestination) {
        if (uiState.currentDestination != AppDestination.FIREWALL) {
            isSniDpiSelected = false
        }
    }

    val currentWifiSsid = remember(uiState.connectionDetails, uiState.currentDestination) {
        viewModel.currentWifiSsid()
    }
    val filteredBypassApps = remember(
        uiState.allManagedApps,
        uiState.websiteBlockerState.bypassPackages,
        bypassAppSearchQuery
    ) {
        val query = bypassAppSearchQuery.trim().lowercase()
        uiState.allManagedApps
            .filter { app ->
                query.isEmpty() ||
                    app.appName.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query)
            }
            .sortedWith(
                compareByDescending<ManagedApp> {
                    it.packageName in uiState.websiteBlockerState.bypassPackages
                }.thenBy { it.appName.lowercase() }
            )
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            GlassToast.show(message)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    BackHandler(enabled = !uiState.isSelectionMode && isSniDpiSelected) {
        isSniDpiSelected = false
    }

    BackHandler(
        enabled = !uiState.isSelectionMode &&
            !isSniDpiSelected &&
            uiState.currentDestination == AppDestination.FIREWALL &&
            selectedFirewallTab == FirewallContentTab.WEBSITES
    ) {
        selectedFirewallTab = FirewallContentTab.ALL_APPS
    }

    BackHandler(
        enabled = !uiState.isSelectionMode &&
            !isSniDpiSelected &&
            uiState.currentDestination != AppDestination.FIREWALL
    ) {
        selectAppDestination(AppDestination.FIREWALL)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshConnectionDetails()
        if (uiState.currentDestination == AppDestination.DATA_USAGE) {
            viewModel.refreshDataUsage()
        } else if (uiState.currentDestination == AppDestination.HOTSPOT) {
            viewModel.refreshHotspotEnvironment()
        }
    }

    val isFirewallActive = uiState.preferences.isFirewallEnabled &&
        uiState.vpnRuntimeState.isServiceRunning
    val isDark = LocalIsDarkTheme.current

    FrostedBackdrop(
        isFirewallActive = isFirewallActive,
        backgroundAnimation = uiState.preferences.backgroundAnimation,
        backgroundParallaxEnabled = uiState.preferences.backgroundParallaxEnabled,
        backgroundSeasonalEnabled = uiState.preferences.backgroundSeasonalEnabled,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth >= 800.dp
            val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val hasOpenSheet = uiState.isSettingsOpen ||
                uiState.isConnectionInfoOpen ||
                showBackgroundSheet ||
                uiState.inspectedApp != null

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .blurEngineModalDepth(active = hasOpenSheet),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            AnimatedContent(
                                targetState = uiState.currentDestination,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 4 }) togetherWith
                                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 })
                                },
                                label = "topbar_title_transition"
                            ) { destination ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when (destination) {
                                            AppDestination.DATA_USAGE -> stringResource(R.string.data_usage_title)
                                            AppDestination.WEBSITES -> stringResource(R.string.websites_title)
                                            AppDestination.DNS -> stringResource(R.string.dns_title)
                                            AppDestination.HOTSPOT -> stringResource(R.string.hotspot_title)
                                            AppDestination.VPN_GUARD -> stringResource(R.string.vpn_guard_title)
                                            AppDestination.FIREWALL -> stringResource(R.string.firewall_top_title)
                                        },
                                        style = MaterialTheme.typography.headlineSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when (destination) {
                                            AppDestination.DATA_USAGE -> stringResource(R.string.data_usage_subtitle)
                                            AppDestination.WEBSITES -> stringResource(R.string.websites_subtitle)
                                            AppDestination.DNS -> stringResource(R.string.dns_subtitle)
                                            AppDestination.HOTSPOT -> stringResource(R.string.hotspot_subtitle)
                                            AppDestination.VPN_GUARD -> stringResource(R.string.vpn_guard_subtitle)
                                            AppDestination.FIREWALL -> stringResource(R.string.firewall_top_subtitle)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.openSettings() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("open_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = stringResource(R.string.action_settings)
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideLayout) {
                        NavigationRail(
                            containerColor = if (isDark) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                            }
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.FIREWALL,
                                onClick = { selectAppDestination(AppDestination.FIREWALL) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = stringResource(R.string.nav_firewall)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_firewall)) },
                                modifier = Modifier.testTag("nav_rail_firewall")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.WEBSITES,
                                onClick = { selectAppDestination(AppDestination.WEBSITES) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Public,
                                        contentDescription = stringResource(R.string.nav_websites)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_websites)) },
                                modifier = Modifier.testTag("nav_rail_websites")
                            )
                            NavigationRailItem(
                                selected = isSniDpiSelected,
                                onClick = selectSniDpi,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "SNI & DPI"
                                    )
                                },
                                label = { Text("SNI & DPI") },
                                modifier = Modifier.testTag("nav_rail_sni_dpi")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.VPN_GUARD,
                                onClick = { selectAppDestination(AppDestination.VPN_GUARD) },
                                icon = {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield),
                                        contentDescription = stringResource(R.string.nav_vpn_guard)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_vpn_guard)) },
                                modifier = Modifier.testTag("nav_rail_vpn_guard")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.DNS,
                                onClick = { selectAppDestination(AppDestination.DNS) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Dns,
                                        contentDescription = stringResource(R.string.nav_dns)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_dns)) },
                                modifier = Modifier.testTag("nav_rail_dns")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.HOTSPOT,
                                onClick = { selectAppDestination(AppDestination.HOTSPOT) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.WifiTethering,
                                        contentDescription = stringResource(R.string.nav_hotspot)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_hotspot)) },
                                modifier = Modifier.testTag("nav_rail_hotspot")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.DATA_USAGE &&
                                    uiState.dataUsageState.selectedSubTab != DataUsageSubTab.WIFI &&
                                    uiState.dataUsageState.selectedSubTab != DataUsageSubTab.SPEED_TEST,
                                onClick = {
                                    selectAppDestination(AppDestination.DATA_USAGE)
                                    viewModel.selectDataUsageSubTab(DataUsageSubTab.OVERVIEW)
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.BarChart,
                                        contentDescription = stringResource(R.string.nav_data_usage)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_data_usage)) },
                                modifier = Modifier.testTag("nav_rail_data_usage")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.DATA_USAGE &&
                                    uiState.dataUsageState.selectedSubTab == DataUsageSubTab.WIFI,
                                onClick = {
                                    selectAppDestination(AppDestination.DATA_USAGE)
                                    viewModel.selectDataUsageSubTab(DataUsageSubTab.WIFI)
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = "Wi-Fi"
                                    )
                                },
                                label = { Text("Wi-Fi") },
                                modifier = Modifier.testTag("nav_rail_wifi")
                            )
                            NavigationRailItem(
                                selected = !isSniDpiSelected && uiState.currentDestination == AppDestination.DATA_USAGE &&
                                    uiState.dataUsageState.selectedSubTab == DataUsageSubTab.SPEED_TEST,
                                onClick = {
                                    selectAppDestination(AppDestination.DATA_USAGE)
                                    viewModel.selectDataUsageSubTab(DataUsageSubTab.SPEED_TEST)
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = "Speed Test"
                                    )
                                },
                                label = { Text("Speed Test") },
                                modifier = Modifier.testTag("nav_rail_speed_test")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val activeStyle = LocalThemeStyle.current
                        val overlayLiquidState = LocalOverlayLiquidState.current
                        val activeScreenKey = Pair(uiState.currentDestination, isSniDpiSelected)
                        AnimatedContent(
                            targetState = activeScreenKey,
                            transitionSpec = {
                                val forward = navOrderIndex(targetState) >= navOrderIndex(initialState)
                                when (activeStyle) {
                                    NetGateThemeStyle.MATERIAL_YOU_LITE -> {
                                        (fadeIn(tween(150)) togetherWith fadeOut(tween(110)))
                                            .using(SizeTransform(clip = false))
                                    }
                                    NetGateThemeStyle.LIQUID_GLASS,
                                    NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> {
                                        val motionSlide = spring<androidx.compose.ui.unit.IntOffset>(
                                            dampingRatio = activeStyle.springDampingRatio,
                                            stiffness = activeStyle.springStiffness
                                        )
                                        val motionScale = spring<Float>(
                                            dampingRatio = activeStyle.springDampingRatio,
                                            stiffness = activeStyle.springStiffness
                                        )
                                        val enter = slideInHorizontally(
                                            animationSpec = motionSlide,
                                            initialOffsetX = { fullWidth -> if (forward) fullWidth / 5 else -fullWidth / 5 }
                                        ) + fadeIn(
                                            animationSpec = tween(190, easing = FastOutSlowInEasing)
                                        ) + scaleIn(
                                            initialScale = 0.955f,
                                            animationSpec = motionScale
                                        )
                                        val exit = slideOutHorizontally(
                                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> if (forward) -fullWidth / 7 else fullWidth / 7 }
                                        ) + fadeOut(
                                            animationSpec = tween(140, easing = FastOutSlowInEasing)
                                        ) + scaleOut(
                                            targetScale = 0.98f,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                                        )
                                        (enter togetherWith exit).using(SizeTransform(clip = false))
                                    }
                                    NetGateThemeStyle.NETGATE_DEFAULT -> {
                                        val enter = slideInHorizontally(
                                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                                            initialOffsetX = { fullWidth -> if (forward) fullWidth / 6 else -fullWidth / 6 }
                                        ) + fadeIn(
                                            animationSpec = tween(210, easing = FastOutSlowInEasing)
                                        ) + scaleIn(
                                            initialScale = 0.975f,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        )
                                        val exit = slideOutHorizontally(
                                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> if (forward) -fullWidth / 7 else fullWidth / 7 }
                                        ) + fadeOut(
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + scaleOut(
                                            targetScale = 0.985f,
                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                        )
                                        (enter togetherWith exit).using(SizeTransform(clip = false))
                                    }
                                }
                            },
                            label = "destination_shared_axis",
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (activeStyle == NetGateThemeStyle.LIQUID_GLASS && overlayLiquidState != null) {
                                        Modifier.liquefiable(overlayLiquidState)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) { (destination, sniDpiActive) ->
                            if (sniDpiActive) {
                                SniDpiContent(
                                    state = uiState.websiteBlockerState,
                                    isFirewallActive = isFirewallActive,
                                    onToggleHttpsProxy = viewModel::setHttpsProxyEnabled,
                                    onSelectProxyPort = viewModel::setHttpsProxyPort,
                                    onToggleProxyStripTracking = viewModel::setProxyStripTrackingParams,
                                    onSelectSniPreset = viewModel::selectSniProxyPreset,
                                    onSaveCustomSniProxy = viewModel::saveCustomSniProxy,
                                    onSelectDpiMode = viewModel::setDpiBypassMode,
                                    onSelectDpiChunkSize = viewModel::setDpiFragmentChunkSize,
                                    onToggleDpiIdleKeepAlive = viewModel::setDpiIdleKeepAliveEnabled,
                                    onToggleBlockQuic = viewModel::setBlockQuicForDpiBypass,
                                    onToggleAntiDnsPoison = viewModel::setAntiDnsPoisonEnabled,
                                    onSelectDnsTransportMode = viewModel::setDnsTransportMode,
                                    onFlushDpiCache = viewModel::flushDnsAndAutopilotCache,
                                    onCopiedProxyEndpoint = viewModel::notifyCopiedDnsHostname,
                                    onClearDnsLogs = viewModel::clearDnsLogs,
                                    onRequestEnableFirewall = onRequestEnableFirewall,
                                    onOpenDpiLogs = { viewModel.openVpnDpiLogs(VpnDpiLogCategory.DPI_BYPASS) },
                                    onAllowDomainTemporarily = viewModel::allowDomainTemporarily,
                                    onAllowDomainPermanently = viewModel::allowDomainPermanently,
                                    onBlockDomainPermanently = viewModel::blockDomainPermanently,
                                    onReportFalsePositive = viewModel::reportFalsePositive,
                                    onNotifyCopied = viewModel::notifyFilterLogAction
                                )
                            } else when (destination) {
                                AppDestination.DATA_USAGE -> {
                                    val activeNetLabel = when (uiState.effectiveNetworkType) {
                                        ActiveNetworkType.WIFI -> "Wi-Fi"
                                        ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
                                        ActiveNetworkType.OTHER -> "Ethernet / Other"
                                        ActiveNetworkType.NONE -> "Offline"
                                    }
                                    DataUsageContent(
                                        state = uiState.dataUsageState,
                                        liveTelemetry = uiState.liveTelemetry,
                                        isFirewallActive = isFirewallActive,
                                        isDeepMonitorEnabled = uiState.preferences.isDeepMonitorEnabled,
                                        isHttpsProxyActive = uiState.vpnRuntimeState.isHttpsProxyActive,
                                        isStatusBarSpeedEnabled = uiState.preferences.isStatusBarSpeedEnabled,
                                        statusBarSpeedStyle = uiState.preferences.statusBarSpeedStyle,
                                        showStatusBarDailyUsage = uiState.preferences.showStatusBarDailyUsage,
                                        loadIcon = viewModel::getAppIconBitmap,
                                        onSelectSubTab = viewModel::selectDataUsageSubTab,
                                        onTimeRangeChange = viewModel::setUsageTimeRange,
                                        onChartModeChange = viewModel::setUsageChartMode,
                                        onFilterCategoryChange = viewModel::setUsageFilterCategory,
                                        onRefreshUsage = viewModel::refreshDataUsage,
                                        onClearHistory = viewModel::clearTrafficHistory,
                                        onInspectApp = viewModel::openAppDetails,
                                        onToggleStatusBarSpeed = onRequestToggleStatusBarSpeed,
                                        onSelectStatusBarStyle = viewModel::setStatusBarSpeedStyle,
                                        onToggleShowDailyUsage = viewModel::setShowStatusBarDailyUsage,
                                        onResetLiveSession = viewModel::resetLiveSessionStats,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onToggleDeepMonitor = viewModel::setDeepMonitorEnabled,
                                        onSetShowLiveSitesFeed = viewModel::setShowLiveSitesFeed,
                                        onSiteFilterChange = viewModel::setSiteTrafficFilter,
                                        onSiteSearchQueryChange = viewModel::setSiteSearchQuery,
                                        onToggleCountryFilter = viewModel::toggleCountryFilter,
                                        onClearConnectionHistory = viewModel::clearConnectionHistory,
                                        onQuickBlockSite = viewModel::quickBlockSiteFromMonitor,
                                        onQuickAllowSite = viewModel::quickAllowSiteFromMonitor,
                                        onQuickBlockApp = viewModel::quickBlockAppFromMonitor,
                                        onQuickAllowApp = viewModel::quickAllowAppFromMonitor,
                                        onRunSecurityAudit = viewModel::runDeviceSecurityAudit,
                                        onOpenFlaggedSitesFromAudit = viewModel::openFlaggedSitesFromAudit,
                                        speedTestState = uiState.speedTestState,
                                        activeNetworkLabel = activeNetLabel,
                                        onStartSpeedTest = viewModel::startSpeedTest,
                                        onStopSpeedTest = viewModel::stopSpeedTest,
                                        onRunVideoTest = viewModel::startVideoPlaybackTest,
                                        onRefreshWifi = viewModel::refreshWifiDiagnostics,
                                        onClearObservedWifi = viewModel::clearObservedWifiHistory
                                    )
                                }

                                AppDestination.WEBSITES -> {
                                    WebsiteBlockerContent(
                                        state = uiState.websiteBlockerState,
                                        allManagedApps = uiState.allManagedApps,
                                        isFirewallActive = isFirewallActive,
                                        connectionDetails = uiState.connectionDetails,
                                        loadIcon = viewModel::getAppIconBitmap,
                                        onSelectFilterMode = viewModel::setWebsiteFilterMode,
                                        onAddWebsiteRule = viewModel::addWebsiteRule,
                                        onToggleWebsiteRuleEnabled = viewModel::toggleWebsiteRuleEnabled,
                                        onDeleteWebsiteRule = viewModel::deleteWebsiteRule,
                                        onClearWebsiteRules = viewModel::clearWebsiteRules,
                                        onToggleBypassApp = viewModel::toggleWebsiteBypassApp,
                                        onClearBypassApps = viewModel::clearWebsiteBypassApps,
                                        onToggleAdBlocker = viewModel::setAdBlockerEnabled,
                                        onToggleAdFilterList = viewModel::toggleAdFilterList,
                                        onSyncAdFilterLists = viewModel::syncAdFilterLists,
                                        onToggleEssentialPassthrough = viewModel::setEssentialPassthroughEnabled,
                                        onToggleUseDohUpstream = viewModel::setUseDohUpstream,
                                        onToggleHttpsProxy = viewModel::setHttpsProxyEnabled,
                                        onSelectProxyPort = viewModel::setHttpsProxyPort,
                                        onToggleProxyStripTracking = viewModel::setProxyStripTrackingParams,
                                        onToggleSafeSearch = viewModel::setSafeSearchEnabled,
                                        onSelectYoutubeMode = viewModel::setYoutubeRestrictedMode,
                                        onToggleYoutubeAdBlock = viewModel::setYoutubeAdBlockEnabled,
                                        onSelectSniPreset = viewModel::selectSniProxyPreset,
                                        onSaveCustomSniProxy = viewModel::saveCustomSniProxy,
                                        onSelectDpiMode = viewModel::setDpiBypassMode,
                                        onSelectDpiChunkSize = viewModel::setDpiFragmentChunkSize,
                                        onToggleDpiIdleKeepAlive = viewModel::setDpiIdleKeepAliveEnabled,
                                        onToggleBlockQuic = viewModel::setBlockQuicForDpiBypass,
                                        onToggleAntiDnsPoison = viewModel::setAntiDnsPoisonEnabled,
                                        onSelectDnsTransportMode = viewModel::setDnsTransportMode,
                                        onFlushDpiCache = viewModel::flushDnsAndAutopilotCache,
                                        onAddCustomAdFilterList = viewModel::addCustomAdFilterList,
                                        onDeleteCustomAdFilterList = viewModel::deleteCustomAdFilterList,
                                        onCopiedProxyEndpoint = viewModel::notifyCopiedDnsHostname,
                                        onClearDnsLogs = viewModel::clearDnsLogs,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onOpenDnsProtection = viewModel::openDnsProtection,
                                        onOpenDpiLogs = { viewModel.openVpnDpiLogs(VpnDpiLogCategory.DPI_BYPASS) },
                                        onSelectSmartProtectionProfile = viewModel::applySmartProtectionProfile,
                                        onAllowDomainTemporarily = viewModel::allowDomainTemporarily,
                                        onAllowDomainPermanently = viewModel::allowDomainPermanently,
                                        onBlockDomainPermanently = viewModel::blockDomainPermanently,
                                        onReportFalsePositive = viewModel::reportFalsePositive,
                                        onImportWebsiteRules = viewModel::importWebsiteRules,
                                        onExportWebsiteRules = viewModel::exportWebsiteRulesText,
                                        onExportBackupJson = viewModel::exportAdBlockBackupJson,
                                        onRestoreBackupJson = viewModel::restoreAdBlockBackupJson,
                                        onNotifyCopied = viewModel::notifyFilterLogAction
                                    )
                                }



                                AppDestination.DNS -> {
                                    DnsProtectionContent(
                                        connectionDetails = uiState.connectionDetails,
                                        preferences = uiState.preferences,
                                        isFirewallActive = isFirewallActive,
                                        vpnRuntimeState = uiState.vpnRuntimeState,
                                        probeState = uiState.dnsProbeState,
                                        leakTestState = uiState.dnsLeakTestState,
                                        dnsCheckState = uiState.dnsCheckState,
                                        onSelectUpstreamDns = viewModel::selectUpstreamDns,
                                        onSaveCustomDns = viewModel::saveCustomDnsEndpoints,
                                        onToggleDohUpstream = viewModel::setUseDohUpstream,
                                        onRunDnsCheck = viewModel::runDnsCheck,
                                        onRunDnsLeakTest = viewModel::runDnsLeakTest,
                                        onTestCloudflareDoh = viewModel::testCloudflareDoh,
                                        onSelectDpiMode = viewModel::setDpiBypassMode,
                                        onSelectDpiChunkSize = viewModel::setDpiFragmentChunkSize,
                                        onToggleDpiIdleKeepAlive = viewModel::setDpiIdleKeepAliveEnabled,
                                        onToggleBlockQuic = viewModel::setBlockQuicForDpiBypass,
                                        onToggleAntiDnsPoison = viewModel::setAntiDnsPoisonEnabled,
                                        onToggleDnsCache = viewModel::setDnsResponseCacheEnabled,
                                        onSelectDnsTransportMode = viewModel::setDnsTransportMode,
                                        onFlushDpiCache = viewModel::flushDnsAndAutopilotCache,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onCopiedHostname = viewModel::notifyCopiedDnsHostname
                                    )
                                }

                                AppDestination.HOTSPOT -> {
                                    val hotspotContext = LocalContext.current
                                    // Android 17+ needs a runtime grant before the proxy may reach
                                    // LAN clients. Ask at the moment the user enables it, then run
                                    // the normal enable path either way: a denial is reported by
                                    // HotspotProxyManager in the proxy card instead of the switch
                                    // silently doing nothing.
                                    val localNetworkLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.RequestPermission()
                                    ) { _ ->
                                        viewModel.setHotspotProxyEnabled(true)
                                    }
                                    HotspotAndWifiContent(
                                        preferences = uiState.preferences,
                                        hotspotState = uiState.hotspotProxyState,
                                        connectionDetails = uiState.connectionDetails,
                                        idleStatus = uiState.wifiIdleStatus,
                                        nowMillis = uiState.nowMillis,
                                        onToggleHotspotProxy = { enabled ->
                                            if (enabled && !LocalNetworkPermission.isGranted(hotspotContext)) {
                                                localNetworkLauncher.launch(LocalNetworkPermission.PERMISSION)
                                            } else {
                                                viewModel.setHotspotProxyEnabled(enabled)
                                            }
                                        },
                                        onSelectHotspotPort = viewModel::setHotspotProxyPort,
                                        onToggleApplyNetGateFilters = viewModel::setHotspotApplyNetGateFilters,
                                        advancedPreferences = advancedPreferences,
                                        onToggleShareActiveTunnel = viewModel::setHotspotShareActiveTunnel,
                                        onToggleRequireApproval = viewModel::setHotspotRequireApproval,
                                        onApproveClient = viewModel::approveHotspotClient,
                                        onBlockClient = viewModel::blockHotspotClient,
                                        onSetClientProfile = viewModel::setHotspotClientProfile,
                                        onSetClientSpeedLimit = viewModel::setHotspotClientSpeedLimit,
                                        onToggleClientBlocked = viewModel::toggleHotspotClientBlocked,
                                        onSetClientDataCap = viewModel::setHotspotClientDataCap,
                                        onSetClientLabel = viewModel::setHotspotClientLabel,
                                        onResetClientStats = viewModel::resetHotspotClientStats,
                                        onClearAllHotspotStats = viewModel::clearAllHotspotStats,
                                        onRefreshHotspot = viewModel::refreshHotspotEnvironment,
                                        onCopiedEndpoint = viewModel::notifyCopiedHotspotEndpoint,
                                        onToggleGlobalWifiLock = { locked ->
                                            viewModel.setGlobalWifiBlocked(locked)
                                            if (locked && VpnService.prepare(context) != null) {
                                                onRequestEnableFirewall()
                                            }
                                        },
                                        onScheduleWifiTimer = { minutes ->
                                            viewModel.scheduleGlobalWifiTimer(minutes)
                                            if (VpnService.prepare(context) != null) {
                                                onRequestEnableFirewall()
                                            }
                                        },
                                        onCancelWifiTimer = viewModel::cancelGlobalWifiTimer,
                                        onToggleAutoIdleLock = { enabled ->
                                            viewModel.setAutoBlockWifiOnIdle(enabled)
                                            if (enabled && VpnService.prepare(context) != null) {
                                                onRequestEnableFirewall()
                                            }
                                        },
                                        onSelectIdleMinutes = viewModel::setAutoBlockWifiIdleMinutes,
                                        onToggleOpenPanelNotify = viewModel::setAutoBlockWifiOpenPanelNotify
                                    )
                                }

                                AppDestination.VPN_GUARD -> {
                                    VpnGuardContent(
                                        state = uiState.vpnGuardState,
                                        allManagedApps = uiState.allManagedApps,
                                        isLocalFirewallActive = isFirewallActive,
                                        nowMillis = uiState.nowMillis,
                                        loadIcon = viewModel::getAppIconBitmap,
                                        onSelectSubTab = viewModel::selectVpnGuardSubTab,
                                        onSelectProfile = viewModel::selectVpnGuardProfile,
                                        onImportWireGuardConfig = viewModel::importWireGuardConfig,
                                        onGenerateWarpProfile = viewModel::generateWarpProfile,
                                        onGenerateKeypairTemplate = viewModel::generateWireGuardKeypairTemplate,
                                        onUpdateProfileRouting = viewModel::updateVpnGuardProfileRouting,
                                        onDeleteProfile = viewModel::deleteVpnGuardProfile,
                                        onConnectProfile = viewModel::connectVpnGuardProfile,
                                        onDisconnect = viewModel::disconnectVpnGuard,
                                        onRefreshVpnGate = viewModel::refreshVpnGateServers,
                                        onVpnGateSearchChange = viewModel::setVpnGateSearchQuery,
                                        onVpnGateSortChange = viewModel::setVpnGateSortMode,
                                        onNotifyMessage = viewModel::notifyVpnGuardMessage,
                                        onOpenVpnLogs = { viewModel.openVpnDpiLogs(VpnDpiLogCategory.VPN_GUARD) },
                                        onConnectVpnGateServer = viewModel::connectVpnGateServerDirect,
                                        onSaveVpnGateServer = viewModel::saveVpnGateServerProfile,
                                        onApplySplitTunnelPreset = viewModel::applySplitTunnelPreset
                                    )
                                }

                                AppDestination.FIREWALL -> {
                                    val websiteState = uiState.websiteBlockerState
                                    val activeBlocklistCount = websiteState.blocklistRules.count { it.isEnabled }
                                    val activeWhitelistCount = websiteState.whitelistRules.count { it.isEnabled }
                                    val websiteRulesCount = when {
                                        websiteState.filterMode == WebsiteFilterMode.WHITELIST_ONLY -> activeWhitelistCount
                                        activeBlocklistCount > 0 -> activeBlocklistCount
                                        activeWhitelistCount > 0 -> activeWhitelistCount
                                        else -> 0
                                    }
                                    val websiteRulesBadge = when {
                                        websiteState.filterMode == WebsiteFilterMode.WHITELIST_ONLY ->
                                            "$activeWhitelistCount Allowed"
                                        activeBlocklistCount == 0 && activeWhitelistCount > 0 ->
                                            "$activeWhitelistCount Allowed"
                                        else ->
                                            "$activeBlocklistCount Blocked"
                                    }
                                    val dnsSummary = if (
                                        uiState.preferences.selectedUpstreamDns == UpstreamDnsServer.CUSTOM_DNS
                                    ) {
                                        uiState.preferences.customUdpDnsIp.ifBlank { "Custom DNS" }
                                    } else {
                                        uiState.preferences.selectedUpstreamDns.primaryUdpIp
                                    }
                                    val openWebsitesTab: () -> Unit = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.exitSelectionMode()
                                        }
                                        selectedFirewallTab = FirewallContentTab.WEBSITES
                                    }

                                    if (isWideLayout) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 20.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .width(360.dp)
                                                    .fillMaxHeight()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                FirewallSectionHeading()
                                                Spacer(modifier = Modifier.height(10.dp))
                                                FirewallHeaderCard(
                                                    isFirewallActive = isFirewallActive,
                                                    connectionDetails = uiState.connectionDetails,
                                                    totalAppsCount = uiState.allManagedApps.size,
                                                    wifiBlockedCount = uiState.wifiBlockedCount,
                                                    dataBlockedCount = uiState.dataBlockedCount,
                                                    activeBlockedNowCount = uiState.vpnRuntimeState.blockedActivePackagesCount,
                                                    isAdBlockerEnabled = uiState.preferences.isAdBlockerEnabled,
                                                    blockedAdsCount = uiState.vpnRuntimeState.blockedAdsCount,
                                                    liveTelemetry = uiState.liveTelemetry,
                                                    isGlobalWifiBlocked = uiState.preferences.globalWifiBlocked,
                                                    isHotspotProxyActive = uiState.hotspotProxyState.isProxyRunning,
                                                    isVpnGuardActive = uiState.vpnGuardState.runtimeState.isConnectedOrConnecting,
                                                    vpnGuardProfileLabel = uiState.vpnGuardState.runtimeState.activeProfileName,
                                                    blockedWebsitesCount = websiteRulesCount,
                                                    websiteRulesBadgeText = websiteRulesBadge,
                                                    dnsSummaryLabel = dnsSummary,
                                                    tunnelError = uiState.vpnRuntimeState.tunnelError,
                                                    onToggleFirewall = { enable ->
                                                        if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                                    },
                                                    onOpenConnectionInfo = viewModel::openConnectionInfo,
                                                    onOpenDnsProtection = viewModel::openDnsProtection,
                                                    onOpenSpeedTest = viewModel::openSpeedTest,
                                                    onOpenAdBlocker = { viewModel.selectDestination(AppDestination.WEBSITES) },
                                                    onOpenWebsitesTab = openWebsitesTab,
                                                    onOpenNetworkMonitor = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
                                                    onOpenHotspot = { viewModel.selectDestination(AppDestination.HOTSPOT) },
                                                    onOpenVpnGuard = { viewModel.selectDestination(AppDestination.VPN_GUARD) }
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                FirewallSegmentedTabToggle(
                                                    selectedTab = selectedFirewallTab,
                                                    totalAppsCount = uiState.allManagedApps.size,
                                                    onSelectTab = { tab ->
                                                        if (tab == FirewallContentTab.WEBSITES && uiState.isSelectionMode) {
                                                            viewModel.exitSelectionMode()
                                                        }
                                                        selectedFirewallTab = tab
                                                    }
                                                )
                                                if (selectedFirewallTab == FirewallContentTab.ALL_APPS) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    SearchAndFiltersBlock(
                                                        uiState = uiState,
                                                        filtersExpanded = areAppFiltersExpanded,
                                                        onToggleFiltersExpanded = {
                                                            areAppFiltersExpanded = !areAppFiltersExpanded
                                                        },
                                                        onSearchQueryChange = viewModel::onSearchQueryChange,
                                                        onFilterModeChange = viewModel::onFilterModeChange
                                                    )
                                                }
                                            }

                                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                if (selectedFirewallTab == FirewallContentTab.ALL_APPS) {
                                                    AppsCollectionContent(
                                                        uiState = uiState,
                                                        isGrid = true,
                                                        viewModel = viewModel,
                                                        bottomPadding = if (uiState.isSelectionMode) 168.dp else 24.dp
                                                    )
                                                } else {
                                                    LazyColumn(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .testTag("firewall_websites_list"),
                                                        contentPadding = PaddingValues(bottom = 24.dp),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        websiteManagementItems(
                                                            state = websiteState,
                                                            isFirewallActive = isFirewallActive,
                                                            connectionDetails = uiState.connectionDetails,
                                                            domainInput = websiteDomainInput,
                                                            selectedSection = selectedWebsiteSection,
                                                            filteredBypassApps = filteredBypassApps,
                                                            appSearchQuery = bypassAppSearchQuery,
                                                            loadIcon = viewModel::getAppIconBitmap,
                                                            onDomainInputChange = { websiteDomainInput = it },
                                                            onSelectFilterMode = viewModel::setWebsiteFilterMode,
                                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                                            onOpenDnsProtection = viewModel::openDnsProtection,
                                                            onAllowDomainPermanently = viewModel::allowDomainPermanently,
                                                            onBlockDomainPermanently = viewModel::blockDomainPermanently,
                                                            onAllowDomainTemporarily = viewModel::allowDomainTemporarily,
                                                            onSelectSection = { selectedWebsiteSection = it },
                                                            onClearWebsiteRules = viewModel::clearWebsiteRules,
                                                            onClearBypassApps = viewModel::clearWebsiteBypassApps,
                                                            onOpenImport = { isImportRulesDialogOpen = true },
                                                            onOpenExport = {
                                                                coroutineScope.launch {
                                                                    exportRulesPayload = viewModel.exportWebsiteRulesText()
                                                                    isExportRulesDialogOpen = true
                                                                }
                                                            },
                                                            onOpenBackupRestore = {
                                                                coroutineScope.launch {
                                                                    exportBackupPayload = viewModel.exportAdBlockBackupJson()
                                                                    isBackupRestoreDialogOpen = true
                                                                }
                                                            },
                                                            onAppSearchQueryChange = { bypassAppSearchQuery = it },
                                                            onToggleWebsiteRuleEnabled = viewModel::toggleWebsiteRuleEnabled,
                                                            onDeleteWebsiteRule = viewModel::deleteWebsiteRule,
                                                            onToggleBypassApp = viewModel::toggleWebsiteBypassApp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .widthIn(max = 680.dp)
                                                    .align(Alignment.TopCenter)
                                            ) {
                                                val firewallHeaderBlock: @Composable () -> Unit = {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 4.dp)
                                                    ) {
                                                        FirewallSectionHeading()
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        FirewallHeaderCard(
                                                            isFirewallActive = isFirewallActive,
                                                            connectionDetails = uiState.connectionDetails,
                                                            totalAppsCount = uiState.allManagedApps.size,
                                                            wifiBlockedCount = uiState.wifiBlockedCount,
                                                            dataBlockedCount = uiState.dataBlockedCount,
                                                            activeBlockedNowCount = uiState.vpnRuntimeState.blockedActivePackagesCount,
                                                            isAdBlockerEnabled = uiState.preferences.isAdBlockerEnabled,
                                                            blockedAdsCount = uiState.vpnRuntimeState.blockedAdsCount,
                                                            liveTelemetry = uiState.liveTelemetry,
                                                            isGlobalWifiBlocked = uiState.preferences.globalWifiBlocked,
                                                            isHotspotProxyActive = uiState.hotspotProxyState.isProxyRunning,
                                                            isVpnGuardActive = uiState.vpnGuardState.runtimeState.isConnectedOrConnecting,
                                                            vpnGuardProfileLabel = uiState.vpnGuardState.runtimeState.activeProfileName,
                                                            blockedWebsitesCount = websiteRulesCount,
                                                            websiteRulesBadgeText = websiteRulesBadge,
                                                            dnsSummaryLabel = dnsSummary,
                                                            tunnelError = uiState.vpnRuntimeState.tunnelError,
                                                            onToggleFirewall = { enable ->
                                                                if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                                            },
                                                            onOpenConnectionInfo = viewModel::openConnectionInfo,
                                                            onOpenDnsProtection = viewModel::openDnsProtection,
                                                            onOpenSpeedTest = viewModel::openSpeedTest,
                                                            onOpenAdBlocker = { viewModel.selectDestination(AppDestination.WEBSITES) },
                                                            onOpenWebsitesTab = openWebsitesTab,
                                                            onOpenNetworkMonitor = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
                                                            onOpenHotspot = { viewModel.selectDestination(AppDestination.HOTSPOT) },
                                                            onOpenVpnGuard = { viewModel.selectDestination(AppDestination.VPN_GUARD) }
                                                        )
                                                        Spacer(modifier = Modifier.height(14.dp))
                                                        FirewallSegmentedTabToggle(
                                                            selectedTab = selectedFirewallTab,
                                                            totalAppsCount = uiState.allManagedApps.size,
                                                            onSelectTab = { tab ->
                                                                if (tab == FirewallContentTab.WEBSITES && uiState.isSelectionMode) {
                                                                    viewModel.exitSelectionMode()
                                                                }
                                                                selectedFirewallTab = tab
                                                            }
                                                        )
                                                    }
                                                }

                                                if (selectedFirewallTab == FirewallContentTab.ALL_APPS) {
                                                    AppsCollectionContent(
                                                        uiState = uiState,
                                                        isGrid = false,
                                                        viewModel = viewModel,
                                                        bottomPadding = navBarBottomInset + if (uiState.isSelectionMode) 172.dp else 120.dp,
                                                        headerContent = {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 4.dp)
                                                            ) {
                                                                firewallHeaderBlock()
                                                                Spacer(modifier = Modifier.height(10.dp))
                                                                SearchAndFiltersBlock(
                                                                    uiState = uiState,
                                                                    filtersExpanded = areAppFiltersExpanded,
                                                                    onToggleFiltersExpanded = {
                                                                        areAppFiltersExpanded = !areAppFiltersExpanded
                                                                    },
                                                                    onSearchQueryChange = viewModel::onSearchQueryChange,
                                                                    onFilterModeChange = viewModel::onFilterModeChange
                                                                )
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    LazyColumn(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .testTag("firewall_websites_list"),
                                                        contentPadding = PaddingValues(
                                                            start = 16.dp,
                                                            end = 16.dp,
                                                            top = 8.dp,
                                                            bottom = navBarBottomInset + 120.dp
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        item(key = "firewall_websites_header") {
                                                            firewallHeaderBlock()
                                                        }
                                                        websiteManagementItems(
                                                            state = websiteState,
                                                            isFirewallActive = isFirewallActive,
                                                            connectionDetails = uiState.connectionDetails,
                                                            domainInput = websiteDomainInput,
                                                            selectedSection = selectedWebsiteSection,
                                                            filteredBypassApps = filteredBypassApps,
                                                            appSearchQuery = bypassAppSearchQuery,
                                                            loadIcon = viewModel::getAppIconBitmap,
                                                            onDomainInputChange = { websiteDomainInput = it },
                                                            onSelectFilterMode = viewModel::setWebsiteFilterMode,
                                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                                            onOpenDnsProtection = viewModel::openDnsProtection,
                                                            onAllowDomainPermanently = viewModel::allowDomainPermanently,
                                                            onBlockDomainPermanently = viewModel::blockDomainPermanently,
                                                            onAllowDomainTemporarily = viewModel::allowDomainTemporarily,
                                                            onSelectSection = { selectedWebsiteSection = it },
                                                            onClearWebsiteRules = viewModel::clearWebsiteRules,
                                                            onClearBypassApps = viewModel::clearWebsiteBypassApps,
                                                            onOpenImport = { isImportRulesDialogOpen = true },
                                                            onOpenExport = {
                                                                coroutineScope.launch {
                                                                    exportRulesPayload = viewModel.exportWebsiteRulesText()
                                                                    isExportRulesDialogOpen = true
                                                                }
                                                            },
                                                            onOpenBackupRestore = {
                                                                coroutineScope.launch {
                                                                    exportBackupPayload = viewModel.exportAdBlockBackupJson()
                                                                    isBackupRestoreDialogOpen = true
                                                                }
                                                            },
                                                            onAppSearchQueryChange = { bypassAppSearchQuery = it },
                                                            onToggleWebsiteRuleEnabled = viewModel::toggleWebsiteRuleEnabled,
                                                            onDeleteWebsiteRule = viewModel::deleteWebsiteRule,
                                                            onToggleBypassApp = viewModel::toggleWebsiteBypassApp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isWideLayout && !uiState.isSelectionMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        FloatingFrostedNavBar(
                            currentDestination = uiState.currentDestination,
                            isConnectionInfoOpen = uiState.isConnectionInfoOpen,
                            onSelectFirewall = { selectAppDestination(AppDestination.FIREWALL) },
                            onSelectWebsites = { selectAppDestination(AppDestination.WEBSITES) },
                            onSelectDataUsage = {
                                selectAppDestination(AppDestination.DATA_USAGE)
                                viewModel.selectDataUsageSubTab(DataUsageSubTab.OVERVIEW)
                            },
                            onSelectDnsProtection = { selectAppDestination(AppDestination.DNS) },
                            onSelectHotspot = { selectAppDestination(AppDestination.HOTSPOT) },
                            onSelectVpnGuard = { selectAppDestination(AppDestination.VPN_GUARD) },
                            isSniDpiSelected = isSniDpiSelected,
                            onSelectSniDpi = selectSniDpi,
                            currentDataUsageSubTab = uiState.dataUsageState.selectedSubTab,
                            onSelectWifi = {
                                selectAppDestination(AppDestination.DATA_USAGE)
                                viewModel.selectDataUsageSubTab(DataUsageSubTab.WIFI)
                            },
                            onSelectSpeedTest = {
                                selectAppDestination(AppDestination.DATA_USAGE)
                                viewModel.selectDataUsageSubTab(DataUsageSubTab.SPEED_TEST)
                            }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.isSelectionMode &&
                            uiState.currentDestination == AppDestination.FIREWALL &&
                            !isSniDpiSelected,
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
        }
    }

    uiState.inspectedApp?.let { inspected ->
        AppDetailBottomSheet(
            app = inspected,
            activeNetworkType = uiState.effectiveNetworkType,
            isFirewallActive = isFirewallActive,
            nowMillis = uiState.nowMillis,
            loadIcon = viewModel::getAppIconBitmap,
            onUpdateRule = { blockWifi, blockData ->
                viewModel.setAppRules(inspected.packageName, blockWifi, blockData)
            },
            onScheduleTimer = { durationMinutes, target ->
                viewModel.scheduleAppTimer(inspected.packageName, durationMinutes, target)
                if (!isFirewallActive) {
                    onRequestEnableFirewall()
                }
            },
            onCancelTimer = {
                viewModel.cancelAppTimer(inspected.packageName)
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
            onThemeStyleChange = viewModel::setThemeStyle,
            onToggleDynamicColor = viewModel::setUseDynamicColor,
            onBlurStyleChange = viewModel::setBlurEngineStyle,
            onBlurIntensityChange = viewModel::setBlurIntensity,
            onToggleBlurAnimation = viewModel::setBlurAnimationEnabled,
            onOpenBackgroundAnimation = {
                viewModel.closeSettings()
                showBackgroundSheet = true
            },
            onAppIconChange = viewModel::setAppIconStyle,
            onReplayStartupAnimation = {
                viewModel.closeSettings()
                onReplayStartupAnimation()
            },
            onToggleShowSystemApps = viewModel::setShowSystemApps,
            onToggleShowOfflineApps = viewModel::setShowOfflineApps,
            onToggleStatusBarSpeed = onRequestToggleStatusBarSpeed,
            onToggleDeepMonitor = viewModel::setDeepMonitorEnabled,
            onToggleFirewallPriority = viewModel::setFirewallPriority,
            onToggleExcludeFromRecents = viewModel::setExcludeFromRecents,
            onSelectStatusBarStyle = viewModel::setStatusBarSpeedStyle,
            onToggleShowDailyUsage = viewModel::setShowStatusBarDailyUsage,
            onToggleLowPowerMode = viewModel::setLowPowerMode,
            onToggleIncludeBetaUpdates = viewModel::setIncludeBetaUpdates,
            appUpdateState = uiState.appUpdateState,
            vpnDpiLogsCount = maxOf(uiState.vpnDpiLogs.size, appLogs.size),
            onCheckForUpdates = viewModel::checkForGitHubUpdates,
            onDownloadUpdate = viewModel::downloadAndInstallUpdate,
            onOpenAdvancedProtection = {
                viewModel.closeSettings()
                showAdvancedProtection = true
            },
            onOpenChangelog = viewModel::openChangelog,
            onOpenVpnDpiLogs = { viewModel.openVpnDpiLogs(VpnDpiLogCategory.ALL) },
            onExportSettings = viewModel::exportSettingsTo,
            onImportSettings = viewModel::importSettingsFrom,
            onClearAllRules = viewModel::clearAllRules,
            onDismiss = viewModel::closeSettings
        )
    }

    if (settingsImportPending) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSettingsImport,
            title = { Text("Apply imported settings?") },
            text = {
                Text(
                    "This replaces your per-app rules and website rules with the imported ones, " +
                        "then reloads filtering. VPN profiles, usage history and hotspot details are " +
                        "not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::applyPendingSettingsImport,
                    modifier = Modifier.testTag("settings_import_apply_confirm")
                ) {
                    Text("Apply & reload")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSettingsImport) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAdvancedProtection) {
        AdvancedProtectionSheet(
            advanced = advancedPreferences,
            backupState = backupState,
            appUpdateState = uiState.appUpdateState,
            currentSsid = currentWifiSsid,
            onToggleKillSwitch = viewModel::setKillSwitchEnabled,
            onToggleAutoReconnect = viewModel::setAutoReconnectEnabled,
            onToggleAutoVpnUnknownWifi = viewModel::setAutoVpnOnUnknownWifi,
            onSetWifiTrusted = viewModel::setWifiTrusted,
            onRequestBatteryExemption = viewModel::requestBatteryOptimizationExemption,
            onCheckUpdates = viewModel::checkForGitHubUpdates,
            onDownloadUpdate = viewModel::downloadAndInstallUpdate,
            onExportBackup = viewModel::exportEncryptedBackup,
            onImportBackup = viewModel::importEncryptedBackup,
            onClearBackupState = viewModel::clearBackupState,
            onOpenNotificationSettings = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            },
            onDismiss = { showAdvancedProtection = false }
        )
    }

    if (uiState.isChangelogOpen) {
        AppChangelogSheet(
            updateState = uiState.appUpdateState,
            onCheckUpdates = viewModel::checkForGitHubUpdates,
            onCopiedChangelog = viewModel::notifyVpnGuardMessage,
            onDismiss = viewModel::closeChangelog
        )
    }

    if (uiState.isVpnDpiLogsOpen) {
        VpnDpiLogsSheet(
            logs = uiState.vpnDpiLogs,
            appLogs = appLogs,
            initialCategory = uiState.vpnDpiLogsInitialCategory,
            vpnRuntimeState = uiState.vpnRuntimeState,
            vpnGuardRuntimeState = uiState.vpnGuardState.runtimeState,
            onClearLogs = viewModel::clearVpnDpiLogs,
            onCopyLogs = viewModel::notifyVpnGuardMessage,
            onDismiss = viewModel::closeVpnDpiLogs
        )
    }

    if (showBackgroundSheet) {
        BackgroundAnimationSheet(
            preferences = uiState.preferences,
            onSelectAnimation = viewModel::setBackgroundAnimation,
            onToggleParallax = viewModel::setBackgroundParallaxEnabled,
            onToggleSeasonal = viewModel::setBackgroundSeasonalEnabled,
            onDismiss = { showBackgroundSheet = false }
        )
    }

    if (isImportRulesDialogOpen) {
        ImportRulesDialog(
            onDismiss = { isImportRulesDialogOpen = false },
            onImport = viewModel::importWebsiteRules
        )
    }

    if (isExportRulesDialogOpen) {
        ExportRulesDialog(
            exportedText = exportRulesPayload,
            onDismiss = { isExportRulesDialogOpen = false },
            onNotifyCopied = viewModel::notifyFilterLogAction
        )
    }

    if (isBackupRestoreDialogOpen) {
        BackupAndRestoreRulesDialog(
            backupJson = exportBackupPayload,
            onDismiss = { isBackupRestoreDialogOpen = false },
            onRestore = viewModel::restoreAdBlockBackupJson,
            onNotifyCopied = viewModel::notifyFilterLogAction
        )
    }
}

private fun navOrderIndex(key: Pair<AppDestination, Boolean>): Int =
    if (key.second) {
        2
    } else {
        when (key.first) {
            AppDestination.FIREWALL -> 0
            AppDestination.WEBSITES -> 1
            AppDestination.VPN_GUARD -> 3
            AppDestination.DNS -> 4
            AppDestination.HOTSPOT -> 5
            AppDestination.DATA_USAGE -> 6
        }
    }

private enum class FirewallContentTab {
    ALL_APPS,
    WEBSITES
}

@Composable
private fun FirewallSectionHeading() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "App Firewall & Rules",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Manage app network access, Wi-Fi, and website rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FirewallSegmentedTabToggle(
    selectedTab: FirewallContentTab,
    totalAppsCount: Int,
    onSelectTab: (FirewallContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = FirewallContentTab.entries
    val tabItems = tabs.map { tab ->
        when (tab) {
            FirewallContentTab.ALL_APPS -> FrostedSegmentedTabItem(
                label = "All Apps ($totalAppsCount)",
                testTag = "firewall_tab_all_apps",
                activeIcon = Icons.Rounded.Apps,
                inactiveIcon = Icons.Outlined.Apps
            )
            FirewallContentTab.WEBSITES -> FrostedSegmentedTabItem(
                label = "Websites",
                testTag = "firewall_tab_websites",
                activeIcon = Icons.Rounded.Public,
                inactiveIcon = Icons.Outlined.Public
            )
        }
    }
    FrostedSegmentedTabBar(
        items = tabItems,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        onSelect = { index -> onSelectTab(tabs[index]) },
        modifier = modifier.testTag("firewall_segmented_tabs")
    )
}

@Composable
private fun SearchAndFiltersBlock(
    uiState: FirewallUiState,
    filtersExpanded: Boolean,
    onToggleFiltersExpanded: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (AppFilterMode) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val hasActiveFilter = uiState.filterMode != AppFilterMode.ALL
    val showFilters = filtersExpanded || hasActiveFilter || uiState.isSelectionMode

    Column(modifier = Modifier.fillMaxWidth()) {
        FrostedSurface(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
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
                            .size(36.dp)
                            .testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFiltersExpanded,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("toggle_app_filters_button")
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Toggle app filters",
                            tint = if (showFilters) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        if (hasActiveFilter) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showFilters) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            AppFilterMode.TIMERS -> uiState.activeTimersCount
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
                        "Tap an app for Timer, long-press to select, or toggle Wi-Fi / Data."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                    key = { it.packageName },
                    contentType = { "app_rule_row" }
                ) { app ->
                    AppRuleRow(
                        app = app,
                        activeNetworkType = uiState.effectiveNetworkType,
                        isFirewallActive = isFirewallActive,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = app.packageName in uiState.selectedPackages,
                        nowMillis = uiState.nowMillis,
                        loadIcon = viewModel::getAppIconBitmap,
                        cachedIcon = viewModel::getCachedAppIconBitmap,
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
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(180),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(140)
                        )
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
                item(key = "header_controls", contentType = "header") {
                    headerContent()
                }
            }

            if (uiState.filteredApps.isEmpty()) {
                item(key = "empty_placeholder", contentType = "empty") {
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
                    key = { it.packageName },
                    contentType = { "app_rule_row" }
                ) { app ->
                    AppRuleRow(
                        app = app,
                        activeNetworkType = uiState.effectiveNetworkType,
                        isFirewallActive = isFirewallActive,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = app.packageName in uiState.selectedPackages,
                        nowMillis = uiState.nowMillis,
                        loadIcon = viewModel::getAppIconBitmap,
                        cachedIcon = viewModel::getCachedAppIconBitmap,
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
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(180),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(140)
                        )
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
                    "Long-press an app to multi-select, or tap Wi-Fi / Data toggles."
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
