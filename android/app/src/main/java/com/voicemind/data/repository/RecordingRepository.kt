package com.voicemind.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.model.Recording
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/recordings")

    fun observeRecordings(): Flow<List<Recording>> = callbackFlow {
        val registration = collection()
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeRecordings")
                    return@addSnapshotListener
                }
                val recordings = snapshot?.toObjects(Recording::class.java) ?: emptyList()
                trySend(recordings)
            }
        awaitClose { registration.remove() }
    }

    fun observeByFolder(folderId: String): Flow<List<Recording>> = callbackFlow {
        val registration = collection()
            .whereEqualTo("isDeleted", false)
            .whereEqualTo("folderId", folderId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeByFolder")
                    return@addSnapshotListener
                }
                val recordings = snapshot?.toObjects(Recording::class.java) ?: emptyList()
                trySend(recordings)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createRecording(recording: Recording): String {
        val docRef = collection().document(recording.id)
        docRef.set(
            mapOf(
                "title" to recording.title,
                "folderId" to recording.folderId,
                "audioPath" to recording.audioPath,
                "transcription" to recording.transcription,
                "isDeleted" to false,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "durationSeconds" to recording.durationSeconds,
            )
        ).await()
        return recording.id
    }

    suspend fun updateTitle(recordingId: String, title: String) {
        collection().document(recordingId).update("title", title.trim().take(25)).await()
    }

    suspend fun moveToFolder(recordingId: String, folderId: String) {
        collection().document(recordingId).update("folderId", folderId).await()
    }

    suspend fun deleteRecording(recording: Recording) {
        collection().document(recording.id).update(
            mapOf(
                "isDeleted" to true,
                "deletedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun getRecording(recordingId: String): Recording? {
        return try {
            collection().document(recordingId).get().await()
                .toObject(Recording::class.java)
        } catch (e: Exception) {
            Timber.e(e, "getRecording")
            null
        }
    }

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

    suspend fun reassignFolder(fromFolderId: String, toFolderId: String) {
        val batch = firestore.batch()
        val docs = collection().whereEqualTo("isDeleted", false).whereEqualTo("folderId", fromFolderId).get().await()
        docs.forEach { batch.update(it.reference, "folderId", toFolderId) }
        batch.commit().await()
    }

    suspend fun deleteRecordings(recordings: List<Recording>) {
        recordings.forEach { deleteRecording(it) }
    }

    suspend fun updateProcessingFailed(recordingId: String, failed: Boolean) {
        collection().document(recordingId).update("processingFailed", failed).await()
    }

    suspend fun invokeProcessRecording(recordingId: String, timezone: String) {
        functions
            .getHttpsCallable("processRecording")
            .call(hashMapOf("recordingId" to recordingId, "timezone" to timezone))
            .await()
    }

    suspend fun moveRecordingsToFolder(recordingIds: List<String>, folderId: String) {
        val batch = firestore.batch()
        recordingIds.forEach { id ->
            batch.update(collection().document(id), "folderId", folderId)
        }
        batch.commit().await()
    }

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
