package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Single place that enumerates Android networks and their DNS servers.
 *
 * Before this, `DnsConnectionChecker`, `DnsLeakTester`, `SystemNetworkMonitor` and
 * `SecurityAuditEngine` each walked `ConnectivityManager.allNetworks` on their own. They now share
 * this capture, while keeping their own behavior: the DNS diagnostics stay independent features, and
 * `TrafficStats` (monitor) and `NetworkStatsManager` (hotspot) remain separate measurement sources.
 *
 * Snapshots are immutable and cheap to reuse; a short TTL cache avoids re-enumerating when several
 * diagnostics run back to back, and the 1 s monitoring loop naturally captures once per tick.
 */
class NetworkSnapshotProvider(
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val lock = Any()
    private var cached: NetworkSnapshot? = null

    /** Cached capture for diagnostics (default) or a fresh one (`allowCached = false`). */
    fun snapshot(context: Context, allowCached: Boolean = true): NetworkSnapshot {
        val cm = runCatching {
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()
        return snapshot(cm, allowCached)
    }

    fun snapshot(cm: ConnectivityManager?, allowCached: Boolean = true): NetworkSnapshot {
        if (allowCached) {
            return cachedOrCapture { capture(cm) }
        }
        val snapshot = capture(cm)
        synchronized(lock) { cached = snapshot }
        return snapshot
    }

    /** Always enumerates; never serves the cache. */
    fun capture(cm: ConnectivityManager?): NetworkSnapshot {
        val interfaces = cm?.let { readInterfaces(it) }.orEmpty()
        val activeHandle = cm?.let { readActiveNetworkHandle(it) }
        return NetworkSnapshot(
            interfaces = interfaces,
            activeNetworkHandle = activeHandle,
            capturedAtMs = clock()
        )
    }

    fun invalidate() {
        synchronized(lock) { cached = null }
    }

    /**
     * Cache core. Returns the previous snapshot while it is younger than [cacheTtlMs], otherwise calls
     * [capture] once and stores the result.
     */
    internal fun cachedOrCapture(capture: () -> NetworkSnapshot): NetworkSnapshot {
        if (cacheTtlMs <= 0L) {
            val fresh = capture()
            synchronized(lock) { cached = fresh }
            return fresh
        }
        synchronized(lock) {
            val existing = cached
            if (existing != null && clock() - existing.capturedAtMs < cacheTtlMs) {
                return existing
            }
        }
        val fresh = capture()
        synchronized(lock) { cached = fresh }
        return fresh
    }

    private fun readInterfaces(cm: ConnectivityManager): List<NetworkInterfaceSnapshot> {
        @Suppress("DEPRECATION")
        val networks = runCatching { cm.allNetworks }.getOrDefault(emptyArray())
        return networks.mapNotNull { network ->
            val capabilities = runCatching { cm.getNetworkCapabilities(network) }.getOrNull()
                ?: return@mapNotNull null
            val linkProperties = runCatching { cm.getLinkProperties(network) }.getOrNull()
            NetworkInterfaceSnapshot(
                handle = networkHandle(network),
                capabilities = capabilities.toCapabilitiesInfo(),
                linkProperties = linkProperties?.toLinkPropertiesSnapshot()
            )
        }
    }

    private fun readActiveNetworkHandle(cm: ConnectivityManager): String? =
        runCatching { cm.activeNetwork?.let(::networkHandle) }.getOrNull()

    private fun networkHandle(network: Network): String =
        runCatching { network.networkHandle.toString() }.getOrDefault(network.toString())

    /** Maps platform metered state onto the previous `!NOT_METERED` convention. */
    private fun NetworkCapabilities.toCapabilitiesInfo(): NetworkCapabilitiesInfo {
        val transports = buildSet {
            if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(NetworkTransport.WIFI)
            if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(NetworkTransport.CELLULAR)
            if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(NetworkTransport.ETHERNET)
            if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(NetworkTransport.VPN)
            if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add(NetworkTransport.BLUETOOTH)
        }
        return NetworkCapabilitiesInfo(
            hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            notVpn = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downstreamKbps = linkDownstreamBandwidthKbps,
            upstreamKbps = linkUpstreamBandwidthKbps,
            transports = transports
        )
    }

    private fun LinkProperties.toLinkPropertiesSnapshot(): LinkPropertiesSnapshot {
        val ipv4 = linkAddresses.mapNotNull { it.address }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
        val ipv6 = linkAddresses.mapNotNull { it.address }
            .filterIsInstance<Inet6Address>()
            .mapNotNull { it.hostAddress }
        val proxy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { httpProxy }.getOrNull()
        } else {
            null
        }
        return LinkPropertiesSnapshot(
            interfaceName = interfaceName?.takeIf { it.isNotBlank() },
            dnsServers = dnsServers.mapNotNull { it.hostAddress }.distinct(),
            ipv4Addresses = ipv4.distinct(),
            ipv6Addresses = ipv6.distinct(),
            gatewayAddress = routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress,
            mtu = mtu,
            httpProxyHost = proxy?.host,
            httpProxyPort = proxy?.port ?: 0
        )
    }

    companion object {
        /** A capture is reused for at most this long (well below the 1 s monitor cadence). */
        const val DEFAULT_CACHE_TTL_MS = 1_000L

        /** Process-wide provider used by diagnostics, security audit and the network monitor. */
        val shared: NetworkSnapshotProvider by lazy { NetworkSnapshotProvider() }
    }
}
