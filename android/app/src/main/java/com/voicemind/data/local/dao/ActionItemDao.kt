package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicemind.data.local.entity.ActionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionItemDao {

    @Upsert
    suspend fun upsert(entity: ActionItemEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ActionItemEntity>)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM action_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE isDeleted = 0 AND recordingId = :recordingId ORDER BY createdAt ASC")
    fun observeByRecordingId(recordingId: String): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ActionItemEntity?>

    @Query("SELECT * FROM action_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ActionItemEntity?

    @Query("SELECT * FROM action_items WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ActionItemEntity>

    @Query("SELECT * FROM action_items WHERE isDeleted = 0 AND sharedFromUid IS NOT NULL ORDER BY createdAt DESC")
    fun observeSharedTasks(): Flow<List<ActionItemEntity>>

    @Query("DELETE FROM action_items")
    suspend fun deleteAll()
}
