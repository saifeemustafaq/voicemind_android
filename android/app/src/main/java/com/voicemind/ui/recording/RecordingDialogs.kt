package com.voicemind.ui.recording

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.voicemind.ui.components.voiceMindTextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicemind.data.model.ActionItem
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.util.LocalAppTimeZone

internal enum class TranscriptTab(val label: String) {
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

    LaunchedEffect(recording.id) {
        viewModel.openTranscriptSheet(recording)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(recording.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            RecordingContentTabs(
                recording = recording,
                sheetState = sheetState,
                onGenerateSummary = { viewModel.generateSummary(recording) },
                onGenerateTasks = { viewModel.generateTasks(recording) },
                onRetryProcessing = { viewModel.retryProcessing(recording) },
                onRetrySummary = { viewModel.generateSummary(recording) },
                scrollableContent = true,
            )
        }
    }
}

@Composable
internal fun TranscriptContent(recording: Recording, onRetry: () -> Unit) {
    when {
        recording.transcription != null -> Text(
            text = recording.transcription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        recording.processingFailed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Processing failed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Processing transcript...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SummaryContent(
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
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Summary is being generated...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is SummaryState.Loaded -> {
            Text(
                text = summaryState.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
internal fun TasksContent(sheetState: TranscriptSheetState) {
    if (sheetState.actionItems.isEmpty()) {
        Text(
            "No tasks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sheetState.actionItems.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.completed)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TaskDateLabels(item)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskDateLabels(item: ActionItem) {
    val now = remember { java.util.Date() }
    val isOverdue = !item.completed
    val appTz = LocalAppTimeZone.current

    item.dueDate?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts, appTz) {
            java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                .apply { timeZone = appTz }
                .format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item.deadline?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Deadline: $formatted",
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                onValueChange = { title = it.take(75) },
                singleLine = true,
                label = { Text("Title") },
                colors = voiceMindTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Move to Folder",
    showFolderIcon: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                folders.forEach { folder ->
                    TextButton(
                        onClick = { onConfirm(folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (showFolderIcon) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folder.name, color = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Text(folder.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
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
        },
    )
}

@Composable
fun DismissRecordingDialog(
    onResume: () -> Unit,
    onStopAndSave: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text("Recording in Progress") },
        text = { Text("What would you like to do with the current recording?") },
        confirmButton = {
            TextButton(onClick = onResume) { Text("Resume") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onStopAndSave) { Text("Stop & Save") }
            }
        },
    )
}
