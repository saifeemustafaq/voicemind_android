package com.voicemind.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.TimeZone

data class TimeZoneEntry(
    val id: String,
    val displayName: String,
    val gmtOffset: String,
    val region: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZonePickerDialog(
    currentTimezoneId: String,
    isAuto: Boolean,
    onSelect: (timezoneId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val allEntries = remember { buildTimeZoneEntries() }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) allEntries
        else {
            val q = query.trim().lowercase()
            allEntries.filter { entry ->
                entry.id.lowercase().contains(q) ||
                    entry.displayName.lowercase().contains(q) ||
                    entry.gmtOffset.lowercase().contains(q) ||
                    entry.region.lowercase().contains(q)
            }
        }
    }

    val grouped = remember(filtered) { filtered.groupBy { it.region } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select timezone") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search timezones...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // "Use device timezone" option at the top
                    item(key = "__auto__") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device timezone (auto)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = TimeZone.getDefault().let { tz ->
                                        "${tz.getDisplayName(false, TimeZone.LONG)} (${formatGmtOffset(tz)})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isAuto) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    grouped.forEach { (region, entries) ->
                        item(key = "header_$region") {
                            Text(
                                text = region,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    top = 16.dp,
                                    bottom = 4.dp,
                                ),
                            )
                        }
                        items(entries, key = { it.id }) { entry ->
                            val selected = !isAuto && entry.id == currentTimezoneId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(entry.id) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = entry.gmtOffset,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTimeZoneEntries(): List<TimeZoneEntry> {
    val validPrefixes = setOf(
        "Africa", "America", "Antarctica", "Asia", "Atlantic",
        "Australia", "Europe", "Indian", "Pacific",
    )
    return TimeZone.getAvailableIDs()
        .filter { id -> validPrefixes.any { id.startsWith("$it/") } }
        .map { id ->
            val tz = TimeZone.getTimeZone(id)
            val region = id.substringBefore("/")
            val city = id.substringAfter("/").replace("_", " ")
            TimeZoneEntry(
                id = id,
                displayName = city,
                gmtOffset = formatGmtOffset(tz),
                region = region,
            )
        }
        .sortedWith(compareBy({ it.region }, { it.displayName }))
}

internal fun formatGmtOffset(tz: TimeZone): String {
    val offsetMs = tz.rawOffset + tz.dstSavings
    val totalMinutes = offsetMs / 60_000
    val hours = totalMinutes / 60
    val minutes = kotlin.math.abs(totalMinutes % 60)
    val sign = if (hours >= 0) "+" else ""
    return if (minutes == 0) "GMT$sign$hours"
    else "GMT$sign$hours:${"%02d".format(minutes)}"
}
