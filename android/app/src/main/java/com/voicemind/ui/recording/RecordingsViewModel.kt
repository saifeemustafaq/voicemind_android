package com.voicemind.ui.recording

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.service.PlaybackCommand
import com.voicemind.service.PlaybackCommandRepository
import com.voicemind.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.local.SyncStatus
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.CollectiveSummaryRepository
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.StorageRepository
import com.voicemind.ui.components.NeedsInternetReason
import com.voicemind.util.ConnectivityObserver
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
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
    val isGeneratingTasks: Boolean = false,
    val generateTasksFailed: Boolean = false,
    val generateTasksNoResults: Boolean = false,
)

data class RecordingsListState(
    val recordings: List<Recording> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val playingRecordingId: String? = null,
    val downloadingRecordingId: String? = null,
    val isPlaybackPaused: Boolean = false,
    val playbackPositionMs: Long = 0,
    val playbackDurationMs: Long = 0,
    val filterFolderId: String? = null,
    val isMultiSelectActive: Boolean = false,
    val selectedRecordingIds: Set<String> = emptySet(),
    val isBulkDeleting: Boolean = false,
    val isBulkMoving: Boolean = false,
    val isCollectiveSummarizing: Boolean = false,
    val collectiveSummarizeError: String? = null,
    val collectiveSummarizeResult: CollectiveSummary? = null,
    val showMultiSelectHint: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val needsInternetDialog: NeedsInternetReason? = null,
    val shareWithUserTarget: Recording? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val folderRepository: FolderRepository,
    private val storageRepository: StorageRepository,
    private val localAudioManager: LocalAudioManager,
    private val actionItemRepository: ActionItemRepository,
    private val collectiveSummaryRepository: CollectiveSummaryRepository,
    private val navPreferenceRepository: NavPreferenceRepository,
    private val playbackCommandRepository: PlaybackCommandRepository,
    private val recordingDao: RecordingDao,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordingsListState())
    val state: StateFlow<RecordingsListState> = _state

    private val _sheetState = MutableStateFlow(TranscriptSheetState())
    val sheetState: StateFlow<TranscriptSheetState> = _sheetState

    private var mediaPlayer: MediaPlayer? = null
    private var recordingsJob: Job? = null
    private var positionPollJob: Job? = null
    private var actionItemsJob: Job? = null
    private var currentPlayingTitle: String = ""

    init {
        observeRecordings()
        viewModelScope.launch {
            playbackCommandRepository.commands.collect { cmd ->
                when (cmd) {
                    is PlaybackCommand.PlayPause ->
                        if (_state.value.isPlaybackPaused) resumePlayback() else pausePlayback()
                    is PlaybackCommand.SkipForward -> skipForward5()
                    is PlaybackCommand.SkipBackward -> skipBackward5()
                }
            }
        }
        viewModelScope.launch {
            folderRepository.observeFolders().collect { folders ->
                _state.update { it.copy(folders = folders) }
            }
        }
        viewModelScope.launch {
            val dismissCount = navPreferenceRepository.multiSelectHintDismissCount.first()
            if (dismissCount % 5 == 0) {
                _state.update { it.copy(showMultiSelectHint = true) }
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
                _state.update { it.copy(recordings = recordings, isLoading = false) }
            }
        }
    }

    fun filterByFolder(folderId: String?) {
        _state.update { it.copy(filterFolderId = folderId, isLoading = true) }
        observeRecordings(folderId)
    }

    fun playAudio(recording: Recording) {
        stopPlayback()
        currentPlayingTitle = recording.title
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = recordingDao.getById(recording.id)
                val localPath = entity?.localAudioPath
                val needsDownload = localPath == null || !File(localPath).exists()
                val strategy = if (needsDownload) navPreferenceRepository.deviceSyncStrategy.first() else null
                if (needsDownload && strategy == "on_demand") {
                    _state.update { it.copy(downloadingRecordingId = recording.id) }
                }
                val dataSource = when {
                    !needsDownload -> requireNotNull(localPath) { "localPath should exist when needsDownload is false" }
                    strategy == "on_demand" -> {
                        val url = storageRepository.getDownloadUrl(recording.audioPath).toString()
                        val tmpFile = java.io.File(context.cacheDir, "${recording.id}_dl.m4a")
                        storageRepository.downloadFromUrl(url, tmpFile)
                        val savedPath = localAudioManager.saveAudio(recording.id, tmpFile)
                        recordingDao.updateLocalAudioPath(recording.id, savedPath)
                        _state.update { it.copy(downloadingRecordingId = null) }
                        savedPath
                    }
                    else -> storageRepository.getDownloadUrl(recording.audioPath).toString()
                }
                mediaPlayer = buildAndPreparePlayer(dataSource, recording)
            } catch (e: Exception) {
                _state.update { it.copy(downloadingRecordingId = null) }
                Timber.e("Playback failed: %s", e.message)
            }
        }
    }

    fun stopPlayback() {
        positionPollJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        _state.update { it.copy(
            playingRecordingId = null,
            isPlaybackPaused = false,
            playbackPositionMs = 0,
            playbackDurationMs = 0,
            playbackSpeed = 1.0f,
        )}
        context.startService(PlaybackService.buildStopIntent(context))
    }

    fun pausePlayback() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        positionPollJob?.cancel()
        _state.update { it.copy(isPlaybackPaused = true) }
        context.startService(
            PlaybackService.buildUpdateIntent(context, currentPlayingTitle, isPlaying = false)
        )
    }

    fun resumePlayback() {
        try { mediaPlayer?.start() } catch (_: Exception) {}
        startPositionPolling()
        _state.update { it.copy(isPlaybackPaused = false) }
        context.startService(
            PlaybackService.buildUpdateIntent(context, currentPlayingTitle, isPlaying = true)
        )
    }

    fun seekTo(positionMs: Long) {
        try { mediaPlayer?.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        _state.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun skipForward15() {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition + 15_000).coerceAtMost(mp.duration)
        seekTo(newPos.toLong())
    }

    fun skipBackward15() {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition - 15_000).coerceAtLeast(0)
        seekTo(newPos.toLong())
    }

    fun skipForward5() {
        val mp = mediaPlayer ?: return
        seekTo((mp.currentPosition + 5_000L).coerceAtMost(mp.duration.toLong()))
    }

    fun skipBackward5() {
        val mp = mediaPlayer ?: return
        seekTo((mp.currentPosition - 5_000L).coerceAtLeast(0L))
    }

    private val speedSteps = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    fun cycleSpeed() {
        val current = _state.value.playbackSpeed
        val idx = speedSteps.indexOf(current).takeIf { it >= 0 } ?: 1
        val next = speedSteps[(idx + 1) % speedSteps.size]
        setSpeed(next)
    }

    fun setSpeed(speed: Float) {
        try {
            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(speed)
        } catch (_: Exception) {}
        _state.update { it.copy(playbackSpeed = speed) }
    }

    private fun buildAndPreparePlayer(dataSource: String, recording: Recording): MediaPlayer =
        MediaPlayer().apply {
            setDataSource(dataSource)
            setOnPreparedListener {
                it.start()
                _state.update { s -> s.copy(
                    playingRecordingId = recording.id,
                    isPlaybackPaused = false,
                    playbackDurationMs = it.duration.toLong(),
                    playbackPositionMs = 0,
                )}
                startPositionPolling()
                context.startForegroundService(
                    PlaybackService.buildStartIntent(context, recording.title, isPlaying = true)
                )
            }
            setOnCompletionListener {
                positionPollJob?.cancel()
                _state.update { it.copy(
                    playingRecordingId = null,
                    isPlaybackPaused = false,
                    playbackPositionMs = 0,
                )}
                context.startService(PlaybackService.buildStopIntent(context))
            }
            prepareAsync()
        }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100L)
                val mp = mediaPlayer ?: break
                try {
                    _state.update { it.copy(playbackPositionMs = mp.currentPosition.toLong()) }
                } catch (_: Exception) { break }
            }
        }
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

    fun requestShareWithUser(recording: Recording) {
        if (!connectivityObserver.isCurrentlyOnline()) {
            _state.update { it.copy(needsInternetDialog = NeedsInternetReason.ShareWithUser) }
            return
        }
        _state.update { it.copy(shareWithUserTarget = recording) }
    }

    fun clearShareWithUser() {
        _state.update { it.copy(shareWithUserTarget = null) }
    }

    fun shareAudio(context: Context, recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "${recording.id}.m4a")
                val localPath = recordingDao.getById(recording.id)?.localAudioPath
                if (localPath != null && File(localPath).exists()) {
                    File(localPath).copyTo(tempFile, overwrite = true)
                } else {
                    val url = storageRepository.getDownloadUrl(recording.audioPath)
                    java.net.URL(url.toString()).openStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
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

    fun copyTranscriptToClipboard(recording: Recording) {
        val text = recording.transcription ?: return
        val clip = ClipData.newPlainText("Transcript", text)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        _state.update { it.copy(snackbarMessage = "Transcript copied") }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun openTranscriptSheet(recording: Recording) {
        _sheetState.value = TranscriptSheetState()
        if (recording.summary != null) {
            _sheetState.update { it.copy(summaryState = SummaryState.Loaded(recording.summary)) }
        }
        actionItemsJob?.cancel()
        actionItemsJob = viewModelScope.launch {
            actionItemRepository.observeByRecordingId(recording.id).collect { items ->
                _sheetState.update { it.copy(actionItems = items, actionItemsLoaded = true) }
            }
        }
    }

    fun generateTasks(recording: Recording) {
        if (_sheetState.value.isGeneratingTasks) return
        if (recording.transcription == null) {
            if (!connectivityObserver.isCurrentlyOnline()) {
                _state.update { it.copy(needsInternetDialog = NeedsInternetReason.GenerateTasks) }
                return
            }
            if (recording.syncStatus != SyncStatus.SYNCED) {
                _state.update { it.copy(needsInternetDialog = NeedsInternetReason.StillProcessing) }
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _sheetState.update {
                it.copy(isGeneratingTasks = true, generateTasksFailed = false, generateTasksNoResults = false)
            }
            try {
                val tz = navPreferenceRepository.appTimezone.first()
                val count = actionItemRepository.retryExtractActionItems(recording.id, tz)
                // New items arrive via the snapshot listener; only handle the no-results case.
                if (count == 0) _sheetState.update { it.copy(generateTasksNoResults = true) }
            } catch (e: Exception) {
                Timber.e("generateTasks failed: %s", e.message)
                _sheetState.update { it.copy(generateTasksFailed = true) }
            } finally {
                _sheetState.update { it.copy(isGeneratingTasks = false) }
            }
        }
    }

    fun retryProcessing(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordingRepository.updateProcessingFailed(recording.id, false)
                val tz = navPreferenceRepository.appTimezone.first()
                recordingRepository.invokeProcessRecording(recording.id, tz)
            } catch (e: Exception) {
                Timber.e("retryProcessing failed: %s", e.message)
                try { recordingRepository.updateProcessingFailed(recording.id, true) } catch (_: Exception) {}
            }
        }
    }

    fun dismissNeedsInternetDialog() {
        _state.update { it.copy(needsInternetDialog = null) }
    }

    fun generateSummary(recording: Recording) {
        if (recording.summary != null) {
            _sheetState.update { it.copy(summaryState = SummaryState.Loaded(recording.summary)) }
            return
        }
        if (_sheetState.value.summaryState is SummaryState.Loading) return

        if (recording.transcription == null) {
            if (!connectivityObserver.isCurrentlyOnline()) {
                _state.update { it.copy(needsInternetDialog = NeedsInternetReason.GenerateSummary) }
                return
            }
            if (recording.syncStatus != SyncStatus.SYNCED) {
                _state.update { it.copy(needsInternetDialog = NeedsInternetReason.StillProcessing) }
                return
            }
        }

        _sheetState.update { it.copy(summaryState = SummaryState.Loading) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = recordingRepository.generateSummary(recording.id)
                _sheetState.update { it.copy(summaryState = SummaryState.Loaded(summary)) }
            } catch (e: Exception) {
                Timber.e("Summary generation failed: %s", e.message)
                _sheetState.update {
                    it.copy(summaryState = SummaryState.Error("Unable to generate summary. Please try again."))
                }
            }
        }
    }

    // Multi-select

    fun dismissMultiSelectHint() {
        _state.update { it.copy(showMultiSelectHint = false) }
        viewModelScope.launch {
            navPreferenceRepository.incrementMultiSelectHintDismissCount()
        }
    }

    fun enterMultiSelect(recordingId: String) {
        _state.update { it.copy(
            isMultiSelectActive = true,
            selectedRecordingIds = setOf(recordingId),
            showMultiSelectHint = false,
        )}
        viewModelScope.launch {
            navPreferenceRepository.incrementMultiSelectHintDismissCount()
        }
    }

    fun toggleSelection(recordingId: String) {
        _state.update { state ->
            state.copy(
                selectedRecordingIds = if (recordingId in state.selectedRecordingIds)
                    state.selectedRecordingIds - recordingId
                else
                    state.selectedRecordingIds + recordingId,
            )
        }
    }

    fun selectAll() {
        _state.update { it.copy(selectedRecordingIds = it.recordings.map { r -> r.id }.toSet()) }
    }

    fun deselectAll() {
        _state.update { it.copy(selectedRecordingIds = emptySet()) }
    }

    fun exitMultiSelect() {
        _state.update { it.copy(
            isMultiSelectActive = false,
            selectedRecordingIds = emptySet(),
            collectiveSummarizeError = null,
            collectiveSummarizeResult = null,
        )}
    }

    fun bulkDelete(recordings: List<Recording>) {
        _state.update { it.copy(isBulkDeleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordings.forEach { if (_state.value.playingRecordingId == it.id) stopPlayback() }
                recordingRepository.deleteRecordings(recordings)
                _state.update { it.copy(isBulkDeleting = false) }
                exitMultiSelect()
            } catch (e: Exception) {
                Timber.e("Bulk delete failed: %s", e.message)
                _state.update { it.copy(isBulkDeleting = false) }
            }
        }
    }

    fun bulkMoveToFolder(recordingIds: List<String>, folderId: String) {
        _state.update { it.copy(isBulkMoving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordingRepository.moveRecordingsToFolder(recordingIds, folderId)
                _state.update { it.copy(isBulkMoving = false) }
                exitMultiSelect()
            } catch (e: Exception) {
                Timber.e("Bulk move failed: %s", e.message)
                _state.update { it.copy(isBulkMoving = false) }
            }
        }
    }

    fun collectiveSummarize(recordingIds: List<String>) {
        if (!connectivityObserver.isCurrentlyOnline()) {
            _state.update { it.copy(needsInternetDialog = NeedsInternetReason.CollectiveSummarize) }
            return
        }
        _state.update { it.copy(
            isCollectiveSummarizing = true,
            collectiveSummarizeError = null,
            collectiveSummarizeResult = null,
        )}
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = collectiveSummaryRepository.generateCollectiveSummary(recordingIds)
                _state.update { it.copy(
                    isCollectiveSummarizing = false,
                    collectiveSummarizeResult = result,
                )}
                exitMultiSelect()
            } catch (e: Exception) {
                Timber.e(e, "Collective summarize failed")
                val msg = when {
                    e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "None of the selected recordings have transcripts to summarize."
                    e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND ->
                        "Summarization function not found. Please ensure it is deployed."
                    e is FirebaseFunctionsException ->
                        "Summarization failed: ${e.message ?: e.code.name}"
                    else -> "Summarization failed: ${e.message ?: "Unknown error"}"
                }
                _state.update { it.copy(
                    isCollectiveSummarizing = false,
                    collectiveSummarizeError = msg,
                )}
            }
        }
    }

    fun clearCollectiveSummarizeError() {
        _state.update { it.copy(collectiveSummarizeError = null) }
    }

    fun clearCollectiveSummarizeResult() {
        _state.update { it.copy(collectiveSummarizeResult = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
