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
 * Startup prompt shown whenever the user has not yet connected Google Calendar.
 * Explains the value of the integration before asking for consent.
 *
 * @param onConnect  User tapped "Connect" — caller should start the OAuth flow.
 * @param onDismiss  User tapped "Not Now" — caller should suppress for this session.
 */
@Composable
fun CalendarSyncPromptDialog(
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Google Calendar") },
        text = {
            Column {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "VoiceMind listens for tasks, deadlines, and scheduled events in your " +
                        "recordings. When Google Calendar is connected, anything with a time or " +
                        "date mentioned is automatically added to your calendar — no copy-pasting needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosSecondaryLabel,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Why we need access",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "We only request permission to create and update events on your behalf. " +
                        "We never read your existing events or share your data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosSecondaryLabel,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConnect) {
                Text("Connect", color = IosAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        },
    )
}
