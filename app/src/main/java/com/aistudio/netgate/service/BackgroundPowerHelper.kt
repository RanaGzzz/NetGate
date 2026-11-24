package com.aistudio.netgate.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers for keeping NetGate's foreground services (speed monitor, firewall VPN, hotspot proxy)
 * exempt from background battery restrictions and synchronizing Android's Overview/Recents flag.
 */
object BackgroundPowerHelper {

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
