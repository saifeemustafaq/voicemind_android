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
import com.voicemind.MainActivity
import com.voicemind.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Foreground service that displays a persistent media notification while audio is playing.
 *
 * Lifecycle:
 * - Started (via [ACTION_START]) when [com.voicemind.ui.recording.RecordingsViewModel.playAudio] is called.
 * - Updated (via [ACTION_UPDATE_STATE]) on pause/resume.
 * - Stopped (via [ACTION_STOP]) when playback ends or is manually stopped.
 *
 * Notification button taps are handled by [PlaybackActionReceiver] which routes them
 * back to the ViewModel via [PlaybackCommandRepository].
 */
@AndroidEntryPoint
class PlaybackService : Service() {

    // Current state kept for notification rebuilds triggered by onStartCommand(ACTION_UPDATE_STATE)
    private var currentTitle: String = ""
    private var currentIsPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
                currentIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(currentTitle, currentIsPlaying),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification(currentTitle, currentIsPlaying))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "PlaybackService: startForeground failed")
                    stopSelf()
                }
            }

            ACTION_UPDATE_STATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, currentIsPlaying)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(currentTitle, currentIsPlaying))
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, isPlaying: Boolean): Notification {
        // Tap notification body → open app at Recordings screen
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RecordingService.EXTRA_OPEN_RECORDINGS, true)
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun broadcastPending(requestCode: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                this, requestCode,
                Intent(action).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val skipBackPending  = broadcastPending(10, PlaybackActionReceiver.ACTION_SKIP_BACKWARD)
        val playPausePending = broadcastPending(11, PlaybackActionReceiver.ACTION_PLAY_PAUSE)
        val skipFwdPending   = broadcastPending(12, PlaybackActionReceiver.ACTION_SKIP_FORWARD)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_white)
            .setContentTitle(title.ifBlank { "VoiceMind" })
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(tapPending)
            .addAction(R.drawable.ic_skip_backward_5, "Skip back 5s", skipBackPending)
            .addAction(
                if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24,
                if (isPlaying) "Pause" else "Play",
                playPausePending,
            )
            .addAction(R.drawable.ic_skip_forward_5, "Skip forward 5s", skipFwdPending)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while VoiceMind is playing a recording"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START        = "com.voicemind.playback.START"
        const val ACTION_UPDATE_STATE = "com.voicemind.playback.UPDATE_STATE"
        const val ACTION_STOP         = "com.voicemind.playback.STOP"

        const val EXTRA_TITLE      = "extra_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        private const val CHANNEL_ID      = "playback_channel"
        private const val NOTIFICATION_ID = 1002

        fun buildStartIntent(context: Context, title: String, isPlaying: Boolean): Intent =
            Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }

        fun buildUpdateIntent(context: Context, title: String, isPlaying: Boolean): Intent =
            Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).apply { action = ACTION_STOP }
    }
}
