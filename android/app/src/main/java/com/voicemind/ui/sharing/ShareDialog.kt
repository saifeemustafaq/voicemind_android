package com.voicemind.ui.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.components.voiceMindTextFieldColors
import com.voicemind.ui.theme.VmDimens

// Visual states for the user lookup result
private enum class LookupState { Idle, Loading, Found, NotFound, AlreadyShared }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    recordingId: String,
    itemType: String = "recording",
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ShareDialogContent(onDismiss = onDismiss)
    }
}

@Composable
private fun ShareDialogContent(onDismiss: () -> Unit) {
    // All state is local and no-op — wired to ShareViewModel in Phase 5
    var email by remember { mutableStateOf("") }
    var lookupState by remember { mutableStateOf(LookupState.Idle) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VmDimens.SpaceXl)
            .padding(bottom = VmDimens.SpaceXxl),
    ) {
        Text(
            text = "Share with User",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = VmDimens.SpaceLg),
        )

        // ── Email lookup ──────────────────────────────────────────────────
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; lookupState = LookupState.Idle },
            label = { Text("Recipient email") },
            singleLine = true,
            colors = voiceMindTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

        PrimaryButton(
            text = "Find",
            enabled = email.isNotBlank() && lookupState != LookupState.Loading,
            onClick = {
                // No-op visual shell — will call ShareViewModel.findUser(email) in Phase 5
                lookupState = LookupState.NotFound
            },
        )

        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

        // ── Lookup result ─────────────────────────────────────────────────
        when (lookupState) {
            LookupState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(32.dp),
                    strokeWidth = 2.dp,
                )
            }
            LookupState.Found -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Jane Smith",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))
                        PrimaryButton(
                            text = "Share",
                            onClick = {
                                // No-op — will call ShareViewModel.shareItem() in Phase 5
                                onDismiss()
                            },
                        )
                    }
                }
            }
            LookupState.NotFound -> {
                Text(
                    text = "No user found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LookupState.AlreadyShared -> {
                Text(
                    text = "Already shared with this user",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LookupState.Idle -> Unit
        }

        // ── Shared with section ───────────────────────────────────────────
        // Placeholder list — will be sourced from SharingRepository.observeMyShares() in Phase 5
        val placeholderRecipients = emptyList<String>()

        if (placeholderRecipients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

            Text(
                text = "Shared with",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            placeholderRecipients.forEach { recipient ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = VmDimens.SpaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipient,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                    IconButton(
                        onClick = {
                            // No-op — will call ShareViewModel.revokeShare() in Phase 5
                        },
                    ) {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = "Revoke access",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
