package com.voicemind.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.repository.StorageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class BulkDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val storageRepository: StorageRepository,
    private val localAudioManager: LocalAudioManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = recordingDao.getRecordingsNeedingAudioDownload()
        if (pending.isEmpty()) return Result.success()

        setForeground(buildForegroundInfo(0, pending.size))

        var downloaded = 0
        pending.forEachIndexed { index, entity ->
            try {
                val destFile = localAudioManager.getAudioFile(entity.id)
                    ?: run {
                        val url = storageRepository.getDownloadUrl(entity.audioPath).toString()
                        val dest = java.io.File(appContext.filesDir, "audio/${entity.id}.m4a")
                        dest.parentFile?.mkdirs()
                        storageRepository.downloadFromUrl(url, dest)
                        dest
                    }
                recordingDao.updateLocalAudioPath(entity.id, destFile.absolutePath)
                downloaded++
                setForeground(buildForegroundInfo(index + 1, pending.size))
            } catch (e: Exception) {
                Timber.w(e, "BulkDownloadWorker: failed to download audio for %s — skipping", entity.id)
            }
        }

        Timber.d("BulkDownloadWorker: downloaded %d / %d recordings", downloaded, pending.size)
        return Result.success()
    }

    private fun buildForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val channelId = "bulk_download"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Download",
                NotificationManager.IMPORTANCE_LOW,
            )
            appContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("Downloading recordings")
            .setContentText("$progress of $total files downloaded")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, progress == 0)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "voicemind_bulk_download"
        private const val NOTIFICATION_ID = 2001
    }
}
