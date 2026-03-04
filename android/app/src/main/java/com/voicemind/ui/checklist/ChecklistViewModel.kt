package com.voicemind.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.ActionItem
import com.voicemind.data.repository.ActionItemRepository
import com.voicemind.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChecklistUiState(
    val todoItems: List<ActionItem> = emptyList(),
    val doneItems: List<ActionItem> = emptyList(),
    val recordingTitles: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
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

    fun addItem(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.createItem(trimmed)
        }
    }
}
