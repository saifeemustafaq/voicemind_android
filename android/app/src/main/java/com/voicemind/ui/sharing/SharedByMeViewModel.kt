package com.voicemind.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.CollectiveSummaryRepository
import com.voicemind.data.repository.RecordingRepository
import com.voicemind.data.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class SharedByMeTab { Recordings, Tasks, Summaries }

data class RecipientUiModel(
    val shareId: String,
    val recipientUid: String,
    val recipientName: String,
    val recipientEmail: String,
    val sharedAt: Timestamp?,
)

data class SharedByMeItemUiModel(
    val itemId: String,
    val itemType: String,
    val itemTitle: String,
    val recipients: List<RecipientUiModel>,
)

data class SharedByMeUiState(
    val recordings: List<SharedByMeItemUiModel> = emptyList(),
    val tasks: List<SharedByMeItemUiModel> = emptyList(),
    val summaries: List<SharedByMeItemUiModel> = emptyList(),
    val selectedTab: SharedByMeTab = SharedByMeTab.Recordings,
    val isLoading: Boolean = true,
    val isRevoking: Set<String> = emptySet(),
)

@HiltViewModel
class SharedByMeViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val recordingRepository: RecordingRepository,
    private val collectiveSummaryRepository: CollectiveSummaryRepository,
    private val actionItemRepository: ActionItemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedByMeUiState())
    val uiState: StateFlow<SharedByMeUiState> = _uiState

    private val titleCache = ConcurrentHashMap<String, String>()

    init {
        viewModelScope.launch {
            sharingRepository.observeAllMyShares().collect { shares ->
                val grouped = withContext(Dispatchers.IO) {
                    shares
                        .groupBy { it.itemId }
                        .map { (itemId, itemShares) ->
                            val first = itemShares.first()
                            val title = titleCache.getOrPut(itemId) {
                                resolveTitle(first.itemType, itemId)
                            }
                            SharedByMeItemUiModel(
                                itemId = itemId,
                                itemType = first.itemType,
                                itemTitle = title,
                                recipients = itemShares.map { share ->
                                    RecipientUiModel(
                                        shareId = share.id,
                                        recipientUid = share.recipientUid,
                                        recipientName = share.recipientName,
                                        recipientEmail = share.recipientEmail,
                                        sharedAt = share.sharedAt,
                                    )
                                },
                            )
                        }
                }
                _uiState.update {
                    it.copy(
                        recordings = grouped.filter { item -> item.itemType == "recording" },
                        tasks = grouped.filter { item -> item.itemType == "task" },
                        summaries = grouped.filter { item -> item.itemType == "collectiveSummary" },
                        isLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun resolveTitle(itemType: String, itemId: String): String = try {
        when (itemType) {
            "recording" -> recordingRepository.getRecording(itemId)?.title ?: "Untitled Recording"
            "task" -> actionItemRepository.getActionItem(itemId)?.title ?: "Untitled Task"
            "collectiveSummary" -> collectiveSummaryRepository.getSummary(itemId)
                ?.summary
                ?.lines()
                ?.firstOrNull { it.isNotBlank() }
                ?.replace(Regex("^#{1,6}\\s+"), "")
                ?.trim()
                ?.take(60)
                ?: "Collective Summary"
            else -> "Shared Item"
        }
    } catch (e: Exception) {
        Timber.e(e, "resolveTitle")
        "Shared Item"
    }

    fun selectTab(tab: SharedByMeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun revokeShare(shareId: String, recipientUid: String) {
        _uiState.update { it.copy(isRevoking = it.isRevoking + shareId) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharingRepository.revokeShare(shareId, recipientUid)
            } catch (e: Exception) {
                Timber.e(e, "revokeShare")
            } finally {
                _uiState.update { it.copy(isRevoking = it.isRevoking - shareId) }
            }
        }
    }
}
