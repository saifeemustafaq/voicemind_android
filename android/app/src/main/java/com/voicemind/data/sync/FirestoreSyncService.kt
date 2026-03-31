package com.voicemind.data.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.model.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains a Firestore snapshot listener for the signed-in user's recordings and
 * upserts changes into Room. Cloud-owned fields (transcription, summary, processingFailed)
 * always overwrite local. User-owned fields (title, folderId) only overwrite when the local
 * record is SYNCED (i.e. no pending local edits).
 *
 * Call [startListening] after sign-in and [stopListening] on sign-out.
 */
@Singleton
class FirestoreSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val recordingDao: RecordingDao,
    private val localAudioManager: LocalAudioManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingsListener: ListenerRegistration? = null

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
                    scope.launch { handleChange(change) }
                }
            }
        Timber.d("FirestoreSyncService: started listening for uid=%s", uid)
    }

    fun stopListening() {
        recordingsListener?.remove()
        recordingsListener = null
        Timber.d("FirestoreSyncService: stopped listening")
    }

    private suspend fun handleChange(change: DocumentChange) {
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
                    // New record from cloud — take everything, no local audio yet.
                    cloud.toRoomEntity(localAudioPath = null, syncStatus = SyncStatus.SYNCED)
                } else {
                    // Merge: cloud owns transcription/summary/processingFailed.
                    // User owns title/folderId unless local is already in sync with cloud.
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
}

/** Local helper — avoids a dependency on EntityMappers for the cloud→Room merge path. */
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
