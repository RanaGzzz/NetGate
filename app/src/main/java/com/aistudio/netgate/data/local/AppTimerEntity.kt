package com.aistudio.netgate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_network_timers")
data class AppTimerEntity(
    @PrimaryKey val packageName: String,
    val blockWifiOnExpire: Boolean,
    val blockMobileDataOnExpire: Boolean,
    val durationMillis: Long,
    val expiresAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)
