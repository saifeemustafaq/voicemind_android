package com.voicemind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.MainActivity
import com.voicemind.R
import com.voicemind.audio.AudioRecorder
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import com.voicemind.util.toDefaultTitle
import com.voicemind.widget.RecordingWidget
import com.voicemind.widget.RecordingWidgetStateKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var audioRecorder: AudioRecorder
    @Inject lateinit var storageRepository: StorageRepository
    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var functions: FirebaseFunctions

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var elapsedSeconds = 0L
    private var audioFile: File? = null
    private var isRecording = false
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Immediately promote to foreground to prevent
        // ForegroundServiceDidNotStartInTimeException if anything below throws.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP_SAVE -> handleStopSave()
            ACTION_DISCARD -> handleDiscard()
            else -> {
                // Unknown or null action -- stop immediately.
                resetAndStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        try {
            audioFile = audioRecorder.start()
            isRecording = true
            isPaused = false
            elapsedSeconds = 0
            updateNotification()
            startTimer()
            scope.launch { pushWidgetState() }
            Timber.d("Widget recording started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording from widget")
            resetAndStop()
        }
    }

    private fun handlePause() {
        if (!isRecording) return
        audioRecorder.pause()
        isRecording = false
        isPaused = true
        timerJob?.cancel()
        updateNotification()
        scope.launch { pushWidgetState() }
        Timber.d("Widget recording paused")
    }

    private fun handleResume() {
        if (!isPaused) return
        audioRecorder.resume()
        isRecording = true
        isPaused = false
        startTimer()
        updateNotification()
        scope.launch { pushWidgetState() }
        Timber.d("Widget recording resumed")
    }

    private fun handleStopSave() {
        if (!isRecording && !isPaused) {
            resetAndStop()
            return
        }
        timerJob?.cancel()
        val file = audioRecorder.stop() ?: run {
            resetAndStop()
            return
        }

        isRecording = false
        isPaused = false

        scope.launch(Dispatchers.IO) {
            try {
                pushWidgetState()

                val recordingId = "rec-${System.currentTimeMillis()}-${(1000..9999).random()}"
                val audioPath = storageRepository.uploadAudio(recordingId, file)
                val title = Date().toDefaultTitle().take(25)

                recordingRepository.createRecording(
                    Recording(
                        id = recordingId,
                        title = title,
                        folderId = Folder.UNFILED_ID,
                        audioPath = audioPath,
                    )
                )

                functions
                    .getHttpsCallable("processRecording")
                    .call(hashMapOf(
                        "recordingId" to recordingId,
                        "timezone" to java.util.TimeZone.getDefault().id,
                    ))

                file.delete()
                Timber.d("Widget recording saved: $recordingId")
            } catch (e: Exception) {
                Timber.e("Failed to save widget recording: %s", e.message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun handleDiscard() {
        if (!isRecording && !isPaused) {
            resetAndStop()
            return
        }
        timerJob?.cancel()
        audioRecorder.discardAndRelease()
        audioFile = null
        isRecording = false
        isPaused = false
        Timber.d("Widget recording discarded")
        resetAndStop()
    }

    private fun resetAndStop() {
        isRecording = false
        isPaused = false
        elapsedSeconds = 0
        scope.launch {
            pushWidgetState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds++
                updateNotification()
                pushWidgetState()
            }
        }
    }

    private suspend fun pushWidgetState() {
        try {
            val manager = GlanceAppWidgetManager(this@RecordingService)
            val glanceIds = manager.getGlanceIds(RecordingWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(this@RecordingService, glanceId) { prefs ->
                    prefs[RecordingWidgetStateKeys.IS_RECORDING] = isRecording
                    prefs[RecordingWidgetStateKeys.IS_PAUSED] = isPaused
                    prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] = elapsedSeconds
                }
                RecordingWidget().update(this@RecordingService, glanceId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget state")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when VoiceMind is recording audio"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            isPaused -> "Paused - ${formatTime(elapsedSeconds)}"
            isRecording -> "Recording - ${formatTime(elapsedSeconds)}"
            else -> "VoiceMind"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VoiceMind")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPending)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.voicemind.action.WIDGET_START"
        const val ACTION_PAUSE = "com.voicemind.action.WIDGET_PAUSE"
        const val ACTION_RESUME = "com.voicemind.action.WIDGET_RESUME"
        const val ACTION_STOP_SAVE = "com.voicemind.action.WIDGET_STOP_SAVE"
        const val ACTION_DISCARD = "com.voicemind.action.WIDGET_DISCARD"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001

        fun buildIntent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).apply { this.action = action }

        private fun formatTime(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return "%d:%02d".format(mins, secs)
        }
    }
}
