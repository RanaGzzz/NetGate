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
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AdFilterListItem
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.DnsTransportMode
import com.aistudio.netgate.data.model.DpiBypassMode
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.SmartProtectionProfile
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.TempAllowDuration
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.ui.components.AdAnalyticsDashboardCard
import com.aistudio.netgate.ui.components.AdBlockerHeroCard
import com.aistudio.netgate.ui.components.AdFilterListRow
import com.aistudio.netgate.ui.components.AdUpstreamAndPassthroughCard
import com.aistudio.netgate.ui.components.EmptyWebsiteRulesCard
import com.aistudio.netgate.ui.components.GlassTabItem
import com.aistudio.netgate.ui.components.GlassTabVariant
import com.aistudio.netgate.ui.components.GlassTabs
import com.aistudio.netgate.ui.components.SafeSearchAndYoutubeCard
import com.aistudio.netgate.ui.components.SmartProtectionProfileCard
import com.aistudio.netgate.ui.components.rememberBottomNavClearancePadding

enum class AdBlockerTab(val label: String) {
    DASHBOARD("Dashboard"),
    ADVANCED("Advanced")
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun WebsiteBlockerContent(
    state: WebsiteBlockerUiState,
    allManagedApps: List<ManagedApp>,
    isFirewallActive: Boolean,
    connectionDetails: ConnectionDetails,
    loadIcon: suspend (String) -> ImageBitmap?,
    onSelectFilterMode: (WebsiteFilterMode) -> Unit,
    onAddWebsiteRule: (String, Boolean) -> Unit,
    onToggleWebsiteRuleEnabled: (String, Boolean) -> Unit,
    onDeleteWebsiteRule: (String) -> Unit,
    onClearWebsiteRules: (Boolean) -> Unit,
    onToggleBypassApp: (String) -> Unit,
    onClearBypassApps: () -> Unit,
    onToggleAdBlocker: (Boolean) -> Unit,
    onToggleAdFilterList: (String, Boolean) -> Unit,
    onSyncAdFilterLists: () -> Unit,
    onToggleEssentialPassthrough: (Boolean) -> Unit,
    onToggleUseDohUpstream: (Boolean) -> Unit,
    onToggleHttpsProxy: (Boolean) -> Unit,
    onSelectProxyPort: (Int) -> Unit,
    onToggleProxyStripTracking: (Boolean) -> Unit,
    onToggleSafeSearch: (Boolean) -> Unit,
    onSelectYoutubeMode: (YouTubeRestrictedMode) -> Unit,
    onToggleYoutubeAdBlock: (Boolean) -> Unit,
    onSelectSniPreset: (SniProxyPreset) -> Unit,
    onSaveCustomSniProxy: (host: String, port: Int) -> Unit,
    onSelectDpiMode: (DpiBypassMode) -> Unit,
    onSelectDpiChunkSize: (Int) -> Unit,
    onToggleDpiIdleKeepAlive: (Boolean) -> Unit,
    onToggleBlockQuic: (Boolean) -> Unit,
    onToggleAntiDnsPoison: (Boolean) -> Unit,
    onSelectDnsTransportMode: (DnsTransportMode) -> Unit,
    onFlushDpiCache: () -> Unit,
    onAddCustomAdFilterList: (title: String, url: String, category: String) -> Unit,
    onDeleteCustomAdFilterList: (String) -> Unit,
    onCopiedProxyEndpoint: (String) -> Unit,
    onClearDnsLogs: () -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onOpenDnsProtection: () -> Unit,
    onOpenDpiLogs: () -> Unit = {},
    onSelectSmartProtectionProfile: (SmartProtectionProfile) -> Unit = {},
    onAllowDomainTemporarily: (domain: String, duration: TempAllowDuration) -> Unit = { _, _ -> },
    onAllowDomainPermanently: (domain: String) -> Unit = {},
    onBlockDomainPermanently: (domain: String) -> Unit = {},
    onReportFalsePositive: (domain: String) -> Unit = {},
    onImportWebsiteRules: (rawText: String, defaultIsWhitelist: Boolean) -> Unit = { _, _ -> },
    onExportWebsiteRules: suspend () -> String = { "" },
    onExportBackupJson: suspend () -> String = { "" },
    onRestoreBackupJson: (rawJson: String) -> Unit = {},
    onNotifyCopied: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(AdBlockerTab.DASHBOARD) }
    var isAddFilterDialogOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != AdBlockerTab.DASHBOARD) {
        selectedTab = AdBlockerTab.DASHBOARD
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("website_blocker_screen")
    ) {
        val isWide = maxWidth >= 800.dp
        val listBottomPadding = if (isWide) 24.dp else rememberBottomNavClearancePadding()
        val sidePadding = if (isWide) 20.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AdBlockerTopPillTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 6.dp)
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = AdBlockerTab.entries.indexOf(targetState) >=
                        AdBlockerTab.entries.indexOf(initialState)
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
                label = "ad_blocker_tab_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { currentTab ->
                when (currentTab) {
                    AdBlockerTab.DASHBOARD -> {
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
                                        .testTag("ad_blocker_dashboard_list"),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    item(key = "ad_blocker_card") {
                                        AdBlockerHeroCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleAdBlocker = onToggleAdBlocker,
                                            onSyncFilterLists = onSyncAdFilterLists,
                                            onToggleEssentialPassthrough = onToggleEssentialPassthrough,
                                            onToggleUseDohUpstream = onToggleUseDohUpstream,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            showUpstreamAndPassthroughInHero = false
                                        )
                                    }
                                    item(key = "safesearch_youtube_card") {
                                        SafeSearchAndYoutubeCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleSafeSearch = onToggleSafeSearch,
                                            onSelectYoutubeMode = onSelectYoutubeMode,
                                            onToggleYoutubeAdBlock = onToggleYoutubeAdBlock,
                                            onRequestEnableFirewall = onRequestEnableFirewall
                                        )
                                    }
                                    item(key = "ad_upstream_passthrough_card") {
                                        AdUpstreamAndPassthroughCard(
                                            upstreamTitle = state.selectedUpstreamDns.title,
                                            useDohUpstream = state.useDohUpstream,
                                            isEssentialPassthroughEnabled = state.isEssentialPassthroughEnabled,
                                            onToggleUseDohUpstream = onToggleUseDohUpstream,
                                            onToggleEssentialPassthrough = onToggleEssentialPassthrough
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
                                    item(key = "smart_protection_profile_card") {
                                        SmartProtectionProfileCard(
                                            currentProfile = state.smartProtectionProfile,
                                            onSelectProfile = onSelectSmartProtectionProfile
                                        )
                                    }
                                    item(key = "ad_analytics_dashboard_card") {
                                        AdAnalyticsDashboardCard(analytics = state.analytics)
                                    }
                                    item(key = "add_custom_filter_cta_dashboard") {
                                        AddCustomFilterButton(onClick = { isAddFilterDialogOpen = true })
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 680.dp)
                                        .align(Alignment.TopCenter)
                                        .testTag("ad_blocker_dashboard_list"),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = listBottomPadding
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "ad_blocker_card") {
                                        AdBlockerHeroCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleAdBlocker = onToggleAdBlocker,
                                            onSyncFilterLists = onSyncAdFilterLists,
                                            onToggleEssentialPassthrough = onToggleEssentialPassthrough,
                                            onToggleUseDohUpstream = onToggleUseDohUpstream,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            showUpstreamAndPassthroughInHero = false
                                        )
                                    }
                                    item(key = "safesearch_youtube_card") {
                                        SafeSearchAndYoutubeCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleSafeSearch = onToggleSafeSearch,
                                            onSelectYoutubeMode = onSelectYoutubeMode,
                                            onToggleYoutubeAdBlock = onToggleYoutubeAdBlock,
                                            onRequestEnableFirewall = onRequestEnableFirewall
                                        )
                                    }
                                    item(key = "ad_upstream_passthrough_card") {
                                        AdUpstreamAndPassthroughCard(
                                            upstreamTitle = state.selectedUpstreamDns.title,
                                            useDohUpstream = state.useDohUpstream,
                                            isEssentialPassthroughEnabled = state.isEssentialPassthroughEnabled,
                                            onToggleUseDohUpstream = onToggleUseDohUpstream,
                                            onToggleEssentialPassthrough = onToggleEssentialPassthrough
                                        )
                                    }
                                    item(key = "smart_protection_profile_card") {
                                        SmartProtectionProfileCard(
                                            currentProfile = state.smartProtectionProfile,
                                            onSelectProfile = onSelectSmartProtectionProfile
                                        )
                                    }
                                    item(key = "ad_analytics_dashboard_card") {
                                        AdAnalyticsDashboardCard(analytics = state.analytics)
                                    }
                                    item(key = "add_custom_filter_cta_dashboard") {
                                        AddCustomFilterButton(onClick = { isAddFilterDialogOpen = true })
                                    }
                                }
                            }
                        }
                    }

                    AdBlockerTab.ADVANCED -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                                    .align(Alignment.TopCenter)
                                    .testTag("ad_blocker_advanced_list"),
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = 8.dp,
                                    bottom = listBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "ad_lists_header") {
                                    AdListsHeaderRow(
                                        enabledCount = state.adFilterLists.count { it.isEnabled },
                                        onSyncAll = onSyncAdFilterLists
                                    )
                                }
                                adFilterSectionItems(
                                    adFilterLists = state.adFilterLists,
                                    onToggleAdFilterList = onToggleAdFilterList,
                                    onOpenAddCustomFilterDialog = { isAddFilterDialogOpen = true },
                                    onDeleteCustomAdFilterList = onDeleteCustomAdFilterList
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddFilterDialogOpen) {
        AddCustomFilterListDialog(
            onDismiss = { isAddFilterDialogOpen = false },
            onConfirm = { title, url, category ->
                onAddCustomAdFilterList(title, url, category)
                isAddFilterDialogOpen = false
            }
        )
    }
}

