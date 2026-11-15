package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNetworkRuleDao {
    @Query("SELECT * FROM app_network_rules")
    fun observeAllRules(): Flow<List<AppNetworkRuleEntity>>

    @Query("SELECT * FROM app_network_rules")
    suspend fun getAllRulesOnce(): List<AppNetworkRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: AppNetworkRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<AppNetworkRuleEntity>)

    @Query("DELETE FROM app_network_rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)

    @Query("DELETE FROM app_network_rules")
    suspend fun clearAllRules()
}
