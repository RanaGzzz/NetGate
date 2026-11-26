package com.aistudio.netgate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BackgroundAnimationStyle
import com.aistudio.netgate.data.model.FirewallPreferencesState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundAnimationSheet(
    preferences: FirewallPreferencesState,
    onSelectAnimation: (BackgroundAnimationStyle) -> Unit,
    onToggleParallax: (Boolean) -> Unit,
    onToggleSeasonal: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var presetsExpanded by rememberSaveable { mutableStateOf(true) }
    var effectsExpanded by rememberSaveable { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                    .testTag("background_animation_sheet")
            ) {
                FrostedSheetDragHandle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Animation",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Live backdrop rendered behind every screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsStatusPill(
                        label = preferences.backgroundAnimation.title,
                        accent = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                SettingsSectionHeader(
                    title = "Animation Style",
                    icon = Icons.Rounded.Wallpaper
                )

                SettingsAccordionCard(
                    title = "Backdrop Presets",
                    summary = "Active: ${preferences.backgroundAnimation.title}",
                    icon = preferences.backgroundAnimation.icon(),
                    statusLabel = preferences.backgroundAnimation.title,
                    expanded = presetsExpanded,
                    onToggleExpand = { presetsExpanded = !presetsExpanded },
                    modifier = Modifier.testTag("background_presets_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BackgroundAnimationStyle.entries.chunked(3).forEach { rowStyles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowStyles.forEach { style ->
                                    BackgroundAnimationTile(
                                        style = style,
                                        selected = preferences.backgroundAnimation == style,
                                        onClick = { onSelectAnimation(style) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowStyles.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SettingsSectionHeader(
                    title = "Motion & Tint",
                    icon = Icons.Rounded.AutoAwesome
                )

                val activeEffects = listOf(
                    preferences.backgroundParallaxEnabled,
                    preferences.backgroundSeasonalEnabled
                ).count { it }
                SettingsAccordionCard(
                    title = "Motion & Seasonal Effects",
                    summary = "Parallax tilt & festive seasonal tint",
                    icon = Icons.Rounded.BlurOn,
                    statusLabel = "$activeEffects/2 On",
                    expanded = effectsExpanded,
                    onToggleExpand = { effectsExpanded = !effectsExpanded },
                    modifier = Modifier.testTag("background_effects_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CompactSwitchRow(
                            title = "Parallax Effect",
                            summary = "Smooth background shifting when tilting the device",
                            checked = preferences.backgroundParallaxEnabled,
                            icon = Icons.Rounded.BlurOn,
                            onCheckedChange = onToggleParallax,
                            testTag = "background_parallax_switch"
                        )
                        SettingsCardDivider()
                        CompactSwitchRow(
                            title = "Seasonal Themes",
                            summary = "Festive tint on the animation for the current season",
                            checked = preferences.backgroundSeasonalEnabled,
                            icon = Icons.Rounded.AutoAwesome,
                            onCheckedChange = onToggleSeasonal,
                            testTag = "background_seasonal_switch"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("background_animation_close")
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundAnimationTile(
    style: BackgroundAnimationStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    FrostedSurface(
        shape = RoundedCornerShape(16.dp),
        tintColor = if (selected) accent.copy(alpha = 0.18f) else null,
        borderAccent = if (selected) accent else null,
        modifier = modifier
            .heightIn(min = 84.dp)
            .clickable(onClick = onClick)
            .testTag("background_animation_${style.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SettingsIconBadge(
                icon = style.icon(),
                accent = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = style.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun BackgroundAnimationStyle.icon(): ImageVector = when (this) {
    BackgroundAnimationStyle.CIRCLES -> Icons.Rounded.Circle
    BackgroundAnimationStyle.RINGS -> Icons.Rounded.DonutLarge
    BackgroundAnimationStyle.MESH -> Icons.Rounded.BlurOn
    BackgroundAnimationStyle.SPACE -> Icons.Rounded.AutoAwesome
    BackgroundAnimationStyle.SHAPES -> Icons.Rounded.Category
    BackgroundAnimationStyle.SNOW -> Icons.Rounded.AcUnit
    BackgroundAnimationStyle.GRID -> Icons.Rounded.Apps
    BackgroundAnimationStyle.PARTICLES -> Icons.Rounded.Grain
    BackgroundAnimationStyle.LAVA_LAMP -> Icons.Rounded.WaterDrop
    BackgroundAnimationStyle.NONE -> Icons.Rounded.Block
    BackgroundAnimationStyle.RANDOM -> Icons.Rounded.Shuffle
}
