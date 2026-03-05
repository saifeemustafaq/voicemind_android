package com.voicemind.data.repository

import com.voicemind.data.model.Folder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active recording state, shared between
 * RecordingService (which drives audio + notification) and RecordingViewModel
 * (which drives the in-app bottom sheet UI).
 *
 * The Service writes to this repository; the ViewModel observes it reactively.
 */
@Singleton
class RecordingStateRepository @Inject constructor() {

    // --- Reactive state (observed by ViewModel) ---

    private val _isActive = MutableStateFlow(false)   // true = recording OR paused (service alive)
    private val _isPaused = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _elapsedSeconds = MutableStateFlow(0L)

    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // --- Pending metadata set by ViewModel, consumed by Service on stop ---

    /** Custom title typed by the user in the in-app bottom sheet. Falls back to
     *  the auto-generated date title in the Service if blank. */
    var pendingTitle: String = ""

    /** Folder selected in-app. Defaults to UNFILED if not set. */
    var pendingFolderId: String = Folder.UNFILED_ID

    // --- State transition methods (called by RecordingService) ---

    fun onRecordingStarted() {
        _isActive.value = true
        _isPaused.value = false
        _isSaving.value = false
        _elapsedSeconds.value = 0
    }

    fun onPaused(elapsed: Long) {
        // isActive stays true — service is still alive
        _isPaused.value = true
        _elapsedSeconds.value = elapsed
    }

    fun onResumed() {
        _isPaused.value = false
    }

    fun onTimerTick(elapsed: Long) {
        _elapsedSeconds.value = elapsed
    }

    /** Called when the recording audio has stopped and the upload is beginning. */
    fun onSaving() {
        _isActive.value = false
        _isPaused.value = false
        _isSaving.value = true
        _elapsedSeconds.value = 0
    }

    /** Called when everything is finished (save complete OR discard). */
    fun onIdle() {
        _isActive.value = false
        _isPaused.value = false
        _isSaving.value = false
        _elapsedSeconds.value = 0
        pendingTitle = ""
        pendingFolderId = Folder.UNFILED_ID
    }
}
