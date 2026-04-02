package com.voicemind.ui.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.VmDimens

@Composable
internal fun StorageSection(
    storageInfo: StorageInfo,
    onClearSharedAudioCache: () -> Unit,
    onClearAllLocalData: () -> Unit,
) {
    var showClearSharedConfirmDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            StorageRow(label = "Total", bytes = storageInfo.totalBytes, context = context, bold = true)

            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

            StorageRow(label = "Own recordings", bytes = storageInfo.ownAudioBytes, context = context)
            Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
            StorageRow(label = "Shared audio", bytes = storageInfo.sharedAudioBytes, context = context)
            Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
            StorageRow(label = "Database", bytes = storageInfo.databaseBytes, context = context)

            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

            FilledTonalButton(
                onClick = { showClearSharedConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear Shared Audio Cache")
            }

            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            FilledTonalButton(
                onClick = { showClearAllConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Clear All Local Data")
            }
        }
    }

    if (showClearSharedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearSharedConfirmDialog = false },
            title = { Text("Clear Shared Audio Cache") },
            text = { Text("This will delete all locally cached shared recordings. They will be re-downloaded when you open them again.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearSharedConfirmDialog = false
                    onClearSharedAudioCache()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearSharedConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = { Text("Clear All Local Data") },
            text = {
                Text(
                    "This will remove all locally stored recordings, tasks, folders, and audio files from this device. " +
                    "Your data remains in the cloud and will re-sync on next launch. " +
                    "You will be prompted to set up local storage again."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirmDialog = false
                        onClearAllLocalData()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StorageRow(
    label: String,
    bytes: Long,
    context: Context,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = Formatter.formatFileSize(context, bytes),
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
