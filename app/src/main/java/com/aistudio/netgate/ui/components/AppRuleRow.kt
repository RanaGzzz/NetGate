package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.formatCountdownTimer
import com.aistudio.netgate.ui.theme.BlockedRed
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.WarningAmber

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
    cachedIcon: (String) -> ImageBitmap? = { null },
    onToggleWifi: () -> Unit,
    onToggleData: () -> Unit,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val initialCached = remember(app.packageName) { cachedIcon(app.packageName) }
    val iconBitmap by produceState<ImageBitmap?>(initialValue = initialCached, key1 = app.packageName) {
        if (value == null) {
            value = loadIcon(app.packageName)
        }
    }

    val isBlockedRightNow = isFirewallActive && app.isBlockedOn(activeNetworkType)
    val activeTimer = app.activeTimer
    val redAccent = BlockedRed
    val greenAccent = EmeraldPrimary

    val borderBrushOverride: Brush? = remember(
        isSelected,
        activeTimer,
        app.blockWifi,
        app.blockMobileData,
        isDark
    ) {
        when {
            isSelected -> null
            activeTimer != null -> null
            app.blockWifi && app.blockMobileData -> {
                // Both blocked: red accent on both left and right edges
                Brush.horizontalGradient(
                    colors = listOf(
                        redAccent.copy(alpha = if (isDark) 0.85f else 0.90f),
                        redAccent.copy(alpha = if (isDark) 0.30f else 0.35f),
                        redAccent.copy(alpha = if (isDark) 0.85f else 0.90f)
                    )
                )
            }
            app.blockWifi -> {
                // Wi-Fi blocked: red accent starts from the right
                Brush.horizontalGradient(
                    colors = listOf(
                        redAccent.copy(alpha = if (isDark) 0.10f else 0.08f),
                        redAccent.copy(alpha = if (isDark) 0.85f else 0.92f)
                    )
                )
            }
            app.blockMobileData -> {
                // Data blocked: red accent starts from the left
                Brush.horizontalGradient(
                    colors = listOf(
                        redAccent.copy(alpha = if (isDark) 0.85f else 0.92f),
                        redAccent.copy(alpha = if (isDark) 0.10f else 0.08f)
                    )
                )
            }
            else -> {
                // Allowed / whitelisted: green color accent in the same style
                Brush.horizontalGradient(
                    colors = listOf(
                        greenAccent.copy(alpha = if (isDark) 0.70f else 0.80f),
                        greenAccent.copy(alpha = if (isDark) 0.28f else 0.35f)
                    )
                )
            }
        }
    }

    val borderAccent = when {
        isSelected -> MaterialTheme.colorScheme.primary
        activeTimer != null -> WarningAmber
        app.isFullyBlocked -> redAccent
        app.isAnyBlocked -> redAccent
        else -> greenAccent
    }
    val glassTint = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        app.isFullyBlocked -> redAccent.copy(alpha = if (isDark) 0.08f else 0.05f)
        app.isAnyBlocked -> redAccent.copy(alpha = if (isDark) 0.05f else 0.03f)
        else -> greenAccent.copy(alpha = if (isDark) 0.05f else 0.03f)
    }

    val rowInteraction = remember { MutableInteractionSource() }
    val isRowPressed by rowInteraction.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isRowPressed) 0.982f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "appRowPressScale"
    )

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = ripple(),
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .testTag("app_row_${app.packageName}"),
        shape = RoundedCornerShape(20.dp),
        tintColor = glassTint,
        borderAccent = borderAccent,
        borderBrushOverride = borderBrushOverride
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
                        modifier = Modifier.size(38.dp)
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
                    else -> greenAccent
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
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val isDark = LocalIsDarkTheme.current
    val activeColor = EmeraldPrimary
    val containerColor by animateColorAsState(
        targetValue = when {
            !isBlocked -> activeColor.copy(alpha = if (isDark) 0.26f else 0.16f)
            isDark -> Color.Black.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "toggleBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (!isBlocked) {
            activeColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "toggleFg"
    )
    val borderColor = if (!isBlocked) {
        activeColor.copy(alpha = 0.72f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 550f),
        label = "togglePressScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.height(34.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (!isBlocked) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
