package com.voicemind.ui.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.repository.CollectiveSummaryRepository
import com.voicemind.data.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SharedSummaryDetailUiState(
    val summary: CollectiveSummary? = null,
    val ownerName: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDuplicating: Boolean = false,
    val duplicateSuccess: Boolean = false,
)

@HiltViewModel
class SharedSummaryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectiveSummaryRepository: CollectiveSummaryRepository,
    private val sharingRepository: SharingRepository,
) : ViewModel() {

    private val ownerUid: String = requireNotNull(savedStateHandle["ownerUid"])
    private val summaryId: String = requireNotNull(savedStateHandle["summaryId"])

    private val _state = MutableStateFlow(SharedSummaryDetailUiState())
    val state: StateFlow<SharedSummaryDetailUiState> = _state

    init {
        viewModelScope.launch {
            collectiveSummaryRepository.observeSharedSummary(ownerUid, summaryId).collect { summary ->
                _state.update { it.copy(summary = summary, isLoading = false) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sharedItem = sharingRepository.getSharedItem(summaryId)
                if (sharedItem != null) {
                    _state.update { it.copy(ownerName = sharedItem.ownerName) }
                }
            } catch (e: Exception) {
                Timber.w(e, "SharedSummaryDetailVM: owner name lookup failed")
            }
        }
    }

    fun duplicateSummary() {
        _state.update { it.copy(isDuplicating = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                collectiveSummaryRepository.duplicateSharedSummary(ownerUid, summaryId)
                _state.update { it.copy(isDuplicating = false, duplicateSuccess = true) }
            } catch (e: Exception) {
                Timber.e(e, "SharedSummaryDetailVM: duplication failed")
                _state.update { it.copy(isDuplicating = false, error = "Failed to duplicate summary") }
            }
        }
    }

    fun clearDuplicateSuccess() {
        _state.update { it.copy(duplicateSuccess = false) }
    }
}
