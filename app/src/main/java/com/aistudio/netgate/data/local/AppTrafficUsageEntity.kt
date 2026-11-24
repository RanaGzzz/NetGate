package com.aistudio.netgate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_traffic_usage")
data class AppTrafficUsageEntity(
    @PrimaryKey val packageName: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val periodDays: Int,
    val syncedAt: Long
)
