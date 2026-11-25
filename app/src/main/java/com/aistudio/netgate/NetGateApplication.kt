package com.aistudio.netgate

import android.app.Application
import com.aistudio.netgate.service.CrashLogRecorder

/**
 * Process entry point. Registers the crash recorder as early as possible so failures in the
 * firewall/VPN services are captured even when they happen before the UI is created.
 */
class NetGateApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogRecorder.install(this)
    }
}
