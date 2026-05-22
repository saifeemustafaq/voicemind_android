package com.voicemind.ui.sharing

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.ui.components.EmptyStateCard
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedByMeScreen(
    onBack: () -> Unit,
    viewModel: SharedByMeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            VoiceMindTopAppBar(
                title = "Shared by Me",
                icon = Icons.Default.Share,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val totalItems = state.recordings.size + state.tasks.size + state.summaries.size
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (totalItems == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = VmDimens.ScreenHorizontalPadding)
                            .padding(top = VmDimens.SpaceXl),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        EmptyStateCard(
                            icon = Icons.Default.Share,
                            message = "You haven't shared anything yet",
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                        modifier = Modifier.padding(
                            horizontal = VmDimens.ScreenHorizontalPadding,
                            vertical = VmDimens.SpaceSm,
                        ),
                    ) {
                        SharedByMeTab.entries.forEach { tab ->
                            FilterChip(
                                selected = state.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                label = { Text(tab.name, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }

                    val activeItems = when (state.selectedTab) {
                        SharedByMeTab.Recordings -> state.recordings
                        SharedByMeTab.Tasks -> state.tasks
                        SharedByMeTab.Summaries -> state.summaries
                    }
                    val emptyMessage = when (state.selectedTab) {
                        SharedByMeTab.Recordings -> "No shared recordings yet"
                        SharedByMeTab.Tasks -> "No shared tasks yet"
                        SharedByMeTab.Summaries -> "No shared summaries yet"
                    }

                    if (activeItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = VmDimens.ScreenHorizontalPadding)
                                .padding(top = VmDimens.SpaceMd),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            EmptyStateCard(icon = Icons.Default.Share, message = emptyMessage)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = VmDimens.ScreenHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                        ) {
                            item { Spacer(modifier = Modifier.height(VmDimens.SpaceXs)) }
                            items(activeItems, key = { it.itemId }) { item ->
                                SharedByMeItemCard(
                                    item = item,
                                    isRevoking = state.isRevoking,
                                    onRevoke = viewModel::revokeShare,
                                )
                            }
                            item { Spacer(modifier = Modifier.height(VmDimens.SpaceXl)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedByMeItemCard(
    item: SharedByMeItemUiModel,
    isRevoking: Set<String>,
    onRevoke: (shareId: String, recipientUid: String) -> Unit,
) {
    var revokeTarget by remember { mutableStateOf<RecipientUiModel?>(null) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.itemTitle,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (item.recipients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(VmDimens.SpaceSm))
                HorizontalDivider()
                item.recipients.forEach { recipient ->
                    RecipientRow(
                        recipient = recipient,
                        isRevoking = isRevoking.contains(recipient.shareId),
                        onRevoke = { revokeTarget = recipient },
                    )
                }
            }
        }
    }

    revokeTarget?.let { recipient ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke Access") },
            text = { Text("Remove access for ${recipient.recipientName.ifEmpty { recipient.recipientEmail }}?") },
            confirmButton = {
                TextButton(onClick = {
                    onRevoke(recipient.shareId, recipient.recipientUid)
                    revokeTarget = null
                }) {
                    Text("Revoke", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecipientRow(
    recipient: RecipientUiModel,
    isRevoking: Boolean,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VmDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipient.recipientName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = recipient.recipientEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
        IconButton(enabled = !isRevoking, onClick = onRevoke) {
            if (isRevoking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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

