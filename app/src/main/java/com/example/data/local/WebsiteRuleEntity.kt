package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object WebsiteRuleType {
    const val BLOCK = "BLOCK"
    const val ALLOW = "ALLOW"
}

@Entity(tableName = "website_rules")
data class WebsiteRuleEntity(
    @PrimaryKey val domain: String,
    val ruleType: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
