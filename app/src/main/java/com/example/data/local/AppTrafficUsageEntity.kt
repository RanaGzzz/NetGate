package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_traffic_usage")
data class AppTrafficUsageEntity(
    @PrimaryKey val packageName: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val periodDays: Int,
    val syncedAt: Long
)
