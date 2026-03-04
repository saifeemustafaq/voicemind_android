package com.voicemind.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.MainActivity
import com.voicemind.R
import com.voicemind.audio.AudioRecorder
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import com.voicemind.util.formatRecordingTime
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
import kotlinx.coroutines.withContext
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

    // Elapsed time — derived from wall-clock to prevent drift
    private var elapsedSeconds = 0L
    private var recordingStartedAt = 0L   // SystemClock.elapsedRealtime() at start/resume
    private var elapsedBeforePause = 0L   // accumulated seconds before the current session

    private var audioFile: File? = null
    private var isRecording = false
    private var isPaused = false

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately to avoid ForegroundServiceDidNotStartInTimeException.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Timber.e(e, "Cannot start foreground — missing permission?")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP_SAVE -> handleStopSave()
            ACTION_DISCARD -> handleDiscard()
            else -> resetAndStop()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        // Guard: mic permission must be granted before starting the recorder.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("RECORD_AUDIO permission not granted — cannot start recording from widget")
            scope.launch {
                pushWidgetState(needsMicPermission = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        try {
            audioFile = audioRecorder.start()
            isRecording = true
            isPaused = false
            elapsedSeconds = 0
            elapsedBeforePause = 0
            acquireWakeLock()
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
        timerJob?.cancel()
        elapsedBeforePause = elapsedSeconds  // snapshot time at pause
        isRecording = false
        isPaused = true
        releaseWakeLock()
        updateNotification()
        scope.launch { pushWidgetState() }
        Timber.d("Widget recording paused")
    }

    private fun handleResume() {
        if (!isPaused) return
        audioRecorder.resume()
        isRecording = true
        isPaused = false
        acquireWakeLock()
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
        releaseWakeLock()

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
                audioFile = null
                Timber.d("Widget recording saved: $recordingId")
            } catch (e: Exception) {
                Timber.e("Failed to save widget recording: %s", e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
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
        releaseWakeLock()
        Timber.d("Widget recording discarded")
        resetAndStop()
    }

    private fun resetAndStop() {
        isRecording = false
        isPaused = false
        elapsedSeconds = 0
        elapsedBeforePause = 0
        scope.launch {
            pushWidgetState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimer() {
        recordingStartedAt = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(500)
                val sessionSeconds = (SystemClock.elapsedRealtime() - recordingStartedAt) / 1000
                elapsedSeconds = elapsedBeforePause + sessionSeconds
                updateNotification()
                pushWidgetState()
            }
        }
    }

    private suspend fun pushWidgetState(needsMicPermission: Boolean = false) {
        try {
            val isSignedIn = FirebaseAuth.getInstance().currentUser != null
            val hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val manager = GlanceAppWidgetManager(this@RecordingService)
            val glanceIds = manager.getGlanceIds(RecordingWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(this@RecordingService, glanceId) { prefs ->
                    prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
                    // needsMicPermission arg takes precedence; otherwise derive from live state
                    prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] =
                        needsMicPermission || !hasMicPerm
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

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoiceMind:RecordingWakeLock",
        ).apply { acquire(4 * 60 * 60 * 1000L) } // safety cap: 4 hours
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
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
            isPaused -> "Paused - ${formatRecordingTime(elapsedSeconds)}"
            isRecording -> "Recording - ${formatRecordingTime(elapsedSeconds)}"
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
        releaseWakeLock()
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
    }
}
