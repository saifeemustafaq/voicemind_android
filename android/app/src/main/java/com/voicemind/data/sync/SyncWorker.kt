package com.voicemind.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.local.toModel
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val actionItemDao: ActionItemDao,
    private val folderDao: FolderDao,
    private val collectiveSummaryDao: CollectiveSummaryDao,
    private val storageRepository: StorageRepository,
    private val recordingRepository: RecordingRepository,
    private val actionItemRepository: ActionItemRepository,
    private val folderRepository: FolderRepository,
    private val navPreferenceRepository: NavPreferenceRepository,
    private val functions: FirebaseFunctions,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            syncPendingRecordings()
            syncPendingActionItems()
            syncPendingFolders()
            syncPendingSummaries()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed")
            Result.retry()
        }
    }

    private suspend fun syncPendingRecordings() {
        recordingDao.getPendingSync().forEach { entity ->
            when (entity.syncStatus) {
                SyncStatus.PENDING_UPLOAD -> uploadRecording(entity)
                SyncStatus.PENDING_UPDATE -> pushRecordingUpdate(entity)
                SyncStatus.PENDING_DELETE -> { /* Phase 4 */ }
                SyncStatus.SYNCED -> { /* no-op */ }
            }
        }
    }

    private suspend fun syncPendingActionItems() {
        actionItemDao.getPendingSync().forEach { entity ->
            when (entity.syncStatus) {
                SyncStatus.PENDING_UPLOAD -> actionItemRepository.pushActionItemCloud(entity)
                SyncStatus.PENDING_UPDATE -> actionItemRepository.pushActionItemUpdate(entity)
                SyncStatus.PENDING_DELETE -> { /* Phase 4 */ }
                SyncStatus.SYNCED -> { /* no-op */ }
            }
        }
    }

    private suspend fun syncPendingFolders() {
        folderDao.getPendingSync().forEach { entity ->
            when (entity.syncStatus) {
                SyncStatus.PENDING_UPLOAD -> folderRepository.pushFolderCloud(entity)
                SyncStatus.PENDING_UPDATE -> folderRepository.pushFolderUpdate(entity)
                SyncStatus.PENDING_DELETE -> { /* Phase 4 */ }
                SyncStatus.SYNCED -> { /* no-op */ }
            }
        }
    }

    private suspend fun syncPendingSummaries() {
        // Summaries are cloud-generated — no PENDING_UPLOAD or PENDING_UPDATE cases.
        // PENDING_DELETE soft-sync is Phase 4.
        collectiveSummaryDao.getPendingSync().forEach { /* Phase 4 */ }
    }

    private suspend fun uploadRecording(entity: RecordingEntity) {
        val localPath = entity.localAudioPath ?: run {
            Timber.w("SyncWorker: no localAudioPath for %s — skipping upload", entity.id)
            return
        }
        val file = File(localPath)
        if (!file.exists()) {
            Timber.w("SyncWorker: local audio file missing for %s — skipping upload", entity.id)
            return
        }

        storageRepository.uploadAudio(entity.id, file)
        recordingRepository.createRecordingCloud(entity.toModel())

        val timezone = navPreferenceRepository.appTimezone.first()
        try {
            functions.getHttpsCallable("processRecording")
                .withTimeout(5, TimeUnit.MINUTES)
                .call(hashMapOf("recordingId" to entity.id, "timezone" to timezone))
                .await()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: processRecording failed for %s", entity.id)
            recordingRepository.updateProcessingFailed(entity.id, true)
        }

        recordingDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
    }

    private suspend fun pushRecordingUpdate(entity: RecordingEntity) {
        recordingRepository.pushRecordingUpdate(entity.id, entity.title, entity.folderId)
    }
}
