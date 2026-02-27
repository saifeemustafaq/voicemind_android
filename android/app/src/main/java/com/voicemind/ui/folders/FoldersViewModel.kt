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
import javax.inject.Inject

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
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
                _uiState.value = FoldersUiState(folders = folders, isLoading = false)
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
