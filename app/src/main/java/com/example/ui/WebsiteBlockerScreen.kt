package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.ConnectionDetails
import com.example.data.model.ManagedApp
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.WebsiteRuleItem
import com.example.data.model.WebsiteTabSection
import com.example.ui.components.FrostedSurface
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
    onRequestEnableFirewall: () -> Unit,
    onOpenDnsProtection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable { mutableStateOf(WebsiteTabSection.BLOCKLIST) }
    var domainInput by rememberSaveable { mutableStateOf("") }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("website_blocker_screen")
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
                            selectedSection = selectedSection,
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
                    item(key = "section_tabs") {
                        WebsiteSectionTabsBar(
                            selectedSection = selectedSection,
                            blocklistCount = state.blocklistRules.size,
                            whitelistCount = state.whitelistRules.size,
                            allowedAppsCount = state.bypassPackages.size,
                            onSelectSection = { selectedSection = it },
                            onClearCurrent = {
                                when (selectedSection) {
                                    WebsiteTabSection.BLOCKLIST -> onClearWebsiteRules(false)
                                    WebsiteTabSection.WHITELIST -> onClearWebsiteRules(true)
                                    WebsiteTabSection.ALLOWED_APPS -> onClearBypassApps()
                                }
                            }
                        )
                    }

                    websiteSectionItems(
                        selectedSection = selectedSection,
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
                            selectedSection = selectedSection,
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

                    item(key = "section_tabs") {
                        WebsiteSectionTabsBar(
                            selectedSection = selectedSection,
                            blocklistCount = state.blocklistRules.size,
                            whitelistCount = state.whitelistRules.size,
                            allowedAppsCount = state.bypassPackages.size,
                            onSelectSection = { selectedSection = it },
                            onClearCurrent = {
                                when (selectedSection) {
                                    WebsiteTabSection.BLOCKLIST -> onClearWebsiteRules(false)
                                    WebsiteTabSection.WHITELIST -> onClearWebsiteRules(true)
                                    WebsiteTabSection.ALLOWED_APPS -> onClearBypassApps()
                                }
                            }
                        )
                    }

                    websiteSectionItems(
                        selectedSection = selectedSection,
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

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.websiteSectionItems(
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
                        subtitle = "Add trusted domains here (e.g. wikipedia.org, bank.com). Whitelisted domains always stay accessible—even in strict Lockdown mode.",
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
                        subtitle = "Clear your app search query to select apps that bypass Website Lockdown.",
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
    }
}

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

@Composable
private fun WebsiteSectionTabsBar(
    selectedSection: WebsiteTabSection,
    blocklistCount: Int,
    whitelistCount: Int,
    allowedAppsCount: Int,
    onSelectSection: (WebsiteTabSection) -> Unit,
    onClearCurrent: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val currentCount = when (selectedSection) {
        WebsiteTabSection.BLOCKLIST -> blocklistCount
        WebsiteTabSection.WHITELIST -> whitelistCount
        WebsiteTabSection.ALLOWED_APPS -> allowedAppsCount
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rules & Allowed Apps",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WebsiteTabSection.entries.forEach { section ->
                val count = when (section) {
                    WebsiteTabSection.BLOCKLIST -> blocklistCount
                    WebsiteTabSection.WHITELIST -> whitelistCount
                    WebsiteTabSection.ALLOWED_APPS -> allowedAppsCount
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
                    modifier = Modifier.testTag("website_section_tab_${section.name.lowercase()}")
                )
            }
        }
    }
}

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
