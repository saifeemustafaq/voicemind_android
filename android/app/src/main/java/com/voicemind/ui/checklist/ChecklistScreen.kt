package com.voicemind.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.data.model.ActionItem
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel = hiltViewModel(),
    onOpenDrawer: (() -> Unit)? = null,
    onTaskClick: (String) -> Unit = {},
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
                            onToggle = { viewModel.toggleCompleted(item) },
                            onClick = { onTaskClick(item.id) },
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
                            onToggle = { viewModel.toggleCompleted(item) },
                            onClick = { onTaskClick(item.id) },
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
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
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
            DateLabels(item)
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
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(date)
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
