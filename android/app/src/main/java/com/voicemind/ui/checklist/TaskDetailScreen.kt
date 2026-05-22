package com.voicemind.ui.checklist

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.sharing.ShareDialog
import com.voicemind.ui.theme.VmDimens

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isNavigatingAway) {
        if (state.isNavigatingAway) onBack()
    }

    if (showShareDialog) {
        state.item?.let { task ->
            ShareDialog(
                itemId = task.id,
                itemType = "task",
                onDismiss = { showShareDialog = false },
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = "Task",
            icon = Icons.Default.Checklist,
            onBack = onBack,
            extraActions = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share with User") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            onClick = {
                                menuExpanded = false
                                showShareDialog = true
                            },
                            enabled = state.item != null,
                        )
                    }
                }
            },
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(VmDimens.IconLg))
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
            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            EditableTitle(
                title = item.title,
                onTitleChanged = { viewModel.renameItem(it) },
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceMd))

            if (state.recordingTitle != null) {
                Text(
                    text = "From ${state.recordingTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = VmDimens.SpaceLg),
                )
            }

            item.sharedFromName?.let { name ->
                Text(
                    text = "Shared by $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = VmDimens.SpaceLg),
                )
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                innerPadding = 0.dp,
                onClick = { viewModel.toggleCompleted() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VmDimens.SpaceLg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(VmDimens.SpaceMd))
                    Text(
                        text = if (item.completed) "Completed" else "Mark as complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(VmDimens.SpaceLg))

            DateDeadlineCard(
                item = item,
                onSetDueDate = { millis, h, m -> viewModel.setDueDate(millis, h, m) },
                onSetDeadline = { millis -> viewModel.setDeadline(millis) },
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceLg))

            NotesCard(
                notes = item.notes,
                onNotesChanged = { viewModel.updateNotes(it) },
            )

            Spacer(modifier = Modifier.height(VmDimens.SpaceXxl))

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

            Spacer(modifier = Modifier.height(VmDimens.SpaceXl))
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
            Box(modifier = Modifier.padding(vertical = VmDimens.SpaceSm)) {
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
                .padding(start = VmDimens.SpaceLg, end = VmDimens.SpaceLg, top = 14.dp, bottom = VmDimens.SpaceXs),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Note,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(VmDimens.IconMd)
                    .padding(top = VmDimens.SpaceXxs),
            )
            Spacer(modifier = Modifier.width(VmDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
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
