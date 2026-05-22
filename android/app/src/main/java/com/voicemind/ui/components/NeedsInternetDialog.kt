package com.voicemind.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

sealed interface NeedsInternetReason {
    data object GenerateSummary : NeedsInternetReason
    data object GenerateTasks : NeedsInternetReason
    data object CollectiveSummarize : NeedsInternetReason
    data object ShareWithUser : NeedsInternetReason
    data object StillProcessing : NeedsInternetReason
}

@Composable
fun NeedsInternetDialog(
    reason: NeedsInternetReason?,
    onDismiss: () -> Unit,
) {
    reason ?: return
    val (title, body) = when (reason) {
        NeedsInternetReason.GenerateSummary, NeedsInternetReason.GenerateTasks ->
            "Internet Required" to "This recording hasn't been processed yet. Please connect to the internet so VoiceMind can transcribe the audio."
        NeedsInternetReason.CollectiveSummarize ->
            "Internet Required" to "Generating a collective summary requires an internet connection. Please connect and try again."
        NeedsInternetReason.StillProcessing ->
            "Still Processing" to "This recording is still being processed. Please wait a moment and try again."
        NeedsInternetReason.ShareWithUser ->
            "Internet Required" to "Sharing requires an internet connection. Please connect and try again."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
