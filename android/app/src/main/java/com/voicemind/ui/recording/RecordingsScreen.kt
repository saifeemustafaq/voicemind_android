package com.voicemind.ui.recording

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.EmptyStateCard
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.RecordFab
import com.voicemind.ui.components.RecordingDialogsHost
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.util.toDateSectionKey
import com.voicemind.util.toShortDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordingsViewModel: RecordingsViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    folderId: String? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    navController: NavController? = null,
) {
    val listState by recordingsViewModel.state.collectAsStateWithLifecycle()
    val recState by recordingViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(folderId) {
        recordingsViewModel.filterByFolder(folderId)
        recordingViewModel.setCurrentFolder(folderId)
    }

    // Navigate to Summaries when a collective summary is generated
    LaunchedEffect(listState.collectiveSummarizeResult) {
        if (listState.collectiveSummarizeResult != null) {
            navController?.navigate(Routes.Summaries.route)
            recordingsViewModel.clearCollectiveSummarizeResult()
        }
    }

    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var summarizingGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState.isCollectiveSummarizing) {
        if (!listState.isCollectiveSummarizing) summarizingGroup = null
    }

    // Back press exits multi-select mode
    BackHandler(enabled = listState.isMultiSelectActive) {
        recordingsViewModel.exitMultiSelect()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (folderId == null) {
                if (listState.isMultiSelectActive) {
                    MultiSelectTopBar(
                        selectedCount = listState.selectedRecordingIds.size,
                        totalCount = listState.recordings.size,
                        isAllSelected = listState.selectedRecordingIds.size == listState.recordings.size,
                        isSummarizing = listState.isCollectiveSummarizing,
                        onClose = { recordingsViewModel.exitMultiSelect() },
                        onSelectAll = {
                            if (listState.selectedRecordingIds.size == listState.recordings.size) {
                                recordingsViewModel.deselectAll()
                            } else {
                                recordingsViewModel.selectAll()
                            }
                        },
                        onDelete = { showBulkDeleteConfirm = true },
                        onMove = { showBulkMoveDialog = true },
                        onSummarize = {
                            val ids = listState.selectedRecordingIds.toList()
                            if (ids.isNotEmpty()) recordingsViewModel.collectiveSummarize(ids)
                        },
                        hasSelection = listState.selectedRecordingIds.isNotEmpty(),
                    )
                } else {
                    VoiceMindTopAppBar(
                        title = "Recordings",
                        icon = Icons.Default.Mic,
                        onOpenDrawer = onOpenDrawer,
                        onSettings = onSettings,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (folderId == null
                    && listState.showMultiSelectHint
                    && !listState.isMultiSelectActive
                    && listState.recordings.size >= 2
                ) {
                    MultiSelectHintBanner(
                        onDismiss = { recordingsViewModel.dismissMultiSelectHint() },
                    )
                }

                if (listState.recordings.isEmpty() && !listState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            icon = if (folderId != null) Icons.Default.Folder else Icons.Default.Mic,
                            message = if (folderId != null) "No recordings in this folder" else "No recordings yet",
                            extraContent = if (folderId != null && onBack != null) {
                                {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    TextButton(onClick = onBack) {
                                        Text("Back to Folders", color = IosAccent)
                                    }
                                }
                            } else null,
                        )
                    }
                }

                val grouped = remember(listState.recordings) {
                    listState.recordings.groupBy { recording ->
                        recording.createdAt?.toDate()?.toDateSectionKey() ?: "Unknown"
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (dateLabel, recordings) ->
                        item(key = "header_$dateLabel") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IosSecondaryLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                if (recordings.size > 1 && !listState.isMultiSelectActive) {
                                    if (summarizingGroup == dateLabel && listState.isCollectiveSummarizing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(end = 4.dp),
                                            color = IosAccent,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                summarizingGroup = dateLabel
                                                recordingsViewModel.collectiveSummarize(recordings.map { it.id })
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = "Summarize $dateLabel recordings",
                                                tint = IosAccent,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(recordings, key = { it.id }) { recording ->
                            val isSelected = recording.id in listState.selectedRecordingIds
                            RecordingRow(
                                recording = recording,
                                isPlaying = listState.playingRecordingId == recording.id,
                                isMultiSelectActive = listState.isMultiSelectActive,
                                isSelected = isSelected,
                                onPlayPause = {
                                    if (listState.playingRecordingId == recording.id) {
                                        recordingsViewModel.stopPlayback()
                                    } else {
                                        recordingsViewModel.playAudio(recording)
                                    }
                                },
                                onLongPress = { if (folderId == null) recordingsViewModel.enterMultiSelect(recording.id) },
                                onToggleSelect = { recordingsViewModel.toggleSelection(recording.id) },
                                onTranscript = { showTranscript = recording },
                                onRename = { showRenameDialog = recording },
                                onMove = { showMoveDialog = recording },
                                onDelete = { showDeleteConfirm = recording },
                                onShareAudio = { recordingsViewModel.shareAudio(context, recording) },
                                onCopyTranscript = {
                                    recording.transcription?.let { text ->
                                        val clip = ClipData.newPlainText("Transcript", text)
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                            .setPrimaryClip(clip)
                                        Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShareTranscript = {
                                    recording.transcription?.let { text ->
                                        recordingsViewModel.shareTranscript(context, text)
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // Loading overlay during bulk operations or collective summarization
        if (listState.isBulkDeleting || listState.isBulkMoving || listState.isCollectiveSummarizing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = IosAccent)
            }
        }

        RecordFab(
            onStartRecording = { recordingViewModel.startRecording() },
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        if (recState.showSheet) {
            RecordingBottomSheet(
                viewModel = recordingViewModel,
                onDismiss = { recordingViewModel.discardRecording() }
            )
        }
    }

    RecordingDialogsHost(
        showTranscript = showTranscript,
        showRenameDialog = showRenameDialog,
        showMoveDialog = showMoveDialog,
        showDeleteConfirm = showDeleteConfirm,
        folders = listState.folders,
        viewModel = recordingsViewModel,
        onDismissTranscript = { showTranscript = null },
        onDismissRename = { showRenameDialog = null },
        onDismissMove = { showMoveDialog = null },
        onDismissDelete = { showDeleteConfirm = null },
    )

    // Bulk delete confirmation
    if (showBulkDeleteConfirm) {
        val selectedCount = listState.selectedRecordingIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete $selectedCount recording${if (selectedCount != 1) "s" else ""}?") },
            text = { Text("This will permanently delete the selected recordings and their audio files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirm = false
                        val selectedIds = listState.selectedRecordingIds
                        val toDelete = listState.recordings.filter { it.id in selectedIds }
                        recordingsViewModel.bulkDelete(toDelete)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Bulk move to folder dialog
    if (showBulkMoveDialog) {
        BulkMoveToFolderDialog(
            folders = listState.folders,
            onDismiss = { showBulkMoveDialog = false },
            onMove = { folderId ->
                showBulkMoveDialog = false
                recordingsViewModel.bulkMoveToFolder(listState.selectedRecordingIds.toList(), folderId)
            },
        )
    }

    // Collective summarize error
    listState.collectiveSummarizeError?.let { error ->
        AlertDialog(
            onDismissRequest = { recordingsViewModel.clearCollectiveSummarizeError() },
            title = { Text("Summarization Failed") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { recordingsViewModel.clearCollectiveSummarizeError() }) {
                    Text("OK")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    isAllSelected: Boolean,
    isSummarizing: Boolean,
    hasSelection: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onSummarize: () -> Unit,
) {
    TopAppBar(
        title = { Text("$selectedCount selected", style = MaterialTheme.typography.titleSmall) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Exit selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                    tint = IosAccent,
                )
            }
            if (hasSelection) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onMove) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = "Move selected", tint = IosAccent)
                }
                if (!isSummarizing) {
                    IconButton(onClick = onSummarize) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Summarize selected", tint = IosAccent)
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), color = IosAccent, strokeWidth = 2.dp)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun BulkMoveToFolderDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder") },
        text = {
            Column {
                folders.forEach { folder ->
                    TextButton(
                        onClick = { onMove(folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = IosAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder.name, color = MaterialTheme.colorScheme.onSurface)
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
private fun MultiSelectHintBanner(onDismiss: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        innerPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = IosAccent,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tip: Long-press a recording to select multiple",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = IosSecondaryLabel,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onPlayPause: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onTranscript: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShareAudio: () -> Unit,
    onCopyTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelectActive) onToggleSelect() },
                onLongClick = { if (!isMultiSelectActive) onLongPress() },
            ),
        innerPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isMultiSelectActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.size(48.dp),
                )
            } else {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = IosAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                recording.createdAt?.toDate()?.let { date ->
                    Text(
                        text = date.toShortDateString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                    )
                }
                if (recording.transcription != null) {
                    Text(
                        text = recording.transcription.take(60) + if (recording.transcription.length > 60) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!isMultiSelectActive) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (recording.transcription != null) {
                            DropdownMenuItem(
                                text = { Text("View Transcript") },
                                onClick = { menuExpanded = false; onTranscript() },
                                leadingIcon = { Icon(Icons.Default.TextSnippet, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuExpanded = false; onRename() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            onClick = { menuExpanded = false; onMove() },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Audio") },
                            onClick = { menuExpanded = false; onShareAudio() },
                            leadingIcon = { Icon(Icons.Default.Share, null) }
                        )
                        if (recording.transcription != null) {
                            DropdownMenuItem(
                                text = { Text("Copy Transcript") },
                                onClick = { menuExpanded = false; onCopyTranscript() },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Transcript") },
                                onClick = { menuExpanded = false; onShareTranscript() },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}
