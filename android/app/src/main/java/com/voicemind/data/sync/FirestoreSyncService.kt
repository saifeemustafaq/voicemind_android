package com.voicemind.data.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.data.local.entity.FolderEntity
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.local.toEntity
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains Firestore snapshot listeners for the signed-in user's collections and upserts
 * changes into Room. Call [startListening] after sign-in and [stopListening] on sign-out.
 *
 * Conflict rules:
 * - Cloud-owned fields always overwrite Room (transcription, summary, processingFailed,
 *   googleTaskId, calendarEventId, autoScheduled on action items; all fields on summaries).
 * - User-owned fields (title, folderId, completed, notes, dueDate, deadline, folder name)
 *   only overwrite if local syncStatus == SYNCED (no pending local edits).
 */
@Singleton
class FirestoreSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val recordingDao: RecordingDao,
    private val actionItemDao: ActionItemDao,
    private val folderDao: FolderDao,
    private val collectiveSummaryDao: CollectiveSummaryDao,
    private val localAudioManager: LocalAudioManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recordingsListener: ListenerRegistration? = null
    private var actionItemsListener: ListenerRegistration? = null
    private var foldersListener: ListenerRegistration? = null
    private var summariesListener: ListenerRegistration? = null

    fun startListening(uid: String) {
        stopListening()

        recordingsListener = firestore
            .collection("users/$uid/recordings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "FirestoreSyncService: recordingsListener error")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    scope.launch { handleRecordingChange(change) }
                }
            }

        actionItemsListener = firestore
            .collection("users/$uid/actionItems")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "FirestoreSyncService: actionItemsListener error")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    scope.launch { handleActionItemChange(change) }
                }
            }

        foldersListener = firestore
            .collection("users/$uid/folders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "FirestoreSyncService: foldersListener error")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    scope.launch { handleFolderChange(change) }
                }
            }

        summariesListener = firestore
            .collection("users/$uid/collectiveSummaries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "FirestoreSyncService: summariesListener error")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    scope.launch { handleSummaryChange(change) }
                }
            }

        Timber.d("FirestoreSyncService: started listening for uid=%s", uid)
    }

    fun stopListening() {
        recordingsListener?.remove()
        actionItemsListener?.remove()
        foldersListener?.remove()
        summariesListener?.remove()
        recordingsListener = null
        actionItemsListener = null
        foldersListener = null
        summariesListener = null
        Timber.d("FirestoreSyncService: stopped listening")
    }

    // ── Recordings ────────────────────────────────────────────────────────────

    private suspend fun handleRecordingChange(change: DocumentChange) {
        when (change.type) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                val cloud = change.document.toObject(Recording::class.java)
                if (cloud.isDeleted) {
                    recordingDao.hardDelete(cloud.id)
                    localAudioManager.deleteAudio(cloud.id)
                    return
                }
                val existing = recordingDao.getById(cloud.id)
                val entity = if (existing == null) {
                    cloud.toRoomEntity(localAudioPath = null, syncStatus = SyncStatus.SYNCED)
                } else {
                    val keepLocalEdits = existing.syncStatus != SyncStatus.SYNCED
                    RecordingEntity(
                        id = existing.id,
                        title = if (keepLocalEdits) existing.title else cloud.title,
                        folderId = if (keepLocalEdits) existing.folderId else cloud.folderId,
                        createdAt = existing.createdAt ?: cloud.createdAt.toEpochMillis(),
                        transcription = cloud.transcription,
                        summary = cloud.summary,
                        audioPath = cloud.audioPath,
                        localAudioPath = existing.localAudioPath,
                        durationSeconds = existing.durationSeconds,
                        isDeleted = false,
                        deletedAt = null,
                        processingFailed = cloud.processingFailed,
                        syncStatus = existing.syncStatus,
                    )
                }
                recordingDao.upsert(entity)
            }
            DocumentChange.Type.REMOVED -> {
                recordingDao.hardDelete(change.document.id)
                localAudioManager.deleteAudio(change.document.id)
            }
        }
    }

    // ── Action Items ──────────────────────────────────────────────────────────

    private suspend fun handleActionItemChange(change: DocumentChange) {
        when (change.type) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                val cloud = change.document.toObject(ActionItem::class.java)
                if (cloud.isDeleted) {
                    actionItemDao.hardDelete(cloud.id)
                    return
                }
                val existing = actionItemDao.getById(cloud.id)
                val entity = if (existing == null) {
                    cloud.toEntity(SyncStatus.SYNCED)
                } else {
                    val keepLocalEdits = existing.syncStatus != SyncStatus.SYNCED
                    ActionItemEntity(
                        id = existing.id,
                        // User-owned: only overwrite if SYNCED (no pending local edits).
                        title = if (keepLocalEdits) existing.title else cloud.title,
                        completed = if (keepLocalEdits) existing.completed else cloud.completed,
                        notes = if (keepLocalEdits) existing.notes else cloud.notes,
                        dueDate = if (keepLocalEdits) existing.dueDate else cloud.dueDate.toEpochMillis(),
                        deadline = if (keepLocalEdits) existing.deadline else cloud.deadline.toEpochMillis(),
                        // Cloud-owned: always overwrite.
                        googleTaskId = cloud.googleTaskId,
                        calendarEventId = cloud.calendarEventId,
                        autoScheduled = cloud.autoScheduled,
                        // Preserved as-is.
                        recordingId = existing.recordingId ?: cloud.recordingId,
                        createdAt = existing.createdAt ?: cloud.createdAt.toEpochMillis(),
                        sharedFromUid = existing.sharedFromUid ?: cloud.sharedFromUid,
                        sharedFromName = existing.sharedFromName ?: cloud.sharedFromName,
                        isDeleted = false,
                        deletedAt = null,
                        syncStatus = existing.syncStatus,
                    )
                }
                actionItemDao.upsert(entity)
            }
            DocumentChange.Type.REMOVED -> actionItemDao.hardDelete(change.document.id)
        }
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    private suspend fun handleFolderChange(change: DocumentChange) {
        when (change.type) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                val cloud = change.document.toObject(Folder::class.java)
                if (cloud.isDeleted) {
                    folderDao.hardDelete(cloud.id)
                    return
                }
                val existing = folderDao.getById(cloud.id)
                val entity = if (existing == null) {
                    cloud.toEntity(SyncStatus.SYNCED)
                } else {
                    val keepLocalEdits = existing.syncStatus != SyncStatus.SYNCED
                    FolderEntity(
                        id = existing.id,
                        name = if (keepLocalEdits) existing.name else cloud.name,
                        createdAt = existing.createdAt ?: cloud.createdAt.toEpochMillis(),
                        isDeleted = false,
                        deletedAt = null,
                        syncStatus = existing.syncStatus,
                    )
                }
                folderDao.upsert(entity)
            }
            DocumentChange.Type.REMOVED -> folderDao.hardDelete(change.document.id)
        }
    }

    // ── Collective Summaries ──────────────────────────────────────────────────

    private suspend fun handleSummaryChange(change: DocumentChange) {
        when (change.type) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                val cloud = change.document.toObject(CollectiveSummary::class.java)
                if (cloud.isDeleted) {
                    collectiveSummaryDao.hardDelete(cloud.id)
                    return
                }
                // Summaries are entirely cloud-owned — always overwrite.
                collectiveSummaryDao.upsert(cloud.toEntity(SyncStatus.SYNCED))
            }
            DocumentChange.Type.REMOVED -> collectiveSummaryDao.hardDelete(change.document.id)
        }
    }
}

/** Local helper — avoids a dependency on EntityMappers for the cloud→Room recording merge path. */
private fun Recording.toRoomEntity(
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
