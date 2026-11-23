package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.data.model.AppIconStyle

/**
 * Switches the launcher icon by toggling the `<activity-alias>` declarations in the manifest.
 * Only one alias is enabled at a time, so the launcher always shows a single NetGate entry.
 */
object AppIconManager {

    fun currentStyle(context: Context): AppIconStyle {
        val pm = context.packageManager
        val enabled = AppIconStyle.entries.firstOrNull { style ->
            pm.getComponentEnabledSetting(ComponentName(context, style.aliasName)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return enabled ?: AppIconStyle.EMERALD
    }

    /**
     * Enables [style] and disables the other aliases. The launcher may restart the app's process
     * after the component state changes; that is expected Android behaviour, not a crash.
     */
    fun apply(context: Context, style: AppIconStyle) {
        val pm = context.packageManager
        AppIconStyle.entries.forEach { candidate ->
            val state = if (candidate == style) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(context, candidate.aliasName),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
