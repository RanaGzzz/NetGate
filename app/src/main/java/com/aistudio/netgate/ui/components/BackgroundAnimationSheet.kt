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
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BackgroundAnimationStyle
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.ui.theme.LocalThemeStyle

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
    val themeStyle = LocalThemeStyle.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("background_animation_sheet")
        ) {
            Text(
                text = "Background animation",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The animation that plays behind the app's screens",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            BackgroundToggleRow(
                title = "Parallax effect",
                subtitle = "Smooth background shifting when tilting the device",
                icon = Icons.Rounded.BlurOn,
                checked = preferences.backgroundParallaxEnabled,
                onCheckedChange = onToggleParallax,
                testTag = "background_parallax_switch"
            )
            Spacer(modifier = Modifier.height(10.dp))
            BackgroundToggleRow(
                title = "Seasonal themes",
                subtitle = "Festive tint on the animation for the current season",
                icon = Icons.Rounded.AutoAwesome,
                checked = preferences.backgroundSeasonalEnabled,
                onCheckedChange = onToggleSeasonal,
                testTag = "background_seasonal_switch"
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("background_animation_close")
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (themeStyle == com.aistudio.netgate.data.model.NetGateThemeStyle.LIQUID_GLASS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
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
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .heightIn(min = 88.dp)
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
            Icon(
                imageVector = style.icon(),
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = style.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BackgroundToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            1.dp,
            if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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
