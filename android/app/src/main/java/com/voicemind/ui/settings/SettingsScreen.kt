package com.voicemind.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import android.text.format.Formatter
import com.voicemind.BuildConfig
import com.voicemind.data.repository.AuthRepository
import kotlinx.coroutines.launch
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.theme.VmDimens
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
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
    val pendingSyncCount by settingsViewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val storageInfo by settingsViewModel.storageInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTimeZonePicker by remember { mutableStateOf(false) }
    var showNtsTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReAuthDialog by remember { mutableStateOf(false) }
    var reAuthPassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showClearSharedConfirmDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is DeleteAccountState.NeedsReAuth -> {
                settingsViewModel.clearDeleteState()
                if (settingsViewModel.isGoogleUser) {
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(true)
                            .setServerClientId(AuthRepository.WEB_CLIENT_ID)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        val result = credentialManager.getCredential(context as Activity, request)
                        val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                        settingsViewModel.reauthAndDeleteWithGoogle(idToken)
                    } catch (e: GetCredentialCancellationException) {
                        deleteError = "Account deletion cancelled."
                    } catch (e: Exception) {
                        deleteError = "Google re-authentication failed: ${e.message}"
                    }
                } else {
                    showReAuthDialog = true
                }
            }
            is DeleteAccountState.Error -> {
                deleteError = state.message
                settingsViewModel.clearDeleteState()
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
        consentLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
    }

    val orderedNavItems = Routes.orderedItems(navOrder)

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
                .padding(horizontal = 16.dp)
        ) {
            // ── ACCOUNT ──────────────────────────────────────────────────
            SettingsSectionHeader("ACCOUNT")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = settingsViewModel.userDisplayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val isDeleting = deleteState is DeleteAccountState.Deleting
                    PrimaryButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        enabled = !isDeleting,
                    )
                }
            }

            // ── NAVIGATION ───────────────────────────────────────────────
            SettingsSectionHeader("NAVIGATION")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // Sidebar toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use sidebar navigation",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (useSidebar) "Swipe or tap menu to open drawer"
                                       else "Tabs shown at the bottom of the screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = useSidebar,
                            onCheckedChange = { settingsViewModel.toggleNavMode() },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    // Default landing page
                    Text(
                        text = "Default landing page",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Screen shown when the app opens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

                    val landingOptions = listOf("recordings" to "Recordings", "checklist" to "Checklist", "folders" to "Folders")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        landingOptions.forEachIndexed { index, (route, label) ->
                            SegmentedButton(
                                selected = defaultLandingPage == route,
                                onClick = { settingsViewModel.setDefaultLandingPage(route) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = landingOptions.size),
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    // Tab order
                    Text(
                        text = "Tab order",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Rearrange the order of navigation tabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

                    // Horizontal strip — mirrors the actual bottom bar layout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            orderedNavItems.forEachIndexed { index, item ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = item.outlinedIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row {
                                        IconButton(
                                            onClick = { settingsViewModel.moveNavItem(item.route, moveUp = true) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.KeyboardArrowLeft,
                                                contentDescription = "Move left",
                                                modifier = Modifier.size(18.dp),
                                                tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            )
                                        }
                                        IconButton(
                                            onClick = { settingsViewModel.moveNavItem(item.route, moveUp = false) },
                                            enabled = index < orderedNavItems.lastIndex,
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.KeyboardArrowRight,
                                                contentDescription = "Move right",
                                                modifier = Modifier.size(18.dp),
                                                tint = if (index < orderedNavItems.lastIndex) MaterialTheme.colorScheme.onSurface
                                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── DATE & TIME ──────────────────────────────────────────────
            SettingsSectionHeader("DATE & TIME")

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showTimeZonePicker = true },
            ) {
                val tz = remember(appTimezone) { TimeZone.getTimeZone(appTimezone) }
                Column {
                    Text(
                        text = "Timezone",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${tz.getDisplayName(false, TimeZone.LONG)} (${formatGmtOffset(tz)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isAutoTimezone) "Auto (device)" else "Manual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (showTimeZonePicker) {
                TimeZonePickerDialog(
                    currentTimezoneId = appTimezone,
                    isAuto = isAutoTimezone,
                    onSelect = { timezoneId ->
                        settingsViewModel.setTimezone(timezoneId)
                        showTimeZonePicker = false
                    },
                    onDismiss = { showTimeZonePicker = false },
                )
            }

            // ── TASK SCHEDULING ───────────────────────────────────────────
            SettingsSectionHeader("TASK SCHEDULING")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Natural Time Selection",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (ntsSettings.enabled)
                                    "Tasks without a date are auto-scheduled"
                                else
                                    "Auto-schedule tasks that have no date set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ntsSettings.enabled,
                            onCheckedChange = { settingsViewModel.setNtsEnabled(it) },
                        )
                    }

                    AnimatedVisibility(visible = ntsSettings.enabled) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Default start time",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "First auto-scheduled task starts at this time",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { showNtsTimePicker = true }) {
                                    Text(
                                        text = formatTime(ntsSettings.startHour, ntsSettings.startMinute),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                            Text(
                                text = "Interval between tasks",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Time gap between sequentially scheduled tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

                            val intervalOptions = listOf(15, 30, 45, 60)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                            ) {
                                intervalOptions.forEach { minutes ->
                                    FilterChip(
                                        selected = ntsSettings.intervalMinutes == minutes,
                                        onClick = { settingsViewModel.setNtsIntervalMinutes(minutes) },
                                        label = { Text("${minutes}m") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showNtsTimePicker) {
                NtsTimePickerDialog(
                    initialHour = ntsSettings.startHour,
                    initialMinute = ntsSettings.startMinute,
                    onConfirm = { hour, minute ->
                        settingsViewModel.setNtsStartTime(hour, minute)
                        showNtsTimePicker = false
                    },
                    onDismiss = { showNtsTimePicker = false },
                )
            }

            // ── PRIVACY ──────────────────────────────────────────────────
            SettingsSectionHeader("PRIVACY")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow others to find me by email",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "When enabled, other VoiceMind users can find you by your email address to share recordings with you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = discoverable,
                        onCheckedChange = { settingsViewModel.setDiscoverable(it) },
                        modifier = Modifier.padding(start = VmDimens.SpaceMd),
                    )
                }
            }

            // ── INTEGRATIONS ─────────────────────────────────────────────
            SettingsSectionHeader("INTEGRATIONS")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Tasks",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (tasksConnected) "Task dates sync to Google Tasks"
                                       else "Sync task dates to Google Tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (tasksLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Switch(
                                checked = tasksConnected,
                                onCheckedChange = {
                                    if (it) {
                                        settingsViewModel.connectTasks(context as Activity)
                                    } else {
                                        settingsViewModel.disconnectTasks()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── SYNC ─────────────────────────────────────────────────────
            SettingsSectionHeader("SYNC")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pendingSyncCount == 0) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                        Text(
                            text = "All synced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                        Text(
                            text = "$pendingSyncCount ${if (pendingSyncCount == 1) "item" else "items"} pending sync",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── STORAGE ───────────────────────────────────────────────────
            SettingsSectionHeader("STORAGE")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    StorageRow(
                        label = "Total",
                        bytes = storageInfo.totalBytes,
                        context = context,
                        bold = true,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    StorageRow(label = "Own recordings", bytes = storageInfo.ownAudioBytes, context = context)
                    Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
                    StorageRow(label = "Shared audio", bytes = storageInfo.sharedAudioBytes, context = context)
                    Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
                    StorageRow(label = "Database", bytes = storageInfo.databaseBytes, context = context)

                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    FilledTonalButton(
                        onClick = { showClearSharedConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear Shared Audio Cache")
                    }

                    Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

                    FilledTonalButton(
                        onClick = { showClearAllConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text("Clear All Local Data")
                    }
                }
            }

            if (showClearSharedConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearSharedConfirmDialog = false },
                    title = { Text("Clear Shared Audio Cache") },
                    text = { Text("This will delete all locally cached shared recordings. They will be re-downloaded when you open them again.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearSharedConfirmDialog = false
                            settingsViewModel.clearSharedAudioCache()
                        }) { Text("Clear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearSharedConfirmDialog = false }) { Text("Cancel") }
                    },
                )
            }

            if (showClearAllConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearAllConfirmDialog = false },
                    title = { Text("Clear All Local Data") },
                    text = {
                        Text(
                            "This will remove all locally stored recordings, tasks, folders, and audio files from this device. " +
                            "Your data remains in the cloud and will re-sync on next launch. " +
                            "You will be prompted to set up local storage again."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearAllConfirmDialog = false
                                settingsViewModel.clearAllLocalData()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("Clear All") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearAllConfirmDialog = false }) { Text("Cancel") }
                    },
                )
            }

            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))

            val isDeleting = deleteState is DeleteAccountState.Deleting
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (isDeleting) {
                    TextButton(onClick = {}, enabled = false) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                        Text(
                            text = "Deleting Account...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    TextButton(onClick = { showDeleteConfirmDialog = true }) {
                        Text(
                            text = "Delete Account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

            tasksError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
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
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { deleteError = null }) {
                            Text("Dismiss")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(error)
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete Account") },
                    text = {
                        Text(
                            "This will permanently delete your account and all your data. " +
                            "Shared copies in other users' accounts will not be affected. " +
                            "This action cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                settingsViewModel.deleteAccount()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            if (showReAuthDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showReAuthDialog = false
                        reAuthPassword = ""
                    },
                    title = { Text("Re-authenticate Required") },
                    text = {
                        Column {
                            Text(
                                "For security, please enter your password to confirm account deletion.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))
                            OutlinedTextField(
                                value = reAuthPassword,
                                onValueChange = { reAuthPassword = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val email = settingsViewModel.userDisplayText
                                val password = reAuthPassword
                                showReAuthDialog = false
                                reAuthPassword = ""
                                settingsViewModel.reauthAndDelete(email, password)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showReAuthDialog = false
                            reAuthPassword = ""
                        }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
}

@Composable
private fun StorageRow(
    label: String,
    bytes: Long,
    context: android.content.Context,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = Formatter.formatFileSize(context, bytes),
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NtsTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Default start time") },
        text = { TimePicker(state = state) },
    )
}
