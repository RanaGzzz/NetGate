package com.aistudio.netgate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_network_rules")
data class AppNetworkRuleEntity(
    @PrimaryKey val packageName: String,
    val blockWifi: Boolean,
    val blockMobileData: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)
