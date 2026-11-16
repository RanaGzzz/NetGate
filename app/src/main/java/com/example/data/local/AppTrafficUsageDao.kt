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
    val totalWifiRxBytes: Long,
    val totalWifiTxBytes: Long,
    val totalMobileRxBytes: Long,
    val totalMobileTxBytes: Long,
    val blockedAppsCount: Int,
    val allowedAppsCount: Int,
    val lastSyncedAt: Long
)

data class AppTrafficWithRuleTuple(
    val packageName: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val periodDays: Int,
    val syncedAt: Long,
    val blockWifi: Boolean,
    val blockMobileData: Boolean
)

data class DailyTrafficHistoryTuple(
    val dateEpochDay: Long,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val appsCount: Int
)

data class HistoricalTotalsTuple(
    val totalWifiRxBytes: Long,
    val totalWifiTxBytes: Long,
    val totalMobileRxBytes: Long,
    val totalMobileTxBytes: Long,
    val daysTrackedCount: Int
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
            COALESCE(SUM(u.wifiRxBytes), 0) AS totalWifiRxBytes,
            COALESCE(SUM(u.wifiTxBytes), 0) AS totalWifiTxBytes,
            COALESCE(SUM(u.mobileRxBytes), 0) AS totalMobileRxBytes,
            COALESCE(SUM(u.mobileTxBytes), 0) AS totalMobileTxBytes,
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
            u.wifiRxBytes AS wifiRxBytes,
            u.wifiTxBytes AS wifiTxBytes,
            u.mobileRxBytes AS mobileRxBytes,
            u.mobileTxBytes AS mobileTxBytes,
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

    @Query(
        """
        SELECT
            dateEpochDay AS dateEpochDay,
            COALESCE(SUM(wifiRxBytes), 0) AS wifiRxBytes,
            COALESCE(SUM(wifiTxBytes), 0) AS wifiTxBytes,
            COALESCE(SUM(mobileRxBytes), 0) AS mobileRxBytes,
            COALESCE(SUM(mobileTxBytes), 0) AS mobileTxBytes,
            COUNT(DISTINCT packageName) AS appsCount
        FROM app_traffic_history
        GROUP BY dateEpochDay
        ORDER BY dateEpochDay DESC
        LIMIT :limit
        """
    )
    fun observeDailyTrafficHistory(limit: Int = 14): Flow<List<DailyTrafficHistoryTuple>>

    @Query(
        """
        SELECT
            COALESCE(SUM(wifiRxBytes), 0) AS totalWifiRxBytes,
            COALESCE(SUM(wifiTxBytes), 0) AS totalWifiTxBytes,
            COALESCE(SUM(mobileRxBytes), 0) AS totalMobileRxBytes,
            COALESCE(SUM(mobileTxBytes), 0) AS totalMobileTxBytes,
            COUNT(DISTINCT dateEpochDay) AS daysTrackedCount
        FROM app_traffic_history
        """
    )
    fun observeHistoricalTotals(): Flow<HistoricalTotalsTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppTrafficUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryEntries(items: List<AppTrafficHistoryEntity>)

    @Query("DELETE FROM app_traffic_usage")
    suspend fun clearAll()

    @Query("DELETE FROM app_traffic_history")
    suspend fun clearHistory()

    @Transaction
    suspend fun replaceSnapshot(items: List<AppTrafficUsageEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
