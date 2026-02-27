package com.voicemind.ui.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.voicemind.data.model.ActionItem
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class TaskDetailUiState(
    val item: ActionItem? = null,
    val recordingTitle: String? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actionItemRepository: ActionItemRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            actionItemRepository.observeActionItem(itemId).collect { item ->
                if (item == null && !_uiState.value.isDeleted) {
                    _uiState.value = _uiState.value.copy(item = null, isLoading = false)
                    return@collect
                }
                val recordingTitle = item?.recordingId?.let { rid ->
                    try {
                        recordingRepository.getRecording(rid)?.title
                    } catch (_: Exception) { null }
                }
                _uiState.value = _uiState.value.copy(
                    item = item,
                    recordingTitle = recordingTitle,
                    isLoading = false,
                )
            }
        }
    }

    fun renameItem(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.updateTitle(itemId, trimmed)
        }
    }

    fun toggleCompleted() {
        val current = _uiState.value.item ?: return
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.toggleCompleted(itemId, !current.completed)
        }
    }

    fun setDueDate(dateMillis: Long?, hour: Int?, minute: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            val ts = if (dateMillis != null) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, hour ?: 0)
                    set(Calendar.MINUTE, minute ?: 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Timestamp(Date(cal.timeInMillis))
            } else null
            actionItemRepository.updateDueDate(itemId, ts)
        }
    }

    fun setDeadline(dateMillis: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            val ts = if (dateMillis != null) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Timestamp(Date(cal.timeInMillis))
            } else null
            actionItemRepository.updateDeadline(itemId, ts)
        }
    }

    fun updateNotes(notes: String) {
        val value = notes.trim().ifEmpty { null }
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.updateNotes(itemId, value)
        }
    }

    fun deleteItem() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isDeleted = true)
            actionItemRepository.deleteItem(itemId)
        }
    }
}
