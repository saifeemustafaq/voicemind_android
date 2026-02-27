package com.voicemind.ui.recording

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSuccess
import com.voicemind.ui.theme.IosTertiaryFill
import com.voicemind.ui.theme.IosWhite

private enum class TranscriptTab(val label: String) {
    Transcript("Transcript"),
    Summary("Summary"),
    Tasks("Tasks"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptSheet(
    recording: Recording,
    viewModel: RecordingsViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(recording.id) {
        viewModel.openTranscriptSheet(recording)
    }

    val hasTasks = sheetState.actionItemsLoaded && sheetState.actionItems.isNotEmpty()
    val tabs = listOf(TranscriptTab.Transcript, TranscriptTab.Summary) +
        if (hasTasks) listOf(TranscriptTab.Tasks) else emptyList()
    if (selectedTab >= tabs.size) selectedTab = 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IosWhite,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(recording.title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, tab ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (tab == TranscriptTab.Summary) {
                                viewModel.generateSummary(recording)
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IosAccent,
                            selectedLabelColor = IosWhite,
                            containerColor = IosTertiaryFill,
                            labelColor = IosLabel,
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Crossfade(
                targetState = tabs.getOrNull(selectedTab) ?: TranscriptTab.Transcript,
                label = "tab_content",
            ) { tab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (tab) {
                        TranscriptTab.Transcript -> TranscriptContent(recording)
                        TranscriptTab.Summary -> SummaryContent(
                            summaryState = sheetState.summaryState,
                            onRetry = { viewModel.generateSummary(recording) },
                        )
                        TranscriptTab.Tasks -> TasksContent(sheetState)
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptContent(recording: Recording) {
    Text(
        text = recording.transcription ?: "No transcript",
        style = MaterialTheme.typography.bodyMedium,
        color = if (recording.transcription != null) IosLabel else IosSecondaryLabel,
    )
}

@Composable
private fun SummaryContent(
    summaryState: SummaryState,
    onRetry: () -> Unit,
) {
    when (summaryState) {
        is SummaryState.Idle, is SummaryState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = IosAccent,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Summary is being generated...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSecondaryLabel,
                    )
                }
            }
        }
        is SummaryState.Loaded -> {
            Text(
                text = summaryState.text,
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabel,
            )
        }
        is SummaryState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Failed to generate summary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text("Retry", color = IosAccent)
                }
            }
        }
    }
}

@Composable
private fun TasksContent(sheetState: TranscriptSheetState) {
    if (sheetState.actionItems.isEmpty()) {
        Text(
            "No tasks",
            style = MaterialTheme.typography.bodyMedium,
            color = IosSecondaryLabel,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sheetState.actionItems.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Filled.CheckCircle
                        else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (item.completed) IosSuccess else IosSecondaryLabel,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosLabel,
                        )
                        TaskDateLabels(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDateLabels(item: ActionItem) {
    val now = remember { java.util.Date() }
    val isOverdue = !item.completed

    item.dueDate?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                .format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) IosDestructive else IosSecondaryLabel,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) IosDestructive else IosSecondaryLabel,
            )
        }
    }

    item.deadline?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                .format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) IosDestructive else IosSecondaryLabel,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Deadline: $formatted",
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) IosDestructive else IosSecondaryLabel,
            )
        }
    }
}

@Composable
fun RenameRecordingDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Recording") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(25) },
                singleLine = true,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder") },
        text = {
            Column {
                folders.forEach { folder ->
                    TextButton(
                        onClick = { onConfirm(folder.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(folder.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteRecordingDialog(
    recordingTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Recording") },
        text = { Text("Delete \"$recordingTitle\"? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
