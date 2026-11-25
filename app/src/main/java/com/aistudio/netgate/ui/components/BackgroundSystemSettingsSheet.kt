package com.aistudio.netgate.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber

@Composable
fun BackgroundSystemSettingsContent(
    context: Context,
    preferences: FirewallPreferencesState,
    isIgnoringBatteryOptimizations: Boolean,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onToggleExcludeFromRecents: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var statusBarSpeedExpanded by rememberSaveable { mutableStateOf(false) }
    var excludeRecentsExpanded by rememberSaveable { mutableStateOf(false) }
    var batteryExpanded by rememberSaveable { mutableStateOf(false) }

    val batteryAccent = if (isIgnoringBatteryOptimizations) EmeraldPrimary else WarningAmber

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("background_system_settings_view")
    ) {
        // Header with Back Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("background_system_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Settings"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background & System",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Speed icon, recents privacy & battery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            SettingsStatusPill(
                label = if (isIgnoringBatteryOptimizations) "Allowed" else "Restricted",
                accent = batteryAccent
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsSectionHeader(
            title = "System & Background Controls",
            icon = Icons.Rounded.Tune
        )

        // 1. Status Bar Speed Icon
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Exclude from Recents
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

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Background & Battery
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
