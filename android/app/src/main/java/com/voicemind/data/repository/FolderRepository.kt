package com.voicemind.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.toTimestamp
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.PendingDeleteDao
import com.voicemind.data.local.entity.FolderEntity
import com.voicemind.data.local.entity.PendingDeleteEntity
import com.voicemind.data.local.toModel
import com.voicemind.data.model.Folder
import com.voicemind.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val folderDao: FolderDao,
    private val syncScheduler: SyncScheduler,
    private val pendingDeleteDao: PendingDeleteDao,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/folders")

    // ── Reads (Room-first) ────────────────────────────────────────────────────

    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { it.map { e -> e.toModel() } }

    // ── Writes — dual-write (Room first, Firestore background) ───────────────

    suspend fun createFolder(name: String): String {
        val docId = collection().document().id
        folderDao.upsert(FolderEntity(
            id = docId,
            name = name,
            createdAt = System.currentTimeMillis(),
            isDeleted = false,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_UPLOAD,
        ))
        try {
            collection().document(docId).set(mapOf(
                "name" to name,
                "isDeleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
            )).await()
            folderDao.updateSyncStatus(docId, SyncStatus.SYNCED)
        } catch (_: Exception) {
            syncScheduler.enqueueSync()
        }
        return docId
    }

    suspend fun renameFolder(folderId: String, newName: String) {
        if (folderId == Folder.UNFILED_ID) return
        folderDao.updateName(folderId, newName, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(folderId).update("name", newName).await()
            folderDao.updateSyncStatus(folderId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun deleteFolder(folderId: String) {
        if (folderId == Folder.UNFILED_ID) return
        pendingDeleteDao.insert(PendingDeleteEntity(entityType = "folder", entityId = folderId))
        folderDao.hardDelete(folderId)
        try {
            collection().document(folderId).update(
                mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            ).await()
            pendingDeleteDao.deleteByEntity("folder", folderId)
        } catch (_: Exception) { /* deleted locally; SyncWorker will push soft-delete */ }
    }

    suspend fun seedDefaultsIfEmpty() {
        // Room-first check: if Room already has folders, nothing to do.
        if (folderDao.getCount() > 0) return

        // Room is empty — check Firestore to distinguish fresh install vs first launch after migration.
        val existing = collection().whereEqualTo("isDeleted", false).get().await()
        if (!existing.isEmpty) return // InitialSyncManager will hydrate Room from Firestore.

        // Both Room and Firestore are empty — seed defaults into both.
        val now = System.currentTimeMillis()
        Folder.DEFAULTS.forEach { folder ->
            folderDao.upsert(FolderEntity(
                id = folder.id,
                name = folder.name,
                createdAt = now,
                isDeleted = false,
                deletedAt = null,
                syncStatus = SyncStatus.PENDING_UPLOAD,
            ))
        }
        try {
            val batch = firestore.batch()
            Folder.DEFAULTS.forEach { folder ->
                batch.set(
                    collection().document(folder.id),
                    mapOf("name" to folder.name, "isDeleted" to false, "createdAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Folder.DEFAULTS.forEach { folderDao.updateSyncStatus(it.id, SyncStatus.SYNCED) }
            Timber.d("FolderRepository: seeded default folders")
        } catch (e: Exception) {
            Timber.e(e, "FolderRepository: seedDefaultsIfEmpty Firestore write failed, SyncWorker will retry")
            syncScheduler.enqueueSync()
        }
    }

    // ── SyncWorker support ───────────────────────────────────────────────────

    suspend fun pushFolderCloud(entity: FolderEntity) {
        collection().document(entity.id).set(mapOf(
            "name" to entity.name,
            "isDeleted" to false,
            "createdAt" to (entity.createdAt.toTimestamp() ?: FieldValue.serverTimestamp()),
        )).await()
        folderDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
    }

    suspend fun pushFolderUpdate(entity: FolderEntity) {
        collection().document(entity.id).update("name", entity.name).await()
        folderDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
    }
}
