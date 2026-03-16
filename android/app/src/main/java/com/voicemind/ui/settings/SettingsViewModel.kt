package com.voicemind.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.voicemind.data.repository.AuthRepository
import com.voicemind.data.repository.CalendarConnectResult
import com.voicemind.data.repository.GoogleCalendarRepository
import com.voicemind.data.repository.NavPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val navPreferenceRepository: NavPreferenceRepository,
    private val authRepository: AuthRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
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

    val calendarConnected: StateFlow<Boolean> = googleCalendarRepository.observeCalendarConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _calendarLoading = MutableStateFlow(false)
    val calendarLoading: StateFlow<Boolean> = _calendarLoading

    private val _calendarError = MutableStateFlow<String?>(null)
    val calendarError: StateFlow<String?> = _calendarError

    fun toggleNavMode() {
        viewModelScope.launch(Dispatchers.IO) {
            navPreferenceRepository.setUseSidebar(!useSidebar.value)
        }
    }

    fun connectCalendar(activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            _calendarLoading.value = true
            _calendarError.value = null
            when (val result = googleCalendarRepository.requestCalendarAccess(activity)) {
                is CalendarConnectResult.Success -> { /* connected, Firestore listener will update */ }
                is CalendarConnectResult.NeedsConsent -> {
                    _pendingConsentResult.value = result.result
                }
                is CalendarConnectResult.Error -> {
                    _calendarError.value = result.message
                }
            }
            _calendarLoading.value = false
        }
    }

    private val _pendingConsentResult = MutableStateFlow<AuthorizationResult?>(null)
    val pendingConsentResult: StateFlow<AuthorizationResult?> = _pendingConsentResult

    fun onConsentResultHandled(result: AuthorizationResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _calendarLoading.value = true
            _pendingConsentResult.value = null
            when (val connectResult = googleCalendarRepository.handleConsentResult(result)) {
                is CalendarConnectResult.Success -> { /* connected */ }
                is CalendarConnectResult.NeedsConsent -> {
                    _calendarError.value = "Consent still required"
                }
                is CalendarConnectResult.Error -> {
                    _calendarError.value = connectResult.message
                }
            }
            _calendarLoading.value = false
        }
    }

    fun disconnectCalendar() {
        viewModelScope.launch(Dispatchers.IO) {
            _calendarLoading.value = true
            _calendarError.value = null
            val success = googleCalendarRepository.disconnectCalendar()
            if (!success) {
                _calendarError.value = "Failed to disconnect. Please try again."
            }
            _calendarLoading.value = false
        }
    }

    fun clearCalendarError() {
        _calendarError.value = null
    }
}
