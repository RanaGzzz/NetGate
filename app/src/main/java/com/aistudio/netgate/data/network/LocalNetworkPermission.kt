package com.aistudio.netgate.data.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17 (API 37) blocks local-network traffic for apps that target 37 unless
 * `android.permission.ACCESS_LOCAL_NETWORK` is granted at runtime.
 *
 * NetGate's hotspot proxy, its PAC file endpoint and per-client accounting all talk to devices on
 * the LAN, so that grant has to exist before the proxy binds. Everything here is a no-op below
 * API 37, because apps targeting 36 or lower still inherit local network access from `INTERNET`,
 * and Android's guidance is explicit that the permission must not be requested before an app
 * targets 37.
 */
object LocalNetworkPermission {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    const val DENIED_MESSAGE =
        "Allow nearby-device access in Settings so the Hotspot proxy can reach clients on Android 17+"

    /** True once the platform actually enforces the permission. */
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
