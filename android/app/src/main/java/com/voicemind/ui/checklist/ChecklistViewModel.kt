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
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val actionItemRepository: ActionItemRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChecklistUiState())
    val uiState: StateFlow<ChecklistUiState> = _uiState

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            combine(
                actionItemRepository.observeActionItems(),
                recordingRepository.observeRecordings(),
                _selectedIds,
            ) { items, recordings, selected ->
                val titleMap = recordings.associate { it.id to it.title }
                val (todoItems, doneItems) = items.partition { !it.completed }
                val validIds = items.map { it.id }.toSet()
                ChecklistUiState(
                    todoItems = todoItems,
                    doneItems = doneItems,
                    recordingTitles = titleMap,
                    isLoading = false,
                    selectedIds = selected.intersect(validIds),
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

    fun onLongPress(itemId: String) {
        _selectedIds.value = _selectedIds.value + itemId
    }

    fun toggleSelection(itemId: String) {
        _selectedIds.value = _selectedIds.value.let { current ->
            if (itemId in current) current - itemId else current + itemId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.deleteItems(ids)
        }
    }

    fun completeSelected() {
        val state = _uiState.value
        val incompleteIds = state.selectedIds.filter { id ->
            state.todoItems.any { it.id == id }
        }
        _selectedIds.value = emptySet()
        if (incompleteIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            actionItemRepository.markCompleted(incompleteIds, true)
        }
    }
}
