package com.example.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BatchAction
import com.example.ui.theme.LocalBackdropLiquidState
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalOverlayLiquidState

@Composable
fun BatchSelectionBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onApplyBatchAction: (BatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val dockTint = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    }

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("batch_selection_bar"),
        shape = RoundedCornerShape(26.dp),
        tintColor = dockTint,
        borderAccent = MaterialTheme.colorScheme.primary,
        elevation = 12.dp,
        liquidStateOverride = LocalOverlayLiquidState.current ?: LocalBackdropLiquidState.current
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExitSelection,
                        modifier = Modifier.testTag("batch_exit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit batch selection"
                        )
                    }
                    Text(
                        text = "$selectedCount app${if (selectedCount == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = if (allVisibleSelected) onClearSelection else onSelectAll,
                    modifier = Modifier.testTag("batch_select_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (allVisibleSelected) "Clear" else "Select All")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatchActionButton(
                    label = "Block Wi-Fi",
                    icon = Icons.Rounded.WifiOff,
                    enabled = selectedCount > 0,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { onApplyBatchAction(BatchAction.BLOCK_WIFI) },
                    testTag = "batch_block_wifi_button",
                    modifier = Modifier.weight(1f)
                )
                BatchActionButton(
                    label = "Block Data",
                    icon = Icons.Rounded.SignalCellularOff,
                    enabled = selectedCount > 0,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { onApplyBatchAction(BatchAction.BLOCK_DATA) },
                    testTag = "batch_block_data_button",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatchActionButton(
                    label = "Block Both",
                    icon = Icons.Rounded.Block,
                    enabled = selectedCount > 0,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = { onApplyBatchAction(BatchAction.BLOCK_BOTH) },
                    testTag = "batch_block_both_button",
                    modifier = Modifier.weight(1f)
                )
                BatchActionButton(
                    label = "Allow All",
                    icon = Icons.Rounded.CheckCircle,
                    enabled = selectedCount > 0,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onApplyBatchAction(BatchAction.ALLOW_ALL) },
                    testTag = "batch_allow_all_button",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BatchActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
