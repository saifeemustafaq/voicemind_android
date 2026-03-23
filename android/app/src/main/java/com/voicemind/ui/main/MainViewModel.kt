package com.voicemind.ui.main

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.voicemind.data.repository.GoogleTasksRepository
import com.voicemind.data.repository.TasksConnectResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the startup Tasks-sync prompt shown in MainActivity.
 * Mirrors the connect/consent flow used by SettingsViewModel, but is scoped to the
 * Activity so it lives only as long as the user's current session.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tasksRepository: GoogleTasksRepository,
) : ViewModel() {

    /**
     * `null`  = Firestore hasn't responded yet (avoid flashing dialog before we know).
     * `false` = confirmed not connected → show prompt.
     * `true`  = connected → hide prompt.
     */
    val tasksConnected: StateFlow<Boolean?> = tasksRepository.observeTasksConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Set when the Google consent resolution intent needs to be launched. */
    private val _pendingConsent = MutableStateFlow<AuthorizationResult?>(null)
    val pendingConsent: StateFlow<AuthorizationResult?> = _pendingConsent

    fun connectTasks(activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = tasksRepository.requestTasksAccess(activity)) {
                is TasksConnectResult.Success -> { /* Firestore listener updates tasksConnected */ }
                is TasksConnectResult.NeedsConsent -> _pendingConsent.value = result.result
                is TasksConnectResult.Error -> { /* silent — user can retry in Settings */ }
            }
        }
    }

    fun onConsentHandled(result: AuthorizationResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _pendingConsent.value = null
            tasksRepository.handleConsentResult(result)
            // Result is silent here; Firestore listener updates tasksConnected on success.
        }
    }
}
