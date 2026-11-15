package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppNetworkRuleEntity
import com.example.data.local.FirewallPreferences
import com.example.data.local.NetGateDatabase
import com.example.data.model.ActiveNetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.IOException

data class VpnRuntimeState(
    val isServiceRunning: Boolean = false,
    val isTunnelActive: Boolean = false,
    val activeNetworkType: ActiveNetworkType = ActiveNetworkType.NONE,
    val blockedActivePackagesCount: Int = 0
)

class NetGateVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tunnelMutex = Mutex()

    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var rulesJob: Job? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private val wifiNetworks = mutableSetOf<Network>()
    private val cellularNetworks = mutableSetOf<Network>()

    private var latestRules: List<AppNetworkRuleEntity> = emptyList()
    private var currentNetworkType: ActiveNetworkType = ActiveNetworkType.NONE

    private lateinit var preferences: FirewallPreferences

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
            updateNetworkSet(network, caps, available = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateNetworkSet(network, networkCapabilities, available = true)
        }

        override fun onLost(network: Network) {
            synchronized(this@NetGateVpnService) {
                wifiNetworks.remove(network)
                cellularNetworks.remove(network)
            }
            recomputeNetworkAndRebuild()
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = FirewallPreferences(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        createNotificationChannel()
        registerPhysicalNetworkCallback()
        observeRules()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    preferences.setFirewallEnabled(false)
                    stopFirewall()
                }
                return START_NOT_STICKY
            }
            ACTION_RELOAD_RULES -> {
                startForegroundCompat(buildNotification(0, currentNetworkType))
                serviceScope.launch { rebuildTunnel() }
                return START_STICKY
            }
            else -> {
                startForegroundCompat(buildNotification(0, currentNetworkType))
                serviceScope.launch {
                    preferences.setFirewallEnabled(true)
                    recomputeNetworkAndRebuild()
                }
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        serviceScope.launch {
            preferences.setFirewallEnabled(false)
            stopFirewall()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterPhysicalNetworkCallback()
        rulesJob?.cancel()
        drainJob?.cancel()
        closeVpnInterface()
        _runtimeState.value = VpnRuntimeState()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeRules() {
        val dao = NetGateDatabase.getInstance(applicationContext).appNetworkRuleDao()
        rulesJob = serviceScope.launch {
            dao.observeAllRules().collectLatest { rules ->
                latestRules = rules
                rebuildTunnel()
            }
        }
    }

    private fun registerPhysicalNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }
        seedInitialNetworks(cm)
    }

    private fun unregisterPhysicalNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        networkCallbackRegistered = false
    }

    private fun seedInitialNetworks(cm: ConnectivityManager) {
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeNetwork != null && caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        ) {
            updateNetworkSet(activeNetwork, caps, available = true)
        } else {
            currentNetworkType = detectPhysicalNetworkFallback(cm)
            _runtimeState.value = _runtimeState.value.copy(
                isServiceRunning = true,
                activeNetworkType = currentNetworkType
            )
        }
    }

    private fun detectPhysicalNetworkFallback(cm: ConnectivityManager): ActiveNetworkType {
        @Suppress("DEPRECATION")
        val all = cm.allNetworks
        var hasWifi = false
        var hasCell = false
        for (net in all) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) hasWifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) hasCell = true
        }
        return when {
            hasWifi -> ActiveNetworkType.WIFI
            hasCell -> ActiveNetworkType.MOBILE_DATA
            else -> ActiveNetworkType.NONE
        }
    }

    private fun updateNetworkSet(
        network: Network,
        caps: NetworkCapabilities,
        available: Boolean
    ) {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
        synchronized(this) {
            if (available && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiNetworks.add(network)
            } else {
                wifiNetworks.remove(network)
            }
            if (available && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                cellularNetworks.add(network)
            } else {
                cellularNetworks.remove(network)
            }
        }
        recomputeNetworkAndRebuild()
    }

    private fun recomputeNetworkAndRebuild() {
        val nextType = synchronized(this) {
            when {
                wifiNetworks.isNotEmpty() -> ActiveNetworkType.WIFI
                cellularNetworks.isNotEmpty() -> ActiveNetworkType.MOBILE_DATA
                else -> ActiveNetworkType.NONE
            }
        }
        currentNetworkType = nextType
        serviceScope.launch {
            rebuildTunnel()
        }
    }

    private suspend fun rebuildTunnel() = tunnelMutex.withLock {
        val netType = currentNetworkType
        val targetBlockedPackages = when (netType) {
            ActiveNetworkType.WIFI -> latestRules.filter { it.blockWifi }.map { it.packageName }
            ActiveNetworkType.MOBILE_DATA -> latestRules.filter { it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.OTHER -> latestRules.filter { it.blockWifi && it.blockMobileData }.map { it.packageName }
            ActiveNetworkType.NONE -> emptyList()
        }.filter { it != packageName }.distinct()

        // Close previous tunnel before rebuilding
        drainJob?.cancel()
        drainJob = null
        closeVpnInterface()

        if (targetBlockedPackages.isEmpty()) {
            _runtimeState.value = VpnRuntimeState(
                isServiceRunning = true,
                isTunnelActive = false,
                activeNetworkType = netType,
                blockedActivePackagesCount = 0
            )
            updateNotification(0, netType)
            return
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.1.10.1", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            .addRoute("::", 0)

        var addedCount = 0
        for (pkg in targetBlockedPackages) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(pkg, 0)
                }
                builder.addAllowedApplication(pkg)
                addedCount++
            } catch (_: PackageManager.NameNotFoundException) {
                // Skip uninstalled packages
            }
        }

        // CRITICAL: Never call establish() with an empty allowed-list, as Android routes ALL apps when empty.
        if (addedCount == 0) {
            _runtimeState.value = VpnRuntimeState(
                isServiceRunning = true,
                isTunnelActive = false,
                activeNetworkType = netType,
                blockedActivePackagesCount = 0
            )
            updateNotification(0, netType)
            return
        }

        val established = runCatching { builder.establish() }.getOrNull()
        if (established == null) {
            _runtimeState.value = VpnRuntimeState(
                isServiceRunning = true,
                isTunnelActive = false,
                activeNetworkType = netType,
                blockedActivePackagesCount = 0
            )
            updateNotification(0, netType)
            return
        }

        vpnInterface = established
        drainJob = serviceScope.launch(Dispatchers.IO) {
            drainPackets(established)
        }

        _runtimeState.value = VpnRuntimeState(
            isServiceRunning = true,
            isTunnelActive = true,
            activeNetworkType = netType,
            blockedActivePackagesCount = addedCount
        )
        updateNotification(addedCount, netType)
    }

    private fun drainPackets(pfd: ParcelFileDescriptor) {
        val buffer = ByteArray(32767)
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                while (serviceScope.isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    // Packets from blocked apps are intentionally sinkholed here.
                }
            }
        } catch (_: IOException) {
            // Expected when interface is closed during rebuild or shutdown
        }
    }

    private fun closeVpnInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private suspend fun stopFirewall() {
        tunnelMutex.withLock {
            drainJob?.cancel()
            drainJob = null
            closeVpnInterface()
        }
        _runtimeState.value = VpnRuntimeState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.firewall_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.firewall_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(blockedCount: Int, networkType: ActiveNetworkType) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(blockedCount, networkType))
    }

    private fun buildNotification(blockedCount: Int, networkType: ActiveNetworkType): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, NetGateVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val netLabel = when (networkType) {
            ActiveNetworkType.WIFI -> "Wi-Fi"
            ActiveNetworkType.MOBILE_DATA -> "Mobile Data"
            ActiveNetworkType.OTHER -> "Network"
            ActiveNetworkType.NONE -> "Offline"
        }

        val contentText = if (blockedCount > 0) {
            "$netLabel active • Blocking $blockedCount app${if (blockedCount == 1) "" else "s"}"
        } else {
            "$netLabel active • Standby (0 apps blocked)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(getString(R.string.firewall_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.firewall_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_RELOAD_RULES = "com.example.service.ACTION_RELOAD_RULES"

        private const val CHANNEL_ID = "netgate_firewall_channel"
        private const val NOTIFICATION_ID = 4041

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        fun startOrReload(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetGateVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
