package com.aistudio.netgate.service

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import android.os.Build
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.network.NetworkSnapshotProvider
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.ThroughputSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlin.math.roundToLong

/**
 * Pure, deterministic rate & daily usage calculator for incoming (Rx) and outgoing (Tx) bytes.
 * Handles baseline initialization, interface changes, counter resets/reboots, and UNSUPPORTED values.
 */
class TrafficRateCalculator(
    private val maxHistorySamples: Int = 45
) {
    private var lastTimestampMs: Long? = null
    private var lastRxBytes: Long? = null
    private var lastTxBytes: Long? = null
    private var lastInterfaceName: String? = null

    private var sessionRxBytes: Long = 0L
    private var sessionTxBytes: Long = 0L
    private var peakDownloadBytesPerSec: Long = 0L
    private var peakUploadBytesPerSec: Long = 0L

    private var currentDayKey: Long = -1L
    private var todayWifiRxBytes: Long = 0L
    private var todayWifiTxBytes: Long = 0L
    private var todayMobileRxBytes: Long = 0L
    private var todayMobileTxBytes: Long = 0L

    private val history = ArrayDeque<ThroughputSample>()

    fun seedDailyTotals(
        dayKey: Long,
        wifiRx: Long,
        wifiTx: Long,
        mobileRx: Long,
        mobileTx: Long
    ) {
        currentDayKey = dayKey
        todayWifiRxBytes = wifiRx.coerceAtLeast(0L)
        todayWifiTxBytes = wifiTx.coerceAtLeast(0L)
        todayMobileRxBytes = mobileRx.coerceAtLeast(0L)
        todayMobileTxBytes = mobileTx.coerceAtLeast(0L)
    }

    fun resetSession() {
        sessionRxBytes = 0L
        sessionTxBytes = 0L
        peakDownloadBytesPerSec = 0L
        peakUploadBytesPerSec = 0L
        history.clear()
    }

    fun update(
        rxBytes: Long,
        txBytes: Long,
        networkType: ActiveNetworkType,
        interfaceName: String?,
        timestampMs: Long,
        dayKey: Long,
        isServiceRunning: Boolean
    ): LiveNetworkTelemetry {
        if (currentDayKey != dayKey) {
            currentDayKey = dayKey
            todayWifiRxBytes = 0L
            todayWifiTxBytes = 0L
            todayMobileRxBytes = 0L
            todayMobileTxBytes = 0L
        }

        val supported = rxBytes != TrafficStats.UNSUPPORTED.toLong() &&
            txBytes != TrafficStats.UNSUPPORTED.toLong() &&
            rxBytes >= 0L &&
            txBytes >= 0L

        if (!supported) {
            val sample = ThroughputSample(
                timestampMs = timestampMs,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L
            )
            appendSample(sample)
            return buildTelemetry(
                downloadRate = 0L,
                uploadRate = 0L,
                networkType = networkType,
                interfaceName = interfaceName,
                isServiceRunning = isServiceRunning,
                isTrafficStatsSupported = false
            )
        }

        val prevTime = lastTimestampMs
        val prevRx = lastRxBytes
        val prevTx = lastTxBytes
        val sameInterface = lastInterfaceName == null ||
            interfaceName == null ||
            lastInterfaceName == interfaceName

        var downloadRate = 0L
        var uploadRate = 0L

        if (prevTime != null && prevRx != null && prevTx != null && sameInterface) {
            val elapsedMs = (timestampMs - prevTime).coerceAtLeast(1L)
            val deltaRx = if (rxBytes >= prevRx) rxBytes - prevRx else 0L
            val deltaTx = if (txBytes >= prevTx) txBytes - prevTx else 0L

            if (elapsedMs in 100L..15_000L) {
                downloadRate = ((deltaRx * 1000.0) / elapsedMs.toDouble()).roundToLong().coerceAtLeast(0L)
                uploadRate = ((deltaTx * 1000.0) / elapsedMs.toDouble()).roundToLong().coerceAtLeast(0L)
            }

            sessionRxBytes += deltaRx
            sessionTxBytes += deltaTx

            when (networkType) {
                ActiveNetworkType.WIFI -> {
                    todayWifiRxBytes += deltaRx
                    todayWifiTxBytes += deltaTx
                }
                ActiveNetworkType.MOBILE_DATA -> {
                    todayMobileRxBytes += deltaRx
                    todayMobileTxBytes += deltaTx
                }
                ActiveNetworkType.OTHER -> {
                    todayWifiRxBytes += deltaRx
                    todayWifiTxBytes += deltaTx
                }
                ActiveNetworkType.NONE -> Unit
            }

            if (downloadRate > peakDownloadBytesPerSec) {
                peakDownloadBytesPerSec = downloadRate
            }
            if (uploadRate > peakUploadBytesPerSec) {
                peakUploadBytesPerSec = uploadRate
            }
        }

        lastTimestampMs = timestampMs
        lastRxBytes = rxBytes
        lastTxBytes = txBytes
        lastInterfaceName = interfaceName

        appendSample(
            ThroughputSample(
                timestampMs = timestampMs,
                rxBytesPerSec = downloadRate,
                txBytesPerSec = uploadRate
            )
        )

        return buildTelemetry(
            downloadRate = downloadRate,
            uploadRate = uploadRate,
            networkType = networkType,
            interfaceName = interfaceName,
            isServiceRunning = isServiceRunning,
            isTrafficStatsSupported = true
        )
    }

    private fun appendSample(sample: ThroughputSample) {
        if (history.size >= maxHistorySamples) {
            history.removeFirst()
        }
        history.addLast(sample)
    }

    private fun buildTelemetry(
        downloadRate: Long,
        uploadRate: Long,
        networkType: ActiveNetworkType,
        interfaceName: String?,
        isServiceRunning: Boolean,
        isTrafficStatsSupported: Boolean
    ): LiveNetworkTelemetry {
        return LiveNetworkTelemetry(
            downloadBytesPerSec = downloadRate,
            uploadBytesPerSec = uploadRate,
            peakDownloadBytesPerSec = peakDownloadBytesPerSec,
            peakUploadBytesPerSec = peakUploadBytesPerSec,
            sessionRxBytes = sessionRxBytes,
            sessionTxBytes = sessionTxBytes,
            todayWifiRxBytes = todayWifiRxBytes,
            todayWifiTxBytes = todayWifiTxBytes,
            todayMobileRxBytes = todayMobileRxBytes,
            todayMobileTxBytes = todayMobileTxBytes,
            activeNetworkType = networkType,
            activeInterfaceName = interfaceName,
            historySamples = history.toList(),
            isMonitorServiceRunning = isServiceRunning,
            isTrafficStatsSupported = isTrafficStatsSupported
        )
    }
}

