package com.voicemind.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.PendingDeleteDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.PendingDeleteEntity
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.local.toModel
import com.voicemind.data.model.Recording
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val recordingDao: RecordingDao,
    private val localAudioManager: LocalAudioManager,
    private val pendingDeleteDao: PendingDeleteDao,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/recordings")

    // ── Reads (Room-first) ────────────────────────────────────────────────────

    fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll().map { entities -> entities.map { it.toModel() } }

    fun observeByFolder(folderId: String): Flow<List<Recording>> =
        recordingDao.observeByFolder(folderId).map { entities -> entities.map { it.toModel() } }

    suspend fun getRecording(recordingId: String): Recording? =
        recordingDao.getById(recordingId)?.toModel()

    // ── Cloud-only write (used by RecordingService + SyncWorker) ─────────────

    suspend fun createRecordingCloud(recording: Recording): String {
        collection().document(recording.id).set(
            mapOf(
                "title" to recording.title,
                "folderId" to recording.folderId,
                "audioPath" to recording.audioPath,
                "transcription" to recording.transcription,
                "isDeleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "durationSeconds" to recording.durationSeconds,
            )
        ).await()
        return recording.id
    }

    // ── Dual writes (Room first, Firestore in background) ────────────────────

    suspend fun updateTitle(recordingId: String, title: String) {
        val trimmed = title.trim().take(25)
        recordingDao.updateTitle(recordingId, trimmed, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(recordingId).update("title", trimmed).await()
            recordingDao.updateSyncStatus(recordingId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun moveToFolder(recordingId: String, folderId: String) {
        recordingDao.updateFolder(recordingId, folderId, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(recordingId).update("folderId", folderId).await()
            recordingDao.updateSyncStatus(recordingId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun deleteRecording(recording: Recording) {
        pendingDeleteDao.insert(PendingDeleteEntity(entityType = "recording", entityId = recording.id))
        recordingDao.hardDelete(recording.id)
        localAudioManager.deleteAudio(recording.id)
        try {
            collection().document(recording.id).update(
                mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            ).await()
            pendingDeleteDao.deleteByEntity("recording", recording.id)
        } catch (_: Exception) {
            // Deleted locally; SyncWorker will push soft-delete when online.
        }
    }

    suspend fun updateProcessingFailed(recordingId: String, failed: Boolean) {
        recordingDao.updateProcessingFailed(recordingId, failed)
        try { collection().document(recordingId).update("processingFailed", failed).await() }
        catch (_: Exception) { }
    }

    // ── Used by SyncWorker to push PENDING_UPDATE to cloud ───────────────────

    suspend fun pushRecordingUpdate(recordingId: String, title: String, folderId: String) {
        collection().document(recordingId)
            .update(mapOf("title" to title, "folderId" to folderId))
            .await()
        recordingDao.updateSyncStatus(recordingId, SyncStatus.SYNCED)
    }

    // ── Bulk operations (Room first, Firestore in background) ─────────────────

    suspend fun deleteRecordings(recordings: List<Recording>) {
        recordings.forEach { deleteRecording(it) }
    }

    suspend fun reassignFolder(fromFolderId: String, toFolderId: String) {
        val affected = recordingDao.observeByFolder(fromFolderId).first()
        affected.forEach { recordingDao.updateFolder(it.id, toFolderId, SyncStatus.PENDING_UPDATE) }
        try {
            val batch = firestore.batch()
            affected.forEach { batch.update(collection().document(it.id), "folderId", toFolderId) }
            batch.commit().await()
            affected.forEach { recordingDao.updateSyncStatus(it.id, SyncStatus.SYNCED) }
        } catch (_: Exception) { /* SyncWorker will retry pending updates */ }
    }

    suspend fun moveRecordingsToFolder(recordingIds: List<String>, folderId: String) {
        recordingIds.forEach { id -> recordingDao.updateFolder(id, folderId, SyncStatus.PENDING_UPDATE) }
        try {
            val batch = firestore.batch()
            recordingIds.forEach { id -> batch.update(collection().document(id), "folderId", folderId) }
            batch.commit().await()
            recordingIds.forEach { id -> recordingDao.updateSyncStatus(id, SyncStatus.SYNCED) }
        } catch (_: Exception) { /* SyncWorker will retry pending updates */ }
    }

    // ── Cloud function calls (unchanged) ─────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun generateSummary(recordingId: String): String {
        val result = functions
            .getHttpsCallable("generateSummary")
            .call(hashMapOf("recordingId" to recordingId))
            .await()
        val data = result.getData() as? Map<*, *>
            ?: throw Exception("Summary generation failed")
        return data["summary"] as? String
            ?: throw Exception("Summary generation failed")
    }

    suspend fun invokeProcessRecording(recordingId: String, timezone: String) {
        functions
            .getHttpsCallable("processRecording")
            .withTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .call(hashMapOf("recordingId" to recordingId, "timezone" to timezone))
            .await()
    }

    // ── Cross-user reads for shared recordings (Firestore, unchanged) ────────

    suspend fun getSharedRecording(ownerUid: String, recordingId: String): Recording? = try {
        firestore.document("users/$ownerUid/recordings/$recordingId")
            .get().await().toObject(Recording::class.java)
    } catch (e: Exception) {
        Timber.e(e, "getSharedRecording")
        null
    }

    fun observeSharedRecording(ownerUid: String, recordingId: String): Flow<Recording?> = callbackFlow {
        val registration = firestore.document("users/$ownerUid/recordings/$recordingId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeSharedRecording")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Recording::class.java))
            }
        awaitClose { registration.remove() }
    }
}
