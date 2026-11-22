package com.example.ui

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdFilterListItem
import com.example.data.model.ConnectionDetails
import com.example.data.model.DnsQueryStatus
import com.example.data.model.DnsTransportMode
import com.example.data.model.DpiBypassMode
import com.example.data.model.ManagedApp
import com.example.data.model.SniProxyPreset
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.WebsiteMainTab
import com.example.data.model.WebsiteRuleItem
import com.example.data.model.WebsiteTabSection
import com.example.data.model.YouTubeRestrictedMode
import com.example.ui.components.AdBlockerHeroCard
import com.example.ui.components.AdFilterListRow
import com.example.ui.components.DnsLogFilter
import com.example.ui.components.DnsLogsFilterCard
import com.example.ui.components.DnsQueryLogRow
import com.example.ui.components.DpiBypassShieldCard
import com.example.ui.components.FrostedSurface
import com.example.ui.components.HttpsFilteringProxyCard
import com.example.ui.components.SafeSearchAndYoutubeCard
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WifiAccent

private val QUICK_SUGGESTED_DOMAINS = listOf(
    "tiktok.com",
    "instagram.com",
    "facebook.com",
    "youtube.com",
    "x.com",
    "reddit.com"
)

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
    modifier: Modifier = Modifier
) {
    var selectedMainTab by rememberSaveable { mutableStateOf(WebsiteMainTab.AD_BLOCKER) }
    var selectedWebsiteSection by rememberSaveable { mutableStateOf(WebsiteTabSection.BLOCKLIST) }
    var domainInput by rememberSaveable { mutableStateOf("") }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var dnsLogSearchQuery by rememberSaveable { mutableStateOf("") }
    var dnsLogFilter by rememberSaveable { mutableStateOf(DnsLogFilter.ALL) }
    var isAddFilterDialogOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectedMainTab != WebsiteMainTab.AD_BLOCKER) {
        selectedMainTab = WebsiteMainTab.AD_BLOCKER
    }

    val filteredBypassApps = remember(allManagedApps, appSearchQuery, state.bypassPackages) {
        val q = appSearchQuery.trim().lowercase()
        val matching = if (q.isEmpty()) {
            allManagedApps
        } else {
            allManagedApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        matching.sortedWith(
            compareByDescending<ManagedApp> { it.packageName in state.bypassPackages }
                .thenBy { it.appName.lowercase() }
        )
    }

    val filteredDnsLogs = remember(state.dnsQueryLogs, dnsLogSearchQuery, dnsLogFilter) {
        val q = dnsLogSearchQuery.trim().lowercase()
        state.dnsQueryLogs.filter { entry ->
            val matchesQuery = q.isEmpty() ||
                entry.domain.lowercase().contains(q) ||
                entry.reason.lowercase().contains(q)
            val matchesStatus = when (dnsLogFilter) {
                DnsLogFilter.ALL -> true
                DnsLogFilter.ADS_BLOCKED -> entry.status == DnsQueryStatus.BLOCKED_AD ||
                    entry.status == DnsQueryStatus.BLOCKED_HTTPS
                DnsLogFilter.ALLOWED -> entry.status == DnsQueryStatus.ALLOWED ||
                    entry.status == DnsQueryStatus.PASSTHROUGH
            }
            matchesQuery && matchesStatus
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("website_blocker_screen")
    ) {
        val isWide = maxWidth >= 800.dp
        val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val listBottomPadding = if (isWide) 24.dp else navInsetBottom + 96.dp
        val sidePadding = if (isWide) 20.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize()) {
            WebsiteMainTabBar(
                selectedTab = selectedMainTab,
                state = state,
                onSelectTab = { selectedMainTab = it },
                modifier = Modifier.padding(horizontal = sidePadding, vertical = 4.dp)
            )

            AnimatedContent(
                targetState = selectedMainTab,
                transitionSpec = {
                    val forward = WebsiteMainTab.entries.indexOf(targetState) >=
                        WebsiteMainTab.entries.indexOf(initialState)
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
                label = "website_main_tab_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { currentTab ->
                when (currentTab) {
                    WebsiteMainTab.AD_BLOCKER -> {
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
                                    item(key = "ad_blocker_card") {
                                        AdBlockerHeroCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleAdBlocker = onToggleAdBlocker,
                                            onSyncFilterLists = onSyncAdFilterLists,
                                            onToggleEssentialPassthrough = onToggleEssentialPassthrough,
                                            onToggleUseDohUpstream = onToggleUseDohUpstream,
                                            onRequestEnableFirewall = onRequestEnableFirewall
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
                                            onRequestEnableFirewall = onRequestEnableFirewall
                                        )
                                    }
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

                    WebsiteMainTab.WEBSITES -> {
                        val effectiveRuleSection = when (selectedWebsiteSection) {
                            WebsiteTabSection.BLOCKLIST,
                            WebsiteTabSection.WHITELIST,
                            WebsiteTabSection.ALLOWED_APPS -> selectedWebsiteSection
                            else -> WebsiteTabSection.BLOCKLIST
                        }
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
                                    item(key = "mode_card") {
                                        WebsiteModeControlCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            connectionDetails = connectionDetails,
                                            onSelectFilterMode = onSelectFilterMode,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onOpenDnsProtection = onOpenDnsProtection
                                        )
                                    }
                                    item(key = "manual_input") {
                                        ManualDomainEntryCard(
                                            domainInput = domainInput,
                                            selectedSection = effectiveRuleSection,
                                            onDomainInputChange = { domainInput = it },
                                            onAddRule = { isWhitelist ->
                                                onAddWebsiteRule(domainInput, isWhitelist)
                                                domainInput = ""
                                            },
                                            onQuickAddDomain = { domain, isWhitelist ->
                                                onAddWebsiteRule(domain, isWhitelist)
                                            }
                                        )
                                    }
                                    if (state.recentBlockedDomains.isNotEmpty()) {
                                        item(key = "recent_blocked") {
                                            RecentBlockedDomainsCard(
                                                domains = state.recentBlockedDomains,
                                                onAllowDomain = { domain ->
                                                    onAddWebsiteRule(domain, true)
                                                }
                                            )
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    item(key = "website_rule_tabs") {
                                        WebsiteRulesSubTabsBar(
                                            selectedSection = effectiveRuleSection,
                                            blocklistCount = state.blocklistRules.size,
                                            whitelistCount = state.whitelistRules.size,
                                            allowedAppsCount = state.bypassPackages.size,
                                            onSelectSection = { selectedWebsiteSection = it },
                                            onClearCurrent = {
                                                when (effectiveRuleSection) {
                                                    WebsiteTabSection.BLOCKLIST -> onClearWebsiteRules(false)
                                                    WebsiteTabSection.WHITELIST -> onClearWebsiteRules(true)
                                                    WebsiteTabSection.ALLOWED_APPS -> onClearBypassApps()
                                                    else -> Unit
                                                }
                                            }
                                        )
                                    }
                                    websiteRulesSectionItems(
                                        selectedSection = effectiveRuleSection,
                                        blocklistRules = state.blocklistRules,
                                        whitelistRules = state.whitelistRules,
                                        filteredBypassApps = filteredBypassApps,
                                        bypassPackages = state.bypassPackages,
                                        appSearchQuery = appSearchQuery,
                                        onAppSearchQueryChange = { appSearchQuery = it },
                                        loadIcon = loadIcon,
                                        onToggleRuleEnabled = onToggleWebsiteRuleEnabled,
                                        onDeleteRule = onDeleteWebsiteRule,
                                        onToggleBypassApp = onToggleBypassApp
                                    )
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
                                        bottom = listBottomPadding
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "mode_card") {
                                        WebsiteModeControlCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            connectionDetails = connectionDetails,
                                            onSelectFilterMode = onSelectFilterMode,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onOpenDnsProtection = onOpenDnsProtection
                                        )
                                    }
                                    item(key = "manual_input") {
                                        ManualDomainEntryCard(
                                            domainInput = domainInput,
                                            selectedSection = effectiveRuleSection,
                                            onDomainInputChange = { domainInput = it },
                                            onAddRule = { isWhitelist ->
                                                onAddWebsiteRule(domainInput, isWhitelist)
                                                domainInput = ""
                                            },
                                            onQuickAddDomain = { domain, isWhitelist ->
                                                onAddWebsiteRule(domain, isWhitelist)
                                            }
                                        )
                                    }
                                    if (state.recentBlockedDomains.isNotEmpty()) {
                                        item(key = "recent_blocked") {
                                            RecentBlockedDomainsCard(
                                                domains = state.recentBlockedDomains,
                                                onAllowDomain = { domain ->
                                                    onAddWebsiteRule(domain, true)
                                                }
                                            )
                                        }
                                    }
                                    item(key = "website_rule_tabs") {
                                        WebsiteRulesSubTabsBar(
                                            selectedSection = effectiveRuleSection,
                                            blocklistCount = state.blocklistRules.size,
                                            whitelistCount = state.whitelistRules.size,
                                            allowedAppsCount = state.bypassPackages.size,
                                            onSelectSection = { selectedWebsiteSection = it },
                                            onClearCurrent = {
                                                when (effectiveRuleSection) {
                                                    WebsiteTabSection.BLOCKLIST -> onClearWebsiteRules(false)
                                                    WebsiteTabSection.WHITELIST -> onClearWebsiteRules(true)
                                                    WebsiteTabSection.ALLOWED_APPS -> onClearBypassApps()
                                                    else -> Unit
                                                }
                                            }
                                        )
                                    }
                                    websiteRulesSectionItems(
                                        selectedSection = effectiveRuleSection,
                                        blocklistRules = state.blocklistRules,
                                        whitelistRules = state.whitelistRules,
                                        filteredBypassApps = filteredBypassApps,
                                        bypassPackages = state.bypassPackages,
                                        appSearchQuery = appSearchQuery,
                                        onAppSearchQueryChange = { appSearchQuery = it },
                                        loadIcon = loadIcon,
                                        onToggleRuleEnabled = onToggleWebsiteRuleEnabled,
                                        onDeleteRule = onDeleteWebsiteRule,
                                        onToggleBypassApp = onToggleBypassApp
                                    )
                                }
                            }
                        }
                    }

                    WebsiteMainTab.SAFE_SEARCH -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 720.dp)
                                    .align(Alignment.TopCenter),
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = 8.dp,
                                    bottom = listBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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
                            }
                        }
                    }

                    WebsiteMainTab.PROXY_DPI -> {
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
                                    item(key = "https_proxy_card") {
                                        HttpsFilteringProxyCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleHttpsProxy = onToggleHttpsProxy,
                                            onSelectProxyPort = onSelectProxyPort,
                                            onToggleStripTracking = onToggleProxyStripTracking,
                                            onSelectSniPreset = onSelectSniPreset,
                                            onSaveCustomSniProxy = onSaveCustomSniProxy,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onCopiedEndpoint = onCopiedProxyEndpoint
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
                                            onRequestEnableFirewall = onRequestEnableFirewall
                                        )
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
                                        .testTag("website_proxy_dpi_list"),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = listBottomPadding
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "https_proxy_card") {
                                        HttpsFilteringProxyCard(
                                            state = state,
                                            isFirewallActive = isFirewallActive,
                                            onToggleHttpsProxy = onToggleHttpsProxy,
                                            onSelectProxyPort = onSelectProxyPort,
                                            onToggleStripTracking = onToggleProxyStripTracking,
                                            onSelectSniPreset = onSelectSniPreset,
                                            onSaveCustomSniProxy = onSaveCustomSniProxy,
                                            onRequestEnableFirewall = onRequestEnableFirewall,
                                            onCopiedEndpoint = onCopiedProxyEndpoint
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
                                            onRequestEnableFirewall = onRequestEnableFirewall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    WebsiteMainTab.DNS_LOGS -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 820.dp)
                                    .align(Alignment.TopCenter),
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = 8.dp,
                                    bottom = listBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "dns_logs_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Live DNS Query Feed",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (state.dnsQueryLogs.isNotEmpty()) {
                                            TextButton(
                                                onClick = onClearDnsLogs,
                                                modifier = Modifier.testTag("clear_dns_logs_button")
                                            ) {
                                                Text("Clear (${state.dnsQueryLogs.size})")
                                            }
                                        }
                                    }
                                }
                                item(key = "dns_logs_filter_header") {
                                    DnsLogsFilterCard(
                                        searchQuery = dnsLogSearchQuery,
                                        selectedFilter = dnsLogFilter,
                                        totalLogsCount = state.dnsQueryLogs.size,
                                        onSearchQueryChange = { dnsLogSearchQuery = it },
                                        onSelectFilter = { dnsLogFilter = it }
                                    )
                                }
                                if (filteredDnsLogs.isEmpty()) {
                                    item(key = "empty_dns_logs") {
                                        EmptyWebsiteRulesCard(
                                            title = if (state.dnsQueryLogs.isEmpty()) {
                                                "No DNS queries logged yet"
                                            } else {
                                                "No matching DNS queries"
                                            },
                                            subtitle = if (state.dnsQueryLogs.isEmpty()) {
                                                "Turn on Ad Blocker, Website Filter, or DPI Bypass with NetGate Firewall active to inspect live lookups here."
                                            } else {
                                                "Clear your search or filter chip to view all recent DNS lookups."
                                            },
                                            icon = Icons.Rounded.Dns
                                        )
                                    }
                                } else {
                                    items(items = filteredDnsLogs, key = { "dnslog_${it.id}" }) { entry ->
                                        DnsQueryLogRow(
                                            entry = entry,
                                            onBlockDomain = { domain -> onAddWebsiteRule(domain, false) },
                                            onAllowDomain = { domain -> onAddWebsiteRule(domain, true) }
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
private fun WebsiteMainTabBar(
    selectedTab: WebsiteMainTab,
    state: WebsiteBlockerUiState,
    onSelectTab: (WebsiteMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("website_main_tab_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(WebsiteMainTab.entries) { tab ->
            val selected = selectedTab == tab
            val label = when (tab) {
                WebsiteMainTab.AD_BLOCKER -> if (state.isAdBlockerEnabled) "${tab.label} • ON" else tab.label
                WebsiteMainTab.WEBSITES -> {
                    val count = state.blocklistRules.size + state.whitelistRules.size
                    if (count > 0) "${tab.label} ($count)" else tab.label
                }
                WebsiteMainTab.SAFE_SEARCH -> {
                    if (state.isSafeSearchEnabled || state.youtubeRestrictedMode != YouTubeRestrictedMode.OFF || state.isYoutubeAdBlockEnabled) {
                        "${tab.label} • ON"
                    } else {
                        tab.label
                    }
                }
                WebsiteMainTab.PROXY_DPI -> {
                    if (state.isHttpsProxyEnabled || state.dpiBypassMode != DpiBypassMode.OFF) {
                        "${tab.label} • ON"
                    } else {
                        tab.label
                    }
                }
                WebsiteMainTab.DNS_LOGS -> {
                    if (state.dnsQueryLogs.isNotEmpty()) "${tab.label} (${state.dnsQueryLogs.size})" else tab.label
                }
            }
            val icon = when (tab) {
                WebsiteMainTab.AD_BLOCKER -> Icons.Rounded.GppGood
                WebsiteMainTab.WEBSITES -> Icons.Rounded.Public
                WebsiteMainTab.SAFE_SEARCH -> Icons.Rounded.FamilyRestroom
                WebsiteMainTab.PROXY_DPI -> Icons.Rounded.VpnKey
                WebsiteMainTab.DNS_LOGS -> Icons.Rounded.Dns
            }

            FilterChip(
                selected = selected,
                onClick = { onSelectTab(tab) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (isDark) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    },
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("website_main_tab_${tab.name.lowercase()}")
            )
        }
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
            modifier = Modifier.testTag("sync_current_ad_section_button")
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
        OutlinedButton(
            onClick = onOpenAddCustomFilterDialog,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_add_custom_filter_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Custom Hosts / Adblock Filter URL")
        }
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
private fun WebsiteRulesSubTabsBar(
    selectedSection: WebsiteTabSection,
    blocklistCount: Int,
    whitelistCount: Int,
    allowedAppsCount: Int,
    onSelectSection: (WebsiteTabSection) -> Unit,
    onClearCurrent: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val ruleSections = listOf(
        WebsiteTabSection.BLOCKLIST,
        WebsiteTabSection.WHITELIST,
        WebsiteTabSection.ALLOWED_APPS
    )
    val currentCount = when (selectedSection) {
        WebsiteTabSection.BLOCKLIST -> blocklistCount
        WebsiteTabSection.WHITELIST -> whitelistCount
        WebsiteTabSection.ALLOWED_APPS -> allowedAppsCount
        else -> 0
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Domain Rules & App Exceptions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (currentCount > 0) {
                TextButton(
                    onClick = onClearCurrent,
                    modifier = Modifier.testTag("clear_current_website_section_button")
                ) {
                    Text("Clear ($currentCount)")
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ruleSections) { section ->
                val count = when (section) {
                    WebsiteTabSection.BLOCKLIST -> blocklistCount
                    WebsiteTabSection.WHITELIST -> whitelistCount
                    WebsiteTabSection.ALLOWED_APPS -> allowedAppsCount
                    else -> 0
                }
                val selected = selectedSection == section
                FilterChip(
                    selected = selected,
                    onClick = { onSelectSection(section) },
                    label = {
                        Text(
                            text = "${section.label} ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                        },
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("website_section_tab_${section.name.lowercase()}")
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.websiteRulesSectionItems(
    selectedSection: WebsiteTabSection,
    blocklistRules: List<WebsiteRuleItem>,
    whitelistRules: List<WebsiteRuleItem>,
    filteredBypassApps: List<ManagedApp>,
    bypassPackages: Set<String>,
    appSearchQuery: String,
    onAppSearchQueryChange: (String) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggleRuleEnabled: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleBypassApp: (String) -> Unit
) {
    when (selectedSection) {
        WebsiteTabSection.BLOCKLIST -> {
            if (blocklistRules.isEmpty()) {
                item(key = "empty_blocklist") {
                    EmptyWebsiteRulesCard(
                        title = "No blocked websites yet",
                        subtitle = "Type any domain above (e.g. tiktok.com) and tap Blocklist to prevent browsers and apps from resolving it.",
                        icon = Icons.Rounded.PublicOff
                    )
                }
            } else {
                items(items = blocklistRules, key = { "block_${it.domain}" }) { rule ->
                    WebsiteRuleRow(
                        rule = rule,
                        onToggleEnabled = { enabled -> onToggleRuleEnabled(rule.domain, enabled) },
                        onDelete = { onDeleteRule(rule.domain) }
                    )
                }
            }
        }

        WebsiteTabSection.WHITELIST -> {
            if (whitelistRules.isEmpty()) {
                item(key = "empty_whitelist") {
                    EmptyWebsiteRulesCard(
                        title = "No whitelisted websites yet",
                        subtitle = "Add trusted domains here (e.g. wikipedia.org). Whitelisted domains always bypass both Ad Blocker and Website Lockdown.",
                        icon = Icons.Rounded.GppGood
                    )
                }
            } else {
                items(items = whitelistRules, key = { "allow_${it.domain}" }) { rule ->
                    WebsiteRuleRow(
                        rule = rule,
                        onToggleEnabled = { enabled -> onToggleRuleEnabled(rule.domain, enabled) },
                        onDelete = { onDeleteRule(rule.domain) }
                    )
                }
            }
        }

        WebsiteTabSection.ALLOWED_APPS -> {
            item(key = "allowed_apps_header") {
                AllowedAppsSearchCard(
                    query = appSearchQuery,
                    onQueryChange = onAppSearchQueryChange,
                    selectedCount = bypassPackages.size
                )
            }
            if (filteredBypassApps.isEmpty()) {
                item(key = "empty_allowed_apps") {
                    EmptyWebsiteRulesCard(
                        title = "No matching apps",
                        subtitle = "Clear your app search query to select apps that bypass Website & Ad Filtering.",
                        icon = Icons.Rounded.Apps
                    )
                }
            } else {
                items(items = filteredBypassApps, key = { "bypass_${it.packageName}" }) { app ->
                    AllowedBypassAppRow(
                        app = app,
                        isBypassEnabled = app.packageName in bypassPackages,
                        loadIcon = loadIcon,
                        onToggle = { onToggleBypassApp(app.packageName) }
                    )
                }
            }
        }

        else -> Unit
    }
}

// Unused helper removed — replaced by adFilterSectionItems & websiteRulesSectionItems

@Composable
private fun WebsiteModeControlCard(
    state: WebsiteBlockerUiState,
    isFirewallActive: Boolean,
    connectionDetails: ConnectionDetails,
    onSelectFilterMode: (WebsiteFilterMode) -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onOpenDnsProtection: () -> Unit
) {
    val accent = when (state.filterMode) {
        WebsiteFilterMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        WebsiteFilterMode.BLOCKLIST -> BlockedRed
        WebsiteFilterMode.WHITELIST_ONLY -> EmeraldPrimary
    }

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (state.filterMode != WebsiteFilterMode.OFF) accent.copy(alpha = 0.55f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("website_mode_control_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (state.filterMode) {
                                    WebsiteFilterMode.OFF -> Icons.Rounded.Language
                                    WebsiteFilterMode.BLOCKLIST -> Icons.Rounded.PublicOff
                                    WebsiteFilterMode.WHITELIST_ONLY -> Icons.Rounded.Lock
                                },
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Website Filter Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFirewallActive) {
                                "VPN DNS Guard active"
                            } else {
                                "Firewall paused — tap Enable below"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFirewallActive) EmeraldPrimary else WarningAmber
                        )
                    }
                }

                if (isFirewallActive && state.filterMode != WebsiteFilterMode.OFF) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = state.filterMode.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (state.filterMode != WebsiteFilterMode.OFF && isFirewallActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DnsCounterPill(
                        label = "Blocked Queries",
                        count = state.blockedDnsCount,
                        accent = BlockedRed,
                        modifier = Modifier.weight(1f)
                    )
                    DnsCounterPill(
                        label = "Allowed Queries",
                        count = state.allowedDnsCount,
                        accent = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!isFirewallActive && state.filterMode != WebsiteFilterMode.OFF) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestEnableFirewall,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("website_enable_firewall_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Firewall to Enforce Rules")
                }
            }

            if (connectionDetails.isPrivateDnsActive &&
                connectionDetails.privateDnsMode == "hostname" &&
                state.filterMode != WebsiteFilterMode.OFF
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WarningAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Private DNS (${connectionDetails.privateDnsHostname ?: "hostname"}) is active",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Android Private DNS-over-TLS can bypass local website filtering. Set Private DNS to Automatic when using Website Blocker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenDnsProtection) {
                            Text("DNS")
                        }
                    }
                }
            }

            if (state.filterMode != WebsiteFilterMode.OFF) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WifiAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WifiAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("website_doh_guard_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GppGood,
                            contentDescription = null,
                            tint = WifiAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chrome & Firefox Anti-DoH + IPv6 Guard active. Close & reopen any already-open browser tabs to clear cached IPs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WebsiteFilterMode.entries.forEach { mode ->
                    val selected = state.filterMode == mode
                    val modeAccent = when (mode) {
                        WebsiteFilterMode.OFF -> MaterialTheme.colorScheme.primary
                        WebsiteFilterMode.BLOCKLIST -> BlockedRed
                        WebsiteFilterMode.WHITELIST_ONLY -> EmeraldPrimary
                    }
                    val onSelectThisMode = {
                        onSelectFilterMode(mode)
                        if (mode != WebsiteFilterMode.OFF && !isFirewallActive) {
                            onRequestEnableFirewall()
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            modeAccent.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                modeAccent.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onSelectThisMode)
                            .testTag("website_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = onSelectThisMode
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun DnsCounterPill(
    label: String,
    count: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualDomainEntryCard(
    domainInput: String,
    selectedSection: WebsiteTabSection,
    onDomainInputChange: (String) -> Unit,
    onAddRule: (isWhitelist: Boolean) -> Unit,
    onQuickAddDomain: (String, Boolean) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val defaultTargetIsWhitelist = selectedSection == WebsiteTabSection.WHITELIST

    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Add Website Manually",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter a domain or URL (automatically covers subdomains like *.domain.com).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (domainInput.isEmpty()) {
                            Text(
                                text = "e.g. youtube.com or https://news.ycombinator.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = domainInput,
                            onValueChange = onDomainInputChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (domainInput.isNotBlank()) {
                                        onAddRule(defaultTargetIsWhitelist)
                                        keyboard?.hide()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("website_domain_input")
                        )
                    }
                    if (domainInput.isNotEmpty()) {
                        IconButton(
                            onClick = { onDomainInputChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_domain_input_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear domain input",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onAddRule(false)
                        keyboard?.hide()
                    },
                    enabled = domainInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlockedRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_blocklist_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Blocklist", maxLines = 1)
                }

                Button(
                    onClick = {
                        onAddRule(true)
                        keyboard?.hide()
                    },
                    enabled = domainInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_whitelist_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Whitelist", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (defaultTargetIsWhitelist) {
                    "Quick-add to Whitelist:"
                } else {
                    "Quick-add to Blocklist:"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_SUGGESTED_DOMAINS.forEach { domain ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickAddDomain(domain, defaultTargetIsWhitelist) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(domain, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("quick_domain_$domain")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentBlockedDomainsCard(
    domains: List<String>,
    onAllowDomain: (String) -> Unit
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = BlockedRed.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Recently Blocked Domains (tap to Whitelist)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                domains.forEach { domain ->
                    FilterChip(
                        selected = false,
                        onClick = { onAllowDomain(domain) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = "Allow $domain",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(domain, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

// Replaced by WebsiteRulesSubTabsBar above

@Composable
private fun WebsiteRuleRow(
    rule: WebsiteRuleItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (rule.isWhitelist) EmeraldPrimary else BlockedRed
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = if (rule.isEnabled) accent.copy(alpha = 0.45f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("website_rule_row_${rule.domain}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (rule.isWhitelist) {
                            Icons.Rounded.VerifiedUser
                        } else {
                            Icons.Rounded.Block
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.domain,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (rule.isWhitelist) {
                        "Always allowed (${rule.domain} & *.${rule.domain})"
                    } else {
                        "Blocked (${rule.domain} & *.${rule.domain})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("website_rule_switch_${rule.domain}")
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("website_rule_delete_${rule.domain}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${rule.domain}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AllowedAppsSearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCount: Int
) {
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = DataAccent.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Selected Apps Bypass ($selectedCount selected)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Selected apps bypass Website Blocker & Lockdown so only these apps (plus your Whitelist domains) keep full internet access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
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
                        if (query.isEmpty()) {
                            Text(
                                text = "Filter apps to allow in Lockdown…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bypass_app_search_input")
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear app filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowedBypassAppRow(
    app: ManagedApp,
    isBypassEnabled: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggle: () -> Unit
) {
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app.packageName)
    }

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        tintColor = if (isBypassEnabled) WifiAccent.copy(alpha = 0.12f) else null,
        borderAccent = if (isBypassEnabled) WifiAccent.copy(alpha = 0.55f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .testTag("bypass_app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = app.appName,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        app.isAnyBlocked -> "Blocked in App Firewall • Unblock there to allow"
                        isBypassEnabled -> "Allowed full internet in Lockdown"
                        else -> app.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.isAnyBlocked) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = isBypassEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("bypass_app_switch_${app.packageName}")
            )
        }
    }
}

@Composable
private fun EmptyWebsiteRulesCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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

