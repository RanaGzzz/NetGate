package com.aistudio.netgate.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.netgate.R

/** Receives the result of an in-app APK install session and reports it to the user. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Confirm the NetGate update to finish installing"
            PackageInstaller.STATUS_SUCCESS -> "NetGate update installed"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Update install was cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update install"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Update conflicts with the installed NetGate build"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update is not compatible with the device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded update package is invalid"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update"
            else -> "Update install finished (status $status)"
        }

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmIntent) }
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name) + " updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("NetGate Update")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.aistudio.netgate.service.UPDATE_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "netgate_update_channel"
        private const val NOTIFICATION_ID = 4077
    }
}
