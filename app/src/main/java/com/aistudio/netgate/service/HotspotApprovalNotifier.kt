package com.aistudio.netgate.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.netgate.R

/** Posts (and clears) the "new hotspot client" approval notifications. */
object HotspotApprovalNotifier {

    private const val CHANNEL_ID = "netgate_hotspot_alerts"
    private const val NOTIFICATION_ID_BASE = 5100

    fun notifyNewClient(context: Context, clientIp: String, lastHost: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val allowIntent = PendingIntent.getBroadcast(
            context,
            clientIp.hashCode(),
            Intent(context, HotspotApprovalReceiver::class.java).apply {
                action = HotspotApprovalReceiver.ACTION_ALLOW
                putExtra(HotspotApprovalReceiver.EXTRA_CLIENT_IP, clientIp)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val blockIntent = PendingIntent.getBroadcast(
            context,
            clientIp.hashCode() + 1,
            Intent(context, HotspotApprovalReceiver::class.java).apply {
                action = HotspotApprovalReceiver.ACTION_BLOCK
                putExtra(HotspotApprovalReceiver.EXTRA_CLIENT_IP, clientIp)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hostSuffix = if (lastHost.isBlank()) "" else " • $lastHost"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("New hotspot device: $clientIp")
            .setContentText("A new client is waiting for approval$hostSuffix")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(allowIntent)
            .addAction(0, "Allow", allowIntent)
            .addAction(0, "Block", blockIntent)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID_BASE + (clientIp.hashCode() and 0xFF), notification) }
    }

    fun cancel(context: Context, clientIp: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.cancel(NOTIFICATION_ID_BASE + (clientIp.hashCode() and 0xFF))
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hotspot device alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a new device connects to the NetGate hotspot proxy"
            setShowBadge(true)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }
}
