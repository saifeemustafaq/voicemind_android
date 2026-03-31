package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicemind.data.local.SyncStatus
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

    @Query("UPDATE action_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE action_items SET completed = :completed, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateCompleted(id: String, completed: Boolean, syncStatus: SyncStatus)

    @Query("UPDATE action_items SET title = :title, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, syncStatus: SyncStatus)

    @Query("UPDATE action_items SET dueDate = :dueDate, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateDueDate(id: String, dueDate: Long?, syncStatus: SyncStatus)

    @Query("UPDATE action_items SET deadline = :deadline, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateDeadline(id: String, deadline: Long?, syncStatus: SyncStatus)

    @Query("UPDATE action_items SET notes = :notes, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?, syncStatus: SyncStatus)

    @Query("SELECT * FROM action_items WHERE isDeleted = 0 AND recordingId = :recordingId ORDER BY createdAt ASC")
    suspend fun getByRecordingId(recordingId: String): List<ActionItemEntity>
}
