package com.voicemind.ui.recording

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.voicemind.ui.sharing.ShareDialog
import com.voicemind.ui.navigation.recordingDetailRoute
import com.voicemind.ui.theme.ShimmerBlue
import com.voicemind.ui.theme.ShimmerGold
import com.voicemind.ui.theme.ShimmerPurple
import com.voicemind.ui.theme.VmDimens
import com.voicemind.util.LocalAppTimeZone
import com.voicemind.util.formatRecordingTime
import com.voicemind.util.toDateSectionKey
import com.voicemind.util.toShortDateString
import kotlinx.coroutines.delay

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

    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }
    var showShareDialog by remember { mutableStateOf<Recording?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var summarizingGroup by remember { mutableStateOf<String?>(null) }
    var showSummarizationPopup by remember { mutableStateOf(false) }
    var showCompletionToast by remember { mutableStateOf(false) }
    var wasSummarizing by remember { mutableStateOf(false) }

    // Show popup when summarization starts; show toast on completion.
    // We use wasSummarizing to reliably detect success vs initial state,
    // since collectiveSummarizeResult gets cleared by exitMultiSelect() and
    // may not be visible in a separate composition pass.
    LaunchedEffect(listState.isCollectiveSummarizing) {
        if (listState.isCollectiveSummarizing) {
            wasSummarizing = true
            showSummarizationPopup = true
        } else if (wasSummarizing) {
            showSummarizationPopup = false
            summarizingGroup = null
            if (listState.collectiveSummarizeError == null) {
                showCompletionToast = true
            }
            wasSummarizing = false
        }
    }

    // Auto-dismiss completion toast after 4 seconds
    LaunchedEffect(showCompletionToast) {
        if (showCompletionToast) {
            delay(4000L)
            showCompletionToast = false
        }
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
                                        Text("Back to Folders", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else null,
                        )
                    }
                }

                val appTz = LocalAppTimeZone.current
                val grouped = remember(listState.recordings, appTz) {
                    listState.recordings.groupBy { recording ->
                        recording.createdAt?.toDate()?.toDateSectionKey(appTz) ?: "Unknown"
                    }
                }

                LazyColumn {
                    grouped.forEach { (dateLabel, recordings) ->
                        item(key = "header_$dateLabel") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = VmDimens.ScreenHorizontalPadding, top = VmDimens.SpaceLg, bottom = VmDimens.SpaceXs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (recordings.size > 1 && !listState.isMultiSelectActive) {
                                    if (summarizingGroup == dateLabel && listState.isCollectiveSummarizing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(end = 4.dp),
                                            color = MaterialTheme.colorScheme.primary,
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
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "group_$dateLabel") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Column {
                                    recordings.forEachIndexed { index, recording ->
                                        val isSelected = recording.id in listState.selectedRecordingIds
                                        RecordingRow(
                                            recording = recording,
                                            isPlaying = listState.playingRecordingId == recording.id && !listState.isPlaybackPaused,
                                            isExpanded = listState.playingRecordingId == recording.id,
                                            isPlaybackPaused = listState.isPlaybackPaused,
                                            playbackPositionMs = listState.playbackPositionMs,
                                            playbackDurationMs = listState.playbackDurationMs,
                                            isMultiSelectActive = listState.isMultiSelectActive,
                                            isSelected = isSelected,
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
                                            onLongPress = { if (folderId == null) recordingsViewModel.enterMultiSelect(recording.id) },
                                            onTap = { navController?.navigate(recordingDetailRoute(recording.id)) },
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
                                            },
                                            onShareWithUser = { showShareDialog = recording },
                                        )
                                        if (index < recordings.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp),
                                                thickness = VmDimens.HairlineBorder,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(VmDimens.FabClearance)) }
                }
            }
        }

        // Bulk delete / move overlay (full-screen, blocking — intentional for destructive ops)
        if (listState.isBulkDeleting || listState.isBulkMoving) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Completion toast — slides in from the top, tappable to navigate
        AnimatedVisibility(
            visible = showCompletionToast,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            CompletionToast(
                onClick = {
                    showCompletionToast = false
                    navController?.navigate(Routes.Summaries.route)
                },
            )
        }

        // Scrim — dims background while island popup is visible
        AnimatedVisibility(
            visible = showSummarizationPopup && listState.isCollectiveSummarizing,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }

        // Summarization island — spring-animated floating pill above FAB
        AnimatedVisibility(
            visible = showSummarizationPopup && listState.isCollectiveSummarizing,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
                initialScale = 0.85f,
            ) + fadeIn(animationSpec = tween(150)),
            exit = scaleOut(
                animationSpec = tween(180),
                targetScale = 0.9f,
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = VmDimens.FabClearance + VmDimens.SpaceXl),
        ) {
            SummarizationPopup(onHide = { showSummarizationPopup = false })
        }

        RecordFab(
            onStartRecording = { recordingViewModel.startRecording() },
            modifier = Modifier.align(Alignment.BottomCenter),
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

    if (showShareDialog != null) {
        ShareDialog(onDismiss = { showShareDialog = null })
    }

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

@Composable
private fun SummarizationPopup(onHide: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(ShimmerBlue, ShimmerGold, ShimmerPurple, ShimmerBlue),
        start = Offset(animatedOffset * 800f - 400f, 0f),
        end = Offset(animatedOffset * 800f + 400f, 0f),
    )
    // Liquid glass: fully opaque base + layered specular highlights
    val isDark = isSystemInDarkTheme()

    // Glass rim border: bright white at top fading to shimmer hues at bottom
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.65f else 0.90f),
        0.35f to ShimmerBlue.copy(alpha = 0.50f),
        1.0f to ShimmerPurple.copy(alpha = 0.20f),
    )

    // Solid opaque base with subtle icy-blue tint (optical glass character)
    val islandBackground = if (isDark) Color(0xFF1E2030) else Color(0xFFF2F5FF)

    // Iridescent shimmer tint — diagonal blue→purple wash at low opacity
    val iridescence = Brush.linearGradient(
        colors = listOf(
            ShimmerBlue.copy(alpha = 0.10f),
            ShimmerPurple.copy(alpha = 0.08f),
            Color.Transparent,
        ),
        start = Offset(0f, 0f),
        end = Offset(280f, 56f),
    )
    // Top specular highlight — bright white band that reads as curved glass
    val liquidSpecular = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.75f),
        0.40f to Color.White.copy(alpha = if (isDark) 0.04f else 0.20f),
        1.0f to Color.Transparent,
    )
    val islandShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 220.dp, max = 320.dp)
            .shadow(
                elevation = 12.dp,
                shape = islandShape,
                spotColor = ShimmerBlue.copy(alpha = 0.6f),
                ambientColor = ShimmerPurple.copy(alpha = 0.25f),
            )
            .border(width = 1.5.dp, brush = borderBrush, shape = islandShape)
            .background(color = islandBackground, shape = islandShape)
            .clip(islandShape),
    ) {
        // Iridescent tint layer (bottom-most overlay)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush = iridescence),
        )
        // Specular highlight layer (topmost — the glass sheen)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush = liquidSpecular),
        )
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PulseRingIcon()
            Text(
                text = "Generating Summary…",
                style = MaterialTheme.typography.bodyMedium.copy(brush = shimmerBrush),
                maxLines = 1,
            )
            IconButton(
                onClick = onHide,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Hide",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun PulseRingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerScale",
    )
    val innerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerAlpha",
    )
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1.3f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerScale",
    )
    val outerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerAlpha",
    )

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(outerScale)
                .alpha(outerAlpha)
                .background(color = ShimmerBlue.copy(alpha = 0.3f), shape = CircleShape),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(innerScale)
                .alpha(innerAlpha)
                .background(color = ShimmerPurple.copy(alpha = 0.4f), shape = CircleShape),
        )
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ShimmerBlue,
        )
    }
}

