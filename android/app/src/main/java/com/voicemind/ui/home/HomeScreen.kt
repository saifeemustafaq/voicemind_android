package com.voicemind.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.recording.DeleteRecordingDialog
import com.voicemind.ui.recording.MoveToFolderDialog
import com.voicemind.ui.recording.RecordingBottomSheet
import com.voicemind.ui.recording.RecordingViewModel
import com.voicemind.ui.recording.RecordingsViewModel
import com.voicemind.ui.recording.RenameRecordingDialog
import com.voicemind.ui.recording.TranscriptSheet
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSeparator
import com.voicemind.ui.theme.IosWhite
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onFolderClick: (String) -> Unit,
    onRecordingClick: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    homeViewModel: HomeViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    recordingsViewModel: RecordingsViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val recordingState by recordingViewModel.uiState.collectAsState()
    val listState by recordingsViewModel.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) recordingViewModel.startRecording()
    }

    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (onOpenDrawer != null) {
                TopAppBar(
                    title = { Text("VoiceMind AI", style = MaterialTheme.typography.titleSmall) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (homeState.folders.isNotEmpty()) {
                    item {
                        Text(
                            text = "Folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp) {
                            Column {
                                homeState.folders.forEachIndexed { index, folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFolderClick(folder.id) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = IosAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp),
                                        )
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = IosSecondaryLabel,
                                        )
                                    }
                                    if (index < homeState.folders.lastIndex) {
                                        HorizontalDivider(
                                            color = IosSeparator,
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(start = 52.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (homeState.recentRecordings.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recent Files",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp) {
                            Column {
                                homeState.recentRecordings.forEachIndexed { index, recording ->
                                    HomeRecordingRow(
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
                                        onDelete = { showDeleteConfirm = recording },
                                        onRename = { showRenameDialog = recording },
                                        onShareAudio = { recordingsViewModel.shareAudio(context, recording) },
                                        onMoveToFolder = { showMoveDialog = recording },
                                    )
                                    if (index < homeState.recentRecordings.lastIndex) {
                                        HorizontalDivider(
                                            color = IosSeparator,
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(start = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (homeState.recentRecordings.isEmpty() && homeState.folders.isEmpty() && !homeState.isLoading) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = IosSecondaryLabel
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tap the record button to capture your first thought",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IosSecondaryLabel,
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) recordingViewModel.startRecording()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(72.dp),
            containerColor = IosAccent,
            contentColor = IosWhite,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Record",
                modifier = Modifier.size(32.dp)
            )
        }

        if (recordingState.showSheet) {
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
            onConfirm = { folderId ->
                recordingsViewModel.moveToFolder(recording.id, folderId)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onTranscript: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShareAudio: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    var longPressMenuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { longPressMenuExpanded = true },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recording.createdAt?.toDate()?.let { date ->
                        Text(
                            text = SimpleDateFormat(
                                "MMM dd, yyyy 'at' h:mm a",
                                Locale.getDefault()
                            ).format(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = IosAccent,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    IconButton(
                        onClick = onTranscript,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "View transcript",
                            tint = IosAccent,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = IosDestructive,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

        DropdownMenu(
            expanded = longPressMenuExpanded,
            onDismissRequest = { longPressMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { longPressMenuExpanded = false; onRename() },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
            )
            DropdownMenuItem(
                text = { Text("Share Audio") },
                onClick = { longPressMenuExpanded = false; onShareAudio() },
                leadingIcon = { Icon(Icons.Default.Share, null) },
            )
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                onClick = { longPressMenuExpanded = false; onMoveToFolder() },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
            )
        }
    }
}
