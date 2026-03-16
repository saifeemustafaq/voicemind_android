package com.voicemind.ui.recording

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Minimal ViewModel responsible only for waveform extraction on the Recording Detail screen.
 * All playback state is owned by [RecordingsViewModel] (shared via back-stack scoping).
 */
@HiltViewModel
class RecordingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    data class State(
        val waveformBars: List<Float> = emptyList(),
        val isExtractingWaveform: Boolean = false,
        val audioUrl: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])

    init {
        loadWaveform()
    }

    private fun loadWaveform() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExtractingWaveform = true) }
            try {
                val recording = recordingRepository.getRecording(recordingId) ?: return@launch
                val url = storageRepository.getDownloadUrl(recording.audioPath).toString()
                _state.update { it.copy(audioUrl = url) }
                val bars = WaveformExtractor.extract(url)
                _state.update { it.copy(waveformBars = bars) }
            } catch (e: Exception) {
                Timber.w(e, "RecordingDetailViewModel: waveform extraction failed")
            } finally {
                _state.update { it.copy(isExtractingWaveform = false) }
            }
        }
    }
}
