package com.voicemind.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.theme.VmBlushPink
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmError
import com.voicemind.ui.theme.VmLinkBlue
import com.voicemind.ui.theme.VmTextPrimary
import com.voicemind.ui.theme.VmTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel = hiltViewModel(),
    onOpenDrawer: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (onOpenDrawer != null) {
            TopAppBar(
                title = { Text("Checklist", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {

        // To-do section — stacked with Done (no gap)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            innerPadding = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "To-do",
                    style = MaterialTheme.typography.titleSmall,
                    color = VmTextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (state.todoItems.isEmpty()) {
                    Text(
                        text = "No pending items",
                        style = MaterialTheme.typography.bodySmall,
                        color = VmTextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                state.todoItems.forEach { item ->
                    ActionItemRow(
                        item = item,
                        recordingTitle = item.recordingId?.let { state.recordingTitles[it] },
                        onToggle = { viewModel.toggleCompleted(item) },
                        onDelete = { viewModel.deleteItem(item) },
                    )
                }
            }
        }

        // Done section — no vertical gap between panels
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            innerPadding = 0.dp,
            alpha = 0.18f
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleSmall,
                    color = VmTextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (state.doneItems.isEmpty()) {
                    Text(
                        text = "Completed items appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = VmTextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                state.doneItems.forEach { item ->
                    ActionItemRow(
                        item = item,
                        recordingTitle = item.recordingId?.let { state.recordingTitles[it] },
                        onToggle = { viewModel.toggleCompleted(item) },
                        onDelete = { viewModel.deleteItem(item) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionItemRow(
    item: ActionItem,
    recordingTitle: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
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
                tint = if (item.completed) VmDeepViolet else VmTextSecondary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (item.completed) VmTextSecondary else VmTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (recordingTitle != null) {
                Text(
                    text = "From $recordingTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = VmLinkBlue,
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = VmError.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
