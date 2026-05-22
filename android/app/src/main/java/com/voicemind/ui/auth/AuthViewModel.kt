package com.voicemind.ui.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.voicemind.data.repository.AuthRepository
import com.voicemind.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreateAccount: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> = authRepository.authStateFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUser != null)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email and password are required")
            return
        }
        launchAuth { authRepository.signInWithEmail(email, password) }
    }

    fun signUpWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email and password are required")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        launchAuth { authRepository.signUpWithEmail(email, password) }
    }

    fun signInWithGoogle(idToken: String) {
        launchAuth { authRepository.signInWithGoogleCredential(idToken) }
    }

    private fun launchAuth(block: suspend () -> AuthResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = block()) {
                is AuthResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false)
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun toggleCreateAccount() {
        _uiState.value = _uiState.value.copy(
            isCreateAccount = !_uiState.value.isCreateAccount,
            error = null
        )
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, isLoading = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun signInWithGoogleCredential(activity: Activity) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(AuthRepository.WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                signInWithGoogle(idToken)
            } catch (e: GetCredentialCancellationException) {
                Timber.d("Google Sign-In cancelled by user")
            } catch (e: NoCredentialException) {
                Timber.e(e, "Google Sign-In: no credentials available")
                setError("No Google accounts found. Please add a Google account to your device and try again.")
            } catch (e: Exception) {
                Timber.e(e, "Google Sign-In failed: ${e.message}")
                setError("Google Sign-In failed: ${e.message}")
            }
        }
    }

    fun registerFcmToken() = authRepository.registerFcmToken()

    fun signOut() {
        authRepository.signOut()
    }
}
