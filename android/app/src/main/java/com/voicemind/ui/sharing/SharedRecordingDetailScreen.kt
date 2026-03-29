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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRecordingDetailScreen(
    ownerUid: String,
    recordingId: String,
    onBack: () -> Unit,
) {
    // All state is hardcoded/placeholder — wired to SharedRecordingDetailViewModel in Phase 6
    var isPlaying by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shared Recording",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Shared by Someone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                                leadingIcon = { Icon(Icons.Default.FileCopy, null) },
                                // No-op: will call duplicateSharedRecording Cloud Function in Phase 8
                                onClick = { menuExpanded = false },
                                enabled = false,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VmDimens.SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(VmDimens.SpaceLg))

            // ── Time display (placeholder) ───────────────────────────────
            Text(
                text = "0:00.0",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "0:00.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(VmDimens.SpaceXl))

            // ── Waveform placeholder ─────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VmDimens.SpaceXxxl + VmDimens.SpaceXxl),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Audio available after loading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(VmDimens.SpaceXl))

            // ── Transport controls (visual shell) ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { /* no-op: skip backward — wired in Phase 6 */ },
                    modifier = Modifier.size(VmDimens.TouchTarget),
                    enabled = false,
                ) {
                    Text(
                        text = "-5",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                FilledIconButton(
                    onClick = { /* no-op: play — wired in Phase 6 */ },
                    modifier = Modifier.size(VmDimens.IconXl + VmDimens.SpaceXl),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    enabled = false,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(VmDimens.IconLg + VmDimens.SpaceXs),
                    )
                }

                FilledTonalIconButton(
                    onClick = { /* no-op: skip forward — wired in Phase 6 */ },
                    modifier = Modifier.size(VmDimens.TouchTarget),
                    enabled = false,
                ) {
                    Text(
                        text = "+5",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(VmDimens.SpaceXxl))

            // ── Transcription section ────────────────────────────────────
            SectionCard(
                title = "Transcription",
                bodyText = "Transcription will appear here once loaded",
            )

            Spacer(Modifier.height(VmDimens.SpaceMd))

            // ── Summary section ──────────────────────────────────────────
            SectionCard(
                title = "Summary",
                bodyText = "Summary will appear here once loaded",
            )

            Spacer(Modifier.height(VmDimens.SpaceMd))

            // ── Tasks section ────────────────────────────────────────────
            SectionCard(
                title = "Tasks",
                bodyText = "Tasks will appear here once loaded",
            )

            Spacer(Modifier.height(VmDimens.SpaceXl))
        }
    }
}

@Composable
private fun SectionCard(title: String, bodyText: String) {
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
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(VmDimens.IconMd),
                )
            }
            Spacer(Modifier.height(VmDimens.SpaceSm))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
