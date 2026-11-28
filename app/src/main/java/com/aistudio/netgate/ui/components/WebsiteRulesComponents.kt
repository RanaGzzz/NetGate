package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.GppGood
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.TempAllowDuration
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.WebsiteRuleItem
import com.aistudio.netgate.data.model.WebsiteTabSection
import com.aistudio.netgate.ui.WebsiteBlockerUiState
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent

private val QUICK_SUGGESTED_DOMAINS = listOf(
    "tiktok.com",
    "instagram.com",
    "facebook.com",
    "youtube.com",
    "x.com",
    "reddit.com"
)

fun LazyListScope.websiteManagementItems(
    state: WebsiteBlockerUiState,
    isFirewallActive: Boolean,
    connectionDetails: ConnectionDetails,
    domainInput: String,
    selectedSection: WebsiteTabSection,
    filteredBypassApps: List<ManagedApp>,
    appSearchQuery: String,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDomainInputChange: (String) -> Unit,
    onSelectFilterMode: (WebsiteFilterMode) -> Unit,
    onRequestEnableFirewall: () -> Unit,
    onOpenDnsProtection: () -> Unit,
    onAllowDomainPermanently: (String) -> Unit,
    onBlockDomainPermanently: (String) -> Unit,
    onAllowDomainTemporarily: (String, TempAllowDuration) -> Unit,
    onSelectSection: (WebsiteTabSection) -> Unit,
    onClearWebsiteRules: (Boolean) -> Unit,
    onClearBypassApps: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onToggleWebsiteRuleEnabled: (String, Boolean) -> Unit,
    onDeleteWebsiteRule: (String) -> Unit,
    onToggleBypassApp: (String) -> Unit
) {
    val effectiveRuleSection = when (selectedSection) {
        WebsiteTabSection.BLOCKLIST,
        WebsiteTabSection.WHITELIST,
        WebsiteTabSection.ALLOWED_APPS -> selectedSection
        else -> WebsiteTabSection.BLOCKLIST
    }

    item(key = "website_mode_card") {
        WebsiteModeControlCard(
            state = state,
            isFirewallActive = isFirewallActive,
            connectionDetails = connectionDetails,
            onSelectFilterMode = onSelectFilterMode,
            onRequestEnableFirewall = onRequestEnableFirewall,
            onOpenDnsProtection = onOpenDnsProtection
        )
    }

    item(key = "website_manual_input") {
        ManualDomainEntryCard(
            domainInput = domainInput,
            selectedSection = effectiveRuleSection,
            onDomainInputChange = onDomainInputChange,
            onAddRule = { isWhitelist ->
                if (isWhitelist) {
                    onAllowDomainPermanently(domainInput)
                } else {
                    onBlockDomainPermanently(domainInput)
                }
                onDomainInputChange("")
            },
            onQuickAddDomain = { domain, isWhitelist ->
                if (isWhitelist) {
                    onAllowDomainPermanently(domain)
                } else {
                    onBlockDomainPermanently(domain)
                }
            },
            onAddTemporaryRule = { domain, duration ->
                onAllowDomainTemporarily(domain, duration)
                onDomainInputChange("")
            }
        )
    }

    if (state.recentBlockedDomains.isNotEmpty()) {
        item(key = "website_recent_blocked") {
            RecentBlockedDomainsCard(
                domains = state.recentBlockedDomains,
                onAllowDomain = onAllowDomainPermanently
            )
        }
    }

    item(key = "website_rule_tabs") {
        WebsiteRulesSubTabsBar(
            selectedSection = effectiveRuleSection,
            blocklistCount = state.blocklistRules.size,
            whitelistCount = state.whitelistRules.size,
            allowedAppsCount = state.bypassPackages.size,
            onSelectSection = onSelectSection,
            onClearCurrent = {
                when (effectiveRuleSection) {
                    WebsiteTabSection.BLOCKLIST -> onClearWebsiteRules(false)
                    WebsiteTabSection.WHITELIST -> onClearWebsiteRules(true)
                    WebsiteTabSection.ALLOWED_APPS -> onClearBypassApps()
                    else -> Unit
                }
            },
            onOpenImport = onOpenImport,
            onOpenExport = onOpenExport,
            onOpenBackupRestore = onOpenBackupRestore
        )
    }

    websiteRulesSectionItems(
        selectedSection = effectiveRuleSection,
        blocklistRules = state.blocklistRules,
        whitelistRules = state.whitelistRules,
        filteredBypassApps = filteredBypassApps,
        bypassPackages = state.bypassPackages,
        appSearchQuery = appSearchQuery,
        onAppSearchQueryChange = onAppSearchQueryChange,
        loadIcon = loadIcon,
        onToggleRuleEnabled = onToggleWebsiteRuleEnabled,
        onDeleteRule = onDeleteWebsiteRule,
        onToggleBypassApp = onToggleBypassApp
    )
}