object SystemNetworkMonitor {

    private const val PREFS_NAME = "netgate_live_usage_prefs"
    private const val KEY_DAY = "day_key"
    private const val KEY_WIFI_RX = "today_wifi_rx"
    private const val KEY_WIFI_TX = "today_wifi_tx"
    private const val KEY_MOBILE_RX = "today_mobile_rx"
    private const val KEY_MOBILE_TX = "today_mobile_tx"

    private val lock = Any()
    private val calculator = TrafficRateCalculator(maxHistorySamples = 45)
    private var isSeededFromPrefs = false
    private var lastPersistedAtMs = 0L

    private val _telemetryFlow = MutableStateFlow(LiveNetworkTelemetry())
    val telemetryFlow: StateFlow<LiveNetworkTelemetry> = _telemetryFlow.asStateFlow()

    @Volatile
    var isServiceActive: Boolean = false
        private set

    fun markServiceRunning(running: Boolean) {
        isServiceActive = running
        _telemetryFlow.value = _telemetryFlow.value.copy(isMonitorServiceRunning = running)
    }

    fun resetSession(context: Context) {
        synchronized(lock) {
            calculator.resetSession()
        }
        sampleNow(context)
    }

    fun sampleNow(context: Context): LiveNetworkTelemetry {
        val nowMs = System.currentTimeMillis()
        val dayKey = currentLocalDayKey(nowMs)
        val appContext = context.applicationContext
        val snapshot = readSystemNetworkCounters(appContext)

        val telemetry = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!isSeededFromPrefs) {
                seedFromPrefsLocked(prefs, dayKey)
                isSeededFromPrefs = true
            }

            val updated = calculator.update(
                rxBytes = snapshot.rxBytes,
                txBytes = snapshot.txBytes,
                networkType = snapshot.networkType,
                interfaceName = snapshot.interfaceName,
                timestampMs = nowMs,
                dayKey = dayKey,
                isServiceRunning = isServiceActive
            )

