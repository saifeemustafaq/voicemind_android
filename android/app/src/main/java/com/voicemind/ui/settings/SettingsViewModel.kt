package com.voicemind.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.voicemind.data.repository.AuthRepository
import com.voicemind.data.repository.GoogleTasksRepository
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.repository.NtsSettings
import com.voicemind.data.repository.TasksConnectResult
import com.voicemind.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val navPreferenceRepository: NavPreferenceRepository,
    private val authRepository: AuthRepository,
    private val googleTasksRepository: GoogleTasksRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : ViewModel() {

    val userDisplayText: String
        get() = authRepository.currentUser?.email
            ?: authRepository.currentUser?.displayName
            ?: "Signed in"

    val useSidebar: StateFlow<Boolean> = navPreferenceRepository.useSidebar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultLandingPage: StateFlow<String> = navPreferenceRepository.defaultLandingPage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "recordings")

    fun setDefaultLandingPage(route: String) {
        viewModelScope.launch(Dispatchers.IO) {
            navPreferenceRepository.setDefaultLandingPage(route)
        }
    }

    fun reorderNavItems(newOrder: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            navPreferenceRepository.setNavOrder(newOrder)
        }
    }

    val navOrder: StateFlow<List<String>> = navPreferenceRepository.navOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("folders", "summaries", "checklist", "recordings"))

    fun moveNavItem(route: String, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = navOrder.value.toMutableList()
            val index = current.indexOf(route)
            if (index == -1) return@launch
            val newIndex = if (moveUp) index - 1 else index + 1
            if (newIndex < 0 || newIndex >= current.size) return@launch
            current.removeAt(index)
            current.add(newIndex, route)
            navPreferenceRepository.setNavOrder(current)
        }
    }

    // ── Timezone ──────────────────────────────────────────────────────────────

    val appTimezone: StateFlow<String> = navPreferenceRepository.appTimezone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeZone.getDefault().id)

    val isAutoTimezone: StateFlow<Boolean> = navPreferenceRepository.isAutoTimezone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setTimezone(timezoneId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            navPreferenceRepository.setTimezone(timezoneId)
            val resolved = timezoneId ?: TimeZone.getDefault().id
            userSettingsRepository.syncTimezoneToFirestore(resolved)
        }
    }

    // ── Natural Time Selection ────────────────────────────────────────────

    val ntsSettings: StateFlow<NtsSettings> = userSettingsRepository.observeNtsSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NtsSettings())

    fun setNtsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val tz = navPreferenceRepository.appTimezone.first()
            userSettingsRepository.setNtsEnabledWithTimezone(enabled, tz)
        }
    }

    fun setNtsStartTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userSettingsRepository.setNtsStartTime(hour, minute)
        }
    }

    fun setNtsIntervalMinutes(interval: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userSettingsRepository.setNtsIntervalMinutes(interval)
        }
    }

    // ── Privacy ──────────────────────────────────────────────────────────────

    val discoverable: StateFlow<Boolean> = userSettingsRepository.observeDiscoverable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDiscoverable(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userSettingsRepository.setDiscoverable(enabled)
        }
    }

    // ── Integrations ────────────────────────────────────────────────────────

    val tasksConnected: StateFlow<Boolean> = googleTasksRepository.observeTasksConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _tasksLoading = MutableStateFlow(false)
    val tasksLoading: StateFlow<Boolean> = _tasksLoading

    private val _tasksError = MutableStateFlow<String?>(null)
    val tasksError: StateFlow<String?> = _tasksError

    fun toggleNavMode() {
        viewModelScope.launch(Dispatchers.IO) {
            navPreferenceRepository.setUseSidebar(!useSidebar.value)
        }
    }

    fun connectTasks(activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            _tasksLoading.value = true
            _tasksError.value = null
            when (val result = googleTasksRepository.requestTasksAccess(activity)) {
                is TasksConnectResult.Success -> { /* connected, Firestore listener will update */ }
                is TasksConnectResult.NeedsConsent -> {
                    _pendingConsentResult.value = result.result
                }
                is TasksConnectResult.Error -> {
                    _tasksError.value = result.message
                }
            }
            _tasksLoading.value = false
        }
    }

    private val _pendingConsentResult = MutableStateFlow<AuthorizationResult?>(null)
    val pendingConsentResult: StateFlow<AuthorizationResult?> = _pendingConsentResult

    fun onConsentResultHandled(result: AuthorizationResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _tasksLoading.value = true
            _pendingConsentResult.value = null
            when (val connectResult = googleTasksRepository.handleConsentResult(result)) {
                is TasksConnectResult.Success -> { /* connected */ }
                is TasksConnectResult.NeedsConsent -> {
                    _tasksError.value = "Consent still required"
                }
                is TasksConnectResult.Error -> {
                    _tasksError.value = connectResult.message
                }
            }
            _tasksLoading.value = false
        }
    }

    fun disconnectTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            _tasksLoading.value = true
            _tasksError.value = null
            val success = googleTasksRepository.disconnectTasks()
            if (!success) {
                _tasksError.value = "Failed to disconnect. Please try again."
            }
            _tasksLoading.value = false
        }
    }

    fun clearTasksError() {
        _tasksError.value = null
    }
}
