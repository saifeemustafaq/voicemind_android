package com.voicemind.ui.sharing

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.SharingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.voicemind.ui.recording.WaveformExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SharedRecordingDetailUiState(
    val recording: Recording? = null,
    val ownerName: String = "",
    val tasks: List<ActionItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val audioUrl: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val waveformBars: List<Float> = emptyList(),
    val isExtractingWaveform: Boolean = false,
    val isDuplicating: Boolean = false,
    val duplicateSuccess: String? = null,
    val addedTaskIds: Set<String> = emptySet(),
    val addTaskError: String? = null,
)

@HiltViewModel
class SharedRecordingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val sharingRepository: SharingRepository,
    private val actionItemRepository: ActionItemRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val ownerUid: String = requireNotNull(savedStateHandle["ownerUid"])
    private val recordingId: String = requireNotNull(savedStateHandle["recordingId"])

    private val _state = MutableStateFlow(SharedRecordingDetailUiState())
    val state: StateFlow<SharedRecordingDetailUiState> = _state

    private var mediaPlayer: MediaPlayer? = null
    private var positionPollJob: Job? = null
    private var urlFetchedAt: Long = 0L
    private var urlRetryCount: Int = 0

    private val speedSteps = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    init {
        viewModelScope.launch {
            recordingRepository.observeSharedRecording(ownerUid, recordingId).collect { recording ->
                _state.update { it.copy(recording = recording, isLoading = false) }
            }
        }

        viewModelScope.launch {
            actionItemRepository.observeActionItemsForRecording(ownerUid, recordingId).collect { tasks ->
                val alreadyAdded = withContext(Dispatchers.IO) {
                    coroutineScope {
                        tasks.map { task ->
                            async { if (actionItemRepository.isSharedTaskAdded(task.id, ownerUid)) task.id else null }
                        }.awaitAll().filterNotNull().toSet()
                    }
                }
                _state.update { it.copy(tasks = tasks, addedTaskIds = alreadyAdded) }
            }
        }

        viewModelScope.launch {
            folderRepository.observeFolders().collect { folders ->
                _state.update { it.copy(folders = folders) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Owner name lookup from sharedWithMe inbox entry
            try {
                val sharedItem = sharingRepository.getSharedItemForRecording(recordingId)
                if (sharedItem != null) {
                    _state.update { it.copy(ownerName = sharedItem.ownerName) }
                }
            } catch (e: Exception) {
                Timber.w(e, "SharedRecordingDetailVM: owner name lookup failed")
            }

            // Fetch signed audio URL and extract waveform
            try {
                val url = sharingRepository.getSharedAudioUrl(ownerUid, recordingId)
                urlFetchedAt = System.currentTimeMillis()
                _state.update { it.copy(audioUrl = url, isExtractingWaveform = true) }
                val bars = WaveformExtractor.extract(url)
                _state.update { it.copy(waveformBars = bars, isExtractingWaveform = false) }
            } catch (e: Exception) {
                Timber.e(e, "SharedRecordingDetailVM: audio init failed")
                _state.update { it.copy(error = "Failed to load audio", isExtractingWaveform = false) }
            }
        }
    }

    fun playOrResume() {
        val mp = mediaPlayer
        if (mp != null && _state.value.isPaused) {
            mp.start()
            startPositionPolling()
            _state.update { it.copy(isPlaying = true, isPaused = false) }
            return
        }
        urlRetryCount = 0
        viewModelScope.launch(Dispatchers.IO) {
            val url = getAudioUrlRefreshed() ?: return@launch
            releaseMediaPlayer()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener { player ->
                        player.start()
                        _state.update { s -> s.copy(
                            isPlaying = true,
                            isPaused = false,
                            durationMs = player.duration.toLong(),
                            positionMs = 0L,
                            error = null,
                        )}
                        startPositionPolling()
                    }
                    setOnCompletionListener {
                        positionPollJob?.cancel()
                        _state.update { s -> s.copy(isPlaying = false, isPaused = false, positionMs = 0L) }
                    }
                    setOnErrorListener { _, what, extra ->
                        Timber.e("SharedRecordingDetailVM: MediaPlayer error what=$what extra=$extra")
                        if (urlRetryCount < 1) {
                            urlRetryCount++
                            viewModelScope.launch(Dispatchers.IO) { retryWithFreshUrl() }
                        } else {
                            _state.update { it.copy(error = "Playback error", isPlaying = false, isPaused = false) }
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Timber.e(e, "SharedRecordingDetailVM: play failed")
                _state.update { it.copy(error = "Playback error") }
            }
        }
    }

    fun pause() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        positionPollJob?.cancel()
        _state.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun seekTo(positionMs: Long) {
        try { mediaPlayer?.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun skipForward5() {
        val mp = mediaPlayer ?: return
        seekTo((mp.currentPosition + 5_000L).coerceAtMost(mp.duration.toLong()))
    }

    fun skipBackward5() {
        val mp = mediaPlayer ?: return
        seekTo((mp.currentPosition - 5_000L).coerceAtLeast(0L))
    }

    fun cycleSpeed() {
        val current = _state.value.playbackSpeed
        val idx = speedSteps.indexOf(current).takeIf { it >= 0 } ?: 1
        setSpeed(speedSteps[(idx + 1) % speedSteps.size])
    }

    fun setSpeed(speed: Float) {
        try {
            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(speed)
        } catch (_: Exception) {}
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun duplicateToFolder(folderId: String) {
        _state.update { it.copy(isDuplicating = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newRecordingId = sharingRepository.duplicateSharedRecording(ownerUid, recordingId, folderId)
                _state.update { it.copy(isDuplicating = false, duplicateSuccess = newRecordingId) }
            } catch (e: Exception) {
                Timber.e(e, "SharedRecordingDetailVM: duplication failed")
                _state.update { it.copy(isDuplicating = false, error = "Failed to duplicate recording") }
            }
        }
    }

    fun clearDuplicateSuccess() {
        _state.update { it.copy(duplicateSuccess = null) }
    }

    fun addTaskToChecklist(task: ActionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                actionItemRepository.addSharedTask(
                    originalTaskId = task.id,
                    ownerUid = ownerUid,
                    ownerName = _state.value.ownerName,
                    title = task.title,
                    notes = task.notes,
                    dueDate = task.dueDate,
                    deadline = task.deadline,
                    completed = task.completed,
                )
                _state.update { it.copy(addedTaskIds = it.addedTaskIds + task.id) }
            } catch (e: Exception) {
                Timber.e(e, "SharedRecordingDetailVM: addTaskToChecklist failed")
                _state.update { it.copy(addTaskError = "Failed to add task") }
            }
        }
    }

    fun clearAddTaskError() {
        _state.update { it.copy(addTaskError = null) }
    }

    private suspend fun getAudioUrlRefreshed(): String? {
        val current = _state.value.audioUrl
        if (current != null && System.currentTimeMillis() - urlFetchedAt < 50 * 60 * 1000L) {
            return current
        }
        return try {
            val url = sharingRepository.getSharedAudioUrl(ownerUid, recordingId)
            urlFetchedAt = System.currentTimeMillis()
            _state.update { it.copy(audioUrl = url) }
            url
        } catch (e: Exception) {
            Timber.e(e, "SharedRecordingDetailVM: URL refresh failed")
            _state.update { it.copy(error = "Could not refresh audio") }
            null
        }
    }

    private suspend fun retryWithFreshUrl() {
        val url = try {
            val newUrl = sharingRepository.getSharedAudioUrl(ownerUid, recordingId)
            urlFetchedAt = System.currentTimeMillis()
            _state.update { it.copy(audioUrl = newUrl) }
            newUrl
        } catch (e: Exception) {
            _state.update { it.copy(error = "Playback error") }
            return
        }
        releaseMediaPlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { player ->
                    player.start()
                    _state.update { s -> s.copy(isPlaying = true, isPaused = false, durationMs = player.duration.toLong()) }
                    startPositionPolling()
                }
                setOnCompletionListener {
                    positionPollJob?.cancel()
                    _state.update { s -> s.copy(isPlaying = false, isPaused = false, positionMs = 0L) }
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("SharedRecordingDetailVM: retry MediaPlayer error what=$what extra=$extra")
                    _state.update { it.copy(error = "Playback error", isPlaying = false, isPaused = false) }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Playback error") }
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                delay(100L)
                val mp = mediaPlayer ?: break
                try {
                    _state.update { it.copy(positionMs = mp.currentPosition.toLong()) }
                } catch (_: Exception) { break }
            }
        }
    }

    private fun releaseMediaPlayer() {
        positionPollJob?.cancel()
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseMediaPlayer()
    }
}
