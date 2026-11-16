package com.example.data.local

import androidx.room.Entity

@Entity(
    tableName = "app_traffic_history",
    primaryKeys = ["dateEpochDay", "packageName"]
)
data class AppTrafficHistoryEntity(
    val dateEpochDay: Long,
    val packageName: String,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val recordedAt: Long
) {
    val wifiTotalBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileTotalBytes: Long get() = mobileRxBytes + mobileTxBytes
    val totalBytes: Long get() = wifiTotalBytes + mobileTotalBytes
}
