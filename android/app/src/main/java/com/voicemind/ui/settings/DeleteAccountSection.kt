package com.voicemind.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDimens

@Composable
internal fun DeleteAccountSection(
    isDeleting: Boolean,
    userDisplayText: String,
    showReAuthDialog: Boolean,
    onReAuthDialogDismiss: () -> Unit,
    onDeleteAccount: () -> Unit,
    onReauthAndDelete: (email: String, password: String) -> Unit,
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var reAuthPassword by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (isDeleting) {
            TextButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(VmDimens.SpaceSm))
                Text(
                    text = "Deleting Account...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            TextButton(onClick = { showDeleteConfirmDialog = true }) {
                Text(
                    text = "Delete Account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Account") },
            text = {
                Text(
                    "This will permanently delete your account and all your data. " +
                    "Shared copies in other users' accounts will not be affected. " +
                    "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showReAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                onReAuthDialogDismiss()
                reAuthPassword = ""
            },
            title = { Text("Re-authenticate Required") },
            text = {
                Column {
                    Text(
                        "For security, please enter your password to confirm account deletion.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(VmDimens.SpaceMd))
                    OutlinedTextField(
                        value = reAuthPassword,
                        onValueChange = { reAuthPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val password = reAuthPassword
                        onReAuthDialogDismiss()
                        reAuthPassword = ""
                        onReauthAndDelete(userDisplayText, password)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onReAuthDialogDismiss()
                    reAuthPassword = ""
                }) { Text("Cancel") }
            },
        )
    }
}
