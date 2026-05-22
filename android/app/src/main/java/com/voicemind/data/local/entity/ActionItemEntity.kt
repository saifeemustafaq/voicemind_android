package com.voicemind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voicemind.data.local.SyncStatus

@Entity(tableName = "action_items")
data class ActionItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val completed: Boolean,
    val recordingId: String?,
    val createdAt: Long?,
    val dueDate: Long?,
    val deadline: Long?,
    val notes: String?,
    val googleTaskId: String?,
    val calendarEventId: String?,
    val autoScheduled: Boolean,
    val sharedFromUid: String?,
    val sharedFromName: String?,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)
