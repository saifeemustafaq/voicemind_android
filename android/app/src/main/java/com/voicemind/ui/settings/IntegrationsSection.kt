package com.voicemind.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voicemind.ui.components.GlassCard

@Composable
internal fun IntegrationsSection(
    tasksConnected: Boolean,
    tasksLoading: Boolean,
    onConnectTasks: (Activity) -> Unit,
    onDisconnectTasks: () -> Unit,
) {
    val context = LocalContext.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Tasks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (tasksConnected) "Task dates sync to Google Tasks"
                               else "Sync task dates to Google Tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tasksLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = tasksConnected,
                        onCheckedChange = {
                            if (it) onConnectTasks(context as Activity) else onDisconnectTasks()
                        },
                    )
                }
            }
        }
    }
}
