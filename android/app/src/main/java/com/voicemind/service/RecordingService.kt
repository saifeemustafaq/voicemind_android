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
import android.media.session.MediaSession
import android.media.session.PlaybackState
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
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.RecordingEntity
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.RecordingStateRepository
import com.voicemind.data.repository.StorageRepository
import com.voicemind.data.sync.SyncScheduler
import com.voicemind.util.ConnectivityObserver
import com.voicemind.util.formatRecordingTime
import com.voicemind.util.toDefaultTitle
import com.voicemind.widget.RecordingWidget
import com.voicemind.widget.RecordingWidgetStateKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var audioRecorder: AudioRecorder
    @Inject lateinit var storageRepository: StorageRepository
    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var functions: FirebaseFunctions
    @Inject lateinit var recordingStateRepository: RecordingStateRepository
    @Inject lateinit var navPreferenceRepository: NavPreferenceRepository
    @Inject lateinit var localAudioManager: LocalAudioManager
    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var recordingDao: RecordingDao
    @Inject lateinit var syncScheduler: SyncScheduler

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
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "VoiceMindRecording").apply {
            // Hardware media button / headset button integration
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = handlePause()
                override fun onPlay() = handleResume()
                override fun onStop() = handleStopSave(null, null)
            })
            isActive = true
        }
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
            ACTION_STOP_SAVE -> handleStopSave(
                titleOverride = intent.getStringExtra(EXTRA_TITLE),
                folderIdOverride = intent.getStringExtra(EXTRA_FOLDER_ID),
            )
            ACTION_DISCARD -> handleDiscard()
            else -> resetAndStop()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        // Guard: prevent double-start if already recording or paused.
        if (isRecording || isPaused) {
            Timber.d("Already recording — ignoring duplicate start")
            return
        }
        // Guard: mic permission must be granted before starting the recorder.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("RECORD_AUDIO permission not granted — cannot start recording")
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
            recordingStateRepository.onRecordingStarted()
            updateNotification()
            startTimer()
            scope.launch { pushWidgetState() }
            Timber.d("Recording started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            resetAndStop()
        }
    }

    private fun handlePause() {
        if (!isRecording) return
        audioRecorder.pause()
        timerJob?.cancel()
        elapsedBeforePause = elapsedSeconds
        isRecording = false
        isPaused = true
        releaseWakeLock()
        recordingStateRepository.onPaused(elapsedSeconds)
        updateNotification()
        scope.launch { pushWidgetState() }
        Timber.d("Recording paused")
    }

    private fun handleResume() {
        if (!isPaused) return
        audioRecorder.resume()
        isRecording = true
        isPaused = false
        acquireWakeLock()
        recordingStateRepository.onResumed()
        startTimer()
        updateNotification()
        scope.launch { pushWidgetState() }
        Timber.d("Recording resumed")
    }

    private fun handleStopSave(titleOverride: String?, folderIdOverride: String?) {
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
        // Keep wake lock held through upload + processing; released in the finally block below.
        if (wakeLock?.isHeld != true) acquireWakeLock()

        // Resolve title and folder: explicit extras → pending values from ViewModel → defaults.
        val fallbackTz = runBlocking {
            java.util.TimeZone.getTimeZone(navPreferenceRepository.appTimezone.first())
        }
        val title = titleOverride?.takeIf { it.isNotBlank() }
            ?: recordingStateRepository.pendingTitle.takeIf { it.isNotBlank() }
            ?: Date().toDefaultTitle(fallbackTz)
        val folderId = folderIdOverride
            ?: recordingStateRepository.pendingFolderId.takeIf { it != Folder.UNFILED_ID }
            ?: Folder.UNFILED_ID

        // Signal UI: saving in progress (sheet stays open with spinner)
        recordingStateRepository.onSaving()

        scope.launch(Dispatchers.IO) {
            try {
                pushWidgetState()

                val recordingId = "rec-${System.currentTimeMillis()}-${(1000..9999).random()}"
                val uid = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Not signed in" }
                val cloudAudioPath = "users/$uid/audio/$recordingId.m4a"

                // 1. Move audio from cacheDir to permanent local storage.
                val localAudioPath = localAudioManager.saveAudio(recordingId, file)
                audioFile = null

                // 2. Insert into Room immediately so the recording is visible offline.
                recordingDao.upsert(
                    RecordingEntity(
                        id = recordingId,
                        title = title.take(25),
                        folderId = folderId,
                        createdAt = System.currentTimeMillis(),
                        transcription = null,
                        summary = null,
                        audioPath = cloudAudioPath,
                        localAudioPath = localAudioPath,
                        durationSeconds = elapsedSeconds,
                        isDeleted = false,
                        deletedAt = null,
                        processingFailed = false,
                        syncStatus = SyncStatus.PENDING_UPLOAD,
                    )
                )

                if (connectivityObserver.isOnline.value) {
                    // 3a. Online: upload, create cloud doc, trigger processing.
                    storageRepository.uploadAudio(recordingId, File(localAudioPath))
                    recordingRepository.createRecordingCloud(
                        Recording(
                            id = recordingId,
                            title = title.take(25),
                            folderId = folderId,
                            audioPath = cloudAudioPath,
                            durationSeconds = elapsedSeconds,
                        )
                    )
                    val userTimezone = navPreferenceRepository.appTimezone.first()
                    try {
                        functions
                            .getHttpsCallable("processRecording")
                            .withTimeout(5, TimeUnit.MINUTES)
                            .call(hashMapOf("recordingId" to recordingId, "timezone" to userTimezone))
                            .await()
                    } catch (e: Exception) {
                        Timber.e("processRecording callable failed: %s", e.message)
                        recordingRepository.updateProcessingFailed(recordingId, true)
                    }
                    recordingDao.updateSyncStatus(recordingId, SyncStatus.SYNCED)
                } else {
                    // 3b. Offline: enqueue SyncWorker to handle upload when connected.
                    syncScheduler.enqueueSync()
                }

                Timber.d("Recording saved: %s", recordingId)
            } catch (e: Exception) {
                Timber.e("Failed to save recording: %s", e.message)
            } finally {
                releaseWakeLock()
                recordingStateRepository.onIdle()
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
        Timber.d("Recording discarded")
        resetAndStop()
    }

    private fun resetAndStop() {
        isRecording = false
        isPaused = false
        elapsedSeconds = 0
        elapsedBeforePause = 0
        scope.launch {
            recordingStateRepository.onIdle()
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
                recordingStateRepository.onTimerTick(elapsedSeconds)
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
        ).apply { acquire(4 * 60 * 60 * 1000L) }
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
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Tap: open app and navigate directly to the Recordings screen
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_RECORDINGS, true)
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 0: Delete / Discard
        val deletePending = PendingIntent.getService(
            this, 1,
            buildIntent(this, ACTION_DISCARD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Pause or Resume (state-dependent)
        val pauseResumePending = PendingIntent.getService(
            this, 2,
            buildIntent(this, if (isPaused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Stop & Save
        val stopPending = PendingIntent.getService(
            this, 3,
            buildIntent(this, ACTION_STOP_SAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Keep MediaSession in sync for headset/hardware button support.
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(
                    if (isPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .build()
        )

        // Use NotificationCompat (no MediaStyle). MediaStyle requires setMediaSession() to
        // render anything on Android 13+, and when the session IS set Android overrides our
        // action buttons with its own media-player transport controls. Plain NotificationCompat
        // reliably shows all three action buttons in the expanded notification on all versions.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_white)
            .setContentTitle(if (isPaused) "Paused" else "Recording")
            .setContentText(formatRecordingTime(elapsedSeconds))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_delete_24, "Delete", deletePending)
            .addAction(
                if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                if (isPaused) "Resume" else "Pause",
                pauseResumePending,
            )
            .addAction(R.drawable.ic_stop_24, "Stop & Save", stopPending)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START   = "com.voicemind.action.WIDGET_START"
        const val ACTION_PAUSE   = "com.voicemind.action.WIDGET_PAUSE"
        const val ACTION_RESUME  = "com.voicemind.action.WIDGET_RESUME"
        const val ACTION_STOP_SAVE = "com.voicemind.action.WIDGET_STOP_SAVE"
        const val ACTION_DISCARD = "com.voicemind.action.WIDGET_DISCARD"

        /** Intent extra: navigates to Recordings when tapping the notification body. */
        const val EXTRA_OPEN_RECORDINGS = "open_recordings"

        /** Optional title override passed by the ViewModel on stop-save. */
        const val EXTRA_TITLE = "extra_title"

        /** Optional folder-id override passed by the ViewModel on stop-save. */
        const val EXTRA_FOLDER_ID = "extra_folder_id"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001

        fun buildIntent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).apply { this.action = action }
    }
}
