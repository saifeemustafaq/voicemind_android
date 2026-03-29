package com.voicemind.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.model.ActionItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionItemRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val functions: FirebaseFunctions,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/actionItems")

    fun observeActionItems(): Flow<List<ActionItem>> = callbackFlow {
        val registration = collection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeActionItems")
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(ActionItem::class.java) ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun toggleCompleted(itemId: String, completed: Boolean) {
        collection().document(itemId).update("completed", completed).await()
    }

    suspend fun deleteItem(itemId: String) {
        collection().document(itemId).delete().await()
    }

    suspend fun deleteItems(itemIds: List<String>) {
        val batch = firestore.batch()
        itemIds.forEach { id -> batch.delete(collection().document(id)) }
        batch.commit().await()
    }

    suspend fun markCompleted(itemIds: List<String>, completed: Boolean) {
        val batch = firestore.batch()
        itemIds.forEach { id ->
            batch.update(collection().document(id), "completed", completed)
        }
        batch.commit().await()
    }

    fun observeActionItem(itemId: String): Flow<ActionItem?> = callbackFlow {
        val registration = collection().document(itemId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeActionItem")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(ActionItem::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun updateTitle(itemId: String, title: String) {
        collection().document(itemId).update("title", title).await()
    }

    suspend fun updateDueDate(itemId: String, dueDate: Timestamp?) {
        collection().document(itemId).update("dueDate", dueDate).await()
    }

    suspend fun updateDeadline(itemId: String, deadline: Timestamp?) {
        collection().document(itemId).update("deadline", deadline).await()
    }

    suspend fun updateNotes(itemId: String, notes: String?) {
        collection().document(itemId).update("notes", notes).await()
    }

    suspend fun createItem(title: String) {
        val data = hashMapOf(
            "title" to title,
            "completed" to false,
            "createdAt" to Timestamp.now(),
        )
        collection().add(data).await()
    }

    suspend fun getByRecordingId(recordingId: String): List<ActionItem> {
        return collection()
            .whereEqualTo("recordingId", recordingId)
            .get().await()
            .toObjects(ActionItem::class.java)
            .sortedBy { it.createdAt }
    }

    suspend fun retryExtractActionItems(recordingId: String, timezone: String): Int {
        val result = functions
            .getHttpsCallable("retryExtractActionItems")
            .call(hashMapOf("recordingId" to recordingId, "timezone" to timezone))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<*, *> ?: return 0
        return when (val raw = data["count"]) {
            is Long -> raw.toInt()
            is Double -> raw.toInt()
            is Int -> raw
            else -> 0
        }
    }

    fun observeActionItemsForRecording(ownerUid: String, recordingId: String): Flow<List<ActionItem>> = callbackFlow {
        val registration = firestore.collection("users/$ownerUid/actionItems")
            .whereEqualTo("recordingId", recordingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeActionItemsForRecording")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(ActionItem::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    suspend fun addSharedTask(
        originalTaskId: String,
        ownerUid: String,
        ownerName: String,
        title: String,
        notes: String?,
        dueDate: Timestamp?,
        deadline: Timestamp?,
        completed: Boolean,
    ) {
        val docId = "shared-$ownerUid-$originalTaskId"
        collection().document(docId).set(mapOf(
            "title" to title,
            "notes" to notes,
            "dueDate" to dueDate,
            "deadline" to deadline,
            "completed" to completed,
            "sharedFromUid" to ownerUid,
            "sharedFromName" to ownerName,
            "createdAt" to Timestamp.now(),
        )).await()
    }

    suspend fun isSharedTaskAdded(originalTaskId: String, ownerUid: String): Boolean {
        val docId = "shared-$ownerUid-$originalTaskId"
        return collection().document(docId).get().await().exists()
    }

    fun observeSharedTasks(): Flow<List<ActionItem>> = callbackFlow {
        val registration = collection()
            .whereNotEqualTo("sharedFromUid", null)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeSharedTasks")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(ActionItem::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }
}
