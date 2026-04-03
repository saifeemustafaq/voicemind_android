package com.voicemind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,  // "recording", "actionItem", "folder", "collectiveSummary"
    val entityId: String,
    val createdAt: Long = System.currentTimeMillis(),
)
