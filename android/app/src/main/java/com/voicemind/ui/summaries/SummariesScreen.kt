package com.voicemind.ui.summaries

import android.widget.Toast
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.voicemind.data.model.CollectiveSummary
import com.voicemind.ui.components.EmptyStateCard
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.VmDimens
import com.voicemind.util.LocalAppTimeZone
import com.voicemind.util.toShortDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    viewModel: SummariesViewModel = hiltViewModel(),
    onOpenDrawer: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Summaries",
            icon = Icons.Default.AutoAwesome,
            onOpenDrawer = onOpenDrawer,
            onSettings = onSettings,
            onInfoClick = { viewModel.showInfoSheet() },
        )

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.summaries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Default.AutoAwesome,
                            message = "No summaries yet.\nSelect recordings and tap the summarize icon.",
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(state.summaries, key = { it.id }) { summary ->
                            SummaryRow(
                                summary = summary,
                                onClick = { viewModel.selectSummary(summary) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(200),
                                    fadeOutSpec = tween(200),
                                ),
                            )
                        }
                        item { Spacer(modifier = Modifier.height(VmDimens.FabClearance)) }
                    }
                }
            }
        }
    }

    // Full summary bottom sheet
    state.selectedSummary?.let { summary ->
        SummaryDetailSheet(
            summary = summary,
            onDismiss = { viewModel.clearSelection() },
            onCopy = {
                viewModel.copyToClipboard(context, summary.summary)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onShare = { viewModel.share(context, summary.summary) },
            onDelete = { viewModel.deleteSummary(summary.id) },
        )
    }

    if (state.showInfoSheet) {
        SummariesInfoSheet(onDismiss = { viewModel.dismissInfoSheet() })
    }
}

@Composable
private fun SummaryRow(
    summary: CollectiveSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick, innerPadding = VmDimens.SpaceMd) {
        Column {
            Text(
                text = summary.summary.stripMarkdown(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))
            val sourcesLabel = when {
                summary.recordingTitles.size <= 2 -> summary.recordingTitles.joinToString(", ")
                else -> "${summary.recordingTitles.size} recordings"
            }
            Text(
                text = sourcesLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary.createdAt?.toDate()?.let { date ->
                Text(
                    text = date.toShortDateString(LocalAppTimeZone.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryDetailSheet(
    summary: CollectiveSummary,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VmDimens.SpaceXl)
                .padding(bottom = VmDimens.SpaceXxl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summary", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RichText {
                    Markdown(content = summary.summary)
                }

                if (summary.recordingTitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    summary.recordingTitles.forEach { title ->
                        Text(
                            text = "• $title",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                summary.createdAt?.toDate()?.let { date ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = date.toShortDateString(LocalAppTimeZone.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete summary?") },
            text = { Text("This summary will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummariesInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("How Summaries Work", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val steps = listOf(
                "Go to the Recordings screen.",
                "Long-press a recording to enter multi-select mode.",
                "Select one or more recordings.",
                "Tap the \u2728 Summarize button in the toolbar.",
                "Your summary will appear here!",
            )
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}.  $step",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Summaries are generated from the transcripts of your selected recordings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                
            ) {
                Text("Got it")
            }
        }
    }
}

private fun String.stripMarkdown(): String = this
    .replace(Regex("#{1,6}\\s+"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .replace(Regex("__(.+?)__"), "$1")
    .replace(Regex("_(.+?)_"), "$1")
    .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
    .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
    .replace(Regex("`(.+?)`"), "$1")
    .trim()
