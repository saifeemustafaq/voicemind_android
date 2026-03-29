package com.voicemind.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.data.repository.FolderRepository
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.voicemind.util.countByFolder
import javax.inject.Inject

enum class FolderSort(val key: String) {
    Recency("recency"),
    Count("count");

    companion object {
        fun fromKey(key: String) = values().find { it.key == key } ?: Recency
    }
}

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val folderRecordingCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val sort: FolderSort = FolderSort.Recency,
    val sharedItemsUnreadCount: Int = 0,
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordingRepository: RecordingRepository,
    private val navPreferenceRepository: NavPreferenceRepository,
    private val sharingRepository: SharingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                folderRepository.observeFolders(),
                recordingRepository.observeRecordings(),
                navPreferenceRepository.folderSort,
            ) { folders, recordings, sortKey ->
                Triple(folders, recordings, FolderSort.fromKey(sortKey))
            }.collect { (folders, recordings, sort) ->
                val counts = recordings.countByFolder()
                _uiState.update { current ->
                    current.copy(
                        folders = sortFolders(folders, recordings, counts, sort),
                        folderRecordingCounts = counts,
                        isLoading = false,
                        sort = sort,
                    )
                }
            }
        }
        viewModelScope.launch {
            sharingRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(sharedItemsUnreadCount = count) }
            }
        }
    }

    private fun sortFolders(
        folders: List<Folder>,
        recordings: List<Recording>,
        counts: Map<String, Int>,
        sort: FolderSort,
    ): List<Folder> = when (sort) {
        FolderSort.Recency -> {
            val latestByFolder = recordings
                .groupBy { it.folderId }
                .mapValues { (_, recs) -> recs.maxOfOrNull { it.createdAt?.seconds ?: 0L } ?: 0L }
            folders.sortedByDescending { latestByFolder[it.id] ?: 0L }
        }
        FolderSort.Count -> folders.sortedByDescending { counts[it.id] ?: 0 }
    }

    fun setSortOrder(sort: FolderSort) {
        viewModelScope.launch {
            navPreferenceRepository.setFolderSort(sort.key)
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
