package com.voicemind.ui.checklist

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class ChecklistUiState(
    val todoItems: List<ActionItem> = emptyList(),
    val doneItems: List<ActionItem> = emptyList(),
    val recordingTitles: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val editingItem: ActionItem? = null,
)

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val actionItemRepository: ActionItemRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChecklistUiState())
    val uiState: StateFlow<ChecklistUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                actionItemRepository.observeActionItems(),
                recordingRepository.observeRecordings()
            ) { items, recordings ->
                val titleMap = recordings.associate { it.id to it.title }
                val (todoItems, doneItems) = items.partition { !it.completed }
                ChecklistUiState(
                    todoItems = todoItems,
                    doneItems = doneItems,
                    recordingTitles = titleMap,
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleCompleted(item: ActionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.toggleCompleted(item.id, !item.completed)
        }
    }

    fun deleteItem(item: ActionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.deleteItem(item.id)
        }
    }

    fun startEditingDates(item: ActionItem) {
        _uiState.value = _uiState.value.copy(editingItem = item)
    }

    fun stopEditingDates() {
        _uiState.value = _uiState.value.copy(editingItem = null)
    }

    fun setDueDate(item: ActionItem, dateMillis: Long?, hour: Int?, minute: Int?) {
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
            actionItemRepository.updateDueDate(item.id, ts)
            stopEditingDates()
        }
    }

    fun setDeadline(item: ActionItem, dateMillis: Long?) {
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
            actionItemRepository.updateDeadline(item.id, ts)
            stopEditingDates()
        }
    }
}
