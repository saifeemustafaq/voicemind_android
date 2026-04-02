package com.voicemind.ui.recording

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicemind.data.model.Recording

@Composable
internal fun RecordingContentTabs(
    recording: Recording,
    sheetState: TranscriptSheetState,
    onGenerateSummary: () -> Unit,
    onGenerateTasks: () -> Unit,
    onRetryProcessing: () -> Unit,
    onRetrySummary: () -> Unit,
    scrollableContent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val hasTasks = sheetState.actionItemsLoaded && sheetState.actionItems.isNotEmpty()
    val tabs = listOf(TranscriptTab.Transcript, TranscriptTab.Summary) +
        if (hasTasks) listOf(TranscriptTab.Tasks) else emptyList()
    if (selectedTab >= tabs.size) selectedTab = 0

    LaunchedEffect(hasTasks) {
        if (hasTasks) selectedTab = tabs.indexOf(TranscriptTab.Tasks)
    }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEachIndexed { index, tab ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        if (tab == TranscriptTab.Summary) {
                            onGenerateSummary()
                        }
                    },
                    label = {
                        Text(tab.label, style = MaterialTheme.typography.labelMedium)
                    },
                )
            }

            if (!hasTasks && !recording.transcription.isNullOrBlank() && sheetState.actionItemsLoaded) {
                if (sheetState.isGeneratingTasks) {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = {
                            Text("Generating...", style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        },
                    )
                } else {
                    FilterChip(
                        selected = false,
                        onClick = onGenerateTasks,
                        label = {
                            Text("Generate Tasks", style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
            }
        }

        if (!sheetState.isGeneratingTasks) {
            when {
                sheetState.generateTasksNoResults -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "No tasks could be identified. Try again or edit the transcript.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sheetState.generateTasksFailed -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Something went wrong — tap Generate Tasks to try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Crossfade(
            targetState = tabs.getOrNull(selectedTab) ?: TranscriptTab.Transcript,
            label = "tab_content",
        ) { tab ->
            val content: @Composable () -> Unit = {
                when (tab) {
                    TranscriptTab.Transcript -> TranscriptContent(
                        recording = recording,
                        onRetry = onRetryProcessing,
                    )
                    TranscriptTab.Summary -> SummaryContent(
                        summaryState = sheetState.summaryState,
                        onRetry = onRetrySummary,
                    )
                    TranscriptTab.Tasks -> TasksContent(sheetState)
                }
            }
            if (scrollableContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}
