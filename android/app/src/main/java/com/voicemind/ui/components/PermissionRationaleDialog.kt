package com.voicemind.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosSecondaryLabel

/**
 * Shown after the user denies one or both of the required permissions.
 * Explains why each permission matters and offers to re-request or open Settings.
 *
 * @param missingMic            Microphone permission was denied.
 * @param missingNotification   Notification permission was denied.
 * @param anyPermanentlyDenied  At least one missing permission is permanently denied
 *                              ("Don't ask again"), so "Allow" should open Settings instead
 *                              of re-launching the system dialog.
 * @param onAllow               User tapped "Allow" / "Open Settings".
 * @param onDismiss             User tapped "Not Now" or dismissed the dialog.
 */
@Composable
fun PermissionRationaleDialog(
    missingMic: Boolean,
    missingNotification: Boolean,
    anyPermanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column {
                if (missingMic) {
                    Text(
                        text = "Microphone",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "VoiceMind needs microphone access to record your voice memos. " +
                            "Recording will not work without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSecondaryLabel,
                    )
                    if (missingNotification) Spacer(Modifier.height(16.dp))
                }
                if (missingNotification) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Without notification permission, you won't be able to pause, " +
                            "stop, or delete a recording while VoiceMind is running in the " +
                            "background. All recording controls appear in the notification shade.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSecondaryLabel,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(
                    text = if (anyPermanentlyDenied) "Open Settings" else "Allow",
                    color = IosAccent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        },
    )
}
