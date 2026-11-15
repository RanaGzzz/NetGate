package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class BlockedVsAllowedSummaryTuple(
    val blockedTotalBytes: Long,
    val blockedWifiBytes: Long,
    val blockedMobileBytes: Long,
    val allowedTotalBytes: Long,
    val allowedWifiBytes: Long,
    val allowedMobileBytes: Long,
    val blockedAppsCount: Int,
    val allowedAppsCount: Int,
    val lastSyncedAt: Long
)

data class AppTrafficWithRuleTuple(
    val packageName: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val periodDays: Int,
    val syncedAt: Long,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
)

@Dao
interface AppTrafficUsageDao {

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 1 OR COALESCE(r.blockMobileData, 0) = 1) THEN (u.wifiBytes + u.mobileBytes) ELSE 0 END), 0) AS blockedTotalBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 1 OR COALESCE(r.blockMobileData, 0) = 1) THEN u.wifiBytes ELSE 0 END), 0) AS blockedWifiBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 1 OR COALESCE(r.blockMobileData, 0) = 1) THEN u.mobileBytes ELSE 0 END), 0) AS blockedMobileBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 0 AND COALESCE(r.blockMobileData, 0) = 0) THEN (u.wifiBytes + u.mobileBytes) ELSE 0 END), 0) AS allowedTotalBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 0 AND COALESCE(r.blockMobileData, 0) = 0) THEN u.wifiBytes ELSE 0 END), 0) AS allowedWifiBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 0 AND COALESCE(r.blockMobileData, 0) = 0) THEN u.mobileBytes ELSE 0 END), 0) AS allowedMobileBytes,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 1 OR COALESCE(r.blockMobileData, 0) = 1) THEN 1 ELSE 0 END), 0) AS blockedAppsCount,
            COALESCE(SUM(CASE WHEN (COALESCE(r.blockWifi, 0) = 0 AND COALESCE(r.blockMobileData, 0) = 0) THEN 1 ELSE 0 END), 0) AS allowedAppsCount,
            COALESCE(MAX(u.syncedAt), 0) AS lastSyncedAt
        FROM app_traffic_usage u
        LEFT JOIN app_network_rules r ON u.packageName = r.packageName
        """
    )
    fun observeBlockedVsAllowedSummary(): Flow<BlockedVsAllowedSummaryTuple>

    @Query(
        """
        SELECT
            u.packageName AS packageName,
            u.wifiBytes AS wifiBytes,
            u.mobileBytes AS mobileBytes,
            u.periodDays AS periodDays,
            u.syncedAt AS syncedAt,
            COALESCE(r.blockWifi, 0) AS blockWifi,
            COALESCE(r.blockMobileData, 0) AS blockMobileData
        FROM app_traffic_usage u
        LEFT JOIN app_network_rules r ON u.packageName = r.packageName
        ORDER BY (u.wifiBytes + u.mobileBytes) DESC
        """
    )
    fun observeUsageJoinedWithRules(): Flow<List<AppTrafficWithRuleTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppTrafficUsageEntity>)

    @Query("DELETE FROM app_traffic_usage")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceSnapshot(items: List<AppTrafficUsageEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
