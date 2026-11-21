package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnGuardProfileDao {
    @Query("SELECT * FROM vpn_guard_profiles ORDER BY lastConnectedAt DESC, createdAt DESC")
    fun observeAllProfiles(): Flow<List<VpnGuardProfileEntity>>

    @Query("SELECT * FROM vpn_guard_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): VpnGuardProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: VpnGuardProfileEntity)

    @Query("UPDATE vpn_guard_profiles SET routingMode = :routingMode, targetPackagesCsv = :packagesCsv WHERE id = :id")
    suspend fun updateRoutingScope(id: String, routingMode: String, packagesCsv: String)

    @Query("UPDATE vpn_guard_profiles SET lastConnectedAt = :timestampMs WHERE id = :id")
    suspend fun markConnectedAt(id: String, timestampMs: Long)

    @Query("DELETE FROM vpn_guard_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
}
