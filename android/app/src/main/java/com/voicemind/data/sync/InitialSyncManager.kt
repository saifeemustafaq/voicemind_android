package com.voicemind.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.local.entity.RecordingEntity
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
 * - Fetches all non-deleted recordings from Firestore and inserts them into Room with
 *   syncStatus = SYNCED and localAudioPath = null (audio is fetched on demand in Phase 5).
 * - Marks sync complete in DataStore so it never runs again.
 */
@Singleton
class InitialSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val recordingDao: RecordingDao,
    private val navPreferenceRepository: NavPreferenceRepository,
) {
    suspend fun runIfNeeded() {
        val uid = authRepository.currentUser?.uid ?: return
        if (navPreferenceRepository.isInitialSyncComplete.first()) return

        try {
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
            navPreferenceRepository.setInitialSyncComplete(true)
            Timber.d("InitialSyncManager: hydrated %d recordings into Room", entities.size)
        } catch (e: Exception) {
            Timber.e(e, "InitialSyncManager: initial sync failed — will retry on next launch")
            // Do not mark complete; next launch will retry.
        }
    }
}
