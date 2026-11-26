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
        CrashLogRecorder.install(this)
        AppLogCollector.init(this)
    }
}
