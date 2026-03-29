package com.voicemind.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.voicemind.data.model.MyShare
import com.voicemind.data.repository.AuthRepository
import com.voicemind.data.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class FoundUser(
    val uid: String,
    val displayName: String,
    val email: String,
)

sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Found(val user: FoundUser) : LookupState
    data object NotFound : LookupState
    data object AlreadyShared : LookupState
    data class Error(val message: String) : LookupState
}

data class ShareUiState(
    val lookupState: LookupState = LookupState.Idle,
    val isSharing: Boolean = false,
    val shareSuccess: Boolean = false,
    val shareError: String? = null,
    val myShares: List<MyShare> = emptyList(),
    val isRevoking: Set<String> = emptySet(),
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState

    private var itemId = ""
    private var itemType = ""
    private var observeJob: Job? = null

    fun setItem(itemId: String, itemType: String) {
        _uiState.update { it.copy(lookupState = LookupState.Idle, shareError = null, shareSuccess = false) }
        if (this.itemId == itemId && this.itemType == itemType) return
        this.itemId = itemId
        this.itemType = itemType
        _uiState.value = ShareUiState()
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            sharingRepository.observeMyShares(itemId).collect { shares ->
                _uiState.update { it.copy(myShares = shares) }
            }
        }
    }

    fun findUser(email: String) {
        val trimmed = email.trim()
        if (!trimmed.contains("@")) {
            _uiState.update { it.copy(lookupState = LookupState.Error("Please enter a valid email address")) }
            return
        }
        _uiState.update { it.copy(lookupState = LookupState.Loading) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = sharingRepository.findUserByEmail(trimmed)
                val found = data["found"] as? Boolean ?: false
                val uid = data["uid"] as? String
                if (!found || uid == null) {
                    _uiState.update { it.copy(lookupState = LookupState.NotFound) }
                    return@launch
                }
                if (uid == authRepository.currentUser?.uid) {
                    _uiState.update { it.copy(lookupState = LookupState.Error("You can't share with yourself")) }
                    return@launch
                }
                if (_uiState.value.myShares.any { it.recipientUid == uid }) {
                    _uiState.update { it.copy(lookupState = LookupState.AlreadyShared) }
                    return@launch
                }
                val displayName = (data["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: trimmed
                _uiState.update {
                    it.copy(lookupState = LookupState.Found(FoundUser(uid = uid, displayName = displayName, email = trimmed)))
                }
            } catch (e: FirebaseFunctionsException) {
                _uiState.update {
                    it.copy(
                        lookupState = if (e.code == FirebaseFunctionsException.Code.NOT_FOUND)
                            LookupState.NotFound
                        else
                            LookupState.Error("Something went wrong. Please try again.")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "findUser")
                _uiState.update { it.copy(lookupState = LookupState.Error("Something went wrong. Please try again.")) }
            }
        }
    }

    fun shareItem() {
        val foundUser = (_uiState.value.lookupState as? LookupState.Found)?.user ?: return
        _uiState.update { it.copy(isSharing = true, shareError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharingRepository.shareItem(itemId, itemType, foundUser.uid)
                _uiState.update { it.copy(isSharing = false, shareSuccess = true, lookupState = LookupState.Idle) }
            } catch (e: FirebaseFunctionsException) {
                if (e.code == FirebaseFunctionsException.Code.ALREADY_EXISTS) {
                    _uiState.update { it.copy(isSharing = false, lookupState = LookupState.AlreadyShared) }
                } else {
                    _uiState.update { it.copy(isSharing = false, shareError = "Sharing failed. Please try again.") }
                }
            } catch (e: Exception) {
                Timber.e(e, "shareItem")
                _uiState.update { it.copy(isSharing = false, shareError = "Sharing failed. Please try again.") }
            }
        }
    }

    fun revokeShare(shareId: String, recipientUid: String) {
        _uiState.update { it.copy(isRevoking = it.isRevoking + shareId) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharingRepository.revokeShare(shareId, recipientUid)
            } catch (e: Exception) {
                Timber.e(e, "revokeShare")
            } finally {
                _uiState.update { it.copy(isRevoking = it.isRevoking - shareId) }
            }
        }
    }

    fun resetLookup() {
        _uiState.update { it.copy(lookupState = LookupState.Idle, shareError = null) }
    }

    fun clearShareSuccess() {
        _uiState.update { it.copy(shareSuccess = false) }
    }
}
