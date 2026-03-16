package com.voicemind.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlaybackCommand {
    object PlayPause : PlaybackCommand()
    object SkipForward : PlaybackCommand()
    object SkipBackward : PlaybackCommand()
}

/**
 * Application-scoped command bus that bridges notification button taps
 * (from [PlaybackActionReceiver]) to [com.voicemind.ui.recording.RecordingsViewModel].
 */
@Singleton
class PlaybackCommandRepository @Inject constructor() {
    private val _commands = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<PlaybackCommand> = _commands.asSharedFlow()

    fun emit(command: PlaybackCommand) {
        _commands.tryEmit(command)
    }
}
