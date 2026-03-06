package com.voicemind.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CirclePause
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Trash2
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.voicemind.ui.theme.IosSeparator
import com.voicemind.ui.theme.VmDimens
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
                icon = Lucide.House,
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
                                .padding(start = VmDimens.ScreenHorizontalPadding, end = VmDimens.SpaceSm, top = VmDimens.SpaceSm, bottom = VmDimens.SpaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (foldersExpanded) Lucide.ChevronUp
                                else Lucide.ChevronDown,
                                contentDescription = if (foldersExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    val cardRadius = VmDimens.RadiusMedium
                                    visibleFolders.forEachIndexed { index, folder ->
                                        val isFirst = index == 0
                                        val isLast = index == visibleFolders.lastIndex && !hasMore
                                        val rowShape = when {
                                            isFirst && isLast -> RoundedCornerShape(cardRadius)
                                            isFirst -> RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius)
                                            isLast -> RoundedCornerShape(bottomStart = cardRadius, bottomEnd = cardRadius)
                                            else -> RoundedCornerShape(0.dp)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .clickable { onFolderClick(folder.id) }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Lucide.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(end = 4.dp),
                                                )
                                            }
                                            Icon(
                                                Lucide.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (index < visibleFolders.lastIndex || hasMore) {
                                            HorizontalDivider(
                                                color = IosSeparator,
                                                thickness = VmDimens.HairlineBorder,
                                                modifier = Modifier.padding(start = 52.dp),
                                            )
                                        }
                                    }
                                    if (hasMore) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(bottomStart = cardRadius, bottomEnd = cardRadius))
                                                .clickable { onViewAllFolders() }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = "View more",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
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
                        Text(
                            text = "Recent Files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = VmDimens.ScreenHorizontalPadding, top = VmDimens.SpaceSm, bottom = VmDimens.SpaceXs)
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp) {
                            Column {
                                homeState.recentRecordings.forEachIndexed { index, recording ->
                                    HomeRecordingRow(
                                        recording = recording,
                                        isPlaying = listState.playingRecordingId == recording.id && !listState.isPlaybackPaused,
                                        isExpanded = listState.playingRecordingId == recording.id,
                                        isPlaybackPaused = listState.isPlaybackPaused,
                                        playbackPositionMs = listState.playbackPositionMs,
                                        playbackDurationMs = listState.playbackDurationMs,
                                        onPlayPause = {
                                            if (listState.playingRecordingId == recording.id) {
                                                recordingsViewModel.stopPlayback()
                                            } else {
                                                recordingsViewModel.playAudio(recording)
                                            }
                                        },
                                        onPause = { recordingsViewModel.pausePlayback() },
                                        onResume = { recordingsViewModel.resumePlayback() },
                                        onSeekTo = { recordingsViewModel.seekTo(it) },
                                        onSkipForward = { recordingsViewModel.skipForward15() },
                                        onSkipBackward = { recordingsViewModel.skipBackward15() },
                                        onTranscript = { showTranscript = recording },
                                        onDelete = { showDeleteConfirm = recording },
                                        onRename = { showRenameDialog = recording },
                                        onShareAudio = { recordingsViewModel.shareAudio(context, recording) },
                                        onMoveToFolder = { showMoveDialog = recording },
                                    )
                                    HorizontalDivider(
                                        color = IosSeparator,
                                        thickness = VmDimens.HairlineBorder,
                                        modifier = Modifier.padding(start = 52.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(bottomStart = VmDimens.RadiusMedium, bottomEnd = VmDimens.RadiusMedium))
                                        .clickable { onRecordingClick() }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "View more",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                if (homeState.recentRecordings.isEmpty() && homeState.folders.isEmpty() && !homeState.isLoading) {
                    item {
                        EmptyStateCard(
                            icon = Lucide.Mic,
                            message = "Tap the record button to capture your first thought",
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(VmDimens.FabClearance)) }
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
    isExpanded: Boolean,
    isPlaybackPaused: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onPlayPause: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onTranscript: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShareAudio: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    var longPressMenuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPressMenuExpanded = true
                    },
                )
                .padding(horizontal = VmDimens.ScreenHorizontalPadding, vertical = VmDimens.SpaceMd),
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
                            text = date.toFullDateString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(VmDimens.SpaceSm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(VmDimens.TouchTarget),
                    ) {
                        Icon(
                            if (isPlaying) Lucide.CirclePause else Lucide.CirclePlay,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(VmDimens.IconMd),
                        )
                    }

                    IconButton(
                        onClick = onTranscript,
                        modifier = Modifier.size(VmDimens.TouchTarget),
                    ) {
                        Icon(
                            Lucide.FileText,
                            contentDescription = "View transcript",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(VmDimens.IconMd),
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(VmDimens.TouchTarget),
                    ) {
                        Icon(
                            Lucide.Trash2,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(VmDimens.IconMd),
                        )
                    }
                }
            }

        DropdownMenu(
            expanded = longPressMenuExpanded,
            onDismissRequest = { longPressMenuExpanded = false },
            shape = RoundedCornerShape(VmDimens.RadiusSmall),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(VmDimens.HairlineBorder, MaterialTheme.colorScheme.outline),
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { longPressMenuExpanded = false; onRename() },
                leadingIcon = { Icon(Lucide.Pencil, null) },
            )
            DropdownMenuItem(
                text = { Text("Share Audio") },
                onClick = { longPressMenuExpanded = false; onShareAudio() },
                leadingIcon = { Icon(Lucide.Share, null) },
            )
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                onClick = { longPressMenuExpanded = false; onMoveToFolder() },
                leadingIcon = { Icon(Lucide.FolderInput, null) },
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isExpanded,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
    ) {
        com.voicemind.ui.components.InlinePlayerControls(
            isPlaying = !isPlaybackPaused,
            positionMs = playbackPositionMs,
            durationMs = playbackDurationMs,
            onPlayPause = { if (isPlaybackPaused) onResume() else onPause() },
            onSkipForward = onSkipForward,
            onSkipBackward = onSkipBackward,
            onSeek = onSeekTo,
            modifier = Modifier.padding(start = VmDimens.ScreenHorizontalPadding, end = VmDimens.ScreenHorizontalPadding, bottom = VmDimens.SpaceMd),
        )
    }
    } // end Column
}
