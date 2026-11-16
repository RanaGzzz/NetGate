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
        if (intent?.action != AppTimerScheduler.ACTION_CHECK_TIMERS) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val expired = AppTimerScheduler.processDueTimers(appContext)
                if (expired.isNotEmpty()) {
                    val prefs = FirewallPreferences(appContext).preferencesFlow.first()
                    if (prefs.isFirewallEnabled) {
                        NetGateVpnService.startOrReload(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
