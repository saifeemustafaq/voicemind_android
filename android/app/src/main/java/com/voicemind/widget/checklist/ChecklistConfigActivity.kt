package com.voicemind.widget.checklist

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.voicemind.ui.theme.VoiceMindAITheme
import kotlinx.coroutines.launch

class ChecklistConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            VoiceMindAITheme {
                var showCompleted by remember { mutableStateOf(false) }

                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                    ) {
                        Text(
                            text = "Configure Checklist Widget",
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Which tasks should the widget show?",
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(modifier = Modifier.selectableGroup()) {
                            ConfigOption(
                                label = "Only incomplete tasks",
                                selected = !showCompleted,
                                onClick = { showCompleted = false },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ConfigOption(
                                label = "Both incomplete and completed tasks",
                                description = "Shows tabs to switch between them",
                                selected = showCompleted,
                                onClick = { showCompleted = true },
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { onConfirm(showCompleted) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add Widget")
                        }
                    }
                }
            }
        }
    }

    private fun onConfirm(showCompleted: Boolean) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@ChecklistConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            updateAppWidgetState(this@ChecklistConfigActivity, glanceId) { prefs ->
                prefs[ChecklistWidgetStateKeys.SHOW_COMPLETED] = showCompleted
                prefs[ChecklistWidgetStateKeys.SELECTED_TAB] = ChecklistWidgetStateKeys.TAB_TODO
            }

            ChecklistWidget().update(this@ChecklistConfigActivity, glanceId)

            val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConfigOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
