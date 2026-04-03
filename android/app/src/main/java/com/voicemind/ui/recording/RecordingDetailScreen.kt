package com.voicemind.ui.recording

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.voicemind.R
import com.voicemind.ui.components.AudioWaveform
import com.voicemind.ui.components.NeedsInternetDialog
import com.voicemind.ui.components.SpeedBubble
import com.voicemind.ui.components.formatMmSsDecimal
import com.voicemind.ui.sharing.ShareDialog
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    recordingId: String,
    navController: NavController,
    onBack: () -> Unit,
) {
    val parentEntry = remember(navController) {
        navController.previousBackStackEntry
            ?: throw IllegalStateException("RecordingDetailScreen requires a parent back stack entry")
    }
    val playbackViewModel: RecordingsViewModel = hiltViewModel(parentEntry)
    val detailViewModel: RecordingDetailViewModel = hiltViewModel()

    val state by playbackViewModel.state.collectAsStateWithLifecycle()
    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val recording = state.recordings.find { it.id == recordingId }

    val isThisPlaying = state.playingRecordingId == recordingId
    val isPlaying = isThisPlaying && !state.isPlaybackPaused
    val positionMs = if (isThisPlaying) state.playbackPositionMs else 0L
    val durationMs = if (isThisPlaying) state.playbackDurationMs else
        (recording?.durationSeconds?.times(1000L) ?: 0L)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val sheetState by playbackViewModel.sheetState.collectAsStateWithLifecycle()
    LaunchedEffect(recording?.id) {
        recording?.let { playbackViewModel.openTranscriptSheet(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackViewModel.stopPlayback()
        }
    }

    // Overlay dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    if (recording == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuExpanded = false; showRenameDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Folder") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                onClick = { menuExpanded = false; showMoveDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Share Audio") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menuExpanded = false
                                    playbackViewModel.shareAudio(context, recording)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share with User") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                onClick = {
                                    menuExpanded = false
                                    playbackViewModel.requestShareWithUser(recording)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                text = recording.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // Current position — large monospaced clock
            Text(
                text = formatMmSsDecimal(positionMs),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                textAlign = TextAlign.Center,
            )

            // Total duration — smaller muted text
            Text(
                text = formatMmSsDecimal(durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // Waveform scrubber
            AudioWaveform(
                bars = detailState.waveformBars,
                progress = progress,
                isExtracting = detailState.isExtractingWaveform,
                onSeek = { newProgress ->
                    playbackViewModel.seekTo((newProgress * durationMs).toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Speed bubble
            SpeedBubble(
                currentSpeed = state.playbackSpeed,
                onTap = { playbackViewModel.cycleSpeed() },
                onSpeedSelected = { playbackViewModel.setSpeed(it) },
            )

            Spacer(Modifier.height(16.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { playbackViewModel.skipBackward5() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_backward_5),
                        contentDescription = "Skip back 5 seconds",
                        modifier = Modifier.size(28.dp),
                    )
                }

                FilledIconButton(
                    onClick = {
                        when {
                            !isThisPlaying -> playbackViewModel.playAudio(recording)
                            isPlaying -> playbackViewModel.pausePlayback()
                            else -> playbackViewModel.resumePlayback()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }

                FilledTonalIconButton(
                    onClick = { playbackViewModel.skipForward5() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_forward_5),
                        contentDescription = "Skip forward 5 seconds",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Content tabs
            RecordingContentTabs(
                recording = recording,
                sheetState = sheetState,
                onGenerateSummary = { playbackViewModel.generateSummary(recording) },
                onGenerateTasks = { playbackViewModel.generateTasks(recording) },
                onRetryProcessing = { playbackViewModel.retryProcessing(recording) },
                onRetrySummary = { playbackViewModel.generateSummary(recording) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameRecordingDialog(
            currentTitle = recording.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                playbackViewModel.renameRecording(recording.id, newTitle)
                showRenameDialog = false
            },
        )
    }

    // Move to folder dialog
    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = state.folders,
            onDismiss = { showMoveDialog = false },
            onConfirm = { folderId ->
                playbackViewModel.moveToFolder(recording.id, folderId)
                showMoveDialog = false
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        DeleteRecordingDialog(
            recordingTitle = recording.title,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                playbackViewModel.deleteRecording(recording)
                showDeleteConfirm = false
                onBack()
            },
        )
    }

    // Share with user — routed through ViewModel for connectivity guard
    if (state.shareWithUserTarget?.id == recordingId) {
        ShareDialog(
            itemId = recordingId,
            itemType = "recording",
            onDismiss = { playbackViewModel.clearShareWithUser() },
        )
    }

    // Needs-internet dialog
    NeedsInternetDialog(
        reason = state.needsInternetDialog,
        onDismiss = { playbackViewModel.dismissNeedsInternetDialog() },
    )
}
