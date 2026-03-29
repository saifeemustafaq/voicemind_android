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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.components.voiceMindTextFieldColors
import com.voicemind.ui.theme.VmDimens
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    itemId: String,
    itemType: String = "recording",
    onDismiss: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(itemId, itemType) {
        viewModel.setItem(itemId, itemType)
    }

    LaunchedEffect(state.shareSuccess) {
        if (state.shareSuccess) {
            delay(1500)
            viewModel.clearShareSuccess()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ShareDialogContent(
            state = state,
            itemType = itemType,
            onFind = viewModel::findUser,
            onShare = viewModel::shareItem,
            onRevoke = viewModel::revokeShare,
            onResetLookup = viewModel::resetLookup,
        )
    }
}

@Composable
private fun ShareDialogContent(
    state: ShareUiState,
    itemType: String,
    onFind: (email: String) -> Unit,
    onShare: () -> Unit,
    onRevoke: (shareId: String, recipientUid: String) -> Unit,
    onResetLookup: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.shareSuccess) {
        if (state.shareSuccess) email = ""
    }

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
            onValueChange = { email = it; onResetLookup() },
            label = { Text("Recipient email") },
            singleLine = true,
            colors = voiceMindTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

        PrimaryButton(
            text = "Find",
            enabled = email.isNotBlank() && state.lookupState !is LookupState.Loading,
            onClick = {
                keyboardController?.hide()
                onFind(email)
            },
        )

        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

        // ── Lookup result ─────────────────────────────────────────────────
        when (val ls = state.lookupState) {
            is LookupState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(32.dp),
                    strokeWidth = 2.dp,
                )
            }
            is LookupState.Found -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = ls.user.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = ls.user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(VmDimens.SpaceMd))
                        PrimaryButton(
                            text = if (state.isSharing) "Sharing..." else "Share",
                            enabled = !state.isSharing,
                            onClick = onShare,
                        )
                        state.shareError?.let { error ->
                            Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            is LookupState.NotFound -> {
                Text(
                    text = "No user found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LookupState.AlreadyShared -> {
                Text(
                    text = "Already shared with this user",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LookupState.Error -> {
                Text(
                    text = ls.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is LookupState.Idle -> Unit
        }

        // ── Share success ─────────────────────────────────────────────────
        if (state.shareSuccess) {
            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))
            Text(
                text = "Shared successfully!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // ── Shared with section (recordings/summaries only) ───────────────
        if (itemType != "task" && state.myShares.isNotEmpty()) {
            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

            Text(
                text = "Shared with",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            state.myShares.forEach { share ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = VmDimens.SpaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = share.recipientName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = share.recipientEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                    IconButton(
                        enabled = !state.isRevoking.contains(share.id),
                        onClick = { onRevoke(share.id, share.recipientUid) },
                    ) {
                        if (state.isRevoking.contains(share.id)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
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
}
