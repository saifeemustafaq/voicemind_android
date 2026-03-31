package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Upsert
    suspend fun upsert(entity: RecordingEntity)

    @Upsert
    suspend fun upsertAll(entities: List<RecordingEntity>)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM recordings WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isDeleted = 0 AND folderId = :folderId ORDER BY createdAt DESC")
    fun observeByFolder(folderId: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<RecordingEntity>

    @Query("UPDATE recordings SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE recordings SET localAudioPath = :path WHERE id = :id")
    suspend fun updateLocalAudioPath(id: String, path: String)

    @Query("UPDATE recordings SET title = :title, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, syncStatus: SyncStatus)

    @Query("UPDATE recordings SET folderId = :folderId, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: String, syncStatus: SyncStatus)

    @Query("UPDATE recordings SET transcription = :transcription WHERE id = :id")
    suspend fun updateTranscription(id: String, transcription: String?)

    @Query("UPDATE recordings SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String?)

    @Query("UPDATE recordings SET processingFailed = :failed WHERE id = :id")
    suspend fun updateProcessingFailed(id: String, failed: Boolean)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
