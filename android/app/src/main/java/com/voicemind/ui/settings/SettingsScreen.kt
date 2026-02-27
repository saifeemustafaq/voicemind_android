package com.voicemind.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmLightLavender
import com.voicemind.ui.theme.VmTextPrimary
import com.voicemind.ui.theme.VmTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val user = FirebaseAuth.getInstance().currentUser
    val useSidebar by settingsViewModel.useSidebar.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (onOpenDrawer != null) {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Account",
                        style = MaterialTheme.typography.titleSmall,
                        color = VmTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user?.email ?: user?.displayName ?: "Signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VmTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Navigation",
                        style = MaterialTheme.typography.titleSmall,
                        color = VmTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use sidebar navigation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VmTextPrimary,
                            )
                            Text(
                                text = if (useSidebar) "Swipe or tap menu to open drawer"
                                       else "Tabs shown at the bottom of the screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = VmTextSecondary,
                            )
                        }
                        Switch(
                            checked = useSidebar,
                            onCheckedChange = { settingsViewModel.toggleNavMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VmDeepViolet,
                                checkedTrackColor = VmLightLavender,
                            ),
                        )
                    }
                }
            }
        }
    }
}
