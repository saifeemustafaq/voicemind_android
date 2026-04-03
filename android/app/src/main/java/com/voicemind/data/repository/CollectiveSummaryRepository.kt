package com.voicemind.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.PendingDeleteDao
import com.voicemind.data.local.entity.PendingDeleteEntity
import com.voicemind.data.local.toEntity
import com.voicemind.data.local.toModel
import com.voicemind.data.model.CollectiveSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectiveSummaryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val collectiveSummaryDao: CollectiveSummaryDao,
    private val pendingDeleteDao: PendingDeleteDao,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/collectiveSummaries")

    // ── Reads (Room-first) ────────────────────────────────────────────────────

    fun observeSummaries(): Flow<List<CollectiveSummary>> =
        collectiveSummaryDao.observeAll().map { it.map { e -> e.toModel() } }

    suspend fun getSummary(summaryId: String): CollectiveSummary? =
        collectiveSummaryDao.getById(summaryId)?.toModel()

    // ── Cloud function — result lands in Room via FirestoreSyncService ────────

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

        // FirestoreSyncService may have already inserted it into Room.
        collectiveSummaryDao.getById(summaryId)?.toModel()?.let { return it }

        // Fall back: fetch from Firestore and insert into Room manually.
        val cloudSummary = collection().document(summaryId).get().await()
            .toObject(CollectiveSummary::class.java)
            ?: throw Exception("Collective summary generation failed: could not fetch summary")
        collectiveSummaryDao.upsert(cloudSummary.toEntity(SyncStatus.SYNCED))
        return cloudSummary
    }

    // ── Delete — Room hard-delete + Firestore soft-delete ────────────────────

    suspend fun deleteSummary(summaryId: String) {
        pendingDeleteDao.insert(PendingDeleteEntity(entityType = "collectiveSummary", entityId = summaryId))
        collectiveSummaryDao.hardDelete(summaryId)
        try {
            collection().document(summaryId).update(
                mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            ).await()
            pendingDeleteDao.deleteByEntity("collectiveSummary", summaryId)
        } catch (_: Exception) { /* deleted locally; SyncWorker will push soft-delete */ }
    }

    // ── Unchanged — cross-user reads (Firestore) ──────────────────────────────

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
            "isDeleted" to false,
            "createdAt" to Timestamp.now(),
        )).await()
    }
}
