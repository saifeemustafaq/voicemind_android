package com.voicemind.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.voicemind.util.countByFolder
import javax.inject.Inject

data class HomeUiState(
    val folders: List<Folder> = emptyList(),
    val recentRecordings: List<Recording> = emptyList(),
    val folderRecordingCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.seedDefaultsIfEmpty()
        }
        viewModelScope.launch {
            folderRepository.observeFolders().collect { folders ->
                val latestByFolder = _uiState.value.recentRecordings
                    .groupBy { it.folderId }
                    .mapValues { (_, recs) -> recs.mapNotNull { it.createdAt?.seconds }.maxOrNull() ?: 0L }
                val sortedFolders = folders.sortedByDescending { latestByFolder[it.id] ?: 0L }
                _uiState.value = _uiState.value.copy(folders = sortedFolders, isLoading = false)
            }
        }
        viewModelScope.launch {
            recordingRepository.observeRecordings().collect { recordings ->
                val latestByFolder = recordings
                    .groupBy { it.folderId }
                    .mapValues { (_, recs) -> recs.mapNotNull { it.createdAt?.seconds }.maxOrNull() ?: 0L }
                val sortedFolders = _uiState.value.folders
                    .sortedByDescending { latestByFolder[it.id] ?: 0L }
                _uiState.value = _uiState.value.copy(
                    folders = sortedFolders,
                    recentRecordings = recordings.take(4),
                    folderRecordingCounts = recordings.countByFolder(),
                    isLoading = false,
                )
            }
        }
    }
}