@Composable
private fun CompletionToast(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VmDimens.SpaceLg, vertical = VmDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Summary ready. Tap to view",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
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
                    if (isAllSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (hasSelection) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onMove) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move selected", tint = MaterialTheme.colorScheme.primary)
                }
                if (!isSummarizing) {
                    IconButton(onClick = onSummarize) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Summarize selected", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
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
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tip: Long-press a recording to select multiple",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    isExpanded: Boolean,
    isPlaybackPaused: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onPlayPause: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onTranscript: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShareAudio: () -> Unit,
    onTap: () -> Unit,
    onCopyTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
    onShareWithUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelectActive) onToggleSelect() else onTap() },
                onLongClick = { if (!isMultiSelectActive) onLongPress() },
            )
            .padding(12.dp),
    ) {
        if (isMultiSelectActive) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onToggleSelect() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = CircleShape,
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        } else {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = recording.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            recording.createdAt?.toDate()?.let { date ->
                val durationStr = if (recording.durationSeconds > 0) " · ${formatRecordingTime(recording.durationSeconds)}" else ""
                Text(
                    text = date.toShortDateString(LocalAppTimeZone.current) + durationStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isMultiSelectActive) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    if (recording.transcription != null) {
                        DropdownMenuItem(
                            text = { Text("View Transcript") },
                            onClick = { menuExpanded = false; onTranscript() },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuExpanded = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Folder") },
                        onClick = { menuExpanded = false; onMove() },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Share Audio") },
                        onClick = { menuExpanded = false; onShareAudio() },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                    )
                    if (recording.transcription != null) {
                        DropdownMenuItem(
                            text = { Text("Copy Transcript") },
                            onClick = { menuExpanded = false; onCopyTranscript() },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Share Transcript") },
                            onClick = { menuExpanded = false; onShareTranscript() },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share with User") },
                        onClick = { menuExpanded = false; onShareWithUser() },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
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
            modifier = Modifier.padding(start = 56.dp, end = 8.dp, bottom = 12.dp),
        )
    }
    } // end Column
}
