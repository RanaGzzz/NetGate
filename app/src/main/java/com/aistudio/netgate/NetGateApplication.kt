package com.aistudio.netgate

import android.app.Application
import com.aistudio.netgate.service.AppLogCollector
import com.aistudio.netgate.service.CrashLogRecorder

/**
 * Process entry point. Registers the crash recorder and live app log collector as early as
 * possible so failures in firewall/VPN services are captured even before the UI is created.
 */
class NetGateApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Local-only diagnostics: uncaught exceptions land in filesDir/logs/netgate.log and are
        // then handed to the platform handler, so crash behaviour and reporting are unchanged.
        AppLog.install(this)
        CrashLogRecorder.install(this)
        AppLogCollector.init(this)
    }
}
