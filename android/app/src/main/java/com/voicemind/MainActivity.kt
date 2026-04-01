package com.voicemind

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuth
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.data.sync.FirestoreSyncService
import com.voicemind.data.sync.InitialSyncManager
import com.voicemind.data.sync.SyncScheduler
import com.voicemind.util.ConnectivityObserver
import com.voicemind.service.RecordingService
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.ui.auth.AuthViewModel
import com.voicemind.ui.auth.SignInScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.voicemind.ui.components.TasksSyncPromptDialog
import com.voicemind.ui.components.PermissionRationaleDialog
import com.voicemind.ui.main.MainViewModel
import com.voicemind.ui.navigation.AppNavHost
import com.voicemind.ui.setup.DeviceSetupScreen
import com.voicemind.ui.theme.VoiceMindAITheme
import com.voicemind.util.LocalAppTimeZone
import dagger.hilt.android.AndroidEntryPoint
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var navPreferenceRepository: NavPreferenceRepository
    @Inject lateinit var firestoreSyncService: FirestoreSyncService
    @Inject lateinit var initialSyncManager: InitialSyncManager
    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var recordingDao: RecordingDao

    // --- Notification tap navigation ---
    private var openRecordingsOnStart by mutableStateOf(false)
    private var openSharedItemsOnStart by mutableStateOf(false)

    // --- Permission check ---
    // Incremented in onResume to trigger the permission LaunchedEffect on every foreground.
    private var permissionCheckTrigger by mutableIntStateOf(0)
    // Set to true when the user taps "Not Now" on our rationale dialog. Suppresses both the
    // system permission dialog and our rationale for the rest of this session. Resets to false
    // on the next cold start (Activity recreation).
    private var permissionsDismissedThisSession = false
    private var showPermissionRationale by mutableStateOf(false)
    private var missingMic by mutableStateOf(false)
    private var missingNotification by mutableStateOf(false)
    private var micPermanentlyDenied by mutableStateOf(false)
    private var notificationPermanentlyDenied by mutableStateOf(false)

    // --- Tasks prompt ---
    // Dismissed once per cold start; resets when the Activity is recreated.
    private var tasksPromptDismissed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRecordingsOnStart =
            intent?.getBooleanExtra(RecordingService.EXTRA_OPEN_RECORDINGS, false) == true
        openSharedItemsOnStart =
            intent?.getBooleanExtra(EXTRA_OPEN_SHARED_ITEMS, false) == true
        enableEdgeToEdge()
        setContent {
            val appTzId by navPreferenceRepository.appTimezone
                .collectAsStateWithLifecycle(initialValue = TimeZone.getDefault().id)
            val appTz = remember(appTzId) { TimeZone.getTimeZone(appTzId) }

            VoiceMindAITheme {
                CompositionLocalProvider(LocalAppTimeZone provides appTz) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val isSignedIn by authViewModel.isSignedIn.collectAsStateWithLifecycle()

                if (isSignedIn) {
                    val isSetupComplete by navPreferenceRepository.isDeviceSetupComplete
                        .collectAsStateWithLifecycle(initialValue = true)
                    var roomIsEmpty by remember { mutableStateOf(false) }
                    LaunchedEffect(isSetupComplete) {
                        if (!isSetupComplete) {
                            roomIsEmpty = withContext(Dispatchers.IO) { recordingDao.countAll() == 0 }
                        }
                    }

                    if (!isSetupComplete && roomIsEmpty) {
                        DeviceSetupScreen()
                        return@CompositionLocalProvider
                    }

                    val mainViewModel: MainViewModel = hiltViewModel()
                    val tasksConnected by mainViewModel.tasksConnected.collectAsStateWithLifecycle()
                    val pendingConsent by mainViewModel.pendingConsent.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        authViewModel.registerFcmToken()
                    }

                    // ── Local-first lifecycle ──────────────────────────────────────────────
                    LaunchedEffect(Unit) {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                        withContext(Dispatchers.IO) { initialSyncManager.runIfNeeded() }
                        firestoreSyncService.startListening(uid)
                    }

                    // ── Connectivity-triggered sync ────────────────────────────────────────
                    LaunchedEffect(Unit) {
                        var wasOffline = !connectivityObserver.isCurrentlyOnline()
                        connectivityObserver.isOnline.collect { online ->
                            if (online && wasOffline) syncScheduler.enqueueSync()
                            wasOffline = !online
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose { firestoreSyncService.stopListening() }
                    }

                    // ── Permission request launcher ────────────────────────────────────────
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        // A key absent from the map means we didn't request it (already granted).
                        val micGranted = results[Manifest.permission.RECORD_AUDIO] != false
                        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            results[Manifest.permission.POST_NOTIFICATIONS] != false
                        } else true

                        missingMic = !micGranted
                        missingNotification = !notifGranted

                        // shouldShowRequestPermissionRationale is false after "Don't ask again".
                        if (missingMic) {
                            micPermanentlyDenied =
                                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                        }
                        if (missingNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermanentlyDenied =
                                !shouldShowRequestPermissionRationale(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                        }

                        showPermissionRationale = missingMic || missingNotification
                    }

                    // Run on every onResume. Skip if user already dismissed for this session.
                    LaunchedEffect(permissionCheckTrigger) {
                        if (permissionCheckTrigger == 0 || permissionsDismissedThisSession) return@LaunchedEffect
                        val toRequest = buildList {
                            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED
                            ) add(Manifest.permission.RECORD_AUDIO)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                            ) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (toRequest.isNotEmpty()) {
                            permissionLauncher.launch(toRequest.toTypedArray())
                        }
                    }

                    // ── Google Tasks consent launcher ─────────────────────────────────────
                    val consentLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { activityResult ->
                        if (activityResult.resultCode == Activity.RESULT_OK) {
                            val authResult = Identity.getAuthorizationClient(this@MainActivity)
                                .getAuthorizationResultFromIntent(activityResult.data)
                            mainViewModel.onConsentHandled(authResult)
                        }
                    }

                    LaunchedEffect(pendingConsent) {
                        val consent = pendingConsent ?: return@LaunchedEffect
                        val pendingIntent = consent.pendingIntent ?: return@LaunchedEffect
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }

                    // ── Dialog priority: permission rationale first, tasks second ──────────
                    when {
                        showPermissionRationale -> {
                            val allPermanentlyDenied =
                                (!missingMic || micPermanentlyDenied) &&
                                (!missingNotification || notificationPermanentlyDenied)

                            PermissionRationaleDialog(
                                missingMic = missingMic,
                                missingNotification = missingNotification,
                                anyPermanentlyDenied = allPermanentlyDenied,
                                onAllow = {
                                    showPermissionRationale = false
                                    if (allPermanentlyDenied) {
                                        openAppSettings()
                                    } else {
                                        val toRequest = buildList {
                                            if (missingMic && !micPermanentlyDenied)
                                                add(Manifest.permission.RECORD_AUDIO)
                                            if (missingNotification && !notificationPermanentlyDenied &&
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                            ) add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        if (toRequest.isNotEmpty()) {
                                            permissionLauncher.launch(toRequest.toTypedArray())
                                        }
                                    }
                                },
                                onDismiss = {
                                    showPermissionRationale = false
                                    permissionsDismissedThisSession = true
                                },
                            )
                        }

                        tasksConnected == false && !tasksPromptDismissed && !permissionsDismissedThisSession -> {
                            TasksSyncPromptDialog(
                                onConnect = {
                                    tasksPromptDismissed = true
                                    mainViewModel.connectTasks(this@MainActivity)
                                },
                                onDismiss = {
                                    tasksPromptDismissed = true
                                    permissionsDismissedThisSession = true
                                },
                            )
                        }
                    }

                    // ── One-time local storage consent ────────────────────────────────────
                    val isConsentShown by navPreferenceRepository.isLocalStorageConsentShown
                        .collectAsStateWithLifecycle(initialValue = true)
                    val consentScope = rememberCoroutineScope()

                    if (!isConsentShown) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Local Storage Enabled") },
                            text = {
                                Text(
                                    "VoiceMind now stores your recordings and metadata locally on this device " +
                                    "for instant playback and offline access. You can manage storage usage " +
                                    "and clear local data at any time in Settings."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    consentScope.launch(Dispatchers.IO) {
                                        navPreferenceRepository.setLocalStorageConsentShown(true)
                                    }
                                }) { Text("Got It") }
                            },
                        )
                    }

                    AppNavHost(
                        onSignOut = { authViewModel.signOut() },
                        navPreferenceRepository = navPreferenceRepository,
                        connectivityObserver = connectivityObserver,
                        openRecordingsOnStart = openRecordingsOnStart,
                        onRecordingsOpened = { openRecordingsOnStart = false },
                        openSharedItemsOnStart = openSharedItemsOnStart,
                        onSharedItemsOpened = { openSharedItemsOnStart = false },
                    )
                } else {
                    SignInScreen(viewModel = authViewModel)
                }
            } // CompositionLocalProvider
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionCheckTrigger++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(RecordingService.EXTRA_OPEN_RECORDINGS, false)) {
            openRecordingsOnStart = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SHARED_ITEMS, false)) {
            openSharedItemsOnStart = true
        }
        // Widget's "Open App" button on the mic-permission screen: the user explicitly wants
        // to grant the microphone permission, so clear any "dismissed this session" suppression.
        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC_PERMISSION, false)) {
            permissionsDismissedThisSession = false
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    companion object {
        /** Sent by the widget's "Open App" button when mic permission is missing.
         *  Clears any session-level suppression so the permission dialog fires immediately. */
        const val EXTRA_REQUEST_MIC_PERMISSION = "extra_request_mic_permission"

        /** Sent by FCM notification tap to open the Shared Items screen. */
        const val EXTRA_OPEN_SHARED_ITEMS = "extra_open_shared_items"
    }
}
