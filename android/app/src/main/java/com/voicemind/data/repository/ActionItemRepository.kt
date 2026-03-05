package com.voicemind.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
}
