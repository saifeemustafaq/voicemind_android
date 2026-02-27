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
import javax.inject.Inject

data class HomeUiState(
    val folders: List<Folder> = emptyList(),
    val recentRecordings: List<Recording> = emptyList(),
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
                _uiState.value = _uiState.value.copy(folders = folders, isLoading = false)
            }
        }
        viewModelScope.launch {
            recordingRepository.observeRecordings().collect { recordings ->
                _uiState.value = _uiState.value.copy(
                    recentRecordings = recordings.take(10),
                    isLoading = false
                )
            }
        }
    }
}
