package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ad_filter_lists")
data class AdFilterListEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceUrl: String,
    val category: String,
    val isEnabled: Boolean,
    val domainCount: Int = 0,
    val lastSyncedAt: Long = 0L
)
