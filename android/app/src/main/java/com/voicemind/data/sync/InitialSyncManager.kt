package com.voicemind.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.local.toEntity
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.AuthRepository
import com.voicemind.data.repository.NavPreferenceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time migration that populates Room from Firestore for existing users on first launch
 * after the local-first migration is deployed.
 *
 * - Detects first run: [NavPreferenceRepository.isInitialSyncComplete] is false AND user is signed in.
 * - Fetches all non-deleted documents from Firestore for all collections and inserts them into Room
 *   with syncStatus = SYNCED.
 * - Marks sync complete in DataStore so it never runs again.
 */
@Singleton
class InitialSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val recordingDao: RecordingDao,
    private val actionItemDao: ActionItemDao,
    private val folderDao: FolderDao,
    private val collectiveSummaryDao: CollectiveSummaryDao,
    private val navPreferenceRepository: NavPreferenceRepository,
) {
    suspend fun runIfNeeded() {
        val uid = authRepository.currentUser?.uid ?: return
        if (navPreferenceRepository.isInitialSyncComplete.first()) return

        try {
            hydrateRecordings(uid)
            hydrateActionItems(uid)
            hydrateFolders(uid)
            hydrateSummaries(uid)

            navPreferenceRepository.setInitialSyncComplete(true)
            Timber.d("InitialSyncManager: initial sync complete for uid=%s", uid)
        } catch (e: Exception) {
            Timber.e(e, "InitialSyncManager: initial sync failed — will retry on next launch")
            // Do not mark complete; next launch will retry.
        }
    }

    private suspend fun hydrateRecordings(uid: String) {
        val snapshot = firestore
            .collection("users/$uid/recordings")
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        val entities = snapshot.toObjects(Recording::class.java).map { recording ->
            RecordingEntity(
                id = recording.id,
                title = recording.title,
                folderId = recording.folderId,
                createdAt = recording.createdAt.toEpochMillis(),
                transcription = recording.transcription,
                summary = recording.summary,
                audioPath = recording.audioPath,
                localAudioPath = null,
                durationSeconds = recording.durationSeconds,
                isDeleted = false,
                deletedAt = null,
                processingFailed = recording.processingFailed,
                syncStatus = SyncStatus.SYNCED,
            )
        }
        recordingDao.upsertAll(entities)
        Timber.d("InitialSyncManager: hydrated %d recordings", entities.size)
    }

    private suspend fun hydrateActionItems(uid: String) {
        val snapshot = firestore
            .collection("users/$uid/actionItems")
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        val entities = snapshot.toObjects(ActionItem::class.java)
            .map { it.toEntity(SyncStatus.SYNCED) }
        actionItemDao.upsertAll(entities)
        Timber.d("InitialSyncManager: hydrated %d actionItems", entities.size)
    }

    private suspend fun hydrateFolders(uid: String) {
        val snapshot = firestore
            .collection("users/$uid/folders")
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        val entities = snapshot.toObjects(Folder::class.java)
            .map { it.toEntity(SyncStatus.SYNCED) }
        folderDao.upsertAll(entities)
        Timber.d("InitialSyncManager: hydrated %d folders", entities.size)
    }

    private suspend fun hydrateSummaries(uid: String) {
        val snapshot = firestore
            .collection("users/$uid/collectiveSummaries")
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        val entities = snapshot.toObjects(CollectiveSummary::class.java)
            .map { it.toEntity(SyncStatus.SYNCED) }
        collectiveSummaryDao.upsertAll(entities)
        Timber.d("InitialSyncManager: hydrated %d collectiveSummaries", entities.size)
    }
}
