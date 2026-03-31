package com.voicemind.data.local

import com.google.firebase.Timestamp
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.data.local.entity.CollectiveSummaryEntity
import com.voicemind.data.local.entity.FolderEntity
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording

// ── Timestamp helpers ──────────────────────────────────────────────

fun Timestamp?.toEpochMillis(): Long? =
    this?.let { it.seconds * 1_000 + it.nanoseconds / 1_000_000 }

fun Long?.toTimestamp(): Timestamp? =
    this?.let { Timestamp(it / 1_000, ((it % 1_000) * 1_000_000).toInt()) }

// ── Recording ──────────────────────────────────────────────────────

fun RecordingEntity.toModel() = Recording(
    id = id,
    title = title,
    folderId = folderId,
    createdAt = createdAt.toTimestamp(),
    transcription = transcription,
    summary = summary,
    audioPath = audioPath,
    durationSeconds = durationSeconds,
    isDeleted = isDeleted,
    deletedAt = deletedAt.toTimestamp(),
    processingFailed = processingFailed,
)

fun Recording.toEntity(
    localAudioPath: String?,
    syncStatus: SyncStatus,
) = RecordingEntity(
    id = id,
    title = title,
    folderId = folderId,
    createdAt = createdAt.toEpochMillis(),
    transcription = transcription,
    summary = summary,
    audioPath = audioPath,
    localAudioPath = localAudioPath,
    durationSeconds = durationSeconds,
    isDeleted = isDeleted,
    deletedAt = deletedAt.toEpochMillis(),
    processingFailed = processingFailed,
    syncStatus = syncStatus,
)

// ── ActionItem ─────────────────────────────────────────────────────

fun ActionItemEntity.toModel() = ActionItem(
    id = id,
    title = title,
    completed = completed,
    recordingId = recordingId,
    createdAt = createdAt.toTimestamp(),
    dueDate = dueDate.toTimestamp(),
    deadline = deadline.toTimestamp(),
    notes = notes,
    googleTaskId = googleTaskId,
    calendarEventId = calendarEventId,
    autoScheduled = autoScheduled,
    sharedFromUid = sharedFromUid,
    sharedFromName = sharedFromName,
    isDeleted = isDeleted,
    deletedAt = deletedAt.toTimestamp(),
)

fun ActionItem.toEntity(syncStatus: SyncStatus) = ActionItemEntity(
    id = id,
    title = title,
    completed = completed,
    recordingId = recordingId,
    createdAt = createdAt.toEpochMillis(),
    dueDate = dueDate.toEpochMillis(),
    deadline = deadline.toEpochMillis(),
    notes = notes,
    googleTaskId = googleTaskId,
    calendarEventId = calendarEventId,
    autoScheduled = autoScheduled,
    sharedFromUid = sharedFromUid,
    sharedFromName = sharedFromName,
    isDeleted = isDeleted,
    deletedAt = deletedAt.toEpochMillis(),
    syncStatus = syncStatus,
)

// ── Folder ─────────────────────────────────────────────────────────

fun FolderEntity.toModel() = Folder(
    id = id,
    name = name,
    createdAt = createdAt.toTimestamp(),
    isDeleted = isDeleted,
    deletedAt = deletedAt.toTimestamp(),
)

fun Folder.toEntity(syncStatus: SyncStatus) = FolderEntity(
    id = id,
    name = name,
    createdAt = createdAt.toEpochMillis(),
    isDeleted = isDeleted,
    deletedAt = deletedAt.toEpochMillis(),
    syncStatus = syncStatus,
)

// ── CollectiveSummary ──────────────────────────────────────────────

fun CollectiveSummaryEntity.toModel() = CollectiveSummary(
    id = id,
    summary = summary,
    recordingIds = recordingIds,
    recordingTitles = recordingTitles,
    sharedWith = sharedWith,
    createdAt = createdAt.toTimestamp(),
    isDeleted = isDeleted,
    deletedAt = deletedAt.toTimestamp(),
)

fun CollectiveSummary.toEntity(syncStatus: SyncStatus) = CollectiveSummaryEntity(
    id = id,
    summary = summary,
    recordingIds = recordingIds,
    recordingTitles = recordingTitles,
    sharedWith = sharedWith,
    createdAt = createdAt.toEpochMillis(),
    isDeleted = isDeleted,
    deletedAt = deletedAt.toEpochMillis(),
    syncStatus = syncStatus,
)
