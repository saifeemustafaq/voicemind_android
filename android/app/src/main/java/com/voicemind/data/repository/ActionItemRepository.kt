package com.voicemind.data.repository

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
        firestore.collection("users/${authRepository.currentUser!!.uid}/actionItems")

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
}
