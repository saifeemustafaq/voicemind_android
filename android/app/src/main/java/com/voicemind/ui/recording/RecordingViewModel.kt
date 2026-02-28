package com.voicemind.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.voicemind.audio.AudioRecorder
import com.voicemind.audio.RecorderState
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import com.voicemind.util.toDefaultTitle
import java.util.Date
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
    private val audioRecorder: AudioRecorder,
    private val storageRepository: StorageRepository,
    private val recordingRepository: RecordingRepository,
    private val functions: FirebaseFunctions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState

    private var timerJob: Job? = null
    private var audioFile: File? = null
    private var currentFolderId: String = Folder.UNFILED_ID

    fun setCurrentFolder(folderId: String?) {
        currentFolderId = folderId ?: Folder.UNFILED_ID
    }

    fun startRecording() {
        val defaultTitle = Date().toDefaultTitle()
        audioFile = audioRecorder.start()
        _uiState.value = RecordingUiState(
            isRecording = true,
            title = defaultTitle,
            showSheet = true
        )
        startTimer()
    }

    fun pauseRecording() {
        audioRecorder.pause()
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(isRecording = false, isPaused = true)
    }

    fun resumeRecording() {
        audioRecorder.resume()
        _uiState.value = _uiState.value.copy(isRecording = true, isPaused = false)
        startTimer()
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun stopAndSave() {
        timerJob?.cancel()
        val file = audioRecorder.stop() ?: return
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isPaused = false,
            isSaving = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recordingId = "rec-${System.currentTimeMillis()}-${(1000..9999).random()}"
                val audioPath = storageRepository.uploadAudio(recordingId, file)

                val title = _uiState.value.title.trim().take(25)
                recordingRepository.createRecording(
                    Recording(
                        id = recordingId,
                        title = title,
                        folderId = currentFolderId,
                        audioPath = audioPath,
                    )
                )

                // Trigger server-side processing (transcription, title, action items)
                functions
                    .getHttpsCallable("processRecording")
                    .call(hashMapOf(
                        "recordingId" to recordingId,
                        "timezone" to java.util.TimeZone.getDefault().id,
                    ))

                file.delete()
                Timber.d("Recording saved: $recordingId")
            } catch (e: Exception) {
                Timber.e("Failed to save recording: %s", e.message)
            } finally {
                _uiState.value = RecordingUiState()
            }
        }
    }

    fun discardRecording() {
        timerJob?.cancel()
        audioRecorder.discardAndRelease()
        audioFile = null
        _uiState.value = RecordingUiState()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    elapsedSeconds = _uiState.value.elapsedSeconds + 1
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioRecorder.release()
    }
}
