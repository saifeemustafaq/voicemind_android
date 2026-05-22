package com.voicemind.ui.summaries

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.data.repository.CollectiveSummaryRepository
import com.voicemind.data.repository.NavPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SummariesUiState(
    val summaries: List<CollectiveSummary> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSummary: CollectiveSummary? = null,
    val isDeleting: Boolean = false,
    val showInfoSheet: Boolean = false,
)

@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val collectiveSummaryRepository: CollectiveSummaryRepository,
    private val navPreferenceRepository: NavPreferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SummariesUiState())
    val state: StateFlow<SummariesUiState> = _state

    init {
        viewModelScope.launch {
            collectiveSummaryRepository.observeSummaries().collect { summaries ->
                _state.value = _state.value.copy(summaries = summaries, isLoading = false)
            }
        }
        viewModelScope.launch {
            val alreadyShown = navPreferenceRepository.summariesInfoShown.first()
            if (!alreadyShown) {
                _state.value = _state.value.copy(showInfoSheet = true)
            }
        }
    }

    fun showInfoSheet() {
        _state.value = _state.value.copy(showInfoSheet = true)
    }

    fun dismissInfoSheet() {
        _state.value = _state.value.copy(showInfoSheet = false)
        viewModelScope.launch {
            navPreferenceRepository.setSummariesInfoShown(true)
        }
    }

    fun selectSummary(summary: CollectiveSummary) {
        _state.value = _state.value.copy(selectedSummary = summary)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedSummary = null)
    }

    fun deleteSummary(summaryId: String) {
        _state.value = _state.value.copy(isDeleting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                collectiveSummaryRepository.deleteSummary(summaryId)
                _state.value = _state.value.copy(isDeleting = false, selectedSummary = null)
            } catch (e: Exception) {
                Timber.e("Delete summary failed: %s", e.message)
                _state.value = _state.value.copy(isDeleting = false)
            }
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val clip = ClipData.newPlainText("Summary", text)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Summary"))
    }
}
