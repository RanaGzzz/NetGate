package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.NetGateThemeStyle
import com.example.data.model.ThemeMode
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LiquidGlassAqua
import com.example.ui.theme.LiquidGlassCyan
import com.example.ui.theme.LiquidGlassPrismViolet
import com.example.ui.theme.LocalBackdropLiquidState
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalThemeStyle
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallSettingsSheet(
    preferences: FirewallPreferencesState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeStyleChange: (NetGateThemeStyle) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit,
    onBlurStyleChange: (BlurEngineStyle) -> Unit,
    onToggleBlurAnimation: (Boolean) -> Unit,
    onReplayStartupAnimation: () -> Unit = {},
    onToggleShowSystemApps: (Boolean) -> Unit,
    onToggleShowOfflineApps: (Boolean) -> Unit,
    onToggleStatusBarSpeed: (Boolean) -> Unit,
    onToggleDeepMonitor: (Boolean) -> Unit,
    onClearAllRules: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        text = "Theme Mode (Day & Night)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("theme_style_${style.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = style.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1
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

            FilledTonalButton(
                onClick = onReplayStartupAnimation,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("replay_startup_animation_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preview Startup Animation")
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
