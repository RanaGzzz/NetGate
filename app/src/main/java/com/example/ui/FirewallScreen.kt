package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import android.net.VpnService
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppDestination
import com.example.data.model.AppFilterMode
import com.example.data.model.NetGateThemeStyle
import com.example.data.model.ThemeMode
import com.example.ui.components.AppDetailBottomSheet
import com.example.ui.components.AppRuleRow
import com.example.ui.components.BackgroundAnimationSheet
import com.example.ui.components.BatchSelectionBar
import com.example.ui.components.ConnectionInfoSheet
import com.example.ui.components.DnsProtectionContent
import com.example.ui.components.FirewallHeaderCard
import com.example.ui.components.FirewallSettingsSheet
import com.example.ui.components.FloatingFrostedNavBar
import com.example.ui.components.FrostedBackdrop
import com.example.ui.components.FrostedSurface
import com.example.ui.components.GlassToast
import com.example.ui.components.HotspotAndWifiContent
import com.example.ui.components.VpnGuardContent
import com.example.ui.theme.BlurEngine.blurEngineModalDepth
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalOverlayLiquidState
import com.example.ui.theme.LocalThemeStyle
import io.github.fletchmckee.liquid.liquefiable

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
    val context = LocalContext.current
    var showBackgroundSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            GlassToast.show(message)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    BackHandler(
        enabled = !uiState.isSelectionMode && uiState.currentDestination != AppDestination.FIREWALL
    ) {
        viewModel.selectDestination(AppDestination.FIREWALL)
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
                    TopAppBar(
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
                                Column {
                                    Text(
                                        text = when (destination) {
                                            AppDestination.DATA_USAGE -> stringResource(R.string.data_usage_title)
                                            AppDestination.WEBSITES -> stringResource(R.string.websites_title)
                                            AppDestination.DNS -> stringResource(R.string.dns_title)
                                            AppDestination.HOTSPOT -> stringResource(R.string.hotspot_title)
                                            AppDestination.VPN_GUARD -> stringResource(R.string.vpn_guard_title)
                                            AppDestination.FIREWALL -> stringResource(R.string.app_name)
                                        },
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = when (destination) {
                                            AppDestination.DATA_USAGE -> stringResource(R.string.data_usage_subtitle)
                                            AppDestination.WEBSITES -> stringResource(R.string.websites_subtitle)
                                            AppDestination.DNS -> stringResource(R.string.dns_subtitle)
                                            AppDestination.HOTSPOT -> stringResource(R.string.hotspot_subtitle)
                                            AppDestination.VPN_GUARD -> stringResource(R.string.vpn_guard_subtitle)
                                            AppDestination.FIREWALL -> "Wi-Fi & Data Controller"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (uiState.currentDestination == AppDestination.FIREWALL) {
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
                                onClick = {
                                    when (uiState.currentDestination) {
                                        AppDestination.DATA_USAGE -> {
                                            viewModel.refreshDataUsage()
                                            if (uiState.dataUsageState.selectedSubTab == com.example.data.model.DataUsageSubTab.SECURITY_AUDIT) {
                                                viewModel.runDeviceSecurityAudit()
                                            } else if (uiState.dataUsageState.selectedSubTab == com.example.data.model.DataUsageSubTab.SPEED_TEST) {
                                                viewModel.refreshConnectionDetails()
                                            }
                                        }
                                        AppDestination.DNS -> {
                                            viewModel.refreshConnectionDetails()
                                            viewModel.runDnsCheck()
                                        }
                                        AppDestination.HOTSPOT -> {
                                            viewModel.refreshConnectionDetails()
                                            viewModel.refreshHotspotEnvironment()
                                        }
                                        AppDestination.VPN_GUARD -> {
                                            viewModel.refreshVpnGateServers(force = true)
                                        }
                                        AppDestination.FIREWALL, AppDestination.WEBSITES ->
                                            viewModel.refreshInstalledApps()
                                    }
                                },
                                modifier = Modifier.testTag("refresh_apps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = when (uiState.currentDestination) {
                                        AppDestination.DATA_USAGE -> "Sync data usage"
                                        AppDestination.DNS -> "Refresh connection status"
                                        AppDestination.HOTSPOT -> "Refresh hotspot status"
                                        AppDestination.VPN_GUARD -> "Refresh VPNGate servers"
                                        AppDestination.FIREWALL, AppDestination.WEBSITES -> "Refresh installed apps"
                                    }
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
                                selected = uiState.currentDestination == AppDestination.FIREWALL,
                                onClick = { viewModel.selectDestination(AppDestination.FIREWALL) },
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
                                selected = uiState.currentDestination == AppDestination.WEBSITES,
                                onClick = { viewModel.selectDestination(AppDestination.WEBSITES) },
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
                                selected = uiState.currentDestination == AppDestination.DATA_USAGE,
                                onClick = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
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
                                selected = uiState.currentDestination == AppDestination.DNS,
                                onClick = { viewModel.selectDestination(AppDestination.DNS) },
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
                                selected = uiState.currentDestination == AppDestination.HOTSPOT,
                                onClick = { viewModel.selectDestination(AppDestination.HOTSPOT) },
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
                                selected = uiState.currentDestination == AppDestination.VPN_GUARD,
                                onClick = { viewModel.selectDestination(AppDestination.VPN_GUARD) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.VpnLock,
                                        contentDescription = stringResource(R.string.nav_vpn_guard)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_vpn_guard)) },
                                modifier = Modifier.testTag("nav_rail_vpn_guard")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val activeStyle = LocalThemeStyle.current
                        val overlayLiquidState = LocalOverlayLiquidState.current
                        AnimatedContent(
                            targetState = uiState.currentDestination,
                            transitionSpec = {
                                val forward = AppDestination.entries.indexOf(targetState) >=
                                    AppDestination.entries.indexOf(initialState)
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
                        ) { destination ->
                            when (destination) {
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
                                        onRunSecurityAudit = viewModel::runDeviceSecurityAudit,
                                        onOpenFlaggedSitesFromAudit = viewModel::openFlaggedSitesFromAudit,
                                        speedTestState = uiState.speedTestState,
                                        activeNetworkLabel = activeNetLabel,
                                        onStartSpeedTest = viewModel::startSpeedTest,
                                        onStopSpeedTest = viewModel::stopSpeedTest,
                                        onRunVideoTest = viewModel::startVideoPlaybackTest
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
                                        onOpenDnsProtection = viewModel::openDnsProtection
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
                                        onSelectDnsTransportMode = viewModel::setDnsTransportMode,
                                        onFlushDpiCache = viewModel::flushDnsAndAutopilotCache,
                                        onRequestEnableFirewall = onRequestEnableFirewall,
                                        onCopiedHostname = viewModel::notifyCopiedDnsHostname
                                    )
                                }

                                AppDestination.HOTSPOT -> {
                                    HotspotAndWifiContent(
                                        preferences = uiState.preferences,
                                        hotspotState = uiState.hotspotProxyState,
                                        connectionDetails = uiState.connectionDetails,
                                        idleStatus = uiState.wifiIdleStatus,
                                        nowMillis = uiState.nowMillis,
                                        onToggleHotspotProxy = viewModel::setHotspotProxyEnabled,
                                        onSelectHotspotPort = viewModel::setHotspotProxyPort,
                                        onToggleApplyNetGateFilters = viewModel::setHotspotApplyNetGateFilters,
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
                                        onNotifyMessage = viewModel::notifyVpnGuardMessage
                                    )
                                }

                                AppDestination.FIREWALL -> {
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
                                                    isAdBlockerEnabled = uiState.preferences.isAdBlockerEnabled,
                                                    blockedAdsCount = uiState.vpnRuntimeState.blockedAdsCount,
                                                    liveTelemetry = uiState.liveTelemetry,
                                                    isGlobalWifiBlocked = uiState.preferences.globalWifiBlocked,
                                                    isHotspotProxyActive = uiState.hotspotProxyState.isProxyRunning,
                                                    isVpnGuardActive = uiState.vpnGuardState.runtimeState.isConnectedOrConnecting,
                                                    vpnGuardProfileLabel = uiState.vpnGuardState.runtimeState.activeProfileName,
                                                    onToggleFirewall = { enable ->
                                                        if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                                    },
                                                    onOpenConnectionInfo = viewModel::openConnectionInfo,
                                                    onOpenDnsProtection = viewModel::openDnsProtection,
                                                    onOpenSpeedTest = viewModel::openSpeedTest,
                                                    onOpenAdBlocker = { viewModel.selectDestination(AppDestination.WEBSITES) },
                                                    onOpenNetworkMonitor = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
                                                    onOpenHotspot = { viewModel.selectDestination(AppDestination.HOTSPOT) },
                                                    onOpenVpnGuard = { viewModel.selectDestination(AppDestination.VPN_GUARD) }
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
                                        Box(modifier = Modifier.fillMaxSize()) {
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
                                                    bottomPadding = navBarBottomInset + if (uiState.isSelectionMode) 172.dp else 96.dp,
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
                                                                isAdBlockerEnabled = uiState.preferences.isAdBlockerEnabled,
                                                                blockedAdsCount = uiState.vpnRuntimeState.blockedAdsCount,
                                                                liveTelemetry = uiState.liveTelemetry,
                                                                isGlobalWifiBlocked = uiState.preferences.globalWifiBlocked,
                                                                isHotspotProxyActive = uiState.hotspotProxyState.isProxyRunning,
                                                                isVpnGuardActive = uiState.vpnGuardState.runtimeState.isConnectedOrConnecting,
                                                                vpnGuardProfileLabel = uiState.vpnGuardState.runtimeState.activeProfileName,
                                                                onToggleFirewall = { enable ->
                                                                    if (enable) onRequestEnableFirewall() else viewModel.disableFirewall()
                                                                },
                                                                onOpenConnectionInfo = viewModel::openConnectionInfo,
                                                                onOpenDnsProtection = viewModel::openDnsProtection,
                                                                onOpenSpeedTest = viewModel::openSpeedTest,
                                                                onOpenAdBlocker = { viewModel.selectDestination(AppDestination.WEBSITES) },
                                                                onOpenNetworkMonitor = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
                                                                onOpenHotspot = { viewModel.selectDestination(AppDestination.HOTSPOT) },
                                                                onOpenVpnGuard = { viewModel.selectDestination(AppDestination.VPN_GUARD) }
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
                            onSelectFirewall = { viewModel.selectDestination(AppDestination.FIREWALL) },
                            onSelectWebsites = { viewModel.selectDestination(AppDestination.WEBSITES) },
                            onSelectDataUsage = { viewModel.selectDestination(AppDestination.DATA_USAGE) },
                            onSelectDnsProtection = { viewModel.selectDestination(AppDestination.DNS) },
                            onSelectHotspot = { viewModel.selectDestination(AppDestination.HOTSPOT) },
                            onSelectVpnGuard = { viewModel.selectDestination(AppDestination.VPN_GUARD) }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.isSelectionMode && uiState.currentDestination == AppDestination.FIREWALL,
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
            onClearAllRules = viewModel::clearAllRules,
            onDismiss = viewModel::closeSettings
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
                "Tap an app for Countdown Timer, or tap Wi-Fi / Data to toggle."
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
