package com.aistudio.netgate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteRuleDao {
    @Query("SELECT * FROM website_rules ORDER BY createdAt DESC")
    fun observeAllRules(): Flow<List<WebsiteRuleEntity>>

    @Query("SELECT * FROM website_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesOnce(): List<WebsiteRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: WebsiteRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<WebsiteRuleEntity>)

    @Query("UPDATE website_rules SET isEnabled = :enabled WHERE domain = :domain")
    suspend fun setRuleEnabled(domain: String, enabled: Boolean)

    @Query("DELETE FROM website_rules WHERE domain = :domain")
    suspend fun deleteRule(domain: String)

    @Query("DELETE FROM website_rules WHERE ruleType = :ruleType")
    suspend fun clearRulesByType(ruleType: String)

    @Query("DELETE FROM website_rules WHERE expiresAtMillis > 0 AND expiresAtMillis <= :nowMillis")
    suspend fun deleteExpiredRules(nowMillis: Long): Int

    @Query("DELETE FROM website_rules")
    suspend fun clearAllRules()
}
