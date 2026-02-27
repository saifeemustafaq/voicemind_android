package com.voicemind.ui.recording

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed interface SummaryState {
    data object Idle : SummaryState
    data object Loading : SummaryState
    data class Loaded(val text: String) : SummaryState
    data class Error(val message: String) : SummaryState
}

data class TranscriptSheetState(
    val summaryState: SummaryState = SummaryState.Idle,
    val actionItems: List<ActionItem> = emptyList(),
    val actionItemsLoaded: Boolean = false,
)

data class RecordingsListState(
    val recordings: List<Recording> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val playingRecordingId: String? = null,
    val filterFolderId: String? = null,
)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val folderRepository: FolderRepository,
    private val storageRepository: StorageRepository,
    private val actionItemRepository: ActionItemRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordingsListState())
    val state: StateFlow<RecordingsListState> = _state

    private val _sheetState = MutableStateFlow(TranscriptSheetState())
    val sheetState: StateFlow<TranscriptSheetState> = _sheetState

    private var mediaPlayer: MediaPlayer? = null
    private var recordingsJob: Job? = null

    init {
        observeRecordings()
        viewModelScope.launch {
            folderRepository.observeFolders().collect { folders ->
                _state.value = _state.value.copy(folders = folders)
            }
        }
    }

    private fun observeRecordings(folderId: String? = null) {
        recordingsJob?.cancel()
        recordingsJob = viewModelScope.launch {
            val flow = if (folderId != null) {
                recordingRepository.observeByFolder(folderId)
            } else {
                recordingRepository.observeRecordings()
            }
            flow.collect { recordings ->
                _state.value = _state.value.copy(recordings = recordings, isLoading = false)
            }
        }
    }

    fun filterByFolder(folderId: String?) {
        _state.value = _state.value.copy(filterFolderId = folderId, isLoading = true)
        observeRecordings(folderId)
    }

    fun playAudio(recording: Recording) {
        stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = storageRepository.getDownloadUrl(recording.audioPath)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url.toString())
                    setOnPreparedListener {
                        it.start()
                        _state.value = _state.value.copy(playingRecordingId = recording.id)
                    }
                    setOnCompletionListener {
                        _state.value = _state.value.copy(playingRecordingId = null)
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Timber.e("Playback failed: %s", e.message)
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        _state.value = _state.value.copy(playingRecordingId = null)
    }

    fun renameRecording(recordingId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.updateTitle(recordingId, newTitle)
        }
    }

    fun moveToFolder(recordingId: String, folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.moveToFolder(recordingId, folderId)
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.playingRecordingId == recording.id) stopPlayback()
            recordingRepository.deleteRecording(recording)
        }
    }

    fun shareAudio(context: Context, recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = storageRepository.getDownloadUrl(recording.audioPath)
                val tempFile = File(context.cacheDir, "${recording.id}.m4a")
                java.net.URL(url.toString()).openStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/m4a"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Audio"))
            } catch (e: Exception) {
                Timber.e("Share failed: %s", e.message)
            }
        }
    }

    fun shareTranscript(context: Context, transcript: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, transcript)
        }
        context.startActivity(Intent.createChooser(intent, "Share Transcript"))
    }

    fun openTranscriptSheet(recording: Recording) {
        _sheetState.value = TranscriptSheetState()
        loadActionItems(recording.id)
        if (recording.summary != null) {
            _sheetState.value = _sheetState.value.copy(
                summaryState = SummaryState.Loaded(recording.summary)
            )
        }
    }

    fun loadActionItems(recordingId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = actionItemRepository.getByRecordingId(recordingId)
                _sheetState.value = _sheetState.value.copy(
                    actionItems = items,
                    actionItemsLoaded = true,
                )
            } catch (e: Exception) {
                Timber.e("Failed to load action items: %s", e.message)
                _sheetState.value = _sheetState.value.copy(actionItemsLoaded = true)
            }
        }
    }

    fun generateSummary(recording: Recording) {
        if (recording.summary != null) {
            _sheetState.value = _sheetState.value.copy(
                summaryState = SummaryState.Loaded(recording.summary)
            )
            return
        }
        if (_sheetState.value.summaryState is SummaryState.Loading) return

        _sheetState.value = _sheetState.value.copy(summaryState = SummaryState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = recordingRepository.generateSummary(recording.id)
                _sheetState.value = _sheetState.value.copy(
                    summaryState = SummaryState.Loaded(summary)
                )
            } catch (e: Exception) {
                Timber.e("Summary generation failed: %s", e.message)
                _sheetState.value = _sheetState.value.copy(
                    summaryState = SummaryState.Error(
                        "Unable to generate summary. Please try again."
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
