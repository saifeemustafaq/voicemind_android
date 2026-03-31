package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
