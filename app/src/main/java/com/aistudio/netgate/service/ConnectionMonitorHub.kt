package com.aistudio.netgate.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.aistudio.netgate.data.local.ConnectionEventEntity
import com.aistudio.netgate.data.local.ConnectionHistoryDao
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.model.ConnectionRiskLevel
import com.aistudio.netgate.data.model.SiteConnectionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates live per-site connection capture, incoming/outgoing byte attribution,
 * GeoIP country/region resolution, security risk tagging, and Room persistence.
 */
object ConnectionMonitorHub {

    private const val MAX_LIVE_ITEMS = 120
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val liveRecords = ArrayDeque<SiteConnectionRecord>()
    private val appNameCache = HashMap<String, String>()

    private const val FLUSH_INTERVAL_MS = 2_000L
    private const val MAX_PENDING_EVENTS = 400
    private val pendingLock = Any()
    private val pendingEvents = java.util.LinkedHashMap<String, ConnectionEventEntity>()
    private val flushScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var daoRef: ConnectionHistoryDao? = null

    private val _liveConnectionsFlow = MutableStateFlow<List<SiteConnectionRecord>>(emptyList())
    val liveConnectionsFlow: StateFlow<List<SiteConnectionRecord>> = _liveConnectionsFlow.asStateFlow()

    fun initDao(context: Context): ConnectionHistoryDao {
        return daoRef ?: synchronized(this) {
            daoRef ?: NetGateDatabase.getInstance(context.applicationContext)
                .connectionHistoryDao()
                .also { daoRef = it }
        }
    }

    fun clearLiveFeed() {
        synchronized(lock) {
            liveRecords.clear()
            _liveConnectionsFlow.value = emptyList()
        }
    }

    /** Flushes buffered connection events, e.g. when the tunnel stops. */
    fun flushNow() {
        flushScheduled.set(false)
        scope.launch { flushPendingConnections() }
    }

    fun recordConnection(
        context: Context,
        domain: String,
        remoteIp: String,
        remotePort: Int,
        protocol: String,
        appPackage: String = "",
        bytesSent: Long = 0L,
        bytesReceived: Long = 0L,
        actionStatus: String = "ALLOWED",
        isAdBlocked: Boolean = false,
        dpiBypassed: Boolean = false
    ) {
        val cleanDomain = domain.trim().trimEnd('.').lowercase()
        if (cleanDomain.isEmpty() && remoteIp.isBlank()) return

        val appContext = context.applicationContext
        val dao = initDao(appContext)
        val cleanIp = remoteIp.trim()
        val now = System.currentTimeMillis()
        val resolvedAppName = if (appPackage.isNotBlank()) resolveAppLabel(appContext, appPackage) else ""

        val geo = GeoIpResolver.resolveImmediate(cleanIp, cleanDomain)
        val (riskLevel, riskReason) = SecurityAuditEngine.assessConnectionRisk(
            domain = cleanDomain.ifEmpty { cleanIp },
            remoteIp = cleanIp,
            remotePort = remotePort,
            protocol = protocol,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            actionStatus = actionStatus,
            countryCode = geo.countryCode,
            isAdBlocked = isAdBlocked
        )

        val record = SiteConnectionRecord(
            domain = cleanDomain.ifEmpty { cleanIp },
            remoteIp = cleanIp,
            remotePort = remotePort,
            protocol = protocol,
            appPackage = appPackage,
            appName = resolvedAppName,
            countryCode = geo.countryCode,
            countryName = geo.countryName,
            regionName = geo.regionName,
            ispOrOrg = geo.ispOrOrg,
            bytesSent = bytesSent.coerceAtLeast(0L),
            bytesReceived = bytesReceived.coerceAtLeast(0L),
            actionStatus = actionStatus,
            isAdBlocked = isAdBlocked,
            dpiBypassed = dpiBypassed,
            riskLevel = riskLevel,
            riskReason = riskReason,
            hitCount = 1,
            firstSeenAt = now,
            lastSeenAt = now
        )

        updateLiveDeque(record)

        // Persisting used to mean a Room write (plus a trim query) per connection event, on every
        // proxied request. Events are now merged in memory and flushed in one transaction.
        enqueueForPersistence(
            ConnectionEventEntity(
                domain = record.domain,
                remoteIp = record.remoteIp,
                remotePort = record.remotePort,
                protocol = record.protocol,
                appPackage = record.appPackage,
                appName = record.appName,
                countryCode = record.countryCode,
                countryName = record.countryName,
                regionName = record.regionName,
                ispOrOrg = record.ispOrOrg,
                bytesSent = record.bytesSent,
                bytesReceived = record.bytesReceived,
                actionStatus = record.actionStatus,
                isAdBlocked = record.isAdBlocked,
                dpiBypassed = record.dpiBypassed,
                riskLevel = record.riskLevel.name,
                riskReason = record.riskReason,
                hitCount = 1,
                firstSeenAt = now,
                lastSeenAt = now
            )
        )

        if (GeoIpResolver.shouldFetchOnline(cleanIp)) {
            scope.launch {
                runCatching {
                    val onlineGeo = GeoIpResolver.lookupOnlineBlocking(cleanIp)
                    if (onlineGeo != null) {
                        // Make sure the row exists before the backfill UPDATE runs against it.
                        flushPendingConnections()
                        applyGeoBackfill(dao, cleanIp, onlineGeo)
                    }
                }
            }
        }
    }

