package com.voicemind.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voicemind.data.repository.NtsSettings
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.VmDimens
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskSchedulingSection(
    ntsSettings: NtsSettings,
    onSetNtsEnabled: (Boolean) -> Unit,
    onSetNtsStartTime: (Int, Int) -> Unit,
    onSetNtsIntervalMinutes: (Int) -> Unit,
) {
    var showNtsTimePicker by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Natural Time Selection",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (ntsSettings.enabled)
                            "Tasks without a date are auto-scheduled"
                        else
                            "Auto-schedule tasks that have no date set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ntsSettings.enabled,
                    onCheckedChange = onSetNtsEnabled,
                )
            }

            AnimatedVisibility(visible = ntsSettings.enabled) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default start time",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "First auto-scheduled task starts at this time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showNtsTimePicker = true }) {
                            Text(
                                text = formatTime(ntsSettings.startHour, ntsSettings.startMinute),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

                    Text(
                        text = "Interval between tasks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Time gap between sequentially scheduled tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

                    val intervalOptions = listOf(15, 30, 45, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
                    ) {
                        intervalOptions.forEach { minutes ->
                            FilterChip(
                                selected = ntsSettings.intervalMinutes == minutes,
                                onClick = { onSetNtsIntervalMinutes(minutes) },
                                label = { Text("${minutes}m") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNtsTimePicker) {
        NtsTimePickerDialog(
            initialHour = ntsSettings.startHour,
            initialMinute = ntsSettings.startMinute,
            onConfirm = { hour, minute ->
                onSetNtsStartTime(hour, minute)
                showNtsTimePicker = false
            },
            onDismiss = { showNtsTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NtsTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Default start time") },
        text = { TimePicker(state = state) },
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
}
