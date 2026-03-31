package com.voicemind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voicemind.data.local.SyncStatus

@Entity(tableName = "collective_summaries")
data class CollectiveSummaryEntity(
    @PrimaryKey val id: String,
    val summary: String,
    /** pipe-separated recording IDs — converted by AppTypeConverters */
    val recordingIds: List<String>,
    /** pipe-separated recording titles — converted by AppTypeConverters */
    val recordingTitles: List<String>,
    /** pipe-separated sharedWith UIDs — converted by AppTypeConverters */
    val sharedWith: List<String>,
    val createdAt: Long?,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)