@Composable
fun WebsiteRulesSubTabsBar(
    selectedSection: WebsiteTabSection,
    blocklistCount: Int,
    whitelistCount: Int,
    allowedAppsCount: Int,
    onSelectSection: (WebsiteTabSection) -> Unit,
    onClearCurrent: () -> Unit,
    onOpenImport: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenBackupRestore: () -> Unit = {}
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
        Text(
            text = "Domain Rules & App Exceptions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onOpenImport,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.testTag("rules_import_button")
            ) {
                Text("Import", maxLines = 1, softWrap = false)
            }
            TextButton(
                onClick = onOpenExport,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.testTag("rules_export_button")
            ) {
                Text("Export", maxLines = 1, softWrap = false)
            }
            TextButton(
                onClick = onOpenBackupRestore,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.testTag("rules_backup_restore_button")
            ) {
                Text("Backup", maxLines = 1, softWrap = false)
            }
            if (currentCount > 0) {
                TextButton(
                    onClick = onClearCurrent,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.testTag("clear_current_website_section_button")
                ) {
                    Text("Clear ($currentCount)", maxLines = 1, softWrap = false)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        val sectionTabItems = ruleSections.map { section ->
            val count = when (section) {
                WebsiteTabSection.BLOCKLIST -> blocklistCount
                WebsiteTabSection.WHITELIST -> whitelistCount
                WebsiteTabSection.ALLOWED_APPS -> allowedAppsCount
                else -> 0
            }
            val (activeIcon, inactiveIcon) = when (section) {
                WebsiteTabSection.BLOCKLIST -> Icons.Rounded.Block to Icons.Outlined.Block
                WebsiteTabSection.WHITELIST -> Icons.Rounded.GppGood to Icons.Outlined.GppGood
                else -> Icons.Rounded.Apps to Icons.Outlined.Apps
            }
            GlassTabItem(
                label = "${section.label} ($count)",
                testTag = "website_section_tab_${section.name.lowercase()}",
                activeIcon = activeIcon,
                inactiveIcon = inactiveIcon
            )
        }
        GlassTabs(
            items = sectionTabItems,
            selectedIndex = ruleSections.indexOf(selectedSection).coerceAtLeast(0),
            onSelect = { index -> onSelectSection(ruleSections[index]) },
            variant = GlassTabVariant.TOP_SEGMENTED,
            minScrollableTabWidth = 114.dp
        )
    }
}

fun LazyListScope.websiteRulesSectionItems(
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

@Composable
fun WebsiteModeControlCard(
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
        WebsiteFilterMode.WHITELIST_ONLY -> MaterialTheme.colorScheme.primary
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
                            color = if (isFirewallActive) MaterialTheme.colorScheme.primary else WarningAmber
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
                        WebsiteFilterMode.WHITELIST_ONLY -> MaterialTheme.colorScheme.primary
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

@Composable
fun ManualDomainEntryCard(
    domainInput: String,
    selectedSection: WebsiteTabSection,
    onDomainInputChange: (String) -> Unit,
    onAddRule: (isWhitelist: Boolean) -> Unit,
    onQuickAddDomain: (String, Boolean) -> Unit,
    onAddTemporaryRule: ((domain: String, duration: TempAllowDuration) -> Unit)? = null
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

            if (onAddTemporaryRule != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temp Allow:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TempAllowDuration.entries.forEach { duration ->
                        OutlinedButton(
                            onClick = {
                                if (domainInput.isNotBlank()) {
                                    onAddTemporaryRule(domainInput, duration)
                                    keyboard?.hide()
                                }
                            },
                            enabled = domainInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("temp_allow_${duration.name.lowercase()}_button")
                        ) {
                            Text(
                                text = duration.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
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
            GlassChipFlowGroup(
                items = QUICK_SUGGESTED_DOMAINS.map { domain ->
                    GlassTabItem(
                        label = domain,
                        activeIcon = Icons.Rounded.Add,
                        testTag = "quick_domain_$domain"
                    )
                },
                selectedIndex = null,
                onSelect = { index -> onQuickAddDomain(QUICK_SUGGESTED_DOMAINS[index], defaultTargetIsWhitelist) },
                showContainerTrack = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RecentBlockedDomainsCard(
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
            GlassChipFlowGroup(
                items = domains.map { domain ->
                    GlassTabItem(
                        label = domain,
                        activeIcon = Icons.Rounded.VerifiedUser,
                        contentDescription = "Allow $domain"
                    )
                },
                selectedIndex = null,
                onSelect = { index -> onAllowDomain(domains[index]) },
                showContainerTrack = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WebsiteRuleRow(
    rule: WebsiteRuleItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (rule.isWhitelist) EmeraldPrimary else BlockedRed
    val borderBrush = if (rule.isEnabled) {
        if (rule.isWhitelist) {
            Brush.horizontalGradient(
                listOf(
                    EmeraldPrimary.copy(alpha = 0.75f),
                    EmeraldPrimary.copy(alpha = 0.30f)
                )
            )
        } else {
            Brush.horizontalGradient(
                listOf(
                    BlockedRed.copy(alpha = 0.85f),
                    BlockedRed.copy(alpha = 0.35f)
                )
            )
        }
    } else null

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderBrushOverride = borderBrush,
        borderAccent = if (rule.isEnabled && borderBrush == null) accent.copy(alpha = 0.45f) else null,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.domain,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.isTemporary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WarningAmber.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = if (rule.isExpired()) "Expired" else "Temporary",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        rule.isExpired() -> "Expired temporary allow (inactive)"
                        rule.isTemporary -> {
                            val remainingMinutes = ((rule.expiresAtMillis - System.currentTimeMillis()) / 60000L).coerceAtLeast(1)
                            val remainingHours = remainingMinutes / 60
                            val timeStr = if (remainingHours >= 24) "${remainingHours / 24}d left" else if (remainingHours > 0) "${remainingHours}h left" else "${remainingMinutes}m left"
                            "Temporary allow • $timeStr (${rule.domain} & *.${rule.domain})"
                        }
                        rule.isWhitelist -> "Permanent allow (${rule.domain} & *.${rule.domain})"
                        else -> "Permanent block (${rule.domain} & *.${rule.domain})"
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
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent
                ),
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
fun EmptyWebsiteRulesCard(
    title: String,
    subtitle: String,
    icon: ImageVector
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
