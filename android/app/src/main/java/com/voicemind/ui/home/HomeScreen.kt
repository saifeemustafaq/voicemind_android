package com.voicemind.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.EmptyStateCard
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.RecordFab
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.components.RecordingDialogsHost
import com.voicemind.ui.recording.RecordingBottomSheet
import com.voicemind.ui.recording.RecordingViewModel
import com.voicemind.ui.recording.RecordingsViewModel
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSeparator
import com.voicemind.util.toFullDateString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onFolderClick: (String) -> Unit,
    onRecordingClick: () -> Unit,
    onViewAllFolders: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    homeViewModel: HomeViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    recordingsViewModel: RecordingsViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val recordingState by recordingViewModel.uiState.collectAsStateWithLifecycle()
    val listState by recordingsViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var foldersExpanded by remember { mutableStateOf(true) }
    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VoiceMindTopAppBar(
                title = "VoiceMind AI",
                icon = Icons.Default.Home,
                onOpenDrawer = onOpenDrawer,
                onSettings = onSettings,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (homeState.folders.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { foldersExpanded = !foldersExpanded }
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosSecondaryLabel,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (foldersExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (foldersExpanded) "Collapse" else "Expand",
                                tint = IosSecondaryLabel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    item {
                        val maxVisible = 3
                        val visibleFolders = homeState.folders.take(maxVisible)
                        val hasMore = homeState.folders.size > maxVisible

                        AnimatedVisibility(
                            visible = foldersExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp) {
                                Column {
                                    visibleFolders.forEachIndexed { index, folder ->
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
                                            val count = homeState.folderRecordingCounts[folder.id] ?: 0
                                            if (count > 0) {
                                                Text(
                                                    text = "$count",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = IosSecondaryLabel,
                                                    modifier = Modifier.padding(end = 4.dp),
                                                )
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = IosSecondaryLabel,
                                            )
                                        }
                                        if (index < visibleFolders.lastIndex || hasMore) {
                                            HorizontalDivider(
                                                color = IosSeparator,
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(start = 52.dp),
                                            )
                                        }
                                    }
                                    if (hasMore) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onViewAllFolders() }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = "View more",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = IosAccent,
                                            )
                                        }
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
                                    HorizontalDivider(
                                        color = IosSeparator,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRecordingClick() }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "View more",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = IosAccent,
                                    )
                                }
                            }
                        }
                    }
                }

                if (homeState.recentRecordings.isEmpty() && homeState.folders.isEmpty() && !homeState.isLoading) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Mic,
                            message = "Tap the record button to capture your first thought",
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        RecordFab(
            onStartRecording = { recordingViewModel.startRecording() },
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        if (recordingState.showSheet) {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recording.createdAt?.toDate()?.let { date ->
                        Text(
                            text = date.toFullDateString(),
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
