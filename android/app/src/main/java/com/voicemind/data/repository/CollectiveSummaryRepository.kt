package com.voicemind.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.model.CollectiveSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectiveSummaryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/collectiveSummaries")

    fun observeSummaries(): Flow<List<CollectiveSummary>> = callbackFlow {
        val registration = collection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeSummaries")
                    return@addSnapshotListener
                }
                val summaries = snapshot?.toObjects(CollectiveSummary::class.java) ?: emptyList()
                trySend(summaries)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getSummary(summaryId: String): CollectiveSummary? {
        return try {
            collection().document(summaryId).get().await()
                .toObject(CollectiveSummary::class.java)
        } catch (e: Exception) {
            Timber.e(e, "getSummary")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun generateCollectiveSummary(recordingIds: List<String>): CollectiveSummary {
        val result = functions
            .getHttpsCallable("generateCollectiveSummary")
            .call(hashMapOf("recordingIds" to recordingIds))
            .await()
        val data = result.getData() as? Map<*, *>
            ?: throw Exception("Collective summary generation failed")
        val summaryId = data["summaryId"] as? String
            ?: throw Exception("Collective summary generation failed: missing summaryId")
        return getSummary(summaryId)
            ?: throw Exception("Collective summary generation failed: could not fetch summary")
    }

    suspend fun deleteSummary(summaryId: String) {
        collection().document(summaryId).delete().await()
    }

    suspend fun getSharedSummary(ownerUid: String, summaryId: String): CollectiveSummary? = try {
        firestore.document("users/$ownerUid/collectiveSummaries/$summaryId")
            .get().await().toObject(CollectiveSummary::class.java)
    } catch (e: Exception) {
        Timber.e(e, "getSharedSummary")
        null
    }

    fun observeSharedSummary(ownerUid: String, summaryId: String): Flow<CollectiveSummary?> = callbackFlow {
        val registration = firestore
            .document("users/$ownerUid/collectiveSummaries/$summaryId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Timber.e(error, "observeSharedSummary"); return@addSnapshotListener }
                trySend(snapshot?.toObject(CollectiveSummary::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun duplicateSharedSummary(ownerUid: String, summaryId: String) {
        val docId = "shared-$ownerUid-$summaryId"
        if (collection().document(docId).get().await().exists()) return
        val source = firestore.document("users/$ownerUid/collectiveSummaries/$summaryId")
            .get().await().toObject(CollectiveSummary::class.java)
            ?: throw Exception("Summary not found")
        collection().document(docId).set(mapOf(
            "summary" to source.summary,
            "recordingTitles" to source.recordingTitles,
            "recordingIds" to emptyList<String>(),
            "createdAt" to Timestamp.now(),
        )).await()
    }
}
