package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ConnectionRiskLevel
import com.aistudio.netgate.data.model.CountryTrafficSummary
import com.aistudio.netgate.data.model.SiteConnectionRecord
import com.aistudio.netgate.data.model.SiteTrafficFilter
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.service.DnsPacketFilter
import com.aistudio.netgate.ui.DataUsageUiState
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent

@Composable
fun SitesAndRegionsContent(
    state: DataUsageUiState,
    isFirewallActive: Boolean,
    isDeepMonitorEnabled: Boolean,
    isHttpsProxyActive: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onRequestEnableFirewall: () -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit,
    onSetShowLiveFeed: (Boolean) -> Unit,
    onSiteFilterChange: (SiteTrafficFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleCountryFilter: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQuickBlockSite: (String) -> Unit,
    onQuickAllowSite: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val feedTotalSent = state.displayedConnections.sumOf { it.bytesSent }
    val feedTotalRecv = state.displayedConnections.sumOf { it.bytesReceived }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
                .testTag("sites_and_regions_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "monitor_banner") {
                SiteMonitorBannerCard(
                    isFirewallActive = isFirewallActive,
                    isDeepMonitorEnabled = isDeepMonitorEnabled,
                    isHttpsProxyActive = isHttpsProxyActive,
                    sitesCount = state.displayedConnections.size,
                    countriesCount = state.countrySummaries.size,
                    totalSentBytes = feedTotalSent,
                    totalReceivedBytes = feedTotalRecv,
                    onRequestEnableFirewall = onRequestEnableFirewall,
                    onToggleDeepMonitor = onToggleDeepMonitor
                )
            }

            item(key = "feed_mode_toggle") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedModePill(
                        selected = state.showLiveSitesFeed,
                        label = "Live (${state.liveConnections.size})",
                        icon = Icons.Rounded.RadioButtonChecked,
                        onClick = { onSetShowLiveFeed(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sites_feed_live_button")
                    )
                    FeedModePill(
                        selected = !state.showLiveSitesFeed,
                        label = "Room History (${state.historicalConnections.size})",
                        icon = Icons.Rounded.History,
                        onClick = { onSetShowLiveFeed(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sites_feed_history_button")
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                        },
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onClearHistory)
                            .testTag("clear_site_history_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear site connection history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item(key = "site_search") {
                FrostedSurface(
                    shape = RoundedCornerShape(16.dp),
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
                            if (state.siteSearchQuery.isEmpty()) {
                                Text(
                                    text = "Search site, IP, country, region, ISP, or app…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = state.siteSearchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sites_search_input")
                            )
                        }
                        if (state.siteSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sites_search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear site search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.countrySummaries.isNotEmpty()) {
                item(key = "country_breakdown") {
                    CountryBreakdownSection(
                        summaries = state.countrySummaries,
                        selectedCountryCode = state.selectedCountryFilter,
                        onToggleCountry = onToggleCountryFilter
                    )
                }
            }

            item(key = "site_filter_chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SiteTrafficFilter.entries) { filter ->
                        val selected = state.siteFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSiteFilterChange(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (isDark) {
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                },
                                selectedContainerColor = if (filter == SiteTrafficFilter.FLAGGED) {
                                    BlockedRed.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                }
                            ),
                            modifier = Modifier.testTag("site_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (state.displayedConnections.isEmpty()) {
                item(key = "empty_sites_card") {
                    EmptySitesPlaceholderCard(
                        isFirewallActive = isFirewallActive,
                        hasAnyRawRecords = state.activeFeedTotalCount > 0,
                        showLiveFeed = state.showLiveSitesFeed,
                        onRequestEnableFirewall = onRequestEnableFirewall
                    )
                }
            } else {
                items(
                    items = state.displayedConnections,
                    key = { "${it.id}_${it.domain}_${it.remoteIp}_${it.appPackage}_${it.actionStatus}" }
                ) { record ->
                    SiteConnectionCard(
                        record = record,
                        loadIcon = loadIcon,
                        onQuickBlock = onQuickBlockSite,
                        onQuickAllow = onQuickAllowSite
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteMonitorBannerCard(
    isFirewallActive: Boolean,
    isDeepMonitorEnabled: Boolean,
    isHttpsProxyActive: Boolean,
    sitesCount: Int,
    countriesCount: Int,
    totalSentBytes: Long,
    totalReceivedBytes: Long,
    onRequestEnableFirewall: () -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = if (isFirewallActive && isDeepMonitorEnabled) {
            EmeraldPrimary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        },
        tintColor = if (isFirewallActive && isDeepMonitorEnabled) {
            EmeraldPrimary.copy(alpha = if (isDark) 0.12f else 0.08f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isFirewallActive && isDeepMonitorEnabled) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Sites, Regions & Byte Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !isFirewallActive -> "Enable NetGate Firewall VPN to capture live destinations & countries"
                                !isDeepMonitorEnabled -> "Deep site capture is paused in monitor settings"
                                isHttpsProxyActive -> "DNS + GeoIP + HTTPS Rx/Tx byte stream capture active"
                                else -> "DNS + GeoIP active (enable HTTPS Proxy in Ad & Web tab for Rx/Tx bytes)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isDeepMonitorEnabled,
                    onCheckedChange = onToggleDeepMonitor,
                    modifier = Modifier.testTag("deep_monitor_toggle_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatChip(
                    label = "$sitesCount endpoints",
                    accent = WifiAccent,
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    label = "$countriesCount regions",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    label = "↑ ${formatDataBytes(totalSentBytes)} • ↓ ${formatDataBytes(totalReceivedBytes)}",
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1.4f)
                )
            }

            if (!isFirewallActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestEnableFirewall,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("enable_monitor_vpn_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable NetGate Monitor VPN")
                }
            }
        }
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FeedModePill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        } else if (isDark) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        ),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CountryBreakdownSection(
    summaries: List<CountryTrafficSummary>,
    selectedCountryCode: String?,
    onToggleCountry: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Connected Countries & Regions (${summaries.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selectedCountryCode != null) {
                Text(
                    text = "Tap selected to show all",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleCountry(selectedCountryCode) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = summaries,
                key = { it.countryCode }
            ) { summary ->
                val isSelected = selectedCountryCode == summary.countryCode
                val borderColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    summary.suspiciousCount > 0 -> BlockedRed.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                }
                FrostedSurface(
                    shape = RoundedCornerShape(16.dp),
                    tintColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        null
                    },
                    borderAccent = borderColor,
                    modifier = Modifier
                        .width(176.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleCountry(summary.countryCode) }
                        .testTag("country_summary_${summary.countryCode.lowercase()}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${summary.countryFlag} ${summary.countryName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (summary.suspiciousCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = BlockedRed.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "${summary.suspiciousCount}!",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = BlockedRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (summary.regionSample.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = summary.regionSample,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${summary.sitesCount} site${if (summary.sitesCount == 1) "" else "s"} • ${summary.hitsCount} hits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = "↓ ${formatDataBytes(summary.bytesReceived)} • ↑ ${formatDataBytes(summary.bytesSent)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EmeraldPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteConnectionCard(
    record: SiteConnectionRecord,
    loadIcon: suspend (String) -> ImageBitmap?,
    onQuickBlock: (String) -> Unit,
    onQuickAllow: (String) -> Unit
) {
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = record.appPackage) {
        if (record.appPackage.isNotBlank()) {
            value = loadIcon(record.appPackage)
        }
    }

    val riskAccent = when (record.riskLevel) {
        ConnectionRiskLevel.SAFE -> EmeraldPrimary
        ConnectionRiskLevel.NOTICE -> WarningAmber
        ConnectionRiskLevel.SUSPICIOUS, ConnectionRiskLevel.CRITICAL -> BlockedRed
    }
    val actionAccent = if (record.actionStatus == "BLOCKED") BlockedRed else EmeraldPrimary
    val canFilterDomain = DnsPacketFilter.normalizeDomainInput(record.domain) != null

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = when (record.riskLevel) {
            ConnectionRiskLevel.CRITICAL, ConnectionRiskLevel.SUSPICIOUS -> BlockedRed.copy(alpha = 0.6f)
            ConnectionRiskLevel.NOTICE -> WarningAmber.copy(alpha = 0.45f)
            ConnectionRiskLevel.SAFE -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("site_connection_card_${record.domain}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(riskAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.domain,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (record.remotePort > 0) "${record.protocol}:${record.remotePort}" else record.protocol,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = actionAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, actionAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = record.actionStatus,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = actionAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Region, Country, IP & ISP
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.countryFlag} ${record.locationLabel}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (record.remoteIp.isNotBlank()) {
                    Text(
                        text = " • ${record.remoteIp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (record.ispOrOrg.isNotBlank()) {
                Text(
                    text = "ISP / Org: ${record.ispOrOrg}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sending ↑ / Receiving ↓ bytes + App Attribution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap!!,
                                    contentDescription = record.appName,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = record.appName.ifBlank { "Device / System DNS" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Sent",
                            tint = WarningAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDataBytes(record.bytesSent),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = WarningAmber
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Received",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDataBytes(record.bytesReceived),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = EmeraldPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.riskLevel.label} • ${record.riskReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.riskLevel == ConnectionRiskLevel.SAFE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        riskAccent
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.hitCount}x • ${formatRelativeTimeLabel(record.lastSeenAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canFilterDomain) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onQuickBlock(record.domain) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlockedRed),
                        border = BorderStroke(1.dp, BlockedRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("block_site_${record.domain}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Block Site", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { onQuickAllow(record.domain) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("allow_site_${record.domain}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Allow Site", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySitesPlaceholderCard(
    isFirewallActive: Boolean,
    hasAnyRawRecords: Boolean,
    showLiveFeed: Boolean,
    onRequestEnableFirewall: () -> Unit
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
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    hasAnyRawRecords -> "No sites match your filter"
                    !isFirewallActive -> "Enable NetGate Firewall to capture sites"
                    showLiveFeed -> "Listening for live DNS & HTTPS connections…"
                    else -> "No saved site history yet"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    hasAnyRawRecords -> "Clear the search box, country selection, or direction filter to view all endpoints."
                    else -> "Once Firewall VPN is active, domains, resolved IPs, countries, sending/receiving bytes, and security risk ratings appear here and save to Room."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isFirewallActive && !hasAnyRawRecords) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequestEnableFirewall,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Activate Firewall Monitor")
                }
            }
        }
    }
}

private fun formatRelativeTimeLabel(timestampMs: Long): String {
    if (timestampMs <= 0L) return "just now"
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 10L -> "just now"
        diffSec < 60L -> "${diffSec}s ago"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}
