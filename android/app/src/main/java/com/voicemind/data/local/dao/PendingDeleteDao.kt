package com.voicemind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.voicemind.data.local.entity.PendingDeleteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDeleteDao {
    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Query("SELECT entityId FROM pending_deletes WHERE entityType = :type")
    suspend fun getDeletedIds(type: String): List<String>

    @Insert
    suspend fun insert(entity: PendingDeleteEntity)

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_deletes WHERE entityType = :type AND entityId = :entityId")
    suspend fun deleteByEntity(type: String, entityId: String)

    @Query("DELETE FROM pending_deletes")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM pending_deletes WHERE entityType = :type AND entityId = :entityId LIMIT 1)")
    suspend fun exists(type: String, entityId: String): Boolean

    @Query("SELECT COUNT(*) FROM pending_deletes")
    fun observeCount(): Flow<Int>
}
