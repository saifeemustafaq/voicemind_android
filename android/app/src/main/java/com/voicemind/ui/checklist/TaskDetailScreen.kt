package com.voicemind.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.util.LocalAppTimeZone
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Task",
            icon = Icons.Default.Checklist,
            onBack = onBack,
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            return
        }

        val item = state.item
        if (item == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Task not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Editable title
            EditableTitle(
                title = item.title,
                onTitleChanged = { viewModel.renameItem(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recording source
            if (state.recordingTitle != null) {
                Text(
                    text = "From ${state.recordingTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Completed toggle
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                innerPadding = 0.dp,
                onClick = { viewModel.toggleCompleted() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (item.completed) "Completed" else "Mark as complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time / Deadline card
            DateDeadlineCard(
                item = item,
                onSetDueDate = { millis, h, m -> viewModel.setDueDate(millis, h, m) },
                onSetDeadline = { millis -> viewModel.setDeadline(millis) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Notes / Details
            NotesCard(
                notes = item.notes,
                onNotesChanged = { viewModel.updateNotes(it) },
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Delete button
            Button(
                onClick = { viewModel.deleteItem() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Delete Task")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditableTitle(
    title: String,
    onTitleChanged: (String) -> Unit,
) {
    var text by remember(title) { mutableStateOf(title) }
    var hasFocus by remember { mutableStateOf(false) }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            fontWeight = MaterialTheme.typography.headlineSmall.fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (hasFocus && !focusState.isFocused) {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty() && trimmed != title) {
                        onTitleChanged(trimmed)
                    } else {
                        text = title
                    }
                }
                hasFocus = focusState.isFocused
            },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                if (text.isEmpty()) {
                    Text(
                        "Task title",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

// -- Notes / Details card ---------------------------------------------------------

@Composable
private fun NotesCard(
    notes: String?,
    onNotesChanged: (String) -> Unit,
) {
    var text by remember(notes) { mutableStateOf(notes ?: "") }
    var hasFocus by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        innerPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Note,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .onFocusChanged { focusState ->
                            if (hasFocus && !focusState.isFocused) {
                                if (text.trim() != (notes ?: "")) {
                                    onNotesChanged(text)
                                }
                            }
                            hasFocus = focusState.isFocused
                        },
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "Add details about this task...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

// -- Date & Deadline card ---------------------------------------------------------

private enum class PickerMode { None, DueDateDate, DueDateTime, DeadlineDate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDeadlineCard(
    item: ActionItem,
    onSetDueDate: (dateMillis: Long?, hour: Int?, minute: Int?) -> Unit,
    onSetDeadline: (dateMillis: Long?) -> Unit,
) {
    var pickerMode by remember { mutableStateOf(PickerMode.None) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Deadline row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { pickerMode = PickerMode.DeadlineDate }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (item.googleTaskId != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
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
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
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
