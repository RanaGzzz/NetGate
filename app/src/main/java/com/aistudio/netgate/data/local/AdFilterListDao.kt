package com.aistudio.netgate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdFilterListDao {
    @Query("SELECT * FROM ad_filter_lists")
    fun observeAllFilterLists(): Flow<List<AdFilterListEntity>>

    @Query("SELECT * FROM ad_filter_lists")
    suspend fun getAllFilterListsOnce(): List<AdFilterListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilterLists(lists: List<AdFilterListEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingFilterLists(lists: List<AdFilterListEntity>)

    @Query("UPDATE ad_filter_lists SET isEnabled = :enabled WHERE id = :id")
    suspend fun setListEnabled(id: String, enabled: Boolean)

    @Query("UPDATE ad_filter_lists SET domainCount = :domainCount, lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun updateSyncStats(id: String, domainCount: Int, syncedAt: Long)

    @Query("DELETE FROM ad_filter_lists WHERE id = :id")
    suspend fun deleteFilterList(id: String)

    @Query("DELETE FROM ad_filter_lists WHERE id IN (:ids)")
    suspend fun deleteFilterListsByIds(ids: List<String>)
}
