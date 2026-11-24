package com.aistudio.netgate.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.aistudio.netgate.MainActivity
import com.aistudio.netgate.R
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.WifiIdleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class WifiIdleTickResult(
    val idleElapsedMillis: Long,
    val thresholdMillis: Long,
    val isTracking: Boolean,
    val shouldTriggerLock: Boolean
)

/**
 * Pure deterministic idle Wi-Fi detector.
 * Counts continuous idle milliseconds when connected to Wi-Fi and traffic stays below 2 KB/s.
 */
class WifiIdleStateMachine(
    private val idleBytesPerSecThreshold: Long = DEFAULT_IDLE_BYTES_PER_SEC
) {
    private var accumulatedIdleMs: Long = 0L

    fun reset() {
        accumulatedIdleMs = 0L
    }

    fun onTick(
        isWifiActive: Boolean,
        isAlreadyLocked: Boolean,
        autoLockEnabled: Boolean,
        thresholdMinutes: Int,
        totalBytesPerSec: Long,
        deltaMillis: Long
    ): WifiIdleTickResult {
        val thresholdMs = thresholdMinutes.coerceIn(1, 240) * 60_000L
        if (!autoLockEnabled || !isWifiActive || isAlreadyLocked) {
            accumulatedIdleMs = 0L
            return WifiIdleTickResult(
                idleElapsedMillis = 0L,
                thresholdMillis = thresholdMs,
                isTracking = false,
                shouldTriggerLock = false
            )
        }

        if (totalBytesPerSec >= idleBytesPerSecThreshold) {
            accumulatedIdleMs = 0L
            return WifiIdleTickResult(
                idleElapsedMillis = 0L,
                thresholdMillis = thresholdMs,
                isTracking = true,
                shouldTriggerLock = false
            )
        }

        accumulatedIdleMs = (accumulatedIdleMs + deltaMillis.coerceAtLeast(0L)).coerceAtMost(thresholdMs)
        val triggered = accumulatedIdleMs >= thresholdMs
        if (triggered) {
            accumulatedIdleMs = 0L
        }
        return WifiIdleTickResult(
            idleElapsedMillis = if (triggered) thresholdMs else accumulatedIdleMs,
            thresholdMillis = thresholdMs,
            isTracking = true,
            shouldTriggerLock = triggered
        )
    }

    companion object {
        const val DEFAULT_IDLE_BYTES_PER_SEC = 2048L
    }
}

object WifiAutomationManager {

    const val ACTION_GLOBAL_WIFI_TIMER = "com.aistudio.netgate.service.ACTION_GLOBAL_WIFI_TIMER"
    const val ACTION_UNLOCK_GLOBAL_WIFI = "com.aistudio.netgate.service.ACTION_UNLOCK_GLOBAL_WIFI"

    private const val TIMER_ALARM_REQUEST_CODE = 7099
    private const val CHANNEL_ID = "netgate_wifi_automation_channel"
    private const val NOTIFICATION_ID = 4099

    private val idleLock = Any()
    private val idleStateMachine = WifiIdleStateMachine()
    private var lastIdleCheckMs: Long = 0L

    private val _idleStatus = MutableStateFlow(WifiIdleStatus())
    val idleStatus: StateFlow<WifiIdleStatus> = _idleStatus.asStateFlow()

    suspend fun scheduleWifiTimer(context: Context, durationMinutes: Int): Long {
        val durationMs = durationMinutes.coerceIn(1, 720) * 60_000L
        val nowMs = System.currentTimeMillis()
        val expiresAt = nowMs + durationMs

        val prefs = FirewallPreferences(context.applicationContext)
        prefs.setGlobalWifiTimer(expiresAtMillis = expiresAt, durationMillis = durationMs)
        scheduleAlarm(context.applicationContext, expiresAt)
        return expiresAt
    }

    suspend fun cancelWifiTimer(context: Context) {
        val appContext = context.applicationContext
        FirewallPreferences(appContext).clearGlobalWifiTimer()
        cancelAlarm(appContext)
    }

