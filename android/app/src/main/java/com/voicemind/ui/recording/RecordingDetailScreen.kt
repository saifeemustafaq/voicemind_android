package com.voicemind.ui.recording

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.voicemind.R
import com.voicemind.ui.components.AudioWaveform
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.sharing.ShareDialog
import androidx.compose.ui.res.painterResource

private fun formatMmSsDecimal(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return "%d:%02d.%d".format(minutes, seconds, tenths)
}

private val speedSteps = listOf(0.5f, 1.0f, 1.5f, 2.0f)

@Composable
private fun SpeedBubble(
    currentSpeed: Float,
    onTap: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var hoveredIndex by remember { mutableStateOf(speedSteps.indexOf(currentSpeed).coerceAtLeast(1)) }

    // Width per slot in px — calculated from fixed dp
    val slotWidthDp = 64.dp
    val slotWidthPx = with(density) { slotWidthDp.toPx() }
    val bubbleWidthDp = slotWidthDp * speedSteps.size + 16.dp

    Box(contentAlignment = Alignment.Center) {
        // Drag strip — shown only while dragging
        if (dragging) {
            Surface(
                modifier = Modifier
                    .width(bubbleWidthDp)
                    .height(48.dp)
                    .offset(y = (-56).dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    speedSteps.forEachIndexed { index, speed ->
                        val isHovered = index == hoveredIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isHovered) 1.3f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                            label = "scale$index",
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(
                                    if (isHovered) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                        ) {
                            Text(
                                text = if (speed == 1.0f || speed == 2.0f) "${speed.toInt()}x"
                                       else "${"%.1f".format(speed)}x",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (isHovered) 13.sp else 11.sp,
                                ),
                                color = if (isHovered) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // The chip / bubble itself
        val chipScale by animateFloatAsState(
            targetValue = if (dragging) 1.1f else 1f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
            label = "chipScale",
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .scale(chipScale)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                .pointerInput(currentSpeed) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            hoveredIndex = speedSteps.indexOf(currentSpeed).coerceAtLeast(0)
                            dragOffsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            val baseIndex = speedSteps.indexOf(currentSpeed).coerceAtLeast(0)
                            val delta = (dragOffsetX / slotWidthPx).toInt()
                            hoveredIndex = (baseIndex + delta).coerceIn(0, speedSteps.lastIndex)
                        },
                        onDragEnd = {
                            onSpeedSelected(speedSteps[hoveredIndex])
                            dragging = false
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffsetX = 0f
                        },
                    )
                },
        ) {
            Text(
                text = "${"%.1f".format(currentSpeed)}x",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    recordingId: String,
    navController: NavController,
    onBack: () -> Unit,
) {
    // Share the same RecordingsViewModel instance that RecordingsScreen uses
    val recordingsEntry = remember(navController) {
        navController.getBackStackEntry(Routes.Recordings.route)
    }
    val playbackViewModel: RecordingsViewModel = hiltViewModel(recordingsEntry)
    val detailViewModel: RecordingDetailViewModel = hiltViewModel()

    val state by playbackViewModel.state.collectAsStateWithLifecycle()
    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val recording = state.recordings.find { it.id == recordingId }

    // Auto-play on entry if nothing is playing this recording yet
    LaunchedEffect(recordingId, recording) {
        if (recording != null && state.playingRecordingId != recordingId) {
            playbackViewModel.playAudio(recording)
        }
    }

    val isThisPlaying = state.playingRecordingId == recordingId
    val isPlaying = isThisPlaying && !state.isPlaybackPaused
    val positionMs = if (isThisPlaying) state.playbackPositionMs else 0L
    val durationMs = if (isThisPlaying) state.playbackDurationMs else
        (recording?.durationSeconds?.times(1000L) ?: 0L)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Overlay dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
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
                                onClick = { menuExpanded = false; showShareDialog = true },
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

            Spacer(Modifier.height(32.dp))

            // View Content button
            Button(
                onClick = {
                    playbackViewModel.openTranscriptSheet(recording)
                    showSheet = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View Content")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // TranscriptSheet
    if (showSheet) {
        TranscriptSheet(
            recording = recording,
            viewModel = playbackViewModel,
            onDismiss = { showSheet = false },
        )
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

    if (showShareDialog) {
        ShareDialog(
            recordingId = recordingId,
            itemType = "recording",
            onDismiss = { showShareDialog = false },
        )
    }
}
