package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.service.BackgroundPowerHelper
import com.example.ui.theme.WarningAmber
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AppIconStyle
import com.example.data.model.AppUpdateStatus
import com.example.data.model.AppUpdateUiState
import com.example.data.model.BackgroundAnimationStyle
import com.example.data.model.BlurEngineStyle
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.NetGateThemeStyle
import com.example.data.model.ThemeMode
import com.example.data.model.formatDataBytes
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LiquidGlassAqua
import com.example.ui.theme.LiquidGlassCyan
import com.example.ui.theme.LiquidGlassPrismViolet
import com.example.ui.theme.LocalBackdropLiquidState
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalThemeStyle
import com.example.ui.theme.WifiAccent
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
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
    onToggleBlurAnimation: (Boolean) -> Unit,
    onOpenBackgroundAnimation: () -> Unit = {},
    onAppIconChange: (AppIconStyle) -> Unit = {},
    onReplayStartupAnimation: () -> Unit = {},
    onToggleShowSystemApps: (Boolean) -> Unit,
    onToggleShowOfflineApps: (Boolean) -> Unit,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit,
    onToggleExcludeFromRecents: (Boolean) -> Unit = {},
    appUpdateState: AppUpdateUiState = AppUpdateUiState(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE
    ),
    vpnDpiLogsCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenVpnDpiLogs: () -> Unit = {},
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
    val themeStyle = LocalThemeStyle.current
    val isDark = LocalIsDarkTheme.current
    val sheetLiquidState = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) {
        rememberLiquidState()
    } else {
        null
    }
    val sheetSurfaceColor = MaterialTheme.colorScheme.surface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurfaceColor.copy(alpha = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 0.96f else 0.94f)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (sheetLiquidState != null) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .liquefiable(sheetLiquidState)
                ) {
                    drawRect(color = sheetSurfaceColor)
                    val blobAlpha = if (isDark) 0.24f else 0.22f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(LiquidGlassCyan.copy(alpha = blobAlpha), Color.Transparent),
                            center = Offset(size.width * 0.16f, size.height * 0.18f),
                            radius = size.maxDimension * 0.55f
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.18f),
                        radius = size.maxDimension * 0.55f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(LiquidGlassAqua.copy(alpha = blobAlpha * 0.9f), Color.Transparent),
                            center = Offset(size.width * 0.88f, size.height * 0.45f),
                            radius = size.maxDimension * 0.48f
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.45f),
                        radius = size.maxDimension * 0.48f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(LiquidGlassPrismViolet.copy(alpha = blobAlpha * 0.85f), Color.Transparent),
                            center = Offset(size.width * 0.30f, size.height * 0.82f),
                            radius = size.maxDimension * 0.52f
                        ),
                        center = Offset(size.width * 0.30f, size.height * 0.82f),
                        radius = size.maxDimension * 0.52f
                    )
                }
            }

            CompositionLocalProvider(LocalBackdropLiquidState provides sheetLiquidState) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .testTag("firewall_settings_sheet")
                ) {
            Text(
                text = "Appearance & Firewall",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Theme Mode: Auto / Day / Night
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Follow system, or force Day or Night appearance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = preferences.themeMode == mode
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                ThemeMode.DARK -> Icons.Rounded.DarkMode
                            }
                            FilledTonalButton(
                                onClick = { onThemeModeChange(mode) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                    },
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Theme Style & Motion Presets (Liquid Glass, Material 3 Expressive, Material You Lite, Cyber Slate)
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_style_settings_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Theme Style & Motion",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FrostedSurface(
                            shape = RoundedCornerShape(50),
                            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            borderAccent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("theme_style_preview_pill")
                        ) {
                            Text(
                                text = preferences.themeStyle.badgeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = preferences.themeStyle.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetGateThemeStyle.entries.chunked(2).forEach { rowStyles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowStyles.forEach { style ->
                                    val selected = preferences.themeStyle == style
                                    FilledTonalButton(
                                        onClick = { onThemeStyleChange(style) },
                                        shape = RoundedCornerShape(
                                            when (style) {
                                                NetGateThemeStyle.LIQUID_GLASS -> 16.dp
                                                NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 18.dp
                                                else -> 12.dp
                                            }
                                        ),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                            },
                                            contentColor = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("theme_style_${style.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = style.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSwitchRow(
                    title = "Material You Dynamic Color",
                    subtitle = "Match your device wallpaper palette",
                    checked = preferences.useDynamicColor,
                    icon = Icons.Rounded.Palette,
                    onCheckedChange = onToggleDynamicColor,
                    testTag = "settings_material_you_switch"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Blur Engine Effect Presets
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blur_engine_settings_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BlurOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Blur Engine Effect",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FrostedSurface(
                            shape = RoundedCornerShape(50),
                            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            borderAccent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("blur_engine_preview_pill")
                        ) {
                            Text(
                                text = preferences.blurEngineStyle.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = preferences.blurEngineStyle.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlurEngineStyle.entries.chunked(2).forEach { rowStyles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowStyles.forEach { style ->
                                    val selected = preferences.blurEngineStyle == style
                                    FilledTonalButton(
                                        onClick = { onBlurStyleChange(style) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                            },
                                            contentColor = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("blur_style_${style.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = style.title,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Ambient Motion & Depth Blur",
                subtitle = "Spring-animated backdrop drift & modal blur depth",
                checked = preferences.isBlurAnimationEnabled,
                icon = Icons.Rounded.AutoAwesome,
                onCheckedChange = onToggleBlurAnimation,
                testTag = "settings_blur_animation_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_background_animation_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background animation",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${preferences.backgroundAnimation.title} · animation behind every screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onOpenBackgroundAnimation,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("settings_open_background_animation")
                    ) {
                        Text(
                            text = "Choose animation",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_app_icon_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "App Icon",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adaptive launcher icon style",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIconStyle.entries.forEach { style ->
                            val selected = preferences.appIconStyle == style
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = iconPreviewColor(style).copy(alpha = if (selected) 0.24f else 0.12f),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAppIconChange(style) }
                                    .testTag("app_icon_${style.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AppIconTilePreview(style = style)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Show System Apps",
                subtitle = "Include pre-installed Android system apps",
                checked = preferences.showSystemApps,
                onCheckedChange = onToggleShowSystemApps,
                testTag = "settings_show_system_apps_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Show Offline Apps",
                subtitle = "Include apps without INTERNET permission",
                checked = preferences.showOfflineApps,
                onCheckedChange = onToggleShowOfflineApps,
                testTag = "settings_show_offline_apps_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Status Bar Speed Icon",
                subtitle = "Pin live upload/download speed & daily usage in status bar",
                checked = preferences.isStatusBarSpeedEnabled,
                icon = Icons.Rounded.NotificationsActive,
                onCheckedChange = onToggleStatusBarSpeed,
                testTag = "settings_status_bar_speed_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Deep Site & Region Monitor",
                subtitle = "Log contacted domains, GeoIP countries, Rx/Tx bytes & threat score in Room",
                checked = preferences.isDeepMonitorEnabled,
                icon = Icons.Rounded.Public,
                onCheckedChange = onToggleDeepMonitor,
                testTag = "settings_deep_monitor_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                title = "Exclude from Recents",
                subtitle = "Hide NetGate card from Android's Recent Apps switcher while running",
                checked = preferences.excludeFromRecents,
                icon = Icons.Rounded.VisibilityOff,
                onCheckedChange = onToggleExcludeFromRecents,
                testTag = "settings_exclude_from_recents_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            val batteryAccent = if (isIgnoringBatteryOptimizations) EmeraldPrimary else WarningAmber
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                borderAccent = batteryAccent.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_battery_saver_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
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
                                imageVector = if (isIgnoringBatteryOptimizations) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.BatteryChargingFull
                                },
                                contentDescription = null,
                                tint = batteryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Background & Battery Saver",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) {
                                        "Unrestricted — Firewall, Speed Icon & Hotspot stay alive in background"
                                    } else {
                                        "Allow NetGate in background so Battery Saver cannot pause speed icon or VPN"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = batteryAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, batteryAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("settings_battery_status_badge")
                        ) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Allowed" else "Restricted",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = batteryAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            BackgroundPowerHelper.requestIgnoreBatteryOptimizations(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                tintColor = EmeraldPrimary.copy(alpha = 0.12f),
                borderAccent = EmeraldPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("banking_safe_badge")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Banking-Safe: Zero Accessibility Services",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "NetGate never requests Accessibility permissions, screen overlays, or root access, so your banking and payment apps remain unaffected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Uses Android's built-in local VPN interface. Only blocked apps are routed into the local sinkhole on the active network (Wi-Fi or Mobile Data), keeping allowed apps at full native speed. Note: Android allows one active VPN at a time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Diagnostics & Live VPN / DPI Bypass Logs
            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_diagnostics_logs_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "VPN & DPI Bypass Logs",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Inspect VPN Guard transitions, handshake events & DPI bypass telemetry",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenVpnDpiLogs()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
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
                                "Open VPN & DPI Logs ($vpnDpiLogsCount)"
                            } else {
                                "Open VPN & DPI Logs"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val updateAccent = when (appUpdateState.status) {
                AppUpdateStatus.UPDATE_AVAILABLE -> EmeraldPrimary
                AppUpdateStatus.UP_TO_DATE -> WifiAccent
                AppUpdateStatus.ERROR -> BlockedRed
                else -> MaterialTheme.colorScheme.primary
            }

            FrostedSurface(
                shape = RoundedCornerShape(16.dp),
                borderAccent = if (appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE) {
                    EmeraldPrimary.copy(alpha = 0.6f)
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_about_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "About & Updates",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("settings_app_version")
                            )
                            buildIdentityText()?.let { identity ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = identity,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("settings_build_identity")
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = updateAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, updateAccent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .testTag("settings_update_status_badge")
                        ) {
                            Text(
                                text = when (appUpdateState.status) {
                                    AppUpdateStatus.IDLE -> "v${BuildConfig.VERSION_NAME}"
                                    AppUpdateStatus.CHECKING -> "Checking…"
                                    AppUpdateStatus.UP_TO_DATE -> "Up to date"
                                    AppUpdateStatus.UPDATE_AVAILABLE -> "Update ${appUpdateState.latestRelease?.tagName.orEmpty()}"
                                    AppUpdateStatus.ERROR -> "Check failed"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = updateAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    appUpdateState.latestRelease?.let { release ->
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = BlockedRed,
                            modifier = Modifier.testTag("settings_update_error_text")
                        )
                    }

                    if (appUpdateState.lastCheckedAtMs > 0L) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last checked ${formatCheckedTime(appUpdateState.lastCheckedAtMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onCheckForUpdates,
                            enabled = appUpdateState.status != AppUpdateStatus.CHECKING,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appUpdateState.status == AppUpdateStatus.CHECKING) {
                                    "Checking…"
                                } else {
                                    "Check Updates"
                                },
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
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_open_changelog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Changelog",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    appUpdateState.latestRelease?.let { release ->
                        if (appUpdateState.status == AppUpdateStatus.UPDATE_AVAILABLE) {
                            Spacer(modifier = Modifier.height(8.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))
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
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub Repository",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "github.com/RanaGzzz/NetGate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    onClearAllRules()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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

@Composable
private fun AppIconTilePreview(style: AppIconStyle) {
    val bgColor = when (style) {
        AppIconStyle.EMERALD -> Color(0xFF0D1225)
        AppIconStyle.NEON -> Color(0xFF061626)
        AppIconStyle.VIOLET -> Color(0xFF0E142C)
        AppIconStyle.AMBER -> Color(0xFF0D181A)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bgColor)
    ) {
        when (style) {
            AppIconStyle.EMERALD -> Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = style.title,
                tint = Color(0xFFD9E533),
                modifier = Modifier.size(22.dp)
            )
            AppIconStyle.NEON -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_gate_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .padding(1.dp)
            )
            AppIconStyle.VIOLET -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_tunnel_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .padding(1.dp)
            )
            AppIconStyle.AMBER -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_bastion_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .padding(1.dp)
            )
        }
    }
}

private fun iconPreviewColor(style: AppIconStyle): Color = when (style) {
    AppIconStyle.EMERALD -> Color(0xFFD9E533)
    AppIconStyle.NEON -> Color(0xFF22D3EE)
    AppIconStyle.VIOLET -> Color(0xFF60A5FA)
    AppIconStyle.AMBER -> Color(0xFF34D399)
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

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

/**
 * Short build identity for the About card: the commit the APK was built from and
 * when it was built, or null when neither is known (for example a build from a
 * source archive without git metadata).
 */
private fun buildIdentityText(): String? {
    val sha = BuildConfig.GIT_COMMIT_SHA.trim()
    val timestamp = BuildConfig.BUILD_TIMESTAMP.trim()
    val parts = buildList {
        if (sha.isNotEmpty()) add("commit ${sha.take(7)}")
        if (timestamp.length >= 16) add("built ${timestamp.substring(0, 16).replace('T', ' ')} UTC")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" \u00b7 ")
}
