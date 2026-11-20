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
import com.example.data.local.NetGateDatabase
import com.example.data.model.AppDestination
import com.example.data.model.HotspotProxyRuntimeState
import com.example.data.model.formatDataBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotspotProxyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var configJob: Job? = null
    private var refreshJob: Job? = null
    private lateinit var preferences: FirewallPreferences
    private var lastAdSignature: String? = null
    private var cachedMatcher: AdBlockMatcherSnapshot = AdBlockMatcherSnapshot.EMPTY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = FirewallPreferences(applicationContext)
        createNotificationChannel()
        observePolicies()
        startRefreshLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                preferences.setHotspotProxyEnabled(false)
                stopHotspotService()
            }
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification(HotspotProxyManager.runtimeState.value))
        HotspotProxyManager.refreshEnvironment(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        configJob?.cancel()
        refreshJob?.cancel()
        HotspotProxyManager.stop(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observePolicies() {
        val db = NetGateDatabase.getInstance(applicationContext)
        configJob = serviceScope.launch {
            withContext(Dispatchers.IO) {
                db.adFilterListDao().insertMissingFilterLists(AdBlockFilterEngine.DEFAULT_AD_FILTER_LISTS)
            }
            combine(
                preferences.preferencesFlow,
                db.websiteRuleDao().observeAllRules(),
                db.adFilterListDao().observeAllFilterLists()
            ) { prefs, websiteRules, adLists ->
                Triple(prefs, websiteRules, adLists)
            }.collectLatest { (prefs, websiteRules, adLists) ->
                if (!prefs.hotspotProxyEnabled) {
                    stopHotspotService()
                    return@collectLatest
                }

                val sig = if (!prefs.hotspotApplyNetGateFilters || !prefs.isAdBlockerEnabled) {
                    "OFF"
                } else {
                    adLists.filter { it.isEnabled }
                        .sortedBy { it.id }
                        .joinToString("|") { "${it.id}:${it.lastSyncedAt}:${it.domainCount}" }
                }
                if (sig != lastAdSignature) {
                    lastAdSignature = sig
                    cachedMatcher = if (sig == "OFF") {
                        AdBlockMatcherSnapshot.EMPTY
                    } else {
                        withContext(Dispatchers.IO) {
                            AdBlockFilterEngine.buildMatcherSnapshot(applicationContext, adLists)
                        }
                    }
                }

                HotspotProxyManager.updateFilterPolicies(
                    prefs = prefs,
                    websiteRules = websiteRules,
                    adMatcher = cachedMatcher
                )
                val started = HotspotProxyManager.startOrUpdate(applicationContext, prefs)
                if (!started) {
                    preferences.setHotspotProxyEnabled(false)
                    stopHotspotService()
                    return@collectLatest
                }
                postNotification(HotspotProxyManager.runtimeState.value)
            }
        }
    }

    private fun startRefreshLoop() {
        refreshJob = serviceScope.launch {
            while (isActive) {
                delay(3000L)
                HotspotProxyManager.refreshEnvironment(applicationContext)
                postNotification(HotspotProxyManager.runtimeState.value)
            }
        }
    }

    private fun stopHotspotService() {
        configJob?.cancel()
        refreshJob?.cancel()
        HotspotProxyManager.stop(applicationContext)
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

    private fun postNotification(state: HotspotProxyRuntimeState) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hotspot_proxy_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.hotspot_proxy_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: HotspotProxyRuntimeState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            201,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DESTINATION, AppDestination.HOTSPOT.name)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            202,
            Intent(this, HotspotProxyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clientsCount = state.clients.size
        val summary = "Proxy ${state.suggestedProxyHost}:${state.activePort} • $clientsCount client(s) • ${formatDataBytes(state.totalRelayedBytes)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("NetGate Hotspot Proxy • ${state.shareModeLabel}")
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "Stop Proxy", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.service.HOTSPOT_PROXY_START"
        const val ACTION_STOP = "com.example.service.HOTSPOT_PROXY_STOP"
        private const val CHANNEL_ID = "netgate_hotspot_proxy_channel"
        private const val NOTIFICATION_ID = 4088

        fun start(context: Context) {
            val intent = Intent(context, HotspotProxyService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HotspotProxyService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
