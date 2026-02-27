package com.voicemind.ui.recording

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosBackground
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosWhite
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordingsViewModel: RecordingsViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    folderId: String? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val listState by recordingsViewModel.state.collectAsState()
    val recState by recordingViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) recordingViewModel.startRecording()
    }

    LaunchedEffect(folderId) {
        recordingsViewModel.filterByFolder(folderId)
    }

    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (folderId == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = IosAccent,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recordings", style = MaterialTheme.typography.titleSmall)
                        }
                    },
                    navigationIcon = {
                        if (onOpenDrawer != null) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (listState.recordings.isEmpty() && !listState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (folderId != null) Icons.Default.Folder else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = IosSecondaryLabel
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (folderId != null) "No recordings in this folder"
                                else "No recordings yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosSecondaryLabel,
                            )
                            if (folderId != null && onBack != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                TextButton(onClick = onBack) {
                                    Text("Back to Folders", color = IosAccent)
                                }
                            }
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listState.recordings, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            isPlaying = listState.playingRecordingId == recording.id,
                            onPlayPause = {
                                if (listState.playingRecordingId == recording.id) {
                                    recordingsViewModel.stopPlayback()
                                } else {
                                    recordingsViewModel.playAudio(recording)
                                }
                            },
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
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        val fabDensity = LocalDensity.current
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(160.dp)
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to IosBackground,
                            0.45f to IosBackground,
                            1.0f to Color.Transparent,
                        ),
                        radius = with(fabDensity) { 80.dp.toPx() },
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            FloatingActionButton(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) recordingViewModel.startRecording()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.size(72.dp),
                containerColor = IosAccent,
                contentColor = IosWhite,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Record", modifier = Modifier.size(32.dp))
            }
        }

        if (recState.showSheet) {
            RecordingBottomSheet(
                viewModel = recordingViewModel,
                onDismiss = { recordingViewModel.discardRecording() }
            )
        }
    }

    showTranscript?.let { recording ->
        TranscriptSheet(
            recording = recording,
            viewModel = recordingsViewModel,
            onDismiss = { showTranscript = null },
        )
    }

    showRenameDialog?.let { recording ->
        RenameRecordingDialog(
            currentTitle = recording.title,
            onConfirm = { newTitle ->
                recordingsViewModel.renameRecording(recording.id, newTitle)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    showMoveDialog?.let { recording ->
        MoveToFolderDialog(
            folders = listState.folders,
            onConfirm = { targetFolderId ->
                recordingsViewModel.moveToFolder(recording.id, targetFolderId)
                showMoveDialog = null
            },
            onDismiss = { showMoveDialog = null }
        )
    }

    showDeleteConfirm?.let { recording ->
        DeleteRecordingDialog(
            recordingTitle = recording.title,
            onConfirm = {
                recordingsViewModel.deleteRecording(recording)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onTranscript: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShareAudio: () -> Unit,
    onCopyTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 12.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = IosAccent,
                    modifier = Modifier.size(36.dp)
                )
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
                        text = SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault()).format(date),
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
