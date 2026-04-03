package com.voicemind.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.VmDimens
import com.voicemind.util.LocalAppTimeZone
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class PickerMode { None, DueDateDate, DueDateTime, DeadlineDate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDeadlineCard(
    item: ActionItem,
    onSetDueDate: (dateMillis: Long?, hour: Int?, minute: Int?) -> Unit,
    onSetDeadline: (dateMillis: Long?) -> Unit,
) {
    var pickerMode by remember { mutableStateOf(PickerMode.None) }
    var pendingDateMillis: Long? by remember { mutableStateOf(null) }
    val now = remember { Date() }
    val appTz = LocalAppTimeZone.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        innerPadding = 0.dp,
    ) {
        Column {
            // Date & Time row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { pickerMode = PickerMode.DueDateDate }
                    .padding(horizontal = VmDimens.SpaceLg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(VmDimens.IconMd),
                )
                Spacer(modifier = Modifier.width(VmDimens.SpaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Date & Time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.dueDate != null) {
                        val date = item.dueDate.toDate()
                        val overdue = !item.completed && date.before(now)
                        val formatted = remember(item.dueDate, appTz) {
                            SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                                .apply { timeZone = appTz }
                                .format(date)
                        }
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.dueDate != null) {
                    IconButton(
                        onClick = { onSetDueDate(null, null, null) },
                        modifier = Modifier.size(VmDimens.IconLg),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(VmDimens.IconSm),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = VmDimens.SpaceLg))

            // Deadline row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { pickerMode = PickerMode.DeadlineDate }
                    .padding(horizontal = VmDimens.SpaceLg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(VmDimens.IconMd),
                )
                Spacer(modifier = Modifier.width(VmDimens.SpaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deadline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.deadline != null) {
                        val date = item.deadline.toDate()
                        val overdue = !item.completed && date.before(now)
                        val formatted = remember(item.deadline) {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(date)
                        }
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.deadline != null) {
                    IconButton(
                        onClick = { onSetDeadline(null) },
                        modifier = Modifier.size(VmDimens.IconLg),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(VmDimens.IconSm),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (item.googleTaskId != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = VmDimens.SpaceLg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VmDimens.SpaceLg, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(VmDimens.IconSm),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synced to Google Tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (item.calendarEventId != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = VmDimens.SpaceLg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VmDimens.SpaceLg, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(VmDimens.IconSm),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synced to Google Calendar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }

    // Picker dialogs
    when (pickerMode) {
        PickerMode.None -> {}

        PickerMode.DueDateDate -> {
            val initial = item.dueDate?.toDate()?.time
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { pickerMode = PickerMode.None },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        pickerMode = PickerMode.DueDateTime
                    }) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = { pickerMode = PickerMode.None }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = dateState)
            }
        }

        PickerMode.DueDateTime -> {
            val cal = remember {
                Calendar.getInstance().apply {
                    item.dueDate?.toDate()?.let { time = it }
                }
            }
            val timeState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
            )
            AlertDialog(
                onDismissRequest = { pickerMode = PickerMode.None },
                title = { Text("Select Time") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        onSetDueDate(pendingDateMillis, timeState.hour, timeState.minute)
                        pickerMode = PickerMode.None
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { pickerMode = PickerMode.None }) { Text("Cancel") }
                },
            )
        }

        PickerMode.DeadlineDate -> {
            val initial = item.deadline?.toDate()?.time
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { pickerMode = PickerMode.None },
                confirmButton = {
                    TextButton(onClick = {
                        onSetDeadline(dateState.selectedDateMillis)
                        pickerMode = PickerMode.None
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { pickerMode = PickerMode.None }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = dateState)
            }
        }
    }
}
