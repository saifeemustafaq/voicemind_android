package com.voicemind.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.voicemind.BuildConfig
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.VmDimens

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val useSidebar by settingsViewModel.useSidebar.collectAsStateWithLifecycle()
    val defaultLandingPage by settingsViewModel.defaultLandingPage.collectAsStateWithLifecycle()
    val navOrder by settingsViewModel.navOrder.collectAsStateWithLifecycle()
    val appTimezone by settingsViewModel.appTimezone.collectAsStateWithLifecycle()
    val isAutoTimezone by settingsViewModel.isAutoTimezone.collectAsStateWithLifecycle()
    val tasksConnected by settingsViewModel.tasksConnected.collectAsStateWithLifecycle()
    val tasksLoading by settingsViewModel.tasksLoading.collectAsStateWithLifecycle()
    val tasksError by settingsViewModel.tasksError.collectAsStateWithLifecycle()
    val pendingConsent by settingsViewModel.pendingConsentResult.collectAsStateWithLifecycle()
    val ntsSettings by settingsViewModel.ntsSettings.collectAsStateWithLifecycle()
    val discoverable by settingsViewModel.discoverable.collectAsStateWithLifecycle()
    val deleteState by settingsViewModel.deleteState.collectAsStateWithLifecycle()
    val deleteError by settingsViewModel.deleteError.collectAsStateWithLifecycle()
    val pendingSyncCount by settingsViewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val storageInfo by settingsViewModel.storageInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showReAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is DeleteAccountState.NeedsReAuth -> {
                settingsViewModel.clearDeleteState()
                if (settingsViewModel.isGoogleUser) {
                    settingsViewModel.initiateGoogleReAuth(context as Activity)
                } else {
                    showReAuthDialog = true
                }
            }
            is DeleteAccountState.Error -> {
                settingsViewModel.onDeleteError(state.message)
            }
            else -> {}
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
            settingsViewModel.onConsentResultHandled(authResult)
        }
    }

    LaunchedEffect(pendingConsent) {
        val consent = pendingConsent ?: return@LaunchedEffect
        val pendingIntent = consent.pendingIntent ?: return@LaunchedEffect
        consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Settings",
            icon = Icons.Default.Settings,
            onOpenDrawer = onOpenDrawer,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VmDimens.ScreenHorizontalPadding)
        ) {
            SettingsSectionHeader("ACCOUNT")
            AccountSection(
                userDisplayText = settingsViewModel.userDisplayText,
                isDeleting = deleteState is DeleteAccountState.Deleting,
                onSignOut = onSignOut,
            )

            SettingsSectionHeader("NAVIGATION")
            NavigationSection(
                useSidebar = useSidebar,
                defaultLandingPage = defaultLandingPage,
                navOrder = navOrder,
                onToggleNavMode = { settingsViewModel.toggleNavMode() },
                onSetDefaultLandingPage = { settingsViewModel.setDefaultLandingPage(it) },
                onMoveNavItem = { route, moveUp -> settingsViewModel.moveNavItem(route, moveUp) },
            )

            SettingsSectionHeader("DATE & TIME")
            TimezoneSection(
                appTimezone = appTimezone,
                isAutoTimezone = isAutoTimezone,
                onTimezoneSelected = { settingsViewModel.setTimezone(it) },
            )

            SettingsSectionHeader("TASK SCHEDULING")
            TaskSchedulingSection(
                ntsSettings = ntsSettings,
                onSetNtsEnabled = { settingsViewModel.setNtsEnabled(it) },
                onSetNtsStartTime = { h, m -> settingsViewModel.setNtsStartTime(h, m) },
                onSetNtsIntervalMinutes = { settingsViewModel.setNtsIntervalMinutes(it) },
            )

            SettingsSectionHeader("PRIVACY")
            PrivacySection(
                discoverable = discoverable,
                onSetDiscoverable = { settingsViewModel.setDiscoverable(it) },
            )

            SettingsSectionHeader("INTEGRATIONS")
            IntegrationsSection(
                tasksConnected = tasksConnected,
                tasksLoading = tasksLoading,
                onConnectTasks = { activity -> settingsViewModel.connectTasks(activity) },
                onDisconnectTasks = { settingsViewModel.disconnectTasks() },
            )

            SettingsSectionHeader("SYNC")
            SyncSection(pendingSyncCount = pendingSyncCount)

            SettingsSectionHeader("STORAGE")
            StorageSection(
                storageInfo = storageInfo,
                onClearSharedAudioCache = { settingsViewModel.clearSharedAudioCache() },
                onClearAllLocalData = { settingsViewModel.clearAllLocalData() },
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))
            DeleteAccountSection(
                isDeleting = deleteState is DeleteAccountState.Deleting,
                userDisplayText = settingsViewModel.userDisplayText,
                showReAuthDialog = showReAuthDialog,
                onReAuthDialogDismiss = { showReAuthDialog = false },
                onDeleteAccount = { settingsViewModel.deleteAccount() },
                onReauthAndDelete = { email, password -> settingsViewModel.reauthAndDelete(email, password) },
            )
            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

            tasksError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(bottom = VmDimens.SpaceSm),
                    action = {
                        TextButton(onClick = { settingsViewModel.clearTasksError() }) {
                            Text("Dismiss")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(error)
                }
            }

            deleteError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(bottom = VmDimens.SpaceSm),
                    action = {
                        TextButton(onClick = { settingsViewModel.clearDeleteError() }) {
                            Text("Dismiss")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(error)
                }
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = VmDimens.SpaceXl),
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = VmDimens.ScreenHorizontalPadding,
            top = VmDimens.SpaceLg,
            bottom = VmDimens.SpaceXs,
        ),
    )
}
