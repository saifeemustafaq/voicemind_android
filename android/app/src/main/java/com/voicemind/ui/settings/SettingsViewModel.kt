package com.voicemind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicemind.data.repository.NavPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val navPreferenceRepository: NavPreferenceRepository,
) : ViewModel() {

    val useSidebar: StateFlow<Boolean> = navPreferenceRepository.useSidebar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleNavMode() {
        viewModelScope.launch {
            navPreferenceRepository.setUseSidebar(!useSidebar.value)
        }
    }
}