@Composable
private fun AdBlockerTopPillTabs(
    selectedTab: AdBlockerTab,
    onSelectTab: (AdBlockerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = AdBlockerTab.entries
    val tabItems = tabs.map { tab ->
        val (activeIcon, inactiveIcon) = when (tab) {
            AdBlockerTab.DASHBOARD -> Icons.Rounded.Dashboard to Icons.Outlined.Dashboard
            AdBlockerTab.ADVANCED -> Icons.Rounded.Settings to Icons.Outlined.Settings
        }
        GlassTabItem(
            label = tab.label,
            testTag = "ad_blocker_tab_${tab.name.lowercase()}",
            activeIcon = activeIcon,
            inactiveIcon = inactiveIcon
        )
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        GlassTabs(
            items = tabItems,
            selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
            onSelect = { index -> onSelectTab(tabs[index]) },
            variant = GlassTabVariant.TOP_SEGMENTED,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .testTag("ad_blocker_tab_bar")
        )
    }
}

@Composable
private fun AddCustomFilterButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("open_add_custom_filter_button")
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Add Custom Hosts / Filter URL",
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AdListsHeaderRow(
    enabledCount: Int,
    onSyncAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Filter Lists ($enabledCount active)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onSyncAll,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("sync_current_ad_section_button")
        ) {
            Text("Sync All")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.adFilterSectionItems(
    adFilterLists: List<AdFilterListItem>,
    onToggleAdFilterList: (String, Boolean) -> Unit,
    onOpenAddCustomFilterDialog: () -> Unit,
    onDeleteCustomAdFilterList: (String) -> Unit
) {
    item(key = "add_custom_filter_cta") {
        AddCustomFilterButton(onClick = onOpenAddCustomFilterDialog)
    }
    if (adFilterLists.isEmpty()) {
        item(key = "empty_ad_filters") {
            EmptyWebsiteRulesCard(
                title = "Loading ad filter lists…",
                subtitle = "Built-in ad & tracker protection core is active whenever Ad Blocker is turned on.",
                icon = Icons.Rounded.FilterList
            )
        }
    } else {
        items(items = adFilterLists, key = { "adlist_${it.id}" }) { item ->
            AdFilterListRow(
                item = item,
                onToggleEnabled = { enabled -> onToggleAdFilterList(item.id, enabled) },
                onDeleteCustomList = if (item.id.startsWith("custom_")) {
                    { onDeleteCustomAdFilterList(item.id) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun AddCustomFilterListDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, category: String) -> Unit
) {
    var titleInput by rememberSaveable { mutableStateOf("") }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var categoryInput by rememberSaveable { mutableStateOf("Custom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Filter List") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Supports Hosts (0.0.0.0), Adblock (||domain^), and OISD wildcard (*.domain) lists over HTTPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("List Name") },
                    placeholder = { Text("e.g. HaGeZi Light") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_filter_title_input")
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("HTTPS Filter URL") },
                    placeholder = { Text("https://example.com/hosts.txt") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_filter_url_input")
                )
                OutlinedTextField(
                    value = categoryInput,
                    onValueChange = { categoryInput = it },
                    label = { Text("Category Tag") },
                    placeholder = { Text("Ads & Trackers") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_filter_category_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(titleInput, urlInput, categoryInput) },
                enabled = urlInput.trim().startsWith("https://", ignoreCase = true),
                modifier = Modifier.testTag("confirm_add_custom_filter_button")
            ) {
                Text("Add List")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
