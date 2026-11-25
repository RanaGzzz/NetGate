package com.aistudio.netgate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aistudio.netgate.data.local.AdvancedPreferences
import com.aistudio.netgate.data.local.FirewallPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles "Allow" / "Block" actions from the new-device approval notification. */
class HotspotApprovalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val clientIp = intent.getStringExtra(EXTRA_CLIENT_IP)?.trim().orEmpty()
        if (clientIp.isEmpty()) return
        if (action != ACTION_ALLOW && action != ACTION_BLOCK) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val advanced = AdvancedPreferences(appContext)
                if (action == ACTION_ALLOW) {
                    advanced.approveHotspotClient(clientIp)
                } else {
                    advanced.revokeHotspotClient(clientIp)
                    advanced.markHotspotClientPending(clientIp)
                    FirewallPreferences(appContext).setHotspotClientBlocked(clientIp, true)
                }
                HotspotApprovalNotifier.cancel(appContext, clientIp)
                HotspotProxyManager.refreshEnvironment(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALLOW = "com.aistudio.netgate.service.HOTSPOT_CLIENT_ALLOW"
        const val ACTION_BLOCK = "com.aistudio.netgate.service.HOTSPOT_CLIENT_BLOCK"
        const val EXTRA_CLIENT_IP = "client_ip"
    }
}
