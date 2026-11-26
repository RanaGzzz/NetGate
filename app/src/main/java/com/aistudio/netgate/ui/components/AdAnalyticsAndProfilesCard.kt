package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.AdAnalyticsSummary
import com.aistudio.netgate.data.model.SmartProtectionProfile
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.DataAccent
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartProtectionProfileCard(
    currentProfile: SmartProtectionProfile,
    onSelectProfile: (SmartProtectionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = EmeraldPrimary.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_protection_profile_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Protection Profiles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "One-tap rule profiles tailored for your daily workflow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartProtectionProfile.entries.forEach { profile ->
                    val isSelected = profile == currentProfile
                    val icon = when (profile) {
                        SmartProtectionProfile.BALANCED -> Icons.Rounded.Shield
                        SmartProtectionProfile.MAXIMUM_PROTECTION -> Icons.Rounded.Security
                        SmartProtectionProfile.PRIVACY_FOCUSED -> Icons.Rounded.Lock
                        SmartProtectionProfile.FAMILY_SAFE -> Icons.Rounded.FamilyRestroom
                        SmartProtectionProfile.GAMING_MODE -> Icons.Rounded.Gamepad
                    }
                    val accent = when (profile) {
                        SmartProtectionProfile.BALANCED -> EmeraldPrimary
                        SmartProtectionProfile.MAXIMUM_PROTECTION -> BlockedRed
                        SmartProtectionProfile.PRIVACY_FOCUSED -> WifiAccent
                        SmartProtectionProfile.FAMILY_SAFE -> WarningAmber
                        SmartProtectionProfile.GAMING_MODE -> DataAccent
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectProfile(profile) }
                            .testTag("profile_chip_${profile.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = profile.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile == SmartProtectionProfile.BALANCED) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = EmeraldPrimary.copy(alpha = 0.2f),
                                    modifier = Modifier.size(6.dp)
                                ) {}
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentProfile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Live interception metrics. Rendered inside the Ad & Tracker Blocker card so the blocker and its
 * analytics are one surface, matching the combined dashboard layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdAnalyticsMetricsSection(
    analytics: AdAnalyticsSummary,
    duplicatesRemovedCount: Int,
    compressedCacheBytes: Long,
    avgLookupLatencyMicros: Long,
    skippedFilterListCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalyticsTotal(
                label = "Total Requests",
                value = formatStatNumber(analytics.totalRequests),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                testTag = "analytics_stat_total_requests"
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            AnalyticsTotal(
                label = "Total Blocked",
                value = formatStatNumber(analytics.totalBlockedRequests),
                color = BlockedRed,
                modifier = Modifier.weight(1f),
                testTag = "analytics_stat_total_blocked"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5 Category Breakdown Tiles (Ads, Trackers, Malware, Phishing, Telemetry)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryPill(
                label = "Ads",
                count = analytics.adsBlocked,
                color = BlockedRed,
                icon = Icons.Rounded.Shield,
                modifier = Modifier.weight(1f)
            )
            CategoryPill(
                label = "Trackers",
                count = analytics.trackersBlocked,
                color = WifiAccent,
                icon = Icons.Rounded.TrackChanges,
                modifier = Modifier.weight(1f)
            )
            CategoryPill(
                label = "Malware",
                count = analytics.malwareBlocked,
                color = WarningAmber,
                icon = Icons.Rounded.BugReport,
                modifier = Modifier.weight(1f)
            )
            CategoryPill(
                label = "Phishing",
                count = analytics.phishingBlocked,
                color = Color(0xFFE91E63),
                icon = Icons.Rounded.Phishing,
                modifier = Modifier.weight(1f)
            )
            CategoryPill(
                label = "Telemetry",
                count = analytics.telemetryBlocked,
                color = DataAccent,
                icon = Icons.Rounded.Public,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Engine performance: latency, compressed cache, deduplication and the memory budget note.
        // A FlowRow keeps the memory-budget sentence on its own line instead of squeezing every
        // metric into a single unbreakable row.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ad_engine_performance_bar")
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val latencyLabel = if (avgLookupLatencyMicros in 1..9999) {
                    String.format(Locale.US, "%.1f ms", avgLookupLatencyMicros / 1000.0)
                } else if (avgLookupLatencyMicros >= 10000) {
                    "${avgLookupLatencyMicros / 1000L} ms"
                } else {
                    "< 1 ms"
                }
                EngineMetric(
                    icon = Icons.Rounded.Speed,
                    tint = EmeraldPrimary,
                    text = "DNS Latency: $latencyLabel",
                    textColor = EmeraldPrimary,
                    bold = true
                )
                EngineMetric(
                    icon = Icons.Rounded.FolderZip,
                    tint = DataAccent,
                    text = "GZIP: ${formatBytesCompact(compressedCacheBytes)}",
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (duplicatesRemovedCount > 0) {
                    EngineMetric(
                        icon = Icons.Rounded.Analytics,
                        tint = EmeraldPrimary,
                        text = "Dedup: -${formatStatNumber(duplicatesRemovedCount.toLong())}",
                        textColor = EmeraldPrimary
                    )
                }
                // Tells the user why not every enabled list is filtering: the engine stopped
                // adding domains once the device memory budget was reached.
                if (skippedFilterListCount > 0) {
                    Text(
                        text = "Memory budget: $skippedFilterListCount list" +
                            (if (skippedFilterListCount == 1) "" else "s") + " skipped",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                        modifier = Modifier.testTag("ad_memory_budget_warning")
                    )
                }
            }
        }
    }
}

@Composable
fun AdAnalyticsDashboardCard(
    analytics: AdAnalyticsSummary,
    modifier: Modifier = Modifier
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        borderAccent = DataAccent.copy(alpha = 0.4f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ad_analytics_dashboard_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DataAccent.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Analytics,
                            contentDescription = null,
                            tint = DataAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Blocked Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Top blocked domains & apps generating requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (analytics.topBlockedDomains.isEmpty() && analytics.topApps.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No blocked requests recorded yet. Browse with the firewall active to fill this in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top Blocked Domains (if any)
            if (analytics.topBlockedDomains.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Top Blocked Domains",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analytics.topBlockedDomains.take(5).forEach { top ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = top.domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BlockedRed.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${top.hits} hits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BlockedRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top Apps Generating Requests (if any)
            if (analytics.topApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Top Apps Generating Requests",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analytics.topApps.take(4).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appName.ifBlank { app.appPackage },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${app.totalRequests} req (${app.blockedRequests} blocked)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTotal(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag(testTag)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EngineMetric(
    icon: ImageVector,
    tint: Color,
    text: String,
    textColor: Color,
    bold: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CategoryPill(
    label: String,
    count: Long,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatStatNumber(count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatStatNumber(num: Long): String {
    return when {
        num >= 1_000_000L -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
        num >= 1_000L -> String.format(Locale.US, "%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

private fun formatBytesCompact(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}
