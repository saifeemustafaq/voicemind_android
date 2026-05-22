package com.voicemind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voicemind.data.local.SyncStatus

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long?,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)
