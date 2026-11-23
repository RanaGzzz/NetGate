package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.FirewallPreferences
import com.example.data.model.AppDestination
import com.example.data.model.DpiBypassMode
import com.example.data.model.FirewallPreferencesState
import com.example.service.HotspotProxyManager
import com.example.service.HotspotProxyService
import com.example.service.NetGateVpnService
import com.example.service.VpnGuardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetGateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = runCatching { goAsync() }.getOrNull()
        widgetScope.launch {
            try {
                updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                runCatching { pendingResult?.finish() }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in WIDGET_ACTIONS) return

        val appContext = context.applicationContext
        val pendingResult = runCatching { goAsync() }.getOrNull()
        widgetScope.launch {
            try {
                handleWidgetAction(appContext, action)
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, NetGateWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, manager, ids)
                }
            } finally {
                runCatching { pendingResult?.finish() }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_VPN = "com.example.widget.ACTION_TOGGLE_VPN"
        const val ACTION_TOGGLE_DPI = "com.example.widget.ACTION_TOGGLE_DPI"
        const val ACTION_TOGGLE_ADBLOCK = "com.example.widget.ACTION_TOGGLE_ADBLOCK"
        const val ACTION_TOGGLE_HOTSPOT = "com.example.widget.ACTION_TOGGLE_HOTSPOT"
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"

        private val WIDGET_ACTIONS = setOf(
            ACTION_TOGGLE_VPN,
            ACTION_TOGGLE_DPI,
            ACTION_TOGGLE_ADBLOCK,
            ACTION_TOGGLE_HOTSPOT,
            ACTION_REFRESH
        )

        private const val FALLBACK_COLOR_ACTIVE = 0xFF10B981.toInt()
        private const val FALLBACK_COLOR_ACTIVE_TEXT = 0xFFA7F3D0.toInt()
        private const val FALLBACK_COLOR_INACTIVE = 0xFF94A3B8.toInt()

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(appContext, NetGateWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            if (ids.isEmpty()) return

            widgetScope.launch {
                runCatching { updateWidgets(appContext, manager, ids) }
            }
        }

        internal suspend fun handleWidgetAction(context: Context, action: String) {
            val preferences = FirewallPreferences(context)
            val currentPrefs = preferences.preferencesFlow.first()

            when (action) {
                ACTION_TOGGLE_VPN -> {
                    val isCurrentlyActive = currentPrefs.isFirewallEnabled ||
                        NetGateVpnService.runtimeState.value.isServiceRunning
                    if (isCurrentlyActive) {
                        preferences.setFirewallEnabled(false)
                        NetGateVpnService.stop(context)
                    } else {
                        val prepareIntent = runCatching { VpnService.prepare(context) }.getOrNull()
                        if (prepareIntent != null) {
                            val launchIntent = Intent(context, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_DESTINATION, AppDestination.FIREWALL.name)
                                putExtra(MainActivity.EXTRA_REQUEST_VPN_CONSENT, true)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            runCatching { context.startActivity(launchIntent) }
                        } else {
                            if (VpnGuardManager.runtimeState.value.isConnectedOrConnecting) {
                                VpnGuardManager.disconnect(context, restoreFirewallIfPaused = false)
                            }
                            preferences.setFirewallEnabled(true)
                            NetGateVpnService.startOrReload(context)
                        }
                    }
                }

                ACTION_TOGGLE_DPI -> {
                    val targetMode = if (currentPrefs.dpiBypassMode == DpiBypassMode.OFF) {
                        DpiBypassMode.AUTOPILOT
                    } else {
                        DpiBypassMode.OFF
                    }
                    preferences.setDpiBypassMode(targetMode)
                }

                ACTION_TOGGLE_ADBLOCK -> {
                    preferences.setAdBlockerEnabled(!currentPrefs.isAdBlockerEnabled)
                }

                ACTION_TOGGLE_HOTSPOT -> {
                    val target = !currentPrefs.hotspotProxyEnabled
                    preferences.setHotspotProxyEnabled(target)
                    if (target) {
                        HotspotProxyService.start(context)
                        HotspotProxyManager.startOrUpdate(
                            context,
                            currentPrefs.copy(hotspotProxyEnabled = true)
                        )
                    } else {
                        HotspotProxyService.stop(context)
                        HotspotProxyManager.stop(context)
                    }
                }

                ACTION_REFRESH -> {
                    HotspotProxyManager.refreshEnvironment(context)
                }
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val prefs = FirewallPreferences(context).preferencesFlow.first()
            val vpnRunning = NetGateVpnService.runtimeState.value.isServiceRunning
            val hotspotRunning = HotspotProxyManager.runtimeState.value.isProxyRunning
            val views = buildRemoteViews(
                context = context,
                prefs = prefs,
                vpnRunning = vpnRunning,
                hotspotRunning = hotspotRunning
            )
            for (widgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        internal fun buildRemoteViews(
            context: Context,
            prefs: FirewallPreferencesState,
            vpnRunning: Boolean,
            hotspotRunning: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_netgate_controls)

            val useDynamicYou = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.useDynamicColor
            val activeAccentColor = if (useDynamicYou) {
                ContextCompat.getColor(context, R.color.widget_active_accent)
            } else {
                FALLBACK_COLOR_ACTIVE
            }
            val activeTextColor = if (useDynamicYou) {
                ContextCompat.getColor(context, R.color.widget_active_text)
            } else {
                FALLBACK_COLOR_ACTIVE_TEXT
            }
            val inactiveColor = if (useDynamicYou) {
                ContextCompat.getColor(context, R.color.widget_inactive_tint)
            } else {
                FALLBACK_COLOR_INACTIVE
            }
            val titleTextColor = ContextCompat.getColor(context, R.color.widget_title_text)
            val subtitleTextColor = ContextCompat.getColor(context, R.color.widget_subtitle_text)

            val vpnActive = prefs.isFirewallEnabled || vpnRunning
            val dpiActive = prefs.dpiBypassMode != DpiBypassMode.OFF
            val adBlockActive = prefs.isAdBlockerEnabled
            val hotspotActive = prefs.hotspotProxyEnabled || hotspotRunning

            val activeCount = listOf(vpnActive, dpiActive, adBlockActive, hotspotActive).count { it }
            val statusText = when {
                vpnActive -> "Protected • $activeCount ON"
                hotspotActive -> "Hotspot • $activeCount ON"
                activeCount > 0 -> "$activeCount Ready"
                else -> "Standby"
            }
            val anyHighlighted = vpnActive || hotspotActive
            views.setInt(R.id.widget_brand_icon, "setColorFilter", activeAccentColor)
            views.setTextColor(R.id.widget_title, titleTextColor)
            views.setInt(R.id.widget_refresh_btn, "setColorFilter", subtitleTextColor)

            views.setTextViewText(R.id.widget_status_pill, statusText)
            views.setInt(
                R.id.widget_status_pill,
                "setBackgroundResource",
                if (anyHighlighted) R.drawable.widget_badge_active else R.drawable.widget_badge_inactive
            )
            views.setTextColor(
                R.id.widget_status_pill,
                if (anyHighlighted) activeTextColor else inactiveColor
            )

            val dpiLabel = prefs.dpiBypassMode.title

            bindTile(
                views = views,
                tileId = R.id.widget_tile_vpn,
                iconId = R.id.widget_icon_vpn,
                labelId = R.id.widget_label_vpn,
                stateId = R.id.widget_state_vpn,
                isActive = vpnActive,
                stateLabel = if (vpnActive) "Active" else "Paused",
                activeIconColor = activeAccentColor,
                activeTextColor = activeTextColor,
                inactiveColor = inactiveColor,
                titleTextColor = titleTextColor,
                pendingIntent = createActionPendingIntent(context, ACTION_TOGGLE_VPN, 1001)
            )
            bindTile(
                views = views,
                tileId = R.id.widget_tile_dpi,
                iconId = R.id.widget_icon_dpi,
                labelId = R.id.widget_label_dpi,
                stateId = R.id.widget_state_dpi,
                isActive = dpiActive,
                stateLabel = dpiLabel,
                activeIconColor = activeAccentColor,
                activeTextColor = activeTextColor,
                inactiveColor = inactiveColor,
                titleTextColor = titleTextColor,
                pendingIntent = createActionPendingIntent(context, ACTION_TOGGLE_DPI, 1002)
            )
            bindTile(
                views = views,
                tileId = R.id.widget_tile_adblock,
                iconId = R.id.widget_icon_adblock,
                labelId = R.id.widget_label_adblock,
                stateId = R.id.widget_state_adblock,
                isActive = adBlockActive,
                stateLabel = if (adBlockActive) "On" else "Off",
                activeIconColor = activeAccentColor,
                activeTextColor = activeTextColor,
                inactiveColor = inactiveColor,
                titleTextColor = titleTextColor,
                pendingIntent = createActionPendingIntent(context, ACTION_TOGGLE_ADBLOCK, 1003)
            )
            bindTile(
                views = views,
                tileId = R.id.widget_tile_hotspot,
                iconId = R.id.widget_icon_hotspot,
                labelId = R.id.widget_label_hotspot,
                stateId = R.id.widget_state_hotspot,
                isActive = hotspotActive,
                stateLabel = if (hotspotActive) "Sharing" else "Off",
                activeIconColor = activeAccentColor,
                activeTextColor = activeTextColor,
                inactiveColor = inactiveColor,
                titleTextColor = titleTextColor,
                pendingIntent = createActionPendingIntent(context, ACTION_TOGGLE_HOTSPOT, 1004)
            )

            val openAppIntent = PendingIntent.getActivity(
                context,
                1000,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppIntent)
            views.setOnClickPendingIntent(
                R.id.widget_refresh_btn,
                createActionPendingIntent(context, ACTION_REFRESH, 1005)
            )

            return views
        }

        private fun bindTile(
            views: RemoteViews,
            tileId: Int,
            iconId: Int,
            labelId: Int,
            stateId: Int,
            isActive: Boolean,
            stateLabel: String,
            activeIconColor: Int,
            activeTextColor: Int,
            inactiveColor: Int,
            titleTextColor: Int,
            pendingIntent: PendingIntent
        ) {
            views.setInt(
                tileId,
                "setBackgroundResource",
                if (isActive) R.drawable.widget_tile_active else R.drawable.widget_tile_inactive
            )
            views.setInt(
                iconId,
                "setColorFilter",
                if (isActive) activeIconColor else inactiveColor
            )
            views.setTextColor(labelId, titleTextColor)
            views.setTextViewText(stateId, stateLabel)
            views.setTextColor(
                stateId,
                if (isActive) activeTextColor else inactiveColor
            )
            views.setOnClickPendingIntent(tileId, pendingIntent)
        }

        private fun createActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, NetGateWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
