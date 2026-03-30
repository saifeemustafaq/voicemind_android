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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
    onTaskClick: (taskId: String) -> Unit = {},
    onSummaryClick: (ownerUid: String, summaryId: String) -> Unit = { _, _ -> },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                    modifier = Modifier.padding(
                        horizontal = VmDimens.ScreenHorizontalPadding,
                        vertical = VmDimens.SpaceSm,
                    ),
                ) {
                    SharedItemsTab.entries.forEach { tab ->
                        FilterChip(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text(tab.name, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = VmDimens.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                ) {
                    when (state.selectedTab) {
                        SharedItemsTab.Recordings -> if (state.recordings.isEmpty()) {
                            item { TabEmptyState("No shared recordings yet") }
                        } else {
                            items(state.recordings, key = { it.shareId }) { item ->
                                SharedRecordingRow(
                                    item = item,
                                    onClick = { onRecordingClick(item.ownerUid, item.itemId) },
                                    onDismiss = { viewModel.dismiss(item.shareId) },
                                )
                            }
                        }
                        SharedItemsTab.Tasks -> if (state.tasks.isEmpty()) {
                            item { TabEmptyState("No shared tasks yet") }
                        } else {
                            items(state.tasks, key = { it.id }) { task ->
                                SharedTaskItemRow(
                                    task = task,
                                    onClick = { onTaskClick(task.id) },
                                )
                            }
                        }
                        SharedItemsTab.Summaries -> if (state.summaries.isEmpty()) {
                            item { TabEmptyState("No shared summaries yet") }
                        } else {
                            items(state.summaries, key = { it.shareId }) { item ->
                                SharedRecordingRow(
                                    item = item,
                                    onClick = { onSummaryClick(item.ownerUid, item.itemId) },
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
private fun SharedTaskItemRow(
    task: SharedTaskUiModel,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.completed) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                if (task.sharedFromName.isNotEmpty()) {
                    Text(
                        "Shared by ${task.sharedFromName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VmDimens.SpaceXl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
