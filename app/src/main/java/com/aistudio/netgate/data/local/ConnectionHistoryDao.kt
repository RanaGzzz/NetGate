package com.aistudio.netgate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionHistoryDao {

    @Query("SELECT * FROM connection_events ORDER BY lastSeenAt DESC LIMIT :limit")
    fun observeRecentConnections(limit: Int = 250): Flow<List<ConnectionEventEntity>>

    @Query("SELECT COUNT(*) FROM connection_events WHERE riskLevel IN ('SUSPICIOUS', 'CRITICAL')")
    fun observeFlaggedConnectionsCount(): Flow<Int>

    @Query(
        "SELECT * FROM connection_events " +
            "WHERE domain = :domain AND remoteIp = :remoteIp AND appPackage = :appPackage AND actionStatus = :actionStatus " +
            "LIMIT 1"
    )
    suspend fun findMatchingEvent(
        domain: String,
        remoteIp: String,
        appPackage: String,
        actionStatus: String
    ): ConnectionEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ConnectionEventEntity): Long

    @Update
    suspend fun updateEvent(event: ConnectionEventEntity)

    @Query(
        "UPDATE connection_events " +
            "SET countryCode = :countryCode, countryName = :countryName, regionName = :regionName, ispOrOrg = :ispOrOrg " +
            "WHERE remoteIp = :remoteIp AND (countryCode = '??' OR countryCode = '' OR countryName = 'Resolving…' OR countryName = 'Unknown Region')"
    )
    suspend fun backfillGeoForIp(
        remoteIp: String,
        countryCode: String,
        countryName: String,
        regionName: String,
        ispOrOrg: String
    )

    @Query(
        "DELETE FROM connection_events WHERE id NOT IN " +
            "(SELECT id FROM connection_events ORDER BY lastSeenAt DESC LIMIT :keepLimit)"
    )
    suspend fun trimOldEvents(keepLimit: Int = 400)

    @Query("DELETE FROM connection_events")
    suspend fun clearAll()

    /** Merges a coalesced batch in a single transaction instead of one write per connection. */
    @Transaction
    suspend fun recordOrMergeConnections(events: List<ConnectionEventEntity>) {
        for (event in events) {
            recordOrMergeConnection(event)
        }
    }

    @Transaction
    suspend fun recordOrMergeConnection(event: ConnectionEventEntity) {
        val existing = findMatchingEvent(
            domain = event.domain,
            remoteIp = event.remoteIp,
            appPackage = event.appPackage,
            actionStatus = event.actionStatus
        )
        if (existing == null) {
            insertEvent(event)
            trimOldEvents()
        } else {
            val strongerRisk = pickStrongerRisk(existing.riskLevel, event.riskLevel)
            val strongerReason = if (strongerRisk == event.riskLevel && event.riskReason.isNotBlank()) {
                event.riskReason
            } else {
                existing.riskReason.ifBlank { event.riskReason }
            }
            val resolvedCode = if (existing.countryCode != "??" && existing.countryCode.isNotBlank()) {
                existing.countryCode
            } else {
                event.countryCode
            }
            val resolvedCountry = if (existing.countryName.isNotBlank() && existing.countryName != "Resolving…") {
                existing.countryName
            } else {
                event.countryName
            }
            val resolvedRegion = existing.regionName.ifBlank { event.regionName }
            val resolvedIsp = existing.ispOrOrg.ifBlank { event.ispOrOrg }

            updateEvent(
                existing.copy(
                    remotePort = if (event.remotePort > 0) event.remotePort else existing.remotePort,
                    protocol = if (event.protocol == "HTTPS" || event.protocol == "HTTP") event.protocol else existing.protocol,
                    appName = existing.appName.ifBlank { event.appName },
                    countryCode = resolvedCode,
                    countryName = resolvedCountry,
                    regionName = resolvedRegion,
                    ispOrOrg = resolvedIsp,
                    bytesSent = existing.bytesSent + event.bytesSent,
                    bytesReceived = existing.bytesReceived + event.bytesReceived,
                    riskLevel = strongerRisk,
                    riskReason = strongerReason,
                    hitCount = existing.hitCount + event.hitCount.coerceAtLeast(1),
                    lastSeenAt = maxOf(existing.lastSeenAt, event.lastSeenAt)
                )
            )
        }
    }

    private fun pickStrongerRisk(a: String, b: String): String {
        fun rank(r: String): Int = when (r) {
            "CRITICAL" -> 3
            "SUSPICIOUS" -> 2
            "NOTICE" -> 1
            else -> 0
        }
        return if (rank(b) >= rank(a)) b else a
    }
}
