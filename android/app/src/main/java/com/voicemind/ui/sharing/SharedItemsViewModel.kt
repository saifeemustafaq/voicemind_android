package com.voicemind.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.voicemind.data.local.LocalAudioManager
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.CollectiveSummaryRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

enum class SharedItemsTab { Recordings, Tasks, Summaries }

data class SharedTaskUiModel(
    val id: String,
    val title: String,
    val sharedFromName: String,
    val completed: Boolean,
)

data class SharedItemUiModel(
    val shareId: String,
    val ownerUid: String,
    val ownerName: String,
    val ownerEmail: String,
    val itemType: String,
    val itemId: String,
    val itemTitle: String,
    val sharedAt: Timestamp?,
    val isRead: Boolean,
)

data class SharedItemsUiState(
    val recordings: List<SharedItemUiModel> = emptyList(),
    val summaries: List<SharedItemUiModel> = emptyList(),
    val tasks: List<SharedTaskUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: SharedItemsTab = SharedItemsTab.Recordings,
)

@HiltViewModel
class SharedItemsViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val localAudioManager: LocalAudioManager,
    private val recordingRepository: RecordingRepository,
    private val actionItemRepository: ActionItemRepository,
    private val collectiveSummaryRepository: CollectiveSummaryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedItemsUiState())
    val uiState: StateFlow<SharedItemsUiState> = _uiState

    init {
        viewModelScope.launch {
            sharingRepository.observeSharedWithMe().collect { items ->
                val enriched = withContext(Dispatchers.IO) {
                    items.map { item ->
                        async {
                            val title = when (item.itemType) {
                                "recording" -> recordingRepository
                                    .getSharedRecording(item.ownerUid, item.itemId)
                                    ?.title ?: "Untitled Recording"
                                "collectiveSummary" -> collectiveSummaryRepository
                                    .getSharedSummary(item.ownerUid, item.itemId)
                                    ?.summary
                                    ?.lines()
                                    ?.firstOrNull { it.isNotBlank() }
                                    ?.replace(Regex("^#{1,6}\\s+"), "")
                                    ?.trim()
                                    ?.take(60)
                                    ?: "Collective Summary"
                                else -> "Shared Item"
                            }
                            SharedItemUiModel(
                                shareId = item.id,
                                ownerUid = item.ownerUid,
                                ownerName = item.ownerName,
                                ownerEmail = item.ownerEmail,
                                itemType = item.itemType,
                                itemId = item.itemId,
                                itemTitle = title,
                                sharedAt = item.sharedAt,
                                isRead = item.isRead,
                            )
                        }
                    }.awaitAll()
                }
                _uiState.update { current ->
                    current.copy(
                        recordings = enriched.filter { it.itemType == "recording" },
                        summaries = enriched.filter { it.itemType == "collectiveSummary" },
                        isLoading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            actionItemRepository.observeSharedTasks().collect { items ->
                val taskUiModels = items
                    .sortedByDescending { it.createdAt }
                    .map { item ->
                        SharedTaskUiModel(
                            id = item.id,
                            title = item.title,
                            sharedFromName = item.sharedFromName ?: "",
                            completed = item.completed,
                        )
                    }
                _uiState.update { it.copy(tasks = taskUiModels) }
            }
        }
        markAllAsRead()
    }

    fun selectTab(tab: SharedItemsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun dismiss(shareId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = _uiState.value.recordings.find { it.shareId == shareId }
                if (item != null && item.itemType == "recording") {
                    localAudioManager.deleteSharedAudio("${item.ownerUid}_${item.itemId}")
                }
                sharingRepository.dismissSharedItem(shareId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to dismiss shared item")
            }
        }
    }

    private fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharingRepository.observeSharedWithMe()
                    .first()
                    .filter { !it.isRead }
                    .forEach { sharingRepository.markAsRead(it.id) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark items as read")
            }
        }
    }
}
