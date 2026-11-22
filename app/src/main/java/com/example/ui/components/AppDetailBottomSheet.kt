package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppCountdownTimer
import com.example.data.model.ManagedApp
import com.example.data.model.TimerTargetNetwork
import com.example.data.model.formatCountdownTimer
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.DataAccent
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WifiAccent

private val PRESET_MINUTES = listOf(1, 5, 15, 30, 60, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailBottomSheet(
    app: ManagedApp,
    activeNetworkType: ActiveNetworkType,
    isFirewallActive: Boolean,
    nowMillis: Long,
    loadIcon: suspend (String) -> ImageBitmap?,
    onUpdateRule: (blockWifi: Boolean, blockMobileData: Boolean) -> Unit,
    onScheduleTimer: (durationMinutes: Int, target: TimerTargetNetwork) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app.packageName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("app_detail_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = "${app.appName} icon",
                            modifier = Modifier.size(46.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = "${app.appName} icon",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val liveStatus = when {
                !isFirewallActive -> "Firewall is paused — rules & timers will apply once active"
                app.isBlockedOn(activeNetworkType) -> "Currently blocked on active network"
                else -> "Currently allowed on active network"
            }

            FrostedSurface(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = liveStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            RuleToggleCard(
                title = "Disable Wi-Fi",
                subtitle = "Block all Wi-Fi traffic for this app",
                checked = app.blockWifi,
                icon = if (app.blockWifi) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                iconTint = if (app.blockWifi) MaterialTheme.colorScheme.error else WifiAccent,
                onCheckedChange = { blocked -> onUpdateRule(blocked, app.blockMobileData) },
                testTag = "detail_block_wifi_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            RuleToggleCard(
                title = "Disable Mobile Data",
                subtitle = "Block cellular 4G/5G traffic for this app",
                checked = app.blockMobileData,
                icon = if (app.blockMobileData) Icons.Rounded.SignalCellularOff else Icons.Rounded.SignalCellularAlt,
                iconTint = if (app.blockMobileData) MaterialTheme.colorScheme.error else DataAccent,
                onCheckedChange = { blocked -> onUpdateRule(app.blockWifi, blocked) },
                testTag = "detail_block_data_switch"
            )

            Spacer(modifier = Modifier.height(14.dp))

            AutoDisableTimerCard(
                activeTimer = app.activeTimer,
                nowMillis = nowMillis,
                onScheduleTimer = onScheduleTimer,
                onCancelTimer = onCancelTimer
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_system_app_info_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.open_system_app_info))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoDisableTimerCard(
    activeTimer: AppCountdownTimer?,
    nowMillis: Long,
    onScheduleTimer: (durationMinutes: Int, target: TimerTargetNetwork) -> Unit,
    onCancelTimer: () -> Unit
) {
    var selectedTarget by rememberSaveable { mutableStateOf(TimerTargetNetwork.BOTH) }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(15) }

    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = if (activeTimer != null) WarningAmber else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        tintColor = if (activeTimer != null) WarningAmber.copy(alpha = 0.10f) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_timer_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = WarningAmber.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Disable Countdown Timer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeTimer != null) {
                            "Blocking ${activeTimer.targetLabel()} when timer finishes"
                        } else {
                            "Automatically disable Wi-Fi / Mobile Data at 00:00"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTimer != null) {
                val remaining = activeTimer.remainingMillis(nowMillis)
                val progress = if (activeTimer.durationMillis > 0L) {
                    (remaining.toFloat() / activeTimer.durationMillis.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WarningAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatCountdownTimer(remaining),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = WarningAmber,
                            modifier = Modifier.testTag("active_timer_countdown_text")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = WarningAmber,
                            trackColor = WarningAmber.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCancelTimer,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BlockedRed
                    ),
                    border = BorderStroke(1.dp, BlockedRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_app_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TimerOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Countdown")
                }
            } else {
                Text(
                    text = "Network to disable at 00:00",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimerTargetNetwork.entries.forEach { target ->
                        FilterChip(
                            selected = selectedTarget == target,
                            onClick = { selectedTarget = target },
                            label = { Text(target.label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("timer_target_${target.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Countdown Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PRESET_MINUTES.forEach { mins ->
                        val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("timer_preset_${mins}m")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom stepper row (-1m / +1m or -5m / +5m)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val step = if (selectedMinutes > 15) 5 else 1
                            selectedMinutes = (selectedMinutes - step).coerceAtLeast(1)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("timer_decrease_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease countdown minutes"
                        )
                    }

                    Text(
                        text = "$selectedMinutes min${if (selectedMinutes == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("timer_selected_minutes_text")
                    )

                    IconButton(
                        onClick = {
                            val step = if (selectedMinutes >= 15) 5 else 1
                            selectedMinutes = (selectedMinutes + step).coerceAtMost(480)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("timer_increase_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase countdown minutes"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onScheduleTimer(selectedMinutes, selectedTarget) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_app_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start ${selectedMinutes}m Countdown")
                }
            }
        }
    }
}

@Composable
private fun RuleToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    iconTint: Color,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
