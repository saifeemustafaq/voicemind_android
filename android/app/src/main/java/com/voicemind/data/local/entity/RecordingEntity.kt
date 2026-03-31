package com.voicemind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voicemind.data.local.SyncStatus

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String,
    /** epoch millis; null until Firestore server timestamp resolves */
    val createdAt: Long?,
    val transcription: String?,
    val summary: String?,
    /** cloud storage path (e.g. users/{uid}/audio/{id}.m4a) */
    val audioPath: String,
    /** absolute local file path; null if not yet saved locally */
    val localAudioPath: String?,
    val durationSeconds: Long,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val processingFailed: Boolean,
    val syncStatus: SyncStatus,
)
