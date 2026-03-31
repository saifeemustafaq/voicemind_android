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
    val selectedStrategy: String = "on_demand",
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

    fun selectStrategy(strategy: String) {
        _state.update { it.copy(selectedStrategy = strategy) }
    }

    fun confirm() {
        _state.update { it.copy(isConfirming = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val strategy = _state.value.selectedStrategy
                navPreferenceRepository.setDeviceSyncStrategy(strategy)
                initialSyncManager.runIfNeeded()
                if (strategy == "full") {
                    enqueueBulkDownload()
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
