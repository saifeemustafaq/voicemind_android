package com.voicemind.ui.sharing

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.R
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.AudioWaveform
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.SpeedBubble
import com.voicemind.ui.components.formatMmSsDecimal
import com.voicemind.ui.recording.MoveToFolderDialog
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRecordingDetailScreen(
    ownerUid: String,
    recordingId: String,
    onBack: () -> Unit,
) {
    val viewModel: SharedRecordingDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.duplicateSuccess) {
        if (state.duplicateSuccess != null) {
            snackbarHostState.showSnackbar("Recording duplicated successfully")
            viewModel.clearDuplicateSuccess()
        }
    }

    LaunchedEffect(state.addTaskError) {
        state.addTaskError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAddTaskError()
        }
    }

    val progress = if (state.durationMs > 0)
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    else 0f

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.recording?.title
                                ?: if (state.isLoading) "Loading..." else "Recording unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.ownerName.isNotEmpty()) {
                            Text(
                                text = "Shared by ${state.ownerName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = {
                                    if (state.isDuplicating) {
                                        CircularProgressIndicator(modifier = Modifier.size(VmDimens.IconMd))
                                    } else {
                                        Icon(Icons.Default.FileCopy, null)
                                    }
                                },
                                onClick = {
                                    menuExpanded = false
                                    showFolderPicker = true
                                },
                                enabled = !state.isDuplicating,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.recording == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.recording == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This recording is no longer available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(VmDimens.SpaceXl),
                    )
                }
            }
            else -> {
                val recording = state.recording!!
                val controlsEnabled = state.audioUrl != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VmDimens.SpaceXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(VmDimens.SpaceLg))

                    // Time display
                    Text(
                        text = formatMmSsDecimal(state.positionMs),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatMmSsDecimal(state.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(VmDimens.SpaceXl))

                    // Waveform scrubber
                    AudioWaveform(
                        bars = state.waveformBars,
                        progress = progress,
                        isExtracting = state.isExtractingWaveform,
                        onSeek = { newProgress ->
                            viewModel.seekTo((newProgress * state.durationMs).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    )

                    Spacer(Modifier.height(VmDimens.SpaceXl))

                    // Speed bubble
                    SpeedBubble(
                        currentSpeed = state.playbackSpeed,
                        onTap = { viewModel.cycleSpeed() },
                        onSpeedSelected = { viewModel.setSpeed(it) },
                    )

                    Spacer(Modifier.height(VmDimens.SpaceMd))

                    // Transport controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.skipBackward5() },
                            modifier = Modifier.size(VmDimens.TouchTarget),
                            enabled = controlsEnabled,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_backward_5),
                                contentDescription = "Skip back 5 seconds",
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (state.isPlaying) viewModel.pause() else viewModel.playOrResume()
                            },
                            modifier = Modifier.size(VmDimens.IconXl + VmDimens.SpaceXl),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            enabled = controlsEnabled,
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(VmDimens.IconLg + VmDimens.SpaceXs),
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.skipForward5() },
                            modifier = Modifier.size(VmDimens.TouchTarget),
                            enabled = controlsEnabled,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_forward_5),
                                contentDescription = "Skip forward 5 seconds",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    state.error?.let { errorMsg ->
                        Spacer(Modifier.height(VmDimens.SpaceSm))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(VmDimens.SpaceXxl))

                    // Transcription
                    SharedContentCard(
                        title = "Transcription",
                        bodyText = recording.transcription ?: "No transcription available",
                        hasContent = recording.transcription != null,
                        onCopy = recording.transcription?.let { text ->
                            { clipboardManager.setText(AnnotatedString(text)) }
                        },
                    )

                    Spacer(Modifier.height(VmDimens.SpaceMd))

                    // Summary
                    SharedContentCard(
                        title = "Summary",
                        bodyText = recording.summary ?: "No summary available",
                        hasContent = recording.summary != null,
                        onCopy = recording.summary?.let { text ->
                            { clipboardManager.setText(AnnotatedString(text)) }
                        },
                    )

                    Spacer(Modifier.height(VmDimens.SpaceMd))

                    // Tasks
                    SharedTasksCard(
                        tasks = state.tasks,
                        addedTaskIds = state.addedTaskIds,
                        onAddTask = viewModel::addTaskToChecklist,
                    )

                    Spacer(Modifier.height(VmDimens.SpaceXl))
                }
            }
        }
    }

    if (showFolderPicker) {
        MoveToFolderDialog(
            folders = state.folders,
            title = "Duplicate to Folder",
            onConfirm = { folderId ->
                showFolderPicker = false
                viewModel.duplicateToFolder(folderId)
            },
            onDismiss = { showFolderPicker = false },
        )
    }
}

@Composable
private fun SharedContentCard(
    title: String,
    bodyText: String,
    hasContent: Boolean,
    onCopy: (() -> Unit)?,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (onCopy != null) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(VmDimens.TouchTarget),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy $title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(VmDimens.IconMd),
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(VmDimens.IconMd),
                    )
                }
            }
            Spacer(Modifier.height(VmDimens.SpaceSm))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasContent) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SharedTasksCard(
    tasks: List<ActionItem>,
    addedTaskIds: Set<String>,
    onAddTask: (ActionItem) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(VmDimens.SpaceSm))
            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.forEach { task ->
                    SharedTaskRow(
                        task = task,
                        isAdded = task.id in addedTaskIds,
                        onAddTask = { onAddTask(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTaskRow(
    task: ActionItem,
    isAdded: Boolean,
    onAddTask: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VmDimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (task.completed) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(VmDimens.IconMd),
        )
        Spacer(Modifier.width(VmDimens.SpaceSm))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onAddTask,
            enabled = !isAdded,
            modifier = Modifier.size(VmDimens.TouchTarget),
        ) {
            Icon(
                imageVector = if (isAdded) Icons.Default.CheckCircle else Icons.Default.AddTask,
                contentDescription = if (isAdded) "Added to checklist" else "Add to checklist",
                tint = if (isAdded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(VmDimens.IconMd),
            )
        }
    }
}
