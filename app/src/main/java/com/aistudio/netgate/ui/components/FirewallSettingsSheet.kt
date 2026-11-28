package com.aistudio.netgate.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.data.model.AppIconStyle
import com.aistudio.netgate.data.model.AppUpdateStatus
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WarningAmber
import com.aistudio.netgate.ui.theme.WifiAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallSettingsSheet(
    preferences: FirewallPreferencesState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeStyleChange: (NetGateThemeStyle) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit,
    onBlurStyleChange: (BlurEngineStyle) -> Unit,
    onBlurIntensityChange: (Float) -> Unit = {},
    onToggleBlurAnimation: (Boolean) -> Unit,
    onOpenBackgroundAnimation: () -> Unit = {},
    onAppIconChange: (AppIconStyle) -> Unit = {},
    onReplayStartupAnimation: () -> Unit = {},
    onToggleShowSystemApps: (Boolean) -> Unit,
    onToggleShowOfflineApps: (Boolean) -> Unit,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit,
    onToggleFirewallPriority: (Boolean) -> Unit = {},
    onToggleExcludeFromRecents: (Boolean) -> Unit = {},
    onSelectStatusBarStyle: (com.aistudio.netgate.data.model.StatusBarSpeedStyle) -> Unit = {},
    onToggleShowDailyUsage: (Boolean) -> Unit = {},
    onToggleLowPowerMode: (Boolean) -> Unit = {},
    onToggleIncludeBetaUpdates: (Boolean) -> Unit = {},
    appUpdateState: AppUpdateUiState = AppUpdateUiState(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE
    ),
    vpnDpiLogsCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onOpenAdvancedProtection: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenVpnDpiLogs: () -> Unit = {},
    onExportSettings: (Uri) -> Unit = {},
    onImportSettings: (Uri) -> Unit = {},
    onOpenSelfTest: () -> Unit = {},
    onClearAllRules: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(BackgroundPowerHelper.isIgnoringBatteryOptimizations(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isIgnoringBatteryOptimizations = BackgroundPowerHelper.isIgnoringBatteryOptimizations(context)
    }

    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var backgroundSystemExpanded by rememberSaveable { mutableStateOf(false) }
    var monitorExpanded by rememberSaveable { mutableStateOf(false) }
    var priorityExpanded by rememberSaveable { mutableStateOf(false) }
    var architectureExpanded by rememberSaveable { mutableStateOf(false) }
    var logsExpanded by rememberSaveable { mutableStateOf(false) }
    var protectionExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable {
        mutableStateOf(appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE)
    }
    var resetExpanded by rememberSaveable { mutableStateOf(false) }
    var alwaysOnExpanded by rememberSaveable { mutableStateOf(false) }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportSettings(uri) }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportSettings(uri) }

    LaunchedEffect(appUpdateState.status) {
        if (appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE) {
            aboutExpanded = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        FrostedSheetContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .testTag("firewall_settings_sheet")
            ) {
                FrostedSheetDragHandle()
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Appearance, background controls & protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsStatusPill(
                        label = "v${BuildConfig.VERSION_NAME}",
                        accent = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ─────────────────────────────────────────────────────────────
                // SECTION 1: PERSONALIZATION & SYSTEM MENUS
                // ─────────────────────────────────────────────────────────────
                SettingsSectionHeader(
                    title = "Personalization & System",
                    icon = Icons.Rounded.Palette
                )

                SettingsAccordionCard(
                    title = "Appearance",
                    summary = "${preferences.themeMode.label} • ${preferences.themeStyle.title} • ${preferences.blurEngineStyle.title}",
                    icon = Icons.Rounded.Palette,
                    statusLabel = preferences.themeStyle.badgeLabel,
                    expanded = appearanceExpanded,
                    onToggleExpand = { appearanceExpanded = !appearanceExpanded },
                    modifier = Modifier.testTag("settings_appearance_card"),
                    headerModifier = Modifier.testTag("settings_open_appearance")
                ) {
                    AppearanceSettingsAccordionBody(
                        preferences = preferences,
                        onThemeModeChange = onThemeModeChange,
                        onThemeStyleChange = onThemeStyleChange,
                        onToggleDynamicColor = onToggleDynamicColor,
                        onBlurStyleChange = onBlurStyleChange,
                        onBlurIntensityChange = onBlurIntensityChange,
                        onToggleBlurAnimation = onToggleBlurAnimation,
                        onOpenBackgroundAnimation = onOpenBackgroundAnimation,
                        onAppIconChange = onAppIconChange
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val systemActiveCount = listOf(
                    preferences.isStatusBarSpeedEnabled,
                    preferences.excludeFromRecents,
                    isIgnoringBatteryOptimizations
                ).count { it }
                SettingsAccordionCard(
                    title = "Background & System",
                    summary = "Status Bar Speed Icon • Exclude from Recents • Background & Battery",
                    icon = Icons.Rounded.Tune,
                    iconAccent = if (isIgnoringBatteryOptimizations) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        WarningAmber
                    },
                    statusLabel = "$systemActiveCount/3",
                    statusAccent = if (isIgnoringBatteryOptimizations) {
                        EmeraldPrimary
                    } else {
                        WarningAmber
                    },
                    expanded = backgroundSystemExpanded,
                    onToggleExpand = { backgroundSystemExpanded = !backgroundSystemExpanded },
                    modifier = Modifier.testTag("settings_background_system_card"),
                    headerModifier = Modifier.testTag("settings_open_background_system")
                ) {
                    BackgroundSystemSettingsAccordionBody(
                        context = context,
                        preferences = preferences,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onToggleStatusBarSpeed = onToggleStatusBarSpeed,
                        onToggleExcludeFromRecents = onToggleExcludeFromRecents,
                        onSelectStatusBarStyle = onSelectStatusBarStyle,
                        onToggleShowDailyUsage = onToggleShowDailyUsage,
                        onToggleLowPowerMode = onToggleLowPowerMode,
                        onToggleIncludeBetaUpdates = onToggleIncludeBetaUpdates
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                            // ─────────────────────────────────────────────────────────────
                            // SECTION 2: FIREWALL & MONITORING
                            // ─────────────────────────────────────────────────────────────
                            SettingsSectionHeader(
                                title = "Firewall & Monitoring",
                                icon = Icons.Rounded.Shield
                            )

                            val enabledMonitorCount = listOf(
                                preferences.showSystemApps,
                                preferences.showOfflineApps,
                                preferences.isDeepMonitorEnabled
                            ).count { it }
                            val monitorSummaryParts = buildList {
                                if (preferences.showSystemApps) add("System")
                                if (preferences.showOfflineApps) add("Offline")
                                if (preferences.isDeepMonitorEnabled) add("Deep Monitor")
                            }
                            SettingsAccordionCard(
                                title = "App Visibility & Monitor",
                                summary = if (monitorSummaryParts.isEmpty()) {
                                    "User internet apps • Deep Monitor off"
                                } else {
                                    monitorSummaryParts.joinToString(" • ") + " enabled"
                                },
                                icon = Icons.Rounded.Shield,
                                statusLabel = "$enabledMonitorCount/3 On",
                                statusAccent = if (enabledMonitorCount > 0) {
                                    EmeraldPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                expanded = monitorExpanded,
                                onToggleExpand = { monitorExpanded = !monitorExpanded },
                                modifier = Modifier.testTag("settings_firewall_monitor_card"),
                                headerModifier = Modifier.testTag("settings_firewall_monitor_header")
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CompactSwitchRow(
                                        title = "Show System Apps",
                                        summary = "Include pre-installed Android system apps",
                                        checked = preferences.showSystemApps,
                                        onCheckedChange = onToggleShowSystemApps,
                                        testTag = "settings_show_system_apps_switch"
                                    )
                                    SettingsCardDivider()
                                    CompactSwitchRow(
                                        title = "Show Offline Apps",
                                        summary = "Include apps without INTERNET permission",
                                        checked = preferences.showOfflineApps,
                                        onCheckedChange = onToggleShowOfflineApps,
                                        testTag = "settings_show_offline_apps_switch"
                                    )
                                    SettingsCardDivider()
                                    CompactSwitchRow(
                                        title = "Deep Site & Region Monitor",
                                        summary = "Log contacted domains & GeoIP in Room",
                                        expandedDescription = "Logs contacted domains, GeoIP countries, Rx/Tx byte counters, and threat scores locally in Room database.",
                                        checked = preferences.isDeepMonitorEnabled,
                                        icon = Icons.Rounded.Public,
                                        onCheckedChange = onToggleDeepMonitor,
                                        testTag = "settings_deep_monitor_switch"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SettingsAccordionCard(
                                title = "Blocking Priority",
                                summary = if (preferences.firewallPriority) {
                                    "App blocks enforced by the kernel sinkhole"
                                } else {
                                    "DNS & ad filtering kept, app blocks best-effort"
                                },
                                icon = Icons.Rounded.Block,
                                statusLabel = if (preferences.firewallPriority) "Blocks First" else "Filters First",
                                statusAccent = if (preferences.firewallPriority) {
                                    EmeraldPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                expanded = priorityExpanded,
                                onToggleExpand = { priorityExpanded = !priorityExpanded },
                                modifier = Modifier.testTag("settings_blocking_priority_card")
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CompactSwitchRow(
                                        title = "Block Apps Before Filtering",
                                        summary = "Blocked apps are cut off at the VPN route level",
                                        expandedDescription = "Android gives a VPN session to one app set at a time. With this on, the tunnel carries only the apps you blocked, so their traffic is rejected even over raw IPs, QUIC or DoH. While it is on, DNS ad & website filtering is paused because that needs every app's DNS. Turn it off to keep filtering and enforce app blocks through DNS and the local proxy instead.",
                                        checked = preferences.firewallPriority,
                                        icon = Icons.Rounded.Block,
                                        onCheckedChange = onToggleFirewallPriority,
                                        testTag = "settings_firewall_priority_switch"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SettingsAccordionCard(
                                title = "Always-on VPN",
                                summary = "Keep filtering up after a reboot or crash",
                                icon = Icons.Rounded.Shield,
                                expanded = alwaysOnExpanded,
                                onToggleExpand = { alwaysOnExpanded = !alwaysOnExpanded },
                                modifier = Modifier.testTag("settings_always_on_card"),
                                headerModifier = Modifier.testTag("settings_always_on_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Android's Always-on VPN asks the system to keep NetGate's VPN running after a reboot and reconnect it when the network changes. Turn it on for NetGate in the system VPN settings; NetGate itself does not need to be open.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val opened = runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_VPN_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }.isSuccess
                                            if (!opened) {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_always_on_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Open System VPN Settings",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SettingsAccordionCard(
                                title = "Banking-Safe Architecture",
                                summary = "Zero Accessibility • Local VPN sinkhole",
                                icon = Icons.Rounded.VerifiedUser,
                                iconAccent = EmeraldPrimary,
                                tintColor = EmeraldPrimary.copy(alpha = 0.10f),
                                borderAccent = EmeraldPrimary.copy(alpha = 0.65f),
                                statusLabel = "Verified",
                                statusAccent = EmeraldPrimary,
                                expanded = architectureExpanded,
                                onToggleExpand = { architectureExpanded = !architectureExpanded },
                                modifier = Modifier.testTag("banking_safe_badge"),
                                headerModifier = Modifier.testTag("banking_safe_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "• Zero Accessibility Services: NetGate never requests Accessibility permissions, screen overlays, or root access, so banking and payment apps remain unaffected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Local VPN Sinkhole: Only blocked apps are routed into the local sinkhole on the active network (Wi-Fi or Mobile Data), keeping allowed apps at full native speed. Android allows one active VPN at a time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // ─────────────────────────────────────────────────────────────
                            // SECTION 3: PROTECTION, LOGS & UPDATES
                            // ─────────────────────────────────────────────────────────────
                            SettingsSectionHeader(
                                title = "Protection, Logs & Updates",
                                icon = Icons.Rounded.Terminal
                            )

                            SettingsAccordionCard(
                                title = "Diagnostics & Crash Logs",
                                summary = "Handshake, bypass & crash telemetry",
                                icon = Icons.Rounded.Terminal,
                                statusLabel = if (vpnDpiLogsCount > 0) "$vpnDpiLogsCount logs" else "Live",
                                expanded = logsExpanded,
                                onToggleExpand = { logsExpanded = !logsExpanded },
                                modifier = Modifier.testTag("settings_diagnostics_logs_card"),
                                headerModifier = Modifier.testTag("settings_diagnostics_logs_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Inspect real-time WireGuard/VPN Guard handshakes, Firewall TUN events, QUIC downgrades, DPI packet split telemetry, and any crash recorded by NetGate. Copy an entry to share it for troubleshooting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenVpnDpiLogs()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_open_vpn_dpi_logs_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Terminal,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (vpnDpiLogsCount > 0) {
                                                "Open Diagnostics Logs ($vpnDpiLogsCount)"
                                            } else {
                                                "Open Diagnostics Logs"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onOpenSelfTest,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_open_self_test_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Run in-app self-test",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SettingsAccordionCard(
                                title = "Kill Switch, Backup & Automation",
                                summary = "Auto-reconnect, trusted Wi-Fi & AES-256 backup",
                                icon = Icons.Rounded.Security,
                                iconAccent = MaterialTheme.colorScheme.secondary,
                                statusLabel = "Suite",
                                statusAccent = MaterialTheme.colorScheme.secondary,
                                expanded = protectionExpanded,
                                onToggleExpand = { protectionExpanded = !protectionExpanded },
                                modifier = Modifier.testTag("settings_advanced_protection_card"),
                                headerModifier = Modifier.testTag("settings_advanced_protection_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Configure Android VPN Kill Switch, trusted Wi-Fi automation rules, and encrypted AES-256 rule export & restore.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = onOpenAdvancedProtection,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_advanced_protection")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Open Kill Switch & Backup",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Settings file (JSON): per-app rules, website rules, filter lists and preferences. VPN profiles, usage history and hotspot client details are never included.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                exportSettingsLauncher.launch("netgate-settings-${BuildConfig.VERSION_NAME}.json")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("settings_export_json_button")
                                        ) {
                                            Text(
                                                text = "Export settings",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { importSettingsLauncher.launch(arrayOf("application/json")) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("settings_import_json_button")
                                        ) {
                                            Text(
                                                text = "Import settings",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // About & Updates Accordion Card
                            val updateAccent = when (appUpdateState.status) {
                                AppUpdateStatus.UPDATE_AVAILABLE -> EmeraldPrimary
                                AppUpdateStatus.UP_TO_DATE -> WifiAccent
                                AppUpdateStatus.ERROR -> BlockedRed
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val updateBadgeLabel = when (appUpdateState.status) {
                                AppUpdateStatus.IDLE -> "v${BuildConfig.VERSION_NAME}"
                                AppUpdateStatus.CHECKING -> "Checking…"
                                AppUpdateStatus.UP_TO_DATE -> "Up to date"
                                AppUpdateStatus.UPDATE_AVAILABLE -> appUpdateState.latestRelease?.tagName.orEmpty().ifBlank { "Update" }
                                AppUpdateStatus.ERROR -> "Check failed"
                            }

                            SettingsAccordionCard(
                                title = "About & Updates",
                                summary = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                icon = Icons.Rounded.SystemUpdate,
                                iconAccent = updateAccent,
                                borderAccent = if (appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE) {
                                    EmeraldPrimary.copy(alpha = 0.6f)
                                } else {
                                    null
                                },
                                statusLabel = updateBadgeLabel,
                                statusAccent = updateAccent,
                                statusModifier = Modifier.testTag("settings_update_status_badge"),
                                summaryModifier = Modifier.testTag("settings_app_version"),
                                expanded = aboutExpanded,
                                onToggleExpand = { aboutExpanded = !aboutExpanded },
                                modifier = Modifier.testTag("settings_about_card"),
                                headerModifier = Modifier.testTag("settings_about_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    appUpdateState.latestRelease?.let { release ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = updateAccent.copy(alpha = 0.10f),
                                            border = BorderStroke(1.dp, updateAccent.copy(alpha = 0.28f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("settings_latest_release_summary")
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.NewReleases,
                                                        contentDescription = null,
                                                        tint = updateAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${release.title} (${release.tagName})",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                val metaParts = buildList {
                                                    if (release.publishedAt.isNotBlank()) add("Released ${release.publishedAt}")
                                                    if (!release.apkName.isNullOrBlank()) {
                                                        val size = if (release.apkSizeBytes > 0L) " (${formatDataBytes(release.apkSizeBytes)})" else ""
                                                        add("${release.apkName}$size")
                                                    }
                                                }
                                                if (metaParts.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = metaParts.joinToString(" • "),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    appUpdateState.errorMessage?.let { err ->
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BlockedRed,
                                            modifier = Modifier.testTag("settings_update_error_text")
                                        )
                                    }

                                    if (appUpdateState.lastCheckedAtMs > 0L) {
                                        Text(
                                            text = "Last checked ${formatCheckedTime(appUpdateState.lastCheckedAtMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = onCheckForUpdates,
                                            enabled = appUpdateState.status != AppUpdateStatus.CHECKING,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("settings_check_updates_button")
                                        ) {
                                            if (appUpdateState.status == AppUpdateStatus.CHECKING) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.SystemUpdate,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (appUpdateState.status == AppUpdateStatus.CHECKING) {
                                                    "Checking…"
                                                } else {
                                                    "Check Updates"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onOpenChangelog()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("settings_open_changelog_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Changelog",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    appUpdateState.latestRelease?.let { release ->
                                        if (appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE) {
                                            Button(
                                                onClick = { openUrlInBrowser(context, release.preferredOpenUrl) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("settings_download_update_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (!release.apkName.isNullOrBlank()) {
                                                        "Download ${release.tagName} APK"
                                                    } else {
                                                        "Open Release ${release.tagName}"
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (appUpdateState.canInstallDirectly) {
                                        Button(
                                            onClick = onDownloadUpdate,
                                            enabled = !appUpdateState.isDownloadingUpdate,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("settings_download_update")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SystemUpdate,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (appUpdateState.isDownloadingUpdate) {
                                                    "Downloading ${appUpdateState.downloadProgress}%"
                                                } else {
                                                    "Download & install update"
                                                }
                                            )
                                        }
                                    }

                                    SettingsCardDivider()

                                    buildIdentityText()?.let { identity ->
                                        Text(
                                            text = identity,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("settings_build_identity")
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { openRepository(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_github_link")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "GitHub Repository (RanaGzzz/NetGate)",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Reset All App Rules Accordion Card
                            SettingsAccordionCard(
                                title = "Reset All App Rules",
                                summary = "Restore default Wi-Fi & Mobile Data access",
                                icon = Icons.Rounded.DeleteSweep,
                                iconAccent = MaterialTheme.colorScheme.error,
                                borderAccent = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                                statusLabel = "Reset",
                                statusAccent = MaterialTheme.colorScheme.error,
                                expanded = resetExpanded,
                                onToggleExpand = { resetExpanded = !resetExpanded },
                                modifier = Modifier.testTag("settings_reset_rules_card"),
                                headerModifier = Modifier.testTag("settings_reset_rules_header")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Clears every per-app Wi-Fi and Mobile Data block rule immediately and restores full network access for all installed apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onClearAllRules()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("clear_all_rules_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reset All App Rules")
                                    }
                                }
                            }
                        }
        }
    }
}

private fun openRepository(context: Context) {
    openUrlInBrowser(context, "https://github.com/RanaGzzz/NetGate")
}

private fun openUrlInBrowser(context: Context, url: String) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(url)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun formatCheckedTime(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

private fun buildIdentityText(): String? {
    val sha = BuildConfig.GIT_COMMIT_SHA.trim()
    val timestamp = BuildConfig.BUILD_TIMESTAMP.trim()
    val parts = buildList {
        if (sha.isNotEmpty()) add("commit ${sha.take(7)}")
        if (timestamp.length >= 16) add("built ${timestamp.substring(0, 16).replace('T', ' ')} UTC")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" \u00b7 ")
}
