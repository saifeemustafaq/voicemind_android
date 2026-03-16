package com.voicemind.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives PendingIntent actions from the playback media notification buttons
 * (skip backward, play/pause, skip forward) and routes them through
 * [PlaybackCommandRepository] so [com.voicemind.ui.recording.RecordingsViewModel] can act.
 */
@AndroidEntryPoint
class PlaybackActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: PlaybackCommandRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE    -> repository.emit(PlaybackCommand.PlayPause)
            ACTION_SKIP_FORWARD  -> repository.emit(PlaybackCommand.SkipForward)
            ACTION_SKIP_BACKWARD -> repository.emit(PlaybackCommand.SkipBackward)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE    = "com.voicemind.playback.PLAY_PAUSE"
        const val ACTION_SKIP_FORWARD  = "com.voicemind.playback.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.voicemind.playback.SKIP_BACKWARD"
    }
}
