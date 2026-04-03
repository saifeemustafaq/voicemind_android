package com.voicemind.ui.recording

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voicemind.data.model.Recording
import com.voicemind.ui.components.ProcessingStatusChip
import com.voicemind.util.LocalAppTimeZone
import com.voicemind.util.formatRecordingTime
import com.voicemind.util.toShortDateString

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    isDownloading: Boolean = false,
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
                if (isDownloading) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
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

        ProcessingStatusChip(
            hasTranscription = recording.transcription != null,
            processingFailed = recording.processingFailed,
            syncStatus = recording.syncStatus,
            modifier = Modifier.padding(start = 68.dp, end = 12.dp, bottom = 8.dp),
        )
    }
}
