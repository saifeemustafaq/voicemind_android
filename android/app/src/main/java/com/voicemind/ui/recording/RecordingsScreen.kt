package com.voicemind.ui.recording

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.EmptyStateCard
import com.voicemind.ui.components.NeedsInternetDialog
import com.voicemind.ui.components.RecordFab
import com.voicemind.ui.components.RecordingDialogsHost
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.navigation.recordingDetailRoute
import com.voicemind.ui.sharing.ShareDialog
import com.voicemind.ui.theme.VmDimens
import com.voicemind.util.LocalAppTimeZone
import com.voicemind.util.toDateSectionKey
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(folderId) {
        recordingsViewModel.filterByFolder(folderId)
        recordingViewModel.setCurrentFolder(folderId)
    }

    LaunchedEffect(listState.snackbarMessage) {
        listState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            recordingsViewModel.clearSnackbar()
        }
    }

    var showTranscript by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var showMoveDialog by remember { mutableStateOf<Recording?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Recording?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var summarizingGroup by remember { mutableStateOf<String?>(null) }
    var showSummarizationPopup by remember { mutableStateOf(false) }
    var showCompletionToast by remember { mutableStateOf(false) }
    var wasSummarizing by remember { mutableStateOf(false) }

    // Show popup when summarization starts; show toast on completion.
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

            Column(modifier = Modifier.weight(1f).padding(horizontal = VmDimens.ScreenHorizontalPadding)) {
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
                                            androidx.compose.material3.Icon(
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
                                            isDownloading = listState.downloadingRecordingId == recording.id,
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
                                            onTap = { navController?.navigate(recordingDetailRoute(recording.id)) },
                                            onToggleSelect = { recordingsViewModel.toggleSelection(recording.id) },
                                            onTranscript = { showTranscript = recording },
                                            onRename = { showRenameDialog = recording },
                                            onMove = { showMoveDialog = recording },
                                            onDelete = { showDeleteConfirm = recording },
                                            onShareAudio = { recordingsViewModel.shareAudio(context, recording) },
                                            onCopyTranscript = { recordingsViewModel.copyTranscriptToClipboard(recording) },
                                            onShareTranscript = {
                                                recording.transcription?.let { text ->
                                                    recordingsViewModel.shareTranscript(context, text)
                                                }
                                            },
                                            onShareWithUser = { recordingsViewModel.requestShareWithUser(recording) },
                                        )
                                        if (index < recordings.lastIndex) {
                                            androidx.compose.material3.HorizontalDivider(
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

        // Bulk delete / move overlay
        if (listState.isBulkDeleting || listState.isBulkMoving) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Completion toast — slides in from the top
        AnimatedVisibility(
            visible = showCompletionToast,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                    start = VmDimens.ScreenHorizontalPadding,
                    end = VmDimens.ScreenHorizontalPadding,
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

        // Summarization island
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = VmDimens.FabClearance),
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

    // Share with user
    listState.shareWithUserTarget?.let { recording ->
        ShareDialog(
            itemId = recording.id,
            itemType = "recording",
            onDismiss = { recordingsViewModel.clearShareWithUser() },
        )
    }

    // Needs-internet dialog
    NeedsInternetDialog(
        reason = listState.needsInternetDialog,
        onDismiss = { recordingsViewModel.dismissNeedsInternetDialog() },
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
        MoveToFolderDialog(
            folders = listState.folders,
            showFolderIcon = true,
            onDismiss = { showBulkMoveDialog = false },
            onConfirm = { targetFolderId ->
                showBulkMoveDialog = false
                recordingsViewModel.bulkMoveToFolder(listState.selectedRecordingIds.toList(), targetFolderId)
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
