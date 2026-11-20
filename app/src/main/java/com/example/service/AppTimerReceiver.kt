package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.FirewallPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != AppTimerScheduler.ACTION_CHECK_TIMERS &&
            action != WifiAutomationManager.ACTION_GLOBAL_WIFI_TIMER &&
            action != WifiAutomationManager.ACTION_UNLOCK_GLOBAL_WIFI
        ) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    AppTimerScheduler.ACTION_CHECK_TIMERS -> {
                        val expired = AppTimerScheduler.processDueTimers(appContext)
                        if (expired.isNotEmpty()) {
                            val prefs = FirewallPreferences(appContext).preferencesFlow.first()
                            if (prefs.isFirewallEnabled) {
                                NetGateVpnService.startOrReload(appContext)
                            }
                        }
                    }
                    WifiAutomationManager.ACTION_GLOBAL_WIFI_TIMER -> {
                        WifiAutomationManager.processDueWifiTimer(appContext)
                    }
                    WifiAutomationManager.ACTION_UNLOCK_GLOBAL_WIFI -> {
                        WifiAutomationManager.setGlobalWifiLocked(appContext, locked = false)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
