package com.voicemind.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.Identity
import com.voicemind.BuildConfig
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.theme.VmDimens

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
                    PrimaryButton(
                        text = "Sign Out",
                        onClick = onSignOut,
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
                                text = "Google Calendar",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (calendarConnected) "Task dates sync to your calendar"
                                       else "Sync task dates to Google Calendar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (calendarLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
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
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))

            calendarError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { settingsViewModel.clearCalendarError() }) {
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
