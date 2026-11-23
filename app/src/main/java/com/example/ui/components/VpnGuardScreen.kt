package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.ManagedApp
import com.example.data.model.VpnGateServer
import com.example.data.model.VpnGateSortMode
import com.example.data.model.VpnGuardConnectionState
import com.example.data.model.VpnGuardProfile
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.VpnGuardSubTab
import com.example.data.model.VpnGuardUiState
import com.example.data.model.formatCountdownTimer
import com.example.data.model.formatDataBytes
import com.example.data.model.formatSpeedBytesPerSec
import com.example.service.WireGuardConfigHelper
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WifiAccent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VpnGuardContent(
    state: VpnGuardUiState,
    allManagedApps: List<ManagedApp>,
    isLocalFirewallActive: Boolean,
    nowMillis: Long,
    loadIcon: suspend (String) -> ImageBitmap?,
    onSelectSubTab: (VpnGuardSubTab) -> Unit,
    onSelectProfile: (String) -> Unit,
    onImportWireGuardConfig: (String, String, VpnGuardRoutingMode, Set<String>) -> Unit,
    onGenerateWarpProfile: (String, VpnGuardRoutingMode, Set<String>, Boolean) -> Unit,
    onGenerateKeypairTemplate: (String) -> Unit,
    onUpdateProfileRouting: (String, VpnGuardRoutingMode, Set<String>) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onConnectProfile: (VpnGuardProfile) -> Unit,
    onDisconnect: (Boolean) -> Unit,
    onRefreshVpnGate: (Boolean) -> Unit,
    onVpnGateSearchChange: (String) -> Unit,
    onVpnGateSortChange: (VpnGateSortMode) -> Unit,
    onNotifyMessage: (String) -> Unit,
    onOpenVpnLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var pendingConnectProfile by remember { mutableStateOf<VpnGuardProfile?>(null) }
    var pendingWarpConnectAfterPermission by rememberSaveable { mutableStateOf(false) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    var showPasteDialog by remember { mutableStateOf(false) }
    var viewedConfigProfile by remember { mutableStateOf<VpnGuardProfile?>(null) }
    var appPickerProfile by remember { mutableStateOf<VpnGuardProfile?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConnectProfile?.let { profile ->
                onConnectProfile(profile)
                pendingConnectProfile = null
            }
            if (pendingWarpConnectAfterPermission) {
                pendingWarpConnectAfterPermission = false
                onGenerateWarpProfile("Cloudflare WARP", VpnGuardRoutingMode.WHOLE_SYSTEM, emptySet(), true)
            }
        } else {
            pendingConnectProfile = null
            pendingWarpConnectAfterPermission = false
            onNotifyMessage("Android VPN permission denied")
        }
    }

    val connectWithPermissionCheck: (VpnGuardProfile) -> Unit = { profile ->
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            onConnectProfile(profile)
        } else {
            pendingConnectProfile = profile
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val displayName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringAfterLast(':')
                    ?.ifBlank { "Imported WireGuard" }
                    ?: "Imported WireGuard"
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()
                if (content.isBlank()) {
                    onNotifyMessage("Selected config file is empty")
                } else {
                    onImportWireGuardConfig(content, displayName, VpnGuardRoutingMode.WHOLE_SYSTEM, emptySet())
                }
            }.onFailure { err ->
                onNotifyMessage(err.message ?: "Unable to read config file")
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val textToWrite = pendingExportContent
        pendingExportContent = null
        if (uri != null && !textToWrite.isNullOrBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(textToWrite)
                }
                onNotifyMessage("Saved config file to Downloads / Files")
            }.onFailure { err ->
                onNotifyMessage(err.message ?: "Unable to save config file")
            }
        }
    }

    val startExportConfig: (String, String) -> Unit = { fileName, content ->
        pendingExportContent = content
        exportFileLauncher.launch(fileName)
    }

    val runtime = state.runtimeState
    val isConnected = runtime.connectionState == VpnGuardConnectionState.CONNECTED
    val isConnecting = runtime.connectionState == VpnGuardConnectionState.CONNECTING
    val selectedProfile = state.profiles.firstOrNull { it.id == state.selectedProfileId }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomInset + 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
            .testTag("vpn_guard_screen")
    ) {
        item(key = "vpn_guard_hero") {
            VpnGuardHeroCard(
                state = state,
                selectedProfile = selectedProfile,
                isLocalFirewallActive = isLocalFirewallActive,
                nowMillis = nowMillis,
                onConnect = { profile -> connectWithPermissionCheck(profile) },
                onDisconnect = { restoreFw -> onDisconnect(restoreFw) },
                onQuickGenerateWarpAndConnect = {
                    val prep = VpnService.prepare(context)
                    if (prep == null) {
                        onGenerateWarpProfile("Cloudflare WARP", VpnGuardRoutingMode.WHOLE_SYSTEM, emptySet(), true)
                        } else {
                            pendingWarpConnectAfterPermission = true
                            vpnPermissionLauncher.launch(prep)
                        }
                    },
                    onOpenVpnLogs = onOpenVpnLogs
                )
        }

        item(key = "vpn_guard_subtabs") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VpnGuardSubTab.entries) { tab ->
                    val selected = state.selectedSubTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSubTab(tab) },
                        label = {
                            Text(
                                text = when (tab) {
                                    VpnGuardSubTab.PROFILES -> "${tab.label} (${state.profiles.size})"
                                    VpnGuardSubTab.VPNGATE -> if (state.vpnGateServers.isNotEmpty()) {
                                        "${tab.label} (${state.vpnGateServers.size})"
                                    } else {
                                        tab.label
                                    }
                                    VpnGuardSubTab.PROVIDERS -> tab.label
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (isDark) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            },
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("vpn_guard_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (state.selectedSubTab) {
            VpnGuardSubTab.PROFILES -> {
                item(key = "wg_quick_actions") {
                    FrostedSurface(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Load or Generate VPN Configs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Generate live Cloudflare WARP WireGuard .conf files, import .conf profiles from Proton/Windscribe/Mullvad, and apply per-app or system-wide split tunneling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        onGenerateWarpProfile(
                                            "Cloudflare WARP",
                                            VpnGuardRoutingMode.WHOLE_SYSTEM,
                                            emptySet(),
                                            false
                                        )
                                    },
                                    enabled = !state.isGeneratingWarp,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("vpn_guard_generate_warp_button")
                                ) {
                                    if (state.isGeneratingWarp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (state.isGeneratingWarp) "Registering WARP…" else "Generate WARP .conf")
                                }

                                OutlinedButton(
                                    onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("vpn_guard_import_conf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load .conf File")
                                }

                                OutlinedButton(
                                    onClick = { showPasteDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("vpn_guard_paste_conf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste Config")
                                }

                                OutlinedButton(
                                    onClick = { onGenerateKeypairTemplate("Custom WireGuard") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("vpn_guard_keypair_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EnhancedEncryption,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Keypair .conf")
                                }
                            }
                        }
                    }
                }

                if (state.profiles.isEmpty()) {
                    item(key = "empty_profiles") {
                        FrostedSurface(
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnLock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No WireGuard Profiles Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap \"Generate WARP .conf\" for an instant free Cloudflare WARP profile, or load a .conf file from Proton VPN or your server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.profiles, key = { it.id }) { profile ->
                        val isActiveProfile = runtime.activeProfileId == profile.id &&
                            (isConnected || isConnecting)
                        val isSelectedProfile = state.selectedProfileId == profile.id
                        VpnGuardProfileCard(
                            profile = profile,
                            isSelected = isSelectedProfile,
                            isActive = isActiveProfile,
                            isConnecting = isActiveProfile && isConnecting,
                            onSelect = { onSelectProfile(profile.id) },
                            onConnect = { connectWithPermissionCheck(profile) },
                            onDisconnect = { onDisconnect(false) },
                            onChangeRoutingMode = { newMode ->
                                val nextPackages = when (newMode) {
                                    VpnGuardRoutingMode.WHOLE_SYSTEM -> emptySet()
                                    VpnGuardRoutingMode.SELECTED_APPS ->
                                        profile.targetPackages.ifEmpty { WireGuardConfigHelper.WHATSAPP_PACKAGES }
                                    VpnGuardRoutingMode.EXCLUDE_APPS ->
                                        profile.targetPackages
                                }
                                if (newMode == VpnGuardRoutingMode.EXCLUDE_APPS && nextPackages.isEmpty()) {
                                    appPickerProfile = profile.copy(routingMode = newMode)
                                } else {
                                    onUpdateProfileRouting(profile.id, newMode, nextPackages)
                                }
                            },
                            onOpenAppPicker = { appPickerProfile = profile },
                            onQuickWhatsAppPreset = {
                                onUpdateProfileRouting(
                                    profile.id,
                                    VpnGuardRoutingMode.SELECTED_APPS,
                                    WireGuardConfigHelper.WHATSAPP_PACKAGES
                                )
                            },
                            onDownloadConf = {
                                val effective = WireGuardConfigHelper.buildEffectiveConfigText(
                                    profile.rawConfig,
                                    profile.routingMode,
                                    profile.targetPackages
                                )
                                val safeName = profile.name
                                    .lowercase()
                                    .replace(Regex("[^a-z0-9_-]+"), "_")
                                    .trim('_')
                                    .ifEmpty { "netgate_wg" }
                                startExportConfig("$safeName.conf", effective)
                            },
                            onViewConf = { viewedConfigProfile = profile },
                            onDelete = { onDeleteProfile(profile.id) }
                        )
                    }
                }
            }

            VpnGuardSubTab.VPNGATE -> {
                item(key = "vpngate_controls") {
                    FrostedSurface(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "VPNGate Live Relay Directory",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Live academic OpenVPN (.ovpn) relays worldwide • Download & open in OpenVPN for Android",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onRefreshVpnGate(true) },
                                    enabled = !state.isLoadingVpnGate,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("vpngate_refresh_button")
                                ) {
                                    if (state.isLoadingVpnGate) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = state.vpnGateSearchQuery,
                                onValueChange = onVpnGateSearchChange,
                                singleLine = true,
                                placeholder = { Text("Filter by country (Japan, Germany, US), IP, or operator…") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vpngate_search_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VpnGateSortMode.entries) { mode ->
                                    FilterChip(
                                        selected = state.vpnGateSortMode == mode,
                                        onClick = { onVpnGateSortChange(mode) },
                                        label = { Text(mode.label) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.isLoadingVpnGate && state.vpnGateServers.isEmpty()) {
                    item(key = "vpngate_loading") {
                        FrostedSurface(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Fetching live VPNGate server list via DoH-resilient transport…")
                            }
                        }
                    }
                } else if (state.vpnGateError != null && state.vpnGateServers.isEmpty()) {
                    item(key = "vpngate_error") {
                        FrostedSurface(
                            shape = RoundedCornerShape(20.dp),
                            borderAccent = BlockedRed,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Unable to reach VPNGate feed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BlockedRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.vpnGateError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(onClick = { onRefreshVpnGate(true) }) {
                                    Text("Retry Fetch")
                                }
                            }
                        }
                    }
                } else {
                    items(state.vpnGateServers.take(80), key = { "${it.hostName}_${it.ip}" }) { server ->
                        VpnGateServerCard(
                            server = server,
                            onDownloadOvpn = {
                                val fileName = "vpngate_${server.countryShort.lowercase()}_${server.ip.replace('.', '_')}.ovpn"
                                startExportConfig(fileName, server.ovpnConfig)
                            },
                            onOpenExternalOvpn = {
                                openOvpnInExternalClient(context, server, onNotifyMessage)
                            },
                            onCopyOvpn = {
                                copyToClipboard(context, "VPNGate .ovpn (${server.countryLong})", server.ovpnConfig)
                                onNotifyMessage("Copied ${server.countryLong} .ovpn config")
                            }
                        )
                    }
                }
            }

            VpnGuardSubTab.PROVIDERS -> {
                item(key = "open_source_providers") {
                    OpenSourceProvidersSection(
                        isGeneratingWarp = state.isGeneratingWarp,
                        onGenerateWarp = {
                            onGenerateWarpProfile(
                                "Cloudflare WARP",
                                VpnGuardRoutingMode.WHOLE_SYSTEM,
                                emptySet(),
                                false
                            )
                        },
                        onImportConf = { importFileLauncher.launch(arrayOf("*/*")) },
                        onPasteConf = { showPasteDialog = true },
                        onOpenVpnGate = { onSelectSubTab(VpnGuardSubTab.VPNGATE) },
                        onOpenUrl = { url -> openBrowserUrl(context, url, onNotifyMessage) }
                    )
                }
            }
        }
    }

    if (showPasteDialog) {
        PasteWireGuardConfigDialog(
            onDismiss = { showPasteDialog = false },
            onSave = { name, rawConf, routingMode, packages ->
                showPasteDialog = false
                onImportWireGuardConfig(rawConf, name, routingMode, packages)
            }
        )
    }

    viewedConfigProfile?.let { profile ->
        val effectiveText = WireGuardConfigHelper.buildEffectiveConfigText(
            profile.rawConfig,
            profile.routingMode,
            profile.targetPackages
        )
        AlertDialog(
            onDismissRequest = { viewedConfigProfile = null },
            title = { Text("${profile.name} (.conf)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = effectiveText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, profile.name, effectiveText)
                        onNotifyMessage("Copied WireGuard .conf to clipboard")
                        viewedConfigProfile = null
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewedConfigProfile = null }) {
                    Text("Close")
                }
            }
        )
    }

    appPickerProfile?.let { profile ->
        SplitTunnelAppPickerSheet(
            profile = profile,
            allApps = allManagedApps,
            loadIcon = loadIcon,
            onDismiss = { appPickerProfile = null },
            onSave = { mode, selectedSet ->
                appPickerProfile = null
                onUpdateProfileRouting(profile.id, mode, selectedSet)
            }
        )
    }
}

