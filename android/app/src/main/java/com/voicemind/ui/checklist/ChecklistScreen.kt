package com.voicemind.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel = hiltViewModel(),
    onOpenDrawer: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Checklist",
            icon = Icons.Default.Checklist,
            onOpenDrawer = onOpenDrawer,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "TO-DO",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                innerPadding = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.todoItems.isEmpty()) {
                        Text(
                            text = "No pending items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosSecondaryLabel,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    state.todoItems.forEach { item ->
                        ActionItemRow(
                            item = item,
                            recordingTitle = item.recordingId?.let { state.recordingTitles[it] },
                            onToggle = { viewModel.toggleCompleted(item) },
                            onDelete = { viewModel.deleteItem(item) },
                            onEditDates = { viewModel.startEditingDates(item) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DONE",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                innerPadding = 0.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.doneItems.isEmpty()) {
                        Text(
                            text = "Completed items appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosSecondaryLabel,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    state.doneItems.forEach { item ->
                        ActionItemRow(
                            item = item,
                            recordingTitle = item.recordingId?.let { state.recordingTitles[it] },
                            onToggle = { viewModel.toggleCompleted(item) },
                            onDelete = { viewModel.deleteItem(item) },
                            onEditDates = { viewModel.startEditingDates(item) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    state.editingItem?.let { item ->
        TaskDatePickerDialog(
            item = item,
            onSetDueDate = { millis, hour, minute ->
                viewModel.setDueDate(item, millis, hour, minute)
            },
            onSetDeadline = { millis ->
                viewModel.setDeadline(item, millis)
            },
            onDismiss = { viewModel.stopEditingDates() },
        )
    }
}

@Composable
private fun ActionItemRow(
    item: ActionItem,
    recordingTitle: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEditDates: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
            Icon(
                if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (item.completed) "Mark incomplete" else "Mark complete",
                tint = if (item.completed) IosSuccess else IosSecondaryLabel,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (item.completed) IosSecondaryLabel else IosLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (recordingTitle != null) {
                Text(
                    text = "From $recordingTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosAccent,
                )
            }
            DateLabels(item)
        }

        IconButton(onClick = onEditDates, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.EditCalendar,
                contentDescription = "Set date / deadline",
                tint = IosAccent.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = IosDestructive.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DateLabels(item: ActionItem) {
    val now = remember { Date() }
    val isOverdue = !item.completed

    item.dueDate?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) IosDestructive else IosSecondaryLabel,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) IosDestructive else IosSecondaryLabel,
            )
        }
    }

    item.deadline?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) IosDestructive else IosSecondaryLabel,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Deadline: $formatted",
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) IosDestructive else IosSecondaryLabel,
            )
        }
    }
}

// -- Date / Deadline picker dialog ------------------------------------------------

private enum class PickerMode { Chooser, DueDateDate, DueDateTime, DeadlineDate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDatePickerDialog(
    item: ActionItem,
    onSetDueDate: (dateMillis: Long?, hour: Int?, minute: Int?) -> Unit,
    onSetDeadline: (dateMillis: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(PickerMode.Chooser) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    when (mode) {
        PickerMode.Chooser -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Set Date or Deadline") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Date & Time section
                        TextButton(
                            onClick = { mode = PickerMode.DueDateDate },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (item.dueDate != null) "Change Date & Time" else "Add Date & Time",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (item.dueDate != null) {
                            val formatted = remember(item.dueDate) {
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                    .format(item.dueDate.toDate())
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosSecondaryLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onSetDueDate(null, null, null) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear date",
                                        modifier = Modifier.size(16.dp),
                                        tint = IosDestructive,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Deadline section
                        TextButton(
                            onClick = { mode = PickerMode.DeadlineDate },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (item.deadline != null) "Change Deadline" else "Add Deadline",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (item.deadline != null) {
                            val formatted = remember(item.deadline) {
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    .format(item.deadline.toDate())
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosSecondaryLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onSetDeadline(null) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear deadline",
                                        modifier = Modifier.size(16.dp),
                                        tint = IosDestructive,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                },
            )
        }

        PickerMode.DueDateDate -> {
            val initial = item.dueDate?.toDate()?.time
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { mode = PickerMode.Chooser },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        mode = PickerMode.DueDateTime
                    }) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = { mode = PickerMode.Chooser }) { Text("Cancel") }
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
                onDismissRequest = { mode = PickerMode.Chooser },
                title = { Text("Select Time") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        onSetDueDate(pendingDateMillis, timeState.hour, timeState.minute)
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { mode = PickerMode.Chooser }) { Text("Cancel") }
                },
            )
        }

        PickerMode.DeadlineDate -> {
            val initial = item.deadline?.toDate()?.time
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { mode = PickerMode.Chooser },
                confirmButton = {
                    TextButton(onClick = {
                        onSetDeadline(dateState.selectedDateMillis)
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { mode = PickerMode.Chooser }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = dateState)
            }
        }
    }
}
