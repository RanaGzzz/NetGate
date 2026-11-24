package com.aistudio.netgate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppTimerDao {
    @Query("SELECT * FROM app_network_timers ORDER BY expiresAtMillis ASC")
    fun observeAllTimers(): Flow<List<AppTimerEntity>>

    @Query("SELECT * FROM app_network_timers ORDER BY expiresAtMillis ASC")
    suspend fun getAllTimersOnce(): List<AppTimerEntity>

    @Query("SELECT * FROM app_network_timers WHERE expiresAtMillis <= :nowMillis ORDER BY expiresAtMillis ASC")
    suspend fun getDueTimersOnce(nowMillis: Long): List<AppTimerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimer(timer: AppTimerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimers(timers: List<AppTimerEntity>)

    @Query("DELETE FROM app_network_timers WHERE packageName = :packageName")
    suspend fun deleteTimer(packageName: String)

    @Query("DELETE FROM app_network_timers WHERE packageName IN (:packageNames)")
    suspend fun deleteTimers(packageNames: List<String>)

    @Query("DELETE FROM app_network_timers")
    suspend fun clearAllTimers()
}