@Composable
private fun VpnGuardHeroCard(
    state: VpnGuardUiState,
    selectedProfile: VpnGuardProfile?,
    isLocalFirewallActive: Boolean,
    nowMillis: Long,
    onConnect: (VpnGuardProfile) -> Unit,
    onDisconnect: (Boolean) -> Unit,
    onQuickGenerateWarpAndConnect: () -> Unit,
    onOpenVpnLogs: (() -> Unit)? = null
) {
    val runtime = state.runtimeState
    val isConnected = runtime.connectionState == VpnGuardConnectionState.CONNECTED
    val isConnecting = runtime.connectionState == VpnGuardConnectionState.CONNECTING
    val isDark = LocalIsDarkTheme.current

    val accentColor by animateColorAsState(
        targetValue = when (runtime.connectionState) {
            VpnGuardConnectionState.CONNECTED -> EmeraldPrimary
            VpnGuardConnectionState.CONNECTING -> WifiAccent
            VpnGuardConnectionState.ERROR -> BlockedRed
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "vpnGuardAccent"
    )

    FrostedSurface(
        shape = RoundedCornerShape(26.dp),
        borderAccent = if (isConnected || isConnecting) accentColor else null,
        tintColor = if (isConnected) {
            EmeraldPrimary.copy(alpha = if (isDark) 0.16f else 0.10f)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_guard_hero_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isConnected) Icons.Rounded.Lock else Icons.Rounded.VpnLock,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (runtime.connectionState) {
                                VpnGuardConnectionState.CONNECTED -> "VPN Guard Connected"
                                VpnGuardConnectionState.CONNECTING -> "Establishing WireGuard…"
                                VpnGuardConnectionState.DISCONNECTING -> "Disconnecting…"
                                VpnGuardConnectionState.ERROR -> "Tunnel Error"
                                VpnGuardConnectionState.DISCONNECTED -> "VPN Guard Ready"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isConnected -> "${runtime.activeProfileName} • ${runtime.endpoint}"
                                isConnecting -> runtime.activeProfileName
                                runtime.errorMessage != null -> runtime.errorMessage
                                selectedProfile != null -> "Ready: ${selectedProfile.name} (${selectedProfile.routingMode.title})"
                                else -> "Import WireGuard .conf or generate WARP for per-app or system VPN"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (runtime.errorMessage != null && !isConnected) {
                                BlockedRed
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                val durationMs = (nowMillis - runtime.connectedSinceMs).coerceAtLeast(0L)
                val scopeBadge = when (runtime.routingMode) {
                    VpnGuardRoutingMode.WHOLE_SYSTEM -> "Whole System"
                    VpnGuardRoutingMode.SELECTED_APPS -> "${runtime.routedPackagesCount} Selected App(s)"
                    VpnGuardRoutingMode.EXCLUDE_APPS -> "All Except ${runtime.routedPackagesCount} App(s)"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        label = "Download",
                        value = "${formatDataBytes(runtime.rxBytes)} (${formatSpeedBytesPerSec(runtime.rxSpeedBytesPerSec)})",
                        accent = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Upload",
                        value = "${formatDataBytes(runtime.txBytes)} (${formatSpeedBytesPerSec(runtime.txSpeedBytesPerSec)})",
                        accent = DataAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scope: $scopeBadge • Tunnel IP: ${runtime.tunnelAddresses.ifBlank { "Assigned" }} • Uptime: ${formatCountdownTimer(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLocalFirewallActive && !isConnected && !isConnecting) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WifiAccent.copy(alpha = if (isDark) 0.16f else 0.10f),
                    border = BorderStroke(1.dp, WifiAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Android permits one active VPN service at a time — connecting VPN Guard will pause NetGate's local firewall TUN until you disconnect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected || isConnecting) {
                    Button(
                        onClick = { onDisconnect(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlockedRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vpn_guard_disconnect_button")
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect VPN")
                    }
                    if (runtime.pausedLocalFirewallForVpn) {
                        OutlinedButton(
                            onClick = { onDisconnect(true) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch to Firewall")
                        }
                    }
                } else if (selectedProfile != null) {
                    Button(
                        onClick = { onConnect(selectedProfile) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vpn_guard_connect_button")
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect ${selectedProfile.name}")
                    }
                } else {
                    Button(
                        onClick = onQuickGenerateWarpAndConnect,
                        enabled = !state.isGeneratingWarp,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vpn_guard_connect_button")
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Tap Connect Cloudflare WARP")
                    }
                }
            }

            if (onOpenVpnLogs != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenVpnLogs,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("vpn_guard_open_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View VPN & DPI Logs")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpnGuardProfileCard(
    profile: VpnGuardProfile,
    isSelected: Boolean,
    isActive: Boolean,
    isConnecting: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChangeRoutingMode: (VpnGuardRoutingMode) -> Unit,
    onOpenAppPicker: () -> Unit,
    onQuickWhatsAppPreset: () -> Unit,
    onDownloadConf: () -> Unit,
    onViewConf: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    FrostedSurface(
        shape = RoundedCornerShape(22.dp),
        borderAccent = when {
            isActive -> EmeraldPrimary
            isSelected -> MaterialTheme.colorScheme.primary
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("vpn_guard_profile_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = profile.providerType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${profile.endpointHost.ifBlank { "No endpoint" }} • DNS ${profile.dnsServers} • MTU ${profile.mtu}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete profile ${profile.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Routing Scope",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VpnGuardRoutingMode.entries) { mode ->
                    FilterChip(
                        selected = profile.routingMode == mode,
                        onClick = { onChangeRoutingMode(mode) },
                        label = {
                            val suffix = if (mode != VpnGuardRoutingMode.WHOLE_SYSTEM && profile.routingMode == mode) {
                                " (${profile.targetPackages.size})"
                            } else {
                                ""
                            }
                            Text(mode.title + suffix)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (profile.routingMode != VpnGuardRoutingMode.WHOLE_SYSTEM) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenAppPicker,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Apps (${profile.targetPackages.size})")
                    }
                    if (profile.routingMode == VpnGuardRoutingMode.SELECTED_APPS) {
                        OutlinedButton(
                            onClick = onQuickWhatsAppPreset,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("WhatsApp & Calls Preset")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { if (isActive) onDisconnect() else onConnect() },
                    enabled = !isConnecting,
                    colors = if (isActive) {
                        ButtonDefaults.buttonColors(containerColor = BlockedRed)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        when {
                            isConnecting -> "Connecting…"
                            isActive -> "Disconnect"
                            else -> "Connect"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onDownloadConf,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Download .conf")
                }

                OutlinedButton(
                    onClick = onViewConf,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("View")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpnGateServerCard(
    server: VpnGateServer,
    onDownloadOvpn: () -> Unit,
    onOpenExternalOvpn: () -> Unit,
    onCopyOvpn: () -> Unit
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = server.countryFlag,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = server.countryLong,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${server.ip} • ${server.hostName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldPrimary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (server.pingMs > 0) "${server.pingMs} ms" else "Ping N/A",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Speed: ${server.speedMbpsText} • Sessions: ${server.numVpnSessions} • Operator: ${server.operatorName.ifBlank { "Academic Volunteer" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onDownloadOvpn,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Download .ovpn")
                }

                OutlinedButton(
                    onClick = onOpenExternalOvpn,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Open in OpenVPN")
                }

                OutlinedButton(
                    onClick = onCopyOvpn,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Copy")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceProvidersSection(
    isGeneratingWarp: Boolean,
    onGenerateWarp: () -> Unit,
    onImportConf: () -> Unit,
    onPasteConf: () -> Unit,
    onOpenVpnGate: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProviderGuideCard(
            title = "Cloudflare WARP (WireGuard Engine)",
            badge = "Built-In • Free",
            description = "Registers a live Curve25519 key directly with Cloudflare WARP, generates a genuine WireGuard .conf file you can download or connect inside NetGate with WhatsApp-only or Whole-System split tunneling.",
            primaryLabel = if (isGeneratingWarp) "Registering WARP…" else "Generate WARP .conf",
            onPrimaryClick = onGenerateWarp
        )

        ProviderGuideCard(
            title = "Proton VPN (Open-Source • Free Tier WireGuard)",
            badge = "WireGuard .conf",
            description = "Proton VPN's Android & WireGuard configs are open-source. Download a free WireGuard .conf from account.protonvpn.com, then load it into NetGate to route WhatsApp or your entire phone.",
            primaryLabel = "Get Proton .conf",
            secondaryLabel = "Load .conf in NetGate",
            onPrimaryClick = { onOpenUrl("https://account.protonvpn.com/downloads") },
            onSecondaryClick = onImportConf
        )

        ProviderGuideCard(
            title = "VPNGate Academic Relays (University of Tsukuba)",
            badge = "OpenVPN .ovpn",
            description = "Open-source academic volunteer VPN network with live ping, speed, and Base64 .ovpn configs updated continuously.",
            primaryLabel = "Browse Live VPNGate",
            secondaryLabel = "OpenVPN for Android",
            onPrimaryClick = onOpenVpnGate,
            onSecondaryClick = { onOpenUrl("https://github.com/schwabe/ics-openvpn") }
        )

        ProviderGuideCard(
            title = "RiseupVPN / Windscribe / Self-Hosted wg-easy",
            badge = "Open-Source",
            description = "Any standard WireGuard .conf from Riseup/LEAP, Mullvad, IVPN, Windscribe, or your own VPS (wg-easy / Algo / Amnezia) loads directly into NetGate VPN Guard.",
            primaryLabel = "Paste .conf Text",
            secondaryLabel = "Browse RiseupVPN",
            onPrimaryClick = onPasteConf,
            onSecondaryClick = { onOpenUrl("https://riseup.net/en/vpn") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderGuideCard(
    title: String,
    badge: String,
    description: String,
    primaryLabel: String,
    secondaryLabel: String? = null,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null
) {
    FrostedSurface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(onClick = onPrimaryClick, shape = RoundedCornerShape(12.dp)) {
                    Text(primaryLabel)
                }
                if (secondaryLabel != null && onSecondaryClick != null) {
                    OutlinedButton(onClick = onSecondaryClick, shape = RoundedCornerShape(12.dp)) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PasteWireGuardConfigDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, VpnGuardRoutingMode, Set<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var routingMode by remember { mutableStateOf(VpnGuardRoutingMode.WHOLE_SYSTEM) }
    var whatsappOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste WireGuard .conf") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("[Interface] ... [Peer] ...") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            whatsappOnly = !whatsappOnly
                            routingMode = if (whatsappOnly) {
                                VpnGuardRoutingMode.SELECTED_APPS
                            } else {
                                VpnGuardRoutingMode.WHOLE_SYSTEM
                            }
                        }
                ) {
                    Checkbox(
                        checked = whatsappOnly,
                        onCheckedChange = { checked ->
                            whatsappOnly = checked
                            routingMode = if (checked) {
                                VpnGuardRoutingMode.SELECTED_APPS
                            } else {
                                VpnGuardRoutingMode.WHOLE_SYSTEM
                            }
                        }
                    )
                    Text(
                        text = "Route WhatsApp & Business Calls Only",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pkgs = if (whatsappOnly) WireGuardConfigHelper.WHATSAPP_PACKAGES else emptySet()
                    onSave(name, rawText, routingMode, pkgs)
                },
                enabled = rawText.isNotBlank()
            ) {
                Text("Import Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SplitTunnelAppPickerSheet(
    profile: VpnGuardProfile,
    allApps: List<ManagedApp>,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onSave: (VpnGuardRoutingMode, Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPackages by remember(profile.id) { mutableStateOf(profile.targetPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMode by remember(profile.id) {
        mutableStateOf(
            if (profile.routingMode == VpnGuardRoutingMode.WHOLE_SYSTEM) {
                VpnGuardRoutingMode.SELECTED_APPS
            } else {
                profile.routingMode
            }
        )
    }

    val filteredApps = remember(allApps, searchQuery, selectedPackages) {
        val q = searchQuery.trim().lowercase()
        allApps
            .filter {
                q.isEmpty() ||
                    it.appName.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }
            .sortedWith(
                compareByDescending<ManagedApp> { it.packageName in selectedPackages }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Split-Tunnel Apps (${profile.name})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = currentMode == VpnGuardRoutingMode.SELECTED_APPS,
                    onClick = { currentMode = VpnGuardRoutingMode.SELECTED_APPS },
                    label = { Text("Only Selected Apps Use VPN") }
                )
                FilterChip(
                    selected = currentMode == VpnGuardRoutingMode.EXCLUDE_APPS,
                    onClick = { currentMode = VpnGuardRoutingMode.EXCLUDE_APPS },
                    label = { Text("Bypass Selected Apps") }
                )
                OutlinedButton(
                    onClick = {
                        currentMode = VpnGuardRoutingMode.SELECTED_APPS
                        selectedPackages = selectedPackages + WireGuardConfigHelper.WHATSAPP_PACKAGES
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("+ WhatsApp")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(app.packageName) {
                        iconBitmap = loadIcon(app.packageName)
                    }
                    val checked = app.packageName in selectedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPackages = if (checked) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap!!,
                                contentDescription = app.appName,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = app.appName,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isNowChecked ->
                                selectedPackages = if (isNowChecked) {
                                    selectedPackages + app.packageName
                                } else {
                                    selectedPackages - app.packageName
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(currentMode, selectedPackages) },
                    enabled = selectedPackages.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply (${selectedPackages.size})")
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

private fun openOvpnInExternalClient(
    context: Context,
    server: VpnGateServer,
    onMessage: (String) -> Unit
) {
    runCatching {
        val dir = File(context.cacheDir, "vpn_configs").apply { mkdirs() }
        val file = File(dir, "vpngate_${server.countryShort.lowercase()}_${server.ip.replace('.', '_')}.ovpn")
        file.writeText(server.ovpnConfig)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-openvpn-profile")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open .ovpn with OpenVPN client"))
    }.onFailure {
        onMessage("Install OpenVPN for Android (open-source) or use Download .ovpn")
    }
}

private fun openBrowserUrl(
    context: Context,
    url: String,
    onMessage: (String) -> Unit
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        onMessage("Unable to open $url")
    }
}
