package com.voicemind.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.voicemind.ui.components.GlassCard
import java.util.TimeZone

@Composable
internal fun TimezoneSection(
    appTimezone: String,
    isAutoTimezone: Boolean,
    onTimezoneSelected: (String?) -> Unit,
) {
    var showTimeZonePicker by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showTimeZonePicker = true },
    ) {
        val tz = remember(appTimezone) { TimeZone.getTimeZone(appTimezone) }
        Column {
            Text(
                text = "Timezone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${tz.getDisplayName(false, TimeZone.LONG)} (${formatGmtOffset(tz)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isAutoTimezone) "Auto (device)" else "Manual",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showTimeZonePicker) {
        TimeZonePickerDialog(
            currentTimezoneId = appTimezone,
            isAuto = isAutoTimezone,
            onSelect = { timezoneId ->
                onTimezoneSelected(timezoneId)
                showTimeZonePicker = false
            },
            onDismiss = { showTimeZonePicker = false },
        )
    }
}
