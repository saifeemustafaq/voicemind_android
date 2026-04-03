package com.voicemind.ui.recording

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSelectTopBar(
    selectedCount: Int,
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
