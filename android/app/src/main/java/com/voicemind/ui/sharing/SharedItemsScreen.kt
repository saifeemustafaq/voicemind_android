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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedItemsScreen(
    onBack: () -> Unit,
    onRecordingClick: (ownerUid: String, recordingId: String) -> Unit = { _, _ -> },
    viewModel: SharedItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            VoiceMindTopAppBar(
                title = "Shared Items",
                icon = Icons.Default.FolderShared,
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
            val isEmpty = state.recordings.isEmpty() && state.summaries.isEmpty()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = VmDimens.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isEmpty) {
                    item {
                        Spacer(modifier = Modifier.height(VmDimens.SpaceXl))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Items shared with you by other VoiceMind users will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    if (state.recordings.isNotEmpty()) {
                        item { SectionHeader("RECORDINGS") }
                        items(state.recordings, key = { it.shareId }) { item ->
                            SharedRecordingRow(
                                item = item,
                                onClick = { onRecordingClick(item.ownerUid, item.itemId) },
                                onDismiss = { viewModel.dismiss(item.shareId) },
                            )
                        }
                    }
                    if (state.summaries.isNotEmpty()) {
                        item { SectionHeader("SUMMARIES") }
                        items(state.summaries, key = { it.shareId }) { item ->
                            SharedRecordingRow(
                                item = item,
                                onClick = { },
                                onDismiss = { viewModel.dismiss(item.shareId) },
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(VmDimens.SpaceXl)) }
            }
        }
    }
}

@Composable
private fun SharedRecordingRow(
    item: SharedItemUiModel,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.itemTitle, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Shared by ${item.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
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
