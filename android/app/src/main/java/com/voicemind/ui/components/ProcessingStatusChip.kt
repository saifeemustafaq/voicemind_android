package com.voicemind.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicemind.data.local.SyncStatus

@Composable
fun ProcessingStatusChip(
    hasTranscription: Boolean,
    processingFailed: Boolean,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
) {
    if (hasTranscription && !processingFailed) return
    val (chipText, containerColor, contentColor) = when {
        processingFailed -> Triple(
            "Processing failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        syncStatus == SyncStatus.PENDING_UPLOAD -> Triple(
            "Waiting for upload",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            "Processing...",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = chipText,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
