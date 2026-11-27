package com.aistudio.netgate.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber

/**
 * Inline Background & System controls rendered inside the Background & System [SettingsAccordionCard]
 * on the main Settings list (no separate full-page drill-down).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundSystemSettingsAccordionBody(
    context: Context,
    preferences: FirewallPreferencesState,
    isIgnoringBatteryOptimizations: Boolean,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onToggleExcludeFromRecents: (Boolean) -> Unit,
    onSelectStatusBarStyle: (StatusBarSpeedStyle) -> Unit = {},
    onToggleShowDailyUsage: (Boolean) -> Unit = {},
    onToggleLowPowerMode: (Boolean) -> Unit = {},
    onToggleIncludeBetaUpdates: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var statusBarSpeedExpanded by rememberSaveable { mutableStateOf(false) }
    var excludeRecentsExpanded by rememberSaveable { mutableStateOf(false) }
    var batteryExpanded by rememberSaveable { mutableStateOf(false) }
    var lowPowerExpanded by rememberSaveable { mutableStateOf(false) }
    var updateChannelExpanded by rememberSaveable { mutableStateOf(false) }

    val batteryAccent = if (isIgnoringBatteryOptimizations) EmeraldPrimary else WarningAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("background_system_settings_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsAccordionCard(
            title = "Status Bar Speed Icon",
            summary = "Live upload/download speed & daily usage",
            icon = Icons.Rounded.NotificationsActive,
            iconAccent = if (preferences.isStatusBarSpeedEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            statusLabel = if (preferences.isStatusBarSpeedEnabled) "Active" else "Off",
            statusAccent = if (preferences.isStatusBarSpeedEnabled) {
                EmeraldPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            expanded = statusBarSpeedExpanded,
            onToggleExpand = { statusBarSpeedExpanded = !statusBarSpeedExpanded },
            modifier = Modifier.testTag("settings_status_bar_speed_card"),
            headerModifier = Modifier.testTag("settings_status_bar_speed_header")
        ) {
            AccordionToggleBody(
                description = "Pins a persistent notification showing live Rx/Tx transfer speed and daily mobile/Wi-Fi usage totals in the status bar.",
                toggleLabel = "Show live speed icon",
                checked = preferences.isStatusBarSpeedEnabled,
                onCheckedChange = onToggleStatusBarSpeed,
                switchTestTag = "settings_status_bar_speed_switch"
            )

            if (preferences.isStatusBarSpeedEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Status Bar Icon Style",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBarSpeedStyle.entries.forEach { style ->
                        val selected = preferences.statusBarSpeedStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectStatusBarStyle(style) },
                            label = {
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("settings_status_bar_style_${style.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleShowDailyUsage(!preferences.showStatusBarDailyUsage) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Today's Total in Notification",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (preferences.showStatusBarDailyUsage) {
                                "Displays today's Wi-Fi + Mobile Rx/Tx totals"
                            } else {
                                "Displays current session Rx/Tx totals"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.showStatusBarDailyUsage,
                        onCheckedChange = onToggleShowDailyUsage,
                        modifier = Modifier.testTag("settings_status_bar_daily_usage_switch")
                    )
                }
            }
        }

        SettingsAccordionCard(
            title = "Low Power Mode",
            summary = "Fewer telemetry wakeups for long sessions",
            icon = Icons.Rounded.BatteryChargingFull,
            iconAccent = if (preferences.lowPowerMode) {
                EmeraldPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            statusLabel = if (preferences.lowPowerMode) "On" else "Off",
            statusAccent = if (preferences.lowPowerMode) {
                EmeraldPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            expanded = lowPowerExpanded,
            onToggleExpand = { lowPowerExpanded = !lowPowerExpanded },
            modifier = Modifier.testTag("settings_low_power_card"),
            headerModifier = Modifier.testTag("settings_low_power_header")
        ) {
            AccordionToggleBody(
                description = "Samples live speed every 5 seconds instead of every second and rebuilds " +
                    "the status-bar icon less often. Blocking, DNS filtering and the firewall are " +
                    "unaffected; only the live readouts become coarser.",
                toggleLabel = "Reduce telemetry refresh",
                checked = preferences.lowPowerMode,
                onCheckedChange = onToggleLowPowerMode,
                switchTestTag = "settings_low_power_switch"
            )
        }

        SettingsAccordionCard(
            title = "Update Channel",
            summary = if (preferences.includeBetaUpdates) "Stable and pre-release builds" else "Stable releases only",
            icon = Icons.Rounded.CheckCircle,
            iconAccent = if (preferences.includeBetaUpdates) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            statusLabel = if (preferences.includeBetaUpdates) "Beta" else "Stable",
            statusAccent = if (preferences.includeBetaUpdates) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            expanded = updateChannelExpanded,
            onToggleExpand = { updateChannelExpanded = !updateChannelExpanded },
            modifier = Modifier.testTag("settings_update_channel_card"),
            headerModifier = Modifier.testTag("settings_update_channel_header")
        ) {
            AccordionToggleBody(
                description = "GitHub's latest-release endpoint never returns pre-releases, so beta " +
                    "builds like 3.10-beta.1 are only offered here when this is on.",
                toggleLabel = "Include pre-release builds",
                checked = preferences.includeBetaUpdates,
                onCheckedChange = onToggleIncludeBetaUpdates,
                switchTestTag = "settings_update_channel_switch"
            )
        }

        SettingsAccordionCard(
            title = "Exclude from Recents",
            summary = "Hide NetGate from Android Recent Apps",
            icon = Icons.Rounded.VisibilityOff,
            iconAccent = if (preferences.excludeFromRecents) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            statusLabel = if (preferences.excludeFromRecents) "Hidden" else "Visible",
            statusAccent = if (preferences.excludeFromRecents) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            expanded = excludeRecentsExpanded,
            onToggleExpand = { excludeRecentsExpanded = !excludeRecentsExpanded },
            modifier = Modifier.testTag("settings_exclude_from_recents_card"),
            headerModifier = Modifier.testTag("settings_exclude_from_recents_header")
        ) {
            AccordionToggleBody(
                description = "Hides the NetGate preview card from Android's Recent Apps overview switcher while background firewall protection continues running.",
                toggleLabel = "Hide from Recent Apps",
                checked = preferences.excludeFromRecents,
                onCheckedChange = onToggleExcludeFromRecents,
                switchTestTag = "settings_exclude_from_recents_switch"
            )
        }

        SettingsAccordionCard(
            title = "Background & Battery",
            summary = if (isIgnoringBatteryOptimizations) {
                "Unrestricted background execution"
            } else {
                "Battery Saver may pause VPN or speed icon"
            },
            icon = if (isIgnoringBatteryOptimizations) {
                Icons.Rounded.CheckCircle
            } else {
                Icons.Rounded.BatteryChargingFull
            },
            iconAccent = batteryAccent,
            borderAccent = batteryAccent.copy(alpha = 0.45f),
            statusLabel = if (isIgnoringBatteryOptimizations) "Allowed" else "Restricted",
            statusAccent = batteryAccent,
            statusModifier = Modifier.testTag("settings_battery_status_badge"),
            expanded = batteryExpanded,
            onToggleExpand = { batteryExpanded = !batteryExpanded },
            modifier = Modifier.testTag("settings_battery_saver_card"),
            headerModifier = Modifier.testTag("settings_battery_saver_header")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isIgnoringBatteryOptimizations) {
                        "Unrestricted — Firewall, Status Bar Speed Icon & Hotspot Proxy stay alive reliably in the background."
                    } else {
                        "Allow NetGate to run unrestricted in background so Android Battery Saver cannot pause the speed icon or VPN."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        BackgroundPowerHelper.requestIgnoreBatteryOptimizations(context)
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_battery_optimization_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "Manage Battery Optimization"
                        } else {
                            "Allow Unrestricted Background"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AccordionToggleBody(
    description: String,
    toggleLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = toggleLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(switchTestTag)
            )
        }
    }
}
