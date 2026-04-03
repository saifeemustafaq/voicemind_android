package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Upsert
    suspend fun upsert(entity: FolderEntity)

    @Upsert
    suspend fun upsertAll(entities: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<FolderEntity>

    @Query("SELECT COUNT(*) FROM folders WHERE syncStatus != 'SYNCED'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Query("UPDATE folders SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE folders SET name = :name, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateName(id: String, name: String, syncStatus: SyncStatus)

    @Query("SELECT COUNT(*) FROM folders WHERE isDeleted = 0")
    suspend fun getCount(): Int
}
