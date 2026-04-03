package com.voicemind.ui.checklist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.VmDimens
import com.voicemind.util.LocalAppTimeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel = hiltViewModel(),
    onOpenDrawer: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onTaskClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isSelectionMode) { viewModel.clearSelection() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.completeSelected() }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Mark complete",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            } else {
                VoiceMindTopAppBar(
                    title = "Checklist",
                    icon = Icons.Default.Checklist,
                    onOpenDrawer = onOpenDrawer,
                    onSettings = onSettings,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = VmDimens.ScreenHorizontalPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "TO-DO",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = VmDimens.ScreenHorizontalPadding, top = VmDimens.SpaceSm, bottom = VmDimens.SpaceXs)
                )

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    innerPadding = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(VmDimens.SpaceLg)) {
                        if (state.todoItems.isEmpty()) {
                            Text(
                                text = "No pending items",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = VmDimens.SpaceSm)
                            )
                        }

                        state.todoItems.forEach { item ->
                            ActionItemRow(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = item.id in state.selectedIds,
                                onToggle = { viewModel.toggleCompleted(item) },
                                onClick = {
                                    if (state.isSelectionMode) viewModel.toggleSelection(item.id)
                                    else onTaskClick(item.id)
                                },
                                onLongPress = { viewModel.onLongPress(item.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(VmDimens.SpaceLg))

                Text(
                    text = "DONE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = VmDimens.ScreenHorizontalPadding, top = VmDimens.SpaceSm, bottom = VmDimens.SpaceXs)
                )

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    innerPadding = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(VmDimens.SpaceLg)) {
                        if (state.doneItems.isEmpty()) {
                            Text(
                                text = "Completed items appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = VmDimens.SpaceSm)
                            )
                        }

                        state.doneItems.forEach { item ->
                            ActionItemRow(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = item.id in state.selectedIds,
                                onToggle = { viewModel.toggleCompleted(item) },
                                onClick = {
                                    if (state.isSelectionMode) viewModel.toggleSelection(item.id)
                                    else onTaskClick(item.id)
                                },
                                onLongPress = { viewModel.onLongPress(item.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(VmDimens.FabClearance))
            }
        }

        AnimatedVisibility(
            visible = !state.isSelectionMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(VmDimens.SpaceXl),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onConfirm = { title ->
                viewModel.addItem(title)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showDeleteConfirm) {
        val count = state.selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $count ${if (count == 1) "item" else "items"}?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionItemRow(
    item: ActionItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            IconButton(onClick = onClick, modifier = Modifier.size(VmDimens.TouchTarget)) {
                Icon(
                    if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isSelected) "Deselect" else "Select",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            IconButton(onClick = onToggle, modifier = Modifier.size(VmDimens.TouchTarget)) {
                Icon(
                    if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (item.completed) "Mark incomplete" else "Mark complete",
                    tint = if (item.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DateLabels(item)
        }
    }
}

@Composable
private fun AddTaskDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Task") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Task name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DateLabels(item: ActionItem) {
    val now = remember { Date() }
    val isOverdue = !item.completed
    val appTz = LocalAppTimeZone.current

    item.dueDate?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts, appTz) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                .apply { timeZone = appTz }
                .format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = VmDimens.SpaceXxs),
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(VmDimens.SpaceXs))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item.deadline?.let { ts ->
        val date = ts.toDate()
        val overdue = isOverdue && date.before(now)
        val formatted = remember(ts) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(date)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = VmDimens.SpaceXxs),
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(VmDimens.SpaceXs))
            Text(
                text = "Deadline: $formatted",
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