            if (nowMs - lastPersistedAtMs >= 2000L) {
                persistDailyTotalsLocked(prefs, dayKey, updated)
                lastPersistedAtMs = nowMs
            }

            updated
        }

        _telemetryFlow.value = telemetry
        return telemetry
    }

    fun flushToDisk(context: Context) {
        val nowMs = System.currentTimeMillis()
        val dayKey = currentLocalDayKey(nowMs)
        val appContext = context.applicationContext
        synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            persistDailyTotalsLocked(prefs, dayKey, _telemetryFlow.value)
            lastPersistedAtMs = nowMs
        }
    }

    private fun seedFromPrefsLocked(prefs: SharedPreferences, todayKey: Long) {
        val savedDay = prefs.getLong(KEY_DAY, -1L)
        if (savedDay == todayKey) {
            calculator.seedDailyTotals(
                dayKey = todayKey,
                wifiRx = prefs.getLong(KEY_WIFI_RX, 0L),
                wifiTx = prefs.getLong(KEY_WIFI_TX, 0L),
                mobileRx = prefs.getLong(KEY_MOBILE_RX, 0L),
                mobileTx = prefs.getLong(KEY_MOBILE_TX, 0L)
            )
        } else {
            calculator.seedDailyTotals(todayKey, 0L, 0L, 0L, 0L)
        }
    }

    private fun persistDailyTotalsLocked(
        prefs: SharedPreferences,
        dayKey: Long,
        telemetry: LiveNetworkTelemetry
    ) {
        prefs.edit()
            .putLong(KEY_DAY, dayKey)
            .putLong(KEY_WIFI_RX, telemetry.todayWifiRxBytes)
            .putLong(KEY_WIFI_TX, telemetry.todayWifiTxBytes)
            .putLong(KEY_MOBILE_RX, telemetry.todayMobileRxBytes)
            .putLong(KEY_MOBILE_TX, telemetry.todayMobileTxBytes)
            .apply()
    }

    private data class CounterSnapshot(
        val rxBytes: Long,
        val txBytes: Long,
        val networkType: ActiveNetworkType,
        val interfaceName: String?
    )

    private fun readSystemNetworkCounters(context: Context): CounterSnapshot {
        // The 1 s cadence is unchanged; the shared cache only collapses duplicate captures taken
        // within the same tick. TrafficStats remains the only counter source here
        // (NetworkStatsManager stays hotspot-only).
        val snapshot = NetworkSnapshotProvider.shared.snapshot(context)
        val physicalNetwork = snapshot.activePhysicalNetwork()
        val networkType = snapshot.activeNetworkType()

        val iface = physicalNetwork?.interfaceName?.takeIf { it.isNotBlank() && !it.startsWith("tun") }
        // Per-interface counters (getRxBytes(String)/getTxBytes(String)) only exist from API 31;
        // calling them on older devices would throw and crash the monitor.
        val perInterfaceCountersAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val ifaceRx = if (iface != null && perInterfaceCountersAvailable) {
            runCatching { TrafficStats.getRxBytes(iface) }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())
        } else {
            TrafficStats.UNSUPPORTED.toLong()
        }
        val ifaceTx = if (iface != null && perInterfaceCountersAvailable) {
            runCatching { TrafficStats.getTxBytes(iface) }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())
        } else {
            TrafficStats.UNSUPPORTED.toLong()
        }

        val unsupported = TrafficStats.UNSUPPORTED.toLong()
        val rxBytes: Long
        val txBytes: Long
        val resolvedIface: String?
        if (ifaceRx != unsupported && ifaceTx != unsupported && ifaceRx >= 0L && ifaceTx >= 0L) {
            rxBytes = ifaceRx
            txBytes = ifaceTx
            resolvedIface = iface
        } else {
            rxBytes = runCatching { TrafficStats.getTotalRxBytes() }.getOrDefault(unsupported)
            txBytes = runCatching { TrafficStats.getTotalTxBytes() }.getOrDefault(unsupported)
            resolvedIface = iface ?: "all"
        }

        return CounterSnapshot(
            rxBytes = rxBytes,
            txBytes = txBytes,
            networkType = networkType,
            interfaceName = resolvedIface
        )
    }

    fun currentLocalDayKey(timestampMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val year = cal.get(Calendar.YEAR).toLong()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR).toLong()
        return year * 1000L + dayOfYear
    }
}