    private fun eventKey(event: ConnectionEventEntity): String =
        "${event.domain}|${event.remoteIp}|${event.remotePort}|${event.appPackage}|${event.actionStatus}"

    private fun enqueueForPersistence(event: ConnectionEventEntity) {
        var forceFlush = false
        synchronized(pendingLock) {
            val key = eventKey(event)
            val existing = pendingEvents[key]
            pendingEvents[key] = if (existing == null) {
                event
            } else {
                existing.copy(
                    remotePort = if (event.remotePort > 0) event.remotePort else existing.remotePort,
                    bytesSent = existing.bytesSent + event.bytesSent,
                    bytesReceived = existing.bytesReceived + event.bytesReceived,
                    hitCount = existing.hitCount + event.hitCount,
                    lastSeenAt = maxOf(existing.lastSeenAt, event.lastSeenAt)
                )
            }
            forceFlush = pendingEvents.size >= MAX_PENDING_EVENTS
        }
        if (forceFlush) {
            scope.launch { flushPendingConnections() }
        } else {
            schedulePendingFlush()
        }
    }

    private fun schedulePendingFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(FLUSH_INTERVAL_MS)
            flushScheduled.set(false)
            flushPendingConnections()
        }
    }

    private suspend fun flushPendingConnections() {
        val dao = daoRef ?: return
        val batch = synchronized(pendingLock) {
            if (pendingEvents.isEmpty()) {
                emptyList()
            } else {
                pendingEvents.values.toList().also { pendingEvents.clear() }
            }
        }
        if (batch.isEmpty()) return
        runCatching { dao.recordOrMergeConnections(batch) }
            .onSuccess {
                synchronized(pendingLock) {
                    if (pendingEvents.isNotEmpty() && !flushScheduled.get()) {
                        schedulePendingFlush()
                    }
                }
            }
    }

    private fun updateLiveDeque(incoming: SiteConnectionRecord) {
        synchronized(lock) {
            val existingIdx = liveRecords.indexOfFirst {
                it.domain == incoming.domain &&
                    it.remoteIp == incoming.remoteIp &&
                    it.appPackage == incoming.appPackage &&
                    it.actionStatus == incoming.actionStatus
            }
            if (existingIdx >= 0) {
                val existing = liveRecords.elementAt(existingIdx)
                liveRecords.removeAt(existingIdx)
                val mergedSent = existing.bytesSent + incoming.bytesSent
                val mergedReceived = existing.bytesReceived + incoming.bytesReceived
                val strongerRisk = if (incoming.riskLevel.ordinal >= existing.riskLevel.ordinal) {
                    incoming.riskLevel
                } else {
                    existing.riskLevel
                }
                val updated = existing.copy(
                    remotePort = if (incoming.remotePort > 0) incoming.remotePort else existing.remotePort,
                    protocol = if (incoming.protocol == "HTTPS" || incoming.protocol == "HTTP") incoming.protocol else existing.protocol,
                    appName = existing.appName.ifBlank { incoming.appName },
                    countryCode = if (existing.countryCode != "??") existing.countryCode else incoming.countryCode,
                    countryName = if (existing.countryName != "Resolving…") existing.countryName else incoming.countryName,
                    regionName = existing.regionName.ifBlank { incoming.regionName },
                    ispOrOrg = existing.ispOrOrg.ifBlank { incoming.ispOrOrg },
                    bytesSent = mergedSent,
                    bytesReceived = mergedReceived,
                    riskLevel = strongerRisk,
                    riskReason = if (strongerRisk == incoming.riskLevel && incoming.riskReason.isNotBlank()) {
                        incoming.riskReason
                    } else {
                        existing.riskReason
                    },
                    hitCount = existing.hitCount + 1,
                    lastSeenAt = incoming.lastSeenAt
                )
                liveRecords.addFirst(updated)
            } else {
                liveRecords.addFirst(incoming)
                while (liveRecords.size > MAX_LIVE_ITEMS) {
                    liveRecords.removeLast()
                }
            }
            _liveConnectionsFlow.value = liveRecords.toList()
        }
    }

    private suspend fun applyGeoBackfill(
        dao: ConnectionHistoryDao,
        ip: String,
        geo: GeoLocationResult
    ) {
        synchronized(lock) {
            var changed = false
            val updatedList = liveRecords.map { item ->
                if (item.remoteIp == ip) {
                    changed = true
                    item.copy(
                        countryCode = geo.countryCode,
                        countryName = geo.countryName,
                        regionName = geo.regionName,
                        ispOrOrg = geo.ispOrOrg
                    )
                } else {
                    item
                }
            }
            if (changed) {
                liveRecords.clear()
                liveRecords.addAll(updatedList)
                _liveConnectionsFlow.value = liveRecords.toList()
            }
        }
        runCatching {
            dao.backfillGeoForIp(
                remoteIp = ip,
                countryCode = geo.countryCode,
                countryName = geo.countryName,
                regionName = geo.regionName,
                ispOrOrg = geo.ispOrOrg
            )
        }
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        synchronized(appNameCache) {
            appNameCache[packageName]?.let { return it }
        }
        val pm = context.packageManager
        val label = runCatching {
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
        synchronized(appNameCache) {
            appNameCache[packageName] = label
        }
        return label
    }

    fun entityToModel(entity: ConnectionEventEntity): SiteConnectionRecord {
        val risk = ConnectionRiskLevel.entries.firstOrNull { it.name == entity.riskLevel }
            ?: ConnectionRiskLevel.SAFE
        return SiteConnectionRecord(
            id = entity.id,
            domain = entity.domain,
            remoteIp = entity.remoteIp,
            remotePort = entity.remotePort,
            protocol = entity.protocol,
            appPackage = entity.appPackage,
            appName = entity.appName,
            countryCode = entity.countryCode,
            countryName = entity.countryName,
            regionName = entity.regionName,
            ispOrOrg = entity.ispOrOrg,
            bytesSent = entity.bytesSent,
            bytesReceived = entity.bytesReceived,
            actionStatus = entity.actionStatus,
            isAdBlocked = entity.isAdBlocked,
            dpiBypassed = entity.dpiBypassed,
            riskLevel = risk,
            riskReason = entity.riskReason,
            hitCount = entity.hitCount,
            firstSeenAt = entity.firstSeenAt,
            lastSeenAt = entity.lastSeenAt
        )
    }
}
