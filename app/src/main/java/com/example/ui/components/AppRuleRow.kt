package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveNetworkType
import com.example.data.model.ManagedApp
import com.example.data.model.formatCountdownTimer
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WifiAccent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRuleRow(
    app: ManagedApp,
    activeNetworkType: ActiveNetworkType,
    isFirewallActive: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggleWifi: () -> Unit,
    onToggleData: () -> Unit,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app.packageName)
    }

    val isBlockedRightNow = isFirewallActive && app.isBlockedOn(activeNetworkType)
    val activeTimer = app.activeTimer
    val borderAccent = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isBlockedRightNow -> BlockedRed
        activeTimer != null -> WarningAmber
        else -> null
    }
    val glassTint by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isDark -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rowGlassTint"
    )

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .testTag("app_row_${app.packageName}"),
        shape = RoundedCornerShape(20.dp),
        tintColor = glassTint,
        borderAccent = borderAccent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = "${app.appName} icon",
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = "${app.appName} icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                        ) {
                            Text(
                                text = "SYS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val statusText = when {
                    app.isFullyBlocked -> "Wi-Fi & Data blocked"
                    app.blockWifi -> "Wi-Fi blocked • Data allowed"
                    app.blockMobileData -> "Wi-Fi allowed • Data blocked"
                    else -> "Full network access"
                }
                val statusColor = when {
                    isBlockedRightNow -> MaterialTheme.colorScheme.error
                    app.isAnyBlocked -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (activeTimer != null) {
                    val remaining = activeTimer.remainingMillis(nowMillis)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarningAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("app_timer_badge_${app.packageName}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${formatCountdownTimer(remaining)} → ${activeTimer.targetLabel()} Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onRowClick() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("app_select_checkbox_${app.packageName}")
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NetworkRuleToggleButton(
                        isBlocked = app.blockWifi,
                        allowedIcon = Icons.Rounded.Wifi,
                        blockedIcon = Icons.Rounded.WifiOff,
                        allowedAccent = WifiAccent,
                        label = "Wi-Fi",
                        contentDescription = if (app.blockWifi) {
                            "Unblock Wi-Fi for ${app.appName}"
                        } else {
                            "Block Wi-Fi for ${app.appName}"
                        },
                        onClick = onToggleWifi,
                        testTag = "wifi_toggle_${app.packageName}"
                    )
                    NetworkRuleToggleButton(
                        isBlocked = app.blockMobileData,
                        allowedIcon = Icons.Rounded.SignalCellularAlt,
                        blockedIcon = Icons.Rounded.SignalCellularOff,
                        allowedAccent = DataAccent,
                        label = "Data",
                        contentDescription = if (app.blockMobileData) {
                            "Unblock Mobile Data for ${app.appName}"
                        } else {
                            "Block Mobile Data for ${app.appName}"
                        },
                        onClick = onToggleData,
                        testTag = "data_toggle_${app.packageName}"
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRuleToggleButton(
    isBlocked: Boolean,
    allowedIcon: ImageVector,
    blockedIcon: ImageVector,
    allowedAccent: Color,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val containerColor by animateColorAsState(
        targetValue = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.80f)
        } else {
            allowedAccent.copy(alpha = 0.16f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isBlocked) {
            MaterialTheme.colorScheme.error
        } else {
            allowedAccent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleFg"
    )
    val borderColor = if (isBlocked) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.50f)
    } else {
        allowedAccent.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isBlocked) blockedIcon else allowedIcon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isBlocked) "Off" else label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}