    suspend fun setGlobalWifiLocked(
        context: Context,
        locked: Boolean,
        triggerReasonTitle: String? = null,
        triggerReasonBody: String? = null
    ) {
        val appContext = context.applicationContext
        val prefs = FirewallPreferences(appContext)
        prefs.setGlobalWifiBlocked(locked)
        if (!locked) {
            synchronized(idleLock) {
                idleStateMachine.reset()
                _idleStatus.value = _idleStatus.value.copy(idleElapsedMillis = 0L)
            }
            dismissNotification(appContext)
        } else if (triggerReasonTitle != null && triggerReasonBody != null) {
            val currentPrefs = prefs.preferencesFlow.first()
            if (currentPrefs.autoBlockWifiOpenPanelNotify) {
                postWifiLockedNotification(appContext, triggerReasonTitle, triggerReasonBody)
            }
        }

        val currentPrefs = prefs.preferencesFlow.first()
        if (currentPrefs.isFirewallEnabled || VpnService.prepare(appContext) == null) {
            if (locked && !currentPrefs.isFirewallEnabled) {
                prefs.setFirewallEnabled(true)
            }
            if (locked || currentPrefs.isFirewallEnabled) {
                NetGateVpnService.startOrReload(appContext)
            }
        }
    }

    suspend fun processDueWifiTimer(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val appContext = context.applicationContext
        val prefsRepo = FirewallPreferences(appContext)
        val prefs = prefsRepo.preferencesFlow.first()
        val expiresAt = prefs.globalWifiTimerExpiresAtMillis
        if (expiresAt <= 0L) return false

        if (nowMillis < expiresAt) {
            scheduleAlarm(appContext, expiresAt)
            return false
        }

        prefsRepo.clearGlobalWifiTimer()
        cancelAlarm(appContext)
        setGlobalWifiLocked(
            context = appContext,
            locked = true,
            triggerReasonTitle = "Wi-Fi Timer Expired",
            triggerReasonBody = "NetGate blocked all Wi-Fi app traffic. Tap to turn off the system Wi-Fi switch or restore access."
        )
        return true
    }

    suspend fun evaluateIdleSample(
        context: Context,
        activeNetworkType: ActiveNetworkType,
        totalBytesPerSec: Long,
        prefs: FirewallPreferencesState,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val appContext = context.applicationContext
        val result = synchronized(idleLock) {
            val delta = if (lastIdleCheckMs > 0L) {
                (nowMillis - lastIdleCheckMs).coerceIn(0L, 10_000L)
            } else {
                0L
            }
            lastIdleCheckMs = nowMillis
            idleStateMachine.onTick(
                isWifiActive = activeNetworkType == ActiveNetworkType.WIFI,
                isAlreadyLocked = prefs.globalWifiBlocked,
                autoLockEnabled = prefs.autoBlockWifiOnIdleEnabled,
                thresholdMinutes = prefs.autoBlockWifiIdleMinutes,
                totalBytesPerSec = totalBytesPerSec,
                deltaMillis = delta
            )
        }

        _idleStatus.value = WifiIdleStatus(
            isTracking = result.isTracking,
            idleElapsedMillis = result.idleElapsedMillis,
            thresholdMillis = result.thresholdMillis
        )

        if (result.shouldTriggerLock) {
            setGlobalWifiLocked(
                context = appContext,
                locked = true,
                triggerReasonTitle = "Wi-Fi Idle Lock Triggered",
                triggerReasonBody = "No Wi-Fi activity for ${prefs.autoBlockWifiIdleMinutes} min — Wi-Fi traffic paused by NetGate."
            )
        }
    }

    fun resetIdleProgress() {
        synchronized(idleLock) {
            idleStateMachine.reset()
            lastIdleCheckMs = 0L
        }
        _idleStatus.value = WifiIdleStatus()
    }

    private fun scheduleAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_REQUEST_CODE,
            Intent(context, AppTimerReceiver::class.java).apply { action = ACTION_GLOBAL_WIFI_TIMER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 250L),
                pi
            )
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_REQUEST_CODE,
            Intent(context, AppTimerReceiver::class.java).apply { action = ACTION_GLOBAL_WIFI_TIMER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pi) }
    }

    fun buildWifiPanelIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun buildHotspotSettingsIntent(): Intent {
        return Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun postWifiLockedNotification(context: Context, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wifi_automation_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.wifi_automation_channel_description)
            }
            nm.createNotificationChannel(channel)
        }

        val openAppPi = PendingIntent.getActivity(
            context,
            210,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DESTINATION, AppDestination.HOTSPOT.name)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiPanelPi = PendingIntent.getActivity(
            context,
            211,
            buildWifiPanelIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val unlockPi = PendingIntent.getBroadcast(
            context,
            212,
            Intent(context, AppTimerReceiver::class.java).apply { action = ACTION_UNLOCK_GLOBAL_WIFI },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppPi)
            .addAction(0, "Wi-Fi Panel", wifiPanelPi)
            .addAction(0, "Restore Wi-Fi", unlockPi)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    fun dismissNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(NOTIFICATION_ID) }
    }
}
