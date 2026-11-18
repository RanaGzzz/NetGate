package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.FirewallPreferences
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppDestination
import com.example.data.model.LiveNetworkTelemetry
import com.example.data.model.StatusBarSpeedStyle
import com.example.data.model.formatDataBytes
import com.example.data.model.formatSpeedBytesPerSec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var prefsJob: Job? = null

    private lateinit var preferences: FirewallPreferences

    @Volatile
    private var currentStyle: StatusBarSpeedStyle = StatusBarSpeedStyle.DOWNLOAD_UPLOAD

    @Volatile
    private var showDailyUsage: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = FirewallPreferences(applicationContext)
        createNotificationChannel()
        observePreferences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                preferences.setStatusBarSpeedEnabled(false)
                stopMonitorService()
            }
            return START_NOT_STICKY
        }

        SystemNetworkMonitor.markServiceRunning(true)
        val initialTelemetry = SystemNetworkMonitor.sampleNow(applicationContext)
        startForegroundCompat(buildNotification(initialTelemetry))
        startSamplingLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        prefsJob?.cancel()
        SystemNetworkMonitor.flushToDisk(applicationContext)
        SystemNetworkMonitor.markServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observePreferences() {
        prefsJob?.cancel()
        prefsJob = serviceScope.launch {
            preferences.preferencesFlow.collect { prefs ->
                val styleChanged = currentStyle != prefs.statusBarSpeedStyle ||
                    showDailyUsage != prefs.showStatusBarDailyUsage
                currentStyle = prefs.statusBarSpeedStyle
                showDailyUsage = prefs.showStatusBarDailyUsage

                if (!prefs.isStatusBarSpeedEnabled) {
                    stopMonitorService()
                    return@collect
                }
                if (styleChanged && SystemNetworkMonitor.isServiceActive) {
                    postNotification(SystemNetworkMonitor.telemetryFlow.value)
                }
            }
        }
    }

    private fun startSamplingLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val telemetry = SystemNetworkMonitor.sampleNow(applicationContext)
                postNotification(telemetry)
            }
        }
    }

    private fun stopMonitorService() {
        monitorJob?.cancel()
        monitorJob = null
        SystemNetworkMonitor.flushToDisk(applicationContext)
        SystemNetworkMonitor.markServiceRunning(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification(telemetry: LiveNetworkTelemetry) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(telemetry))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.speed_monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.speed_monitor_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(telemetry: LiveNetworkTelemetry): Notification {
        val openMonitorIntent = PendingIntent.getActivity(
            this,
            102,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DESTINATION, AppDestination.DATA_USAGE.name)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val netLabel = when (telemetry.activeNetworkType) {
            ActiveNetworkType.WIFI -> "Wi-Fi"
            ActiveNetworkType.MOBILE_DATA -> "Mobile"
            ActiveNetworkType.OTHER -> "Ethernet"
            ActiveNetworkType.NONE -> "Offline"
        }

        val dlSpeed = formatSpeedBytesPerSec(telemetry.downloadBytesPerSec)
        val ulSpeed = formatSpeedBytesPerSec(telemetry.uploadBytesPerSec)
        val title = "↓ $dlSpeed   ↑ $ulSpeed"

        val body = if (showDailyUsage) {
            "Today: ↓ ${formatDataBytes(telemetry.todayTotalRxBytes)} • ↑ ${formatDataBytes(telemetry.todayTotalTxBytes)} ($netLabel)"
        } else {
            "Session: ↓ ${formatDataBytes(telemetry.sessionRxBytes)} • ↑ ${formatDataBytes(telemetry.sessionTxBytes)} ($netLabel)"
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("NetGate Live Network • $netLabel")
            .addLine("Incoming (DL): $dlSpeed  (Peak ${formatSpeedBytesPerSec(telemetry.peakDownloadBytesPerSec)})")
            .addLine("Outgoing (UL): $ulSpeed  (Peak ${formatSpeedBytesPerSec(telemetry.peakUploadBytesPerSec)})")
            .addLine("Today Wi-Fi: ↓ ${formatDataBytes(telemetry.todayWifiRxBytes)}  •  ↑ ${formatDataBytes(telemetry.todayWifiTxBytes)}")
            .addLine("Today Mobile: ↓ ${formatDataBytes(telemetry.todayMobileRxBytes)}  •  ↑ ${formatDataBytes(telemetry.todayMobileTxBytes)}")

        val speedIcon = StatusBarIconRenderer.createSpeedIcon(telemetry, currentStyle)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(speedIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openMonitorIntent)
            .addAction(0, getString(R.string.speed_monitor_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.service.NETWORK_MONITOR_START"
        const val ACTION_STOP = "com.example.service.NETWORK_MONITOR_STOP"

        private const val CHANNEL_ID = "netgate_speed_monitor_channel"
        private const val NOTIFICATION_ID = 4042

        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
