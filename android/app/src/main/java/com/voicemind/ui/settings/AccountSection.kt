package com.voicemind.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.theme.VmDimens

@Composable
internal fun AccountSection(
    userDisplayText: String,
    isDeleting: Boolean,
    onSignOut: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = userDisplayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(VmDimens.SpaceLg))
            PrimaryButton(
                text = "Sign Out",
                onClick = onSignOut,
                enabled = !isDeleting,
            )
        }
    }
}
