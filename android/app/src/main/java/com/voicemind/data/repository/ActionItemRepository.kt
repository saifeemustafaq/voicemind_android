package com.voicemind.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.data.local.toEpochMillis
import com.voicemind.data.local.toModel
import com.voicemind.data.local.toTimestamp
import com.voicemind.data.model.ActionItem
import com.voicemind.data.sync.SyncScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionItemRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val functions: FirebaseFunctions,
    private val actionItemDao: ActionItemDao,
    private val syncScheduler: SyncScheduler,
) {
    private fun collection() =
        firestore.collection("users/${requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid}/actionItems")

    // ── Reads (Room-first) ────────────────────────────────────────────────────

    fun observeActionItems(): Flow<List<ActionItem>> =
        actionItemDao.observeAll().map { it.map { e -> e.toModel() } }

    fun observeByRecordingId(recordingId: String): Flow<List<ActionItem>> =
        actionItemDao.observeByRecordingId(recordingId).map { it.map { e -> e.toModel() } }

    fun observeActionItem(itemId: String): Flow<ActionItem?> =
        actionItemDao.observeById(itemId).map { it?.toModel() }

    fun observeSharedTasks(): Flow<List<ActionItem>> =
        actionItemDao.observeSharedTasks().map { it.map { e -> e.toModel() } }

    suspend fun getActionItem(itemId: String): ActionItem? =
        actionItemDao.getById(itemId)?.toModel()

    suspend fun getByRecordingId(recordingId: String): List<ActionItem> =
        actionItemDao.getByRecordingId(recordingId).map { it.toModel() }

    // ── Writes — dual-write (Room first, Firestore background) ───────────────

    suspend fun createItem(title: String) {
        val docId = collection().document().id
        val entity = ActionItemEntity(
            id = docId,
            title = title,
            completed = false,
            recordingId = null,
            createdAt = System.currentTimeMillis(),
            dueDate = null,
            deadline = null,
            notes = null,
            googleTaskId = null,
            calendarEventId = null,
            autoScheduled = false,
            sharedFromUid = null,
            sharedFromName = null,
            isDeleted = false,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )
        actionItemDao.upsert(entity)
        try {
            collection().document(docId).set(mapOf(
                "title" to title,
                "completed" to false,
                "isDeleted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
            )).await()
            actionItemDao.updateSyncStatus(docId, SyncStatus.SYNCED)
        } catch (_: Exception) {
            syncScheduler.enqueueSync()
        }
    }

    suspend fun toggleCompleted(itemId: String, completed: Boolean) {
        actionItemDao.updateCompleted(itemId, completed, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(itemId).update("completed", completed).await()
            actionItemDao.updateSyncStatus(itemId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun updateTitle(itemId: String, title: String) {
        actionItemDao.updateTitle(itemId, title, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(itemId).update("title", title).await()
            actionItemDao.updateSyncStatus(itemId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun updateDueDate(itemId: String, dueDate: Timestamp?) {
        actionItemDao.updateDueDate(itemId, dueDate.toEpochMillis(), SyncStatus.PENDING_UPDATE)
        try {
            collection().document(itemId).update("dueDate", dueDate).await()
            actionItemDao.updateSyncStatus(itemId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun updateDeadline(itemId: String, deadline: Timestamp?) {
        actionItemDao.updateDeadline(itemId, deadline.toEpochMillis(), SyncStatus.PENDING_UPDATE)
        try {
            collection().document(itemId).update("deadline", deadline).await()
            actionItemDao.updateSyncStatus(itemId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    suspend fun updateNotes(itemId: String, notes: String?) {
        actionItemDao.updateNotes(itemId, notes, SyncStatus.PENDING_UPDATE)
        try {
            collection().document(itemId).update("notes", notes).await()
            actionItemDao.updateSyncStatus(itemId, SyncStatus.SYNCED)
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    // ── Deletes — Room hard-delete + Firestore soft-delete ───────────────────

    suspend fun deleteItem(itemId: String) {
        actionItemDao.hardDelete(itemId)
        try {
            collection().document(itemId).update(
                mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            ).await()
        } catch (_: Exception) { /* deleted locally; cloud copy remains for recovery */ }
    }

    suspend fun deleteItems(itemIds: List<String>) {
        itemIds.forEach { actionItemDao.hardDelete(it) }
        try {
            val batch = firestore.batch()
            val softDelete = mapOf("isDeleted" to true, "deletedAt" to FieldValue.serverTimestamp())
            itemIds.forEach { id -> batch.update(collection().document(id), softDelete) }
            batch.commit().await()
        } catch (_: Exception) { /* deleted locally */ }
    }

    suspend fun markCompleted(itemIds: List<String>, completed: Boolean) {
        itemIds.forEach { id -> actionItemDao.updateCompleted(id, completed, SyncStatus.PENDING_UPDATE) }
        try {
            val batch = firestore.batch()
            itemIds.forEach { id -> batch.update(collection().document(id), "completed", completed) }
            batch.commit().await()
            itemIds.forEach { id -> actionItemDao.updateSyncStatus(id, SyncStatus.SYNCED) }
        } catch (_: Exception) { /* SyncWorker will retry */ }
    }

    // ── SyncWorker support ───────────────────────────────────────────────────

    suspend fun pushActionItemCloud(entity: ActionItemEntity) {
        collection().document(entity.id).set(mapOf(
            "title" to entity.title,
            "completed" to entity.completed,
            "recordingId" to entity.recordingId,
            "isDeleted" to false,
            "createdAt" to (entity.createdAt.toTimestamp() ?: FieldValue.serverTimestamp()),
            "notes" to entity.notes,
            "dueDate" to entity.dueDate.toTimestamp(),
            "deadline" to entity.deadline.toTimestamp(),
            "sharedFromUid" to entity.sharedFromUid,
            "sharedFromName" to entity.sharedFromName,
        )).await()
        actionItemDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
    }

    suspend fun pushActionItemUpdate(entity: ActionItemEntity) {
        collection().document(entity.id).update(mapOf(
            "title" to entity.title,
            "completed" to entity.completed,
            "notes" to entity.notes,
            "dueDate" to entity.dueDate.toTimestamp(),
            "deadline" to entity.deadline.toTimestamp(),
        )).await()
        actionItemDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
    }

    // ── Unchanged — cloud-function / cross-user calls ─────────────────────────

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
            .whereEqualTo("isDeleted", false)
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
            "isDeleted" to false,
            "sharedFromUid" to ownerUid,
            "sharedFromName" to ownerName,
            "createdAt" to Timestamp.now(),
        )).await()
    }

    suspend fun isSharedTaskAdded(originalTaskId: String, ownerUid: String): Boolean {
        val docId = "shared-$ownerUid-$originalTaskId"
        return collection().document(docId).get().await().exists()
    }

    suspend fun hasGeneratedTasksForSharedRecording(ownerUid: String, recordingId: String): Boolean =
        collection()
            .whereEqualTo("isDeleted", false)
            .whereEqualTo("recordingId", "shared:$ownerUid:$recordingId")
            .limit(1)
            .get().await()
            .documents.isNotEmpty()
}
