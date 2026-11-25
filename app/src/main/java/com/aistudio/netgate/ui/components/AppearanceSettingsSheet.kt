package com.aistudio.netgate.ui.components

import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.AppIconStyle
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.data.model.ThemeMode

@Composable
fun AppearanceSettingsContent(
    preferences: FirewallPreferencesState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeStyleChange: (NetGateThemeStyle) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onBlurStyleChange: (BlurEngineStyle) -> Unit,
    onToggleBlurAnimation: (Boolean) -> Unit,
    onOpenBackgroundAnimation: () -> Unit,
    onAppIconChange: (AppIconStyle) -> Unit,
    onBack: () -> Unit
) {
    var themeModeExpanded by rememberSaveable { mutableStateOf(false) }
    var themeStyleExpanded by rememberSaveable { mutableStateOf(false) }
    var blurSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var backgroundAnimExpanded by rememberSaveable { mutableStateOf(false) }
    var appIconExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("appearance_settings_view")
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
                        .testTag("appearance_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Settings"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Theme mode, glass engine, motion & launcher icon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            SettingsStatusPill(
                label = preferences.themeStyle.badgeLabel,
                accent = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 1: Theme & Style
        SettingsSectionHeader(
            title = "Theme & Colors",
            icon = Icons.Rounded.Palette
        )

        val themeModeIcon = when (preferences.themeMode) {
            ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
            ThemeMode.LIGHT -> Icons.Rounded.LightMode
            ThemeMode.DARK -> Icons.Rounded.DarkMode
        }
        val themeModeSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Mode: ${preferences.themeMode.label} • Dynamic ${if (preferences.useDynamicColor) "On" else "Off"}"
        } else {
            "System default, Light or Dark appearance"
        }

        SettingsAccordionCard(
            title = "Theme Mode & Colors",
            summary = themeModeSummary,
            icon = themeModeIcon,
            statusLabel = preferences.themeMode.label,
            expanded = themeModeExpanded,
            onToggleExpand = { themeModeExpanded = !themeModeExpanded },
            modifier = Modifier.testTag("theme_mode_settings_card"),
            headerModifier = Modifier.testTag("theme_mode_accordion_toggle")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
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
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsCardDivider()
                    CompactSwitchRow(
                        title = "Material You Dynamic Color",
                        summary = "Match your device wallpaper palette",
                        checked = preferences.useDynamicColor,
                        icon = Icons.Rounded.Palette,
                        onCheckedChange = onToggleDynamicColor,
                        testTag = "settings_material_you_switch"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Theme Style & Motion (Accordion + 2x2 compact grid)
        SettingsAccordionCard(
            title = "Theme Style & Motion",
            summary = preferences.themeStyle.subtitle,
            icon = Icons.Rounded.Layers,
            statusLabel = preferences.themeStyle.badgeLabel,
            statusModifier = Modifier.testTag("theme_style_preview_pill"),
            expanded = themeStyleExpanded,
            onToggleExpand = { themeStyleExpanded = !themeStyleExpanded },
            modifier = Modifier.testTag("theme_style_settings_card"),
            headerModifier = Modifier.testTag("theme_style_accordion_toggle")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NetGateThemeStyle.entries.chunked(2).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStyles.forEach { style ->
                            val selected = preferences.themeStyle == style
                            val buttonShape = RoundedCornerShape(
                                when (style) {
                                    NetGateThemeStyle.LIQUID_GLASS -> 14.dp
                                    NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 16.dp
                                    else -> 12.dp
                                }
                            )
                            FilledTonalButton(
                                onClick = { onThemeStyleChange(style) },
                                shape = buttonShape,
                                border = if (selected) {
                                    BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                                    },
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("theme_style_${style.name.lowercase()}")
                            ) {
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Glass, Motion & Launcher Icon
        SettingsSectionHeader(
            title = "Glass, Motion & Icon",
            icon = Icons.Rounded.BlurOn
        )

        // Blur Engine Effect + Ambient Motion
        SettingsAccordionCard(
            title = "Blur Engine Effect",
            summary = preferences.blurEngineStyle.subtitle,
            icon = Icons.Rounded.BlurOn,
            statusLabel = preferences.blurEngineStyle.title,
            statusModifier = Modifier.testTag("blur_engine_preview_pill"),
            expanded = blurSectionExpanded,
            onToggleExpand = { blurSectionExpanded = !blurSectionExpanded },
            modifier = Modifier.testTag("blur_engine_settings_card"),
            headerModifier = Modifier.testTag("blur_engine_accordion_toggle")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                    border = if (selected) {
                                        BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                                        },
                                        contentColor = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("blur_style_${style.name.lowercase()}")
                                ) {
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsCardDivider()

                CompactSwitchRow(
                    title = "Ambient Motion & Depth Blur",
                    summary = "Spring backdrop drift & modal blur depth",
                    checked = preferences.isBlurAnimationEnabled,
                    icon = Icons.Rounded.AutoAwesome,
                    onCheckedChange = onToggleBlurAnimation,
                    testTag = "settings_blur_animation_switch"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Background Animation Accordion Card
        SettingsAccordionCard(
            title = "Background Animation",
            summary = "Live backdrop rendered behind every screen",
            icon = Icons.Rounded.Wallpaper,
            statusLabel = preferences.backgroundAnimation.title,
            expanded = backgroundAnimExpanded,
            onToggleExpand = { backgroundAnimExpanded = !backgroundAnimExpanded },
            modifier = Modifier.testTag("settings_background_animation_card"),
            headerModifier = Modifier.testTag("settings_background_animation_header")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Active preset: ${preferences.backgroundAnimation.title}. Preview and switch live shaders behind all screens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onOpenBackgroundAnimation,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_open_background_animation")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose Background Animation",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App Icon Accordion Card
        SettingsAccordionCard(
            title = "App Icon",
            summary = "Adaptive launcher icon style",
            icon = Icons.Rounded.Shield,
            statusLabel = preferences.appIconStyle.title,
            expanded = appIconExpanded,
            onToggleExpand = { appIconExpanded = !appIconExpanded },
            modifier = Modifier.testTag("settings_app_icon_card"),
            headerModifier = Modifier.testTag("settings_app_icon_header")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIconStyle.entries.forEach { style ->
                    val selected = preferences.appIconStyle == style
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iconPreviewColor(style).copy(alpha = if (selected) 0.24f else 0.10f),
                        border = BorderStroke(
                            if (selected) 1.75.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clickable { onAppIconChange(style) }
                            .testTag("app_icon_${style.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppIconTilePreview(style = style)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = style.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
    ) {
        when (style) {
            AppIconStyle.EMERALD -> Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = style.title,
                tint = Color(0xFFD9E533),
                modifier = Modifier.size(21.dp)
            )
            AppIconStyle.NEON -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_gate_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
                    .padding(1.dp)
            )
            AppIconStyle.VIOLET -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_tunnel_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
                    .padding(1.dp)
            )
            AppIconStyle.AMBER -> Image(
                painter = painterResource(id = R.drawable.ic_launcher_bastion_img),
                contentDescription = style.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
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
