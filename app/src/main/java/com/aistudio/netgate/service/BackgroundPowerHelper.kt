package com.aistudio.netgate.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Helpers for keeping NetGate's foreground services (speed monitor, firewall VPN, hotspot proxy)
 * exempt from background battery restrictions and synchronizing Android's Overview/Recents flag.
 */
object BackgroundPowerHelper {

    private const val DEFAULT_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    private val wakeLocks = ConcurrentHashMap<String, PowerManager.WakeLock>()

    /**
     * Keeps a filtering service alive through Doze / Battery Saver on Android 15-17.
     *
     * Android 16 tightened foreground-service and Doze handling; a held partial wake lock is what
     * keeps the VPN/TUN read loop and the speed monitor ticking while the screen is off. The lock is
     * refreshed on every call and released in the service's `onDestroy`.
     */
    fun acquireServiceWakeLock(
        context: Context,
        tag: String,
        timeoutMs: Long = DEFAULT_WAKE_LOCK_TIMEOUT_MS
    ) {
        val powerManager = context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        runCatching {
            val existing = wakeLocks[tag]
            if (existing != null && existing.isHeld) return
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NetGate:$tag"
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
            wakeLocks[tag] = lock
        }
    }

    fun releaseServiceWakeLock(tag: String) {
        runCatching {
            wakeLocks.remove(tag)?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching { powerManager.isPowerSaveMode }.getOrDefault(false)
    }

    fun isDeviceIdleMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching { powerManager.isDeviceIdleMode }.getOrDefault(false)
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)

    fun isBackgroundRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return runCatching { activityManager.isBackgroundRestricted }.getOrDefault(false)
    }

    fun areBatteryOptimizationsIgnored(context: Context): Boolean =
        isIgnoringBatteryOptimizations(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    fun buildDirectBatteryOptimizationIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun buildBatteryOptimizationListIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val directStarted = runCatching {
            context.startActivity(buildDirectBatteryOptimizationIntent(context))
            true
        }.getOrDefault(false)
        if (directStarted) return true

        val listStarted = runCatching {
            context.startActivity(buildBatteryOptimizationListIntent())
            true
        }.getOrDefault(false)
        if (listStarted) return true

        return runCatching {
            val detailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(detailsIntent)
            true
        }.getOrDefault(false)
    }

    fun applyExcludeFromRecents(context: Context, exclude: Boolean) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return
        runCatching {
            activityManager.appTasks.forEach { task ->
                runCatching { task.setExcludeFromRecents(exclude) }
            }
        }
    }
}
