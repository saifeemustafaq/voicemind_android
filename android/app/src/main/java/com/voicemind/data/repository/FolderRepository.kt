package com.voicemind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.voicemind.data.model.Folder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/folders")

    fun observeFolders(): Flow<List<Folder>> = callbackFlow {
        val registration = collection()
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeFolders")
                    return@addSnapshotListener
                }
                val folders = snapshot?.toObjects(Folder::class.java) ?: emptyList()
                trySend(folders)
            }
        awaitClose { registration.remove() }
    }

    suspend fun seedDefaultsIfEmpty() {
        val existing = collection().get().await()
        if (existing.isEmpty) {
            val batch = firestore.batch()
            Folder.DEFAULTS.forEach { folder ->
                batch.set(
                    collection().document(folder.id),
                    mapOf(
                        "name" to folder.name,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            Timber.d("Seeded default folders")
        }
    }

    suspend fun createFolder(name: String): String {
        val docRef = collection().document()
        docRef.set(mapOf(
            "name" to name,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        )).await()
        return docRef.id
    }

    suspend fun renameFolder(folderId: String, newName: String) {
        if (folderId == Folder.UNFILED_ID) return
        collection().document(folderId).update("name", newName).await()
    }

    suspend fun deleteFolder(folderId: String) {
        if (folderId == Folder.UNFILED_ID) return
        collection().document(folderId).delete().await()
    }
}
