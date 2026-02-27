package com.voicemind.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.Folder
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.voicemind.util.countByFolder
import javax.inject.Inject

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val folderRecordingCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = _uiState

    init {
        viewModelScope.launch {
            folderRepository.observeFolders().collect { folders ->
                _uiState.value = _uiState.value.copy(folders = folders, isLoading = false)
            }
        }
        viewModelScope.launch {
            recordingRepository.observeRecordings().collect { recordings ->
                _uiState.value = _uiState.value.copy(folderRecordingCounts = recordings.countByFolder())
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.createFolder(name.trim())
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.renameFolder(folderId, newName.trim())
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.reassignFolder(folderId, Folder.UNFILED_ID)
            folderRepository.deleteFolder(folderId)
        }
    }
}
