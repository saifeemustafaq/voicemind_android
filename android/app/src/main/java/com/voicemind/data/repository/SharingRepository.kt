package com.voicemind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.model.MyShare
import com.voicemind.data.model.SharedItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private fun uid() = requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid

    private fun sharedWithMeCollection() =
        firestore.collection("users/${uid()}/sharedWithMe")

    private fun mySharesCollection() =
        firestore.collection("users/${uid()}/myShares")

    fun observeSharedWithMe(): Flow<List<SharedItem>> = callbackFlow {
        val registration = sharedWithMeCollection()
            .orderBy("sharedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeSharedWithMe")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(SharedItem::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun observeMyShares(itemId: String): Flow<List<MyShare>> = callbackFlow {
        val registration = mySharesCollection()
            .whereEqualTo("itemId", itemId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeMyShares")
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(MyShare::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun getUnreadCount(): Flow<Int> = callbackFlow {
        val registration = sharedWithMeCollection()
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "getUnreadCount")
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun findUserByEmail(email: String): Map<String, Any> {
        val result = functions
            .getHttpsCallable("findUserByEmail")
            .call(hashMapOf("email" to email))
            .await()
        return result.getData() as? Map<String, Any>
            ?: throw Exception("findUserByEmail returned no data")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun shareItem(itemId: String, itemType: String, recipientUid: String): String {
        val result = functions
            .getHttpsCallable("shareItem")
            .call(hashMapOf("itemId" to itemId, "itemType" to itemType, "recipientUid" to recipientUid))
            .await()
        val data = result.getData() as? Map<String, Any>
            ?: throw Exception("shareItem returned no data")
        return data["shareId"] as? String
            ?: throw Exception("shareItem returned no shareId")
    }

    suspend fun revokeShare(shareId: String, recipientUid: String) {
        functions
            .getHttpsCallable("revokeShare")
            .call(hashMapOf("shareId" to shareId, "recipientUid" to recipientUid))
            .await()
    }

    suspend fun dismissSharedItem(shareId: String) {
        functions
            .getHttpsCallable("dismissSharedItem")
            .call(hashMapOf("shareId" to shareId))
            .await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getSharedAudioUrl(ownerUid: String, recordingId: String): String {
        val result = functions
            .getHttpsCallable("getSharedAudioUrl")
            .call(hashMapOf("ownerUid" to ownerUid, "recordingId" to recordingId))
            .await()
        val data = result.getData() as? Map<String, Any>
            ?: throw Exception("getSharedAudioUrl returned no data")
        return data["url"] as? String
            ?: throw Exception("getSharedAudioUrl returned no url")
    }

    suspend fun markAsRead(shareId: String) {
        sharedWithMeCollection().document(shareId).update("isRead", true).await()
    }
}
