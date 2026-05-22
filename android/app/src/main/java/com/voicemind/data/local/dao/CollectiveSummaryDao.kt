package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.entity.CollectiveSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectiveSummaryDao {

    @Upsert
    suspend fun upsert(entity: CollectiveSummaryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CollectiveSummaryEntity>)

    @Query("DELETE FROM collective_summaries WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM collective_summaries WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectiveSummaryEntity>>

    @Query("SELECT * FROM collective_summaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CollectiveSummaryEntity?

    @Query("SELECT * FROM collective_summaries WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<CollectiveSummaryEntity>

    @Query("SELECT COUNT(*) FROM collective_summaries WHERE syncStatus != 'SYNCED'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("DELETE FROM collective_summaries")
    suspend fun deleteAll()

    @Query("UPDATE collective_summaries SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)
}
