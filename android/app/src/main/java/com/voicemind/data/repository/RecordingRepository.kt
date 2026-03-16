package com.voicemind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
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
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/recordings")

    fun observeRecordings(): Flow<List<Recording>> = callbackFlow {
        val registration = collection()
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
        collection().document(recording.id).delete().await()
        try {
            storage.reference.child(recording.audioPath).delete().await()
        } catch (e: Exception) {
            Timber.e("Failed to delete audio file: %s", e.message)
        }
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
        val docs = collection().whereEqualTo("folderId", fromFolderId).get().await()
        docs.forEach { batch.update(it.reference, "folderId", toFolderId) }
        batch.commit().await()
    }

    suspend fun deleteRecordings(recordings: List<Recording>) {
        recordings.forEach { deleteRecording(it) }
    }

    suspend fun moveRecordingsToFolder(recordingIds: List<String>, folderId: String) {
        val batch = firestore.batch()
        recordingIds.forEach { id ->
            batch.update(collection().document(id), "folderId", folderId)
        }
        batch.commit().await()
    }
}
