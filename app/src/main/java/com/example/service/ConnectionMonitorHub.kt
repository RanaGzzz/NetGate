package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.local.ConnectionEventEntity
import com.example.data.local.ConnectionHistoryDao
import com.example.data.local.NetGateDatabase
import com.example.data.model.ConnectionRiskLevel
import com.example.data.model.SiteConnectionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        isAdBlocked: Boolean = false
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
            riskLevel = riskLevel,
            riskReason = riskReason,
            hitCount = 1,
            firstSeenAt = now,
            lastSeenAt = now
        )

        updateLiveDeque(record)

        scope.launch {
            runCatching {
                dao.recordOrMergeConnection(
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
                        riskLevel = record.riskLevel.name,
                        riskReason = record.riskReason,
                        hitCount = 1,
                        firstSeenAt = now,
                        lastSeenAt = now
                    )
                )
            }

            if (GeoIpResolver.shouldFetchOnline(cleanIp)) {
                val onlineGeo = GeoIpResolver.lookupOnlineBlocking(cleanIp)
                if (onlineGeo != null) {
                    applyGeoBackfill(dao, cleanIp, onlineGeo)
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
            riskLevel = risk,
            riskReason = entity.riskReason,
            hitCount = entity.hitCount,
            firstSeenAt = entity.firstSeenAt,
            lastSeenAt = entity.lastSeenAt
        )
    }
}
