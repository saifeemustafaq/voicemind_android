package com.voicemind.ui.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.Folder
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.RecordingStateRepository
import com.voicemind.service.RecordingService
import com.voicemind.util.toDefaultTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isSaving: Boolean = false,
    val title: String = "",
    val elapsedSeconds: Long = 0,
    val showSheet: Boolean = false,
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val stateRepository: RecordingStateRepository,
    private val navPreferenceRepository: NavPreferenceRepository,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private fun appTimeZone(): TimeZone =
        TimeZone.getTimeZone(runBlocking { navPreferenceRepository.appTimezone.first() })

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState

    init {
        // Mirror RecordingService state into the UI. The Service is the single source of
        // truth for recording; the ViewModel only adds UI-layer state (title, showSheet).
        viewModelScope.launch {
            combine(
                stateRepository.isActive,
                stateRepository.isPaused,
                stateRepository.isSaving,
                stateRepository.elapsedSeconds,
            ) { isActive, isPaused, saving, elapsed ->
                val current = _uiState.value
                current.copy(
                    isRecording = isActive && !isPaused,
                    isPaused = isActive && isPaused,
                    isSaving = saving,
                    elapsedSeconds = elapsed,
                    showSheet = isActive || saving,
                    // When recording starts externally (widget), seed a default title.
                    // When recording ends, clear the title.
                    title = when {
                        isActive && current.title.isBlank() -> Date().toDefaultTitle(appTimeZone())
                        !isActive && !saving -> ""
                        else -> current.title
                    },
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setCurrentFolder(folderId: String?) {
        stateRepository.pendingFolderId = folderId ?: Folder.UNFILED_ID
    }

    fun startRecording() {
        // Seed a default title and register it with the repository so the Service
        // picks it up even if the user doesn't edit it before tapping Stop.
        val defaultTitle = Date().toDefaultTitle(appTimeZone())
        _uiState.value = _uiState.value.copy(title = defaultTitle)
        stateRepository.pendingTitle = defaultTitle
        context.startForegroundService(
            RecordingService.buildIntent(context, RecordingService.ACTION_START)
        )
    }

    fun pauseRecording() {
        context.startForegroundService(
            RecordingService.buildIntent(context, RecordingService.ACTION_PAUSE)
        )
    }

    fun resumeRecording() {
        context.startForegroundService(
            RecordingService.buildIntent(context, RecordingService.ACTION_RESUME)
        )
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
        // Keep the repository in sync so a notification Stop uses the latest title.
        stateRepository.pendingTitle = title
    }

    fun stopAndSave() {
        // Push the current title to the repository before dispatching stop, so the
        // Service uses the user's title even when the stop comes from the notification.
        stateRepository.pendingTitle = _uiState.value.title.trim()
        context.startForegroundService(
            RecordingService.buildIntent(context, RecordingService.ACTION_STOP_SAVE)
        )
    }

    fun discardRecording() {
        context.startForegroundService(
            RecordingService.buildIntent(context, RecordingService.ACTION_DISCARD)
        )
    }
}
