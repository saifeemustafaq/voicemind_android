package com.voicemind.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.voicemind.BuildConfig
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSuccess
import com.voicemind.ui.theme.IosWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val useSidebar by settingsViewModel.useSidebar.collectAsStateWithLifecycle()
    val calendarConnected by settingsViewModel.calendarConnected.collectAsStateWithLifecycle()
    val calendarLoading by settingsViewModel.calendarLoading.collectAsStateWithLifecycle()
    val calendarError by settingsViewModel.calendarError.collectAsStateWithLifecycle()
    val pendingConsent by settingsViewModel.pendingConsentResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Settings",
            icon = Icons.Default.Settings,
            onOpenDrawer = onOpenDrawer,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "ACCOUNT",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = settingsViewModel.userDisplayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSecondaryLabel,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "NAVIGATION",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use sidebar navigation",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IosLabel,
                            )
                            Text(
                                text = if (useSidebar) "Swipe or tap menu to open drawer"
                                       else "Tabs shown at the bottom of the screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosSecondaryLabel,
                            )
                        }
                        Switch(
                            checked = useSidebar,
                            onCheckedChange = { settingsViewModel.toggleNavMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IosWhite,
                                checkedTrackColor = IosSuccess,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "INTEGRATIONS",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Calendar",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IosLabel,
                            )
                            Text(
                                text = if (calendarConnected) "Task dates sync to your calendar"
                                       else "Sync task dates to Google Calendar",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosSecondaryLabel,
                            )
                        }
                        if (calendarLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = IosAccent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Switch(
                                checked = calendarConnected,
                                onCheckedChange = {
                                    if (it) {
                                        settingsViewModel.connectCalendar(context as Activity)
                                    } else {
                                        settingsViewModel.disconnectCalendar()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = IosWhite,
                                    checkedTrackColor = IosSuccess,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            calendarError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { settingsViewModel.clearCalendarError() }) {
                            Text("Dismiss", color = Color.White)
                        }
                    },
                    containerColor = IosDestructive,
                ) {
                    Text(error)
                }
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )
        }
    }
}
