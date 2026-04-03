package com.voicemind.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.sync.BulkDownloadWorker
import com.voicemind.data.sync.InitialSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DeviceSetupUiState(
    val hasAgreed: Boolean = false,
    val isConfirming: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DeviceSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navPreferenceRepository: NavPreferenceRepository,
    private val initialSyncManager: InitialSyncManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceSetupUiState())
    val state: StateFlow<DeviceSetupUiState> = _state

    fun toggleAgreed() {
        _state.update { it.copy(hasAgreed = !it.hasAgreed) }
    }

    fun confirm() {
        _state.update { it.copy(isConfirming = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                navPreferenceRepository.setDeviceSyncStrategy("full")
                navPreferenceRepository.setLocalStorageConsentShown(true)
                initialSyncManager.runIfNeeded()
                try {
                    enqueueBulkDownload()
                } catch (e: Exception) {
                    Timber.w(e, "DeviceSetupVM: failed to enqueue bulk download — continuing setup")
                }
                navPreferenceRepository.setDeviceSetupComplete(true)
                _state.update { it.copy(isConfirming = false) }
            } catch (e: Exception) {
                Timber.e(e, "DeviceSetupVM: confirm failed")
                _state.update { it.copy(isConfirming = false, error = "Setup failed. Please try again.") }
            }
        }
    }

    private fun enqueueBulkDownload() {
        val request = OneTimeWorkRequestBuilder<BulkDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BulkDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
