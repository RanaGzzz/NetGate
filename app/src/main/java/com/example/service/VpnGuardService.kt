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
import com.example.data.model.AppDestination
import com.example.data.model.VpnGuardConnectionState
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.VpnGuardRuntimeState
import com.example.data.model.formatDataBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VpnGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    VpnGuardManager.disconnect(applicationContext, restoreFirewallIfPaused = false)
                }
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIFICATION -> {
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                mgr?.notify(NOTIFICATION_ID, buildNotification(this, VpnGuardManager.runtimeState.value))
                return START_STICKY
            }
            else -> {
                val notification = buildNotification(this, VpnGuardManager.runtimeState.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "netgate_vpn_guard_channel"
        private const val NOTIFICATION_ID = 1094
        private const val ACTION_START = "com.example.action.VPN_GUARD_START"
        private const val ACTION_STOP = "com.example.action.VPN_GUARD_STOP"
        private const val ACTION_UPDATE_NOTIFICATION = "com.example.action.VPN_GUARD_UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, VpnGuardService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateNotification(context: Context, state: VpnGuardRuntimeState) {
            if (state.connectionState != VpnGuardConnectionState.CONNECTED) return
            createNotificationChannel(context)
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.notify(NOTIFICATION_ID, buildNotification(context, state))
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, VpnGuardService::class.java))
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.vpn_guard_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.vpn_guard_channel_description)
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }

        private fun buildNotification(context: Context, state: VpnGuardRuntimeState): Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DESTINATION, AppDestination.VPN_GUARD.name)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                410,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(context, VpnGuardService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPending = PendingIntent.getService(
                context,
                411,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val scopeText = when (state.routingMode) {
                VpnGuardRoutingMode.WHOLE_SYSTEM -> "Whole System"
                VpnGuardRoutingMode.SELECTED_APPS -> "${state.routedPackagesCount} Selected App(s)"
                VpnGuardRoutingMode.EXCLUDE_APPS -> "All Except ${state.routedPackagesCount} App(s)"
            }
            val title = when (state.connectionState) {
                VpnGuardConnectionState.CONNECTED -> "VPN Guard • ${state.activeProfileName}"
                VpnGuardConnectionState.CONNECTING -> "VPN Guard Connecting…"
                else -> "NetGate VPN Guard"
            }
            val text = if (state.connectionState == VpnGuardConnectionState.CONNECTED) {
                "$scopeText • ↓ ${formatDataBytes(state.rxBytes)} • ↑ ${formatDataBytes(state.txBytes)}"
            } else {
                "$scopeText • ${state.endpoint}"
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPending)
                .addAction(0, "Disconnect", stopPending)
                .build()
        }
    }
}
