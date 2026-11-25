package com.aistudio.netgate.service

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
import androidx.core.app.NotificationManagerCompat
import com.aistudio.netgate.MainActivity
import com.aistudio.netgate.R
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.data.model.formatSpeedBytesPerSec
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

    @Volatile
    private var latestPrefs: FirewallPreferencesState = FirewallPreferencesState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = FirewallPreferences(applicationContext)
        createNotificationChannel()
        observePreferences()
        BackgroundPowerHelper.acquireServiceWakeLock(applicationContext, "speed-monitor")
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
        if (!startForegroundCompat(buildNotification(initialTelemetry))) {
            return START_NOT_STICKY
        }
        startSamplingLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        prefsJob?.cancel()
        BackgroundPowerHelper.releaseServiceWakeLock("speed-monitor")
        SystemNetworkMonitor.flushToDisk(applicationContext)
        SystemNetworkMonitor.markServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observePreferences() {
        prefsJob?.cancel()
        prefsJob = serviceScope.launch {
            preferences.preferencesFlow.collect { prefs ->
                latestPrefs = prefs
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
                BackgroundPowerHelper.acquireServiceWakeLock(applicationContext, "speed-monitor")
                val telemetry = SystemNetworkMonitor.sampleNow(applicationContext)
                if (latestPrefs.autoBlockWifiOnIdleEnabled) {
                    WifiAutomationManager.evaluateIdleSample(
                        context = applicationContext,
                        activeNetworkType = telemetry.activeNetworkType,
                        totalBytesPerSec = telemetry.totalBytesPerSec,
                        prefs = latestPrefs
                    )
                }
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

    private fun startForegroundCompat(notification: Notification): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                // Most commonly a denied POST_NOTIFICATIONS/FGS policy: the system would never show
                // the speed icon, so turn the preference back off instead of running invisibly.
                android.util.Log.w("NetworkMonitorService", "Unable to start speed monitor", error)
                SystemNetworkMonitor.flushToDisk(applicationContext)
                SystemNetworkMonitor.markServiceRunning(false)
                serviceScope.launch {
                    preferences.setStatusBarSpeedEnabled(false)
                    stopSelf()
                }
                false
            }
        )
    }

    private fun postNotification(telemetry: LiveNetworkTelemetry) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(telemetry)) }
    }

    private fun createNotificationChannel() {
        ensureNotificationChannel(applicationContext)
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
            .setColor(android.graphics.Color.WHITE)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openMonitorIntent)
            .addAction(0, getString(R.string.speed_monitor_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.aistudio.netgate.service.NETWORK_MONITOR_START"
        const val ACTION_STOP = "com.aistudio.netgate.service.NETWORK_MONITOR_STOP"

        const val LEGACY_CHANNEL_ID = "netgate_speed_monitor_channel"
        const val CHANNEL_ID = "netgate_speed_monitor_channel_v2"
        private const val NOTIFICATION_ID = 4042

        /**
         * Creates or upgrades the silent-sound IMPORTANCE_DEFAULT channel required on Android 15–17
         * so SystemUI does not hide the live status-bar speed icon as a silent/low-importance item.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.speed_monitor_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.speed_monitor_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        /**
         * Android 15–17 hides the status bar icon when notifications are blocked OR when the
         * channel importance is downgraded below IMPORTANCE_DEFAULT (e.g. Low/Min/Silent).
         */
        fun areNotificationsBlocked(context: Context): Boolean {
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = nm.getNotificationChannel(CHANNEL_ID)
                if (channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    return true
                }
            }
            return false
        }

        fun isChannelImportanceTooLow(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) return false
            val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return false
            return channel.importance != NotificationManager.IMPORTANCE_NONE &&
                channel.importance < NotificationManager.IMPORTANCE_DEFAULT
        }

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
