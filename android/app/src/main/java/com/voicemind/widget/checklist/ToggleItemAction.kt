package com.voicemind.widget.checklist

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.voicemind.data.local.SyncStatus
import dagger.hilt.android.EntryPointAccessors

class ToggleItemAction : ActionCallback {

    companion object {
        val ItemIdKey = ActionParameters.Key<String>("toggle_item_id")
        val CurrentCompletedKey = ActionParameters.Key<Boolean>("toggle_current_completed")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val itemId = parameters[ItemIdKey] ?: return
        val currentCompleted = parameters[CurrentCompletedKey] ?: false
        val newCompleted = !currentCompleted

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ChecklistWidgetEntryPoint::class.java,
        )
        // Persist to DB
        entryPoint.actionItemDao().updateCompleted(itemId, newCompleted, SyncStatus.PENDING_UPDATE)

        // Update DataStore with the toggled state, then signal Glance to re-render.
        // updateAppWidgetState persists state; update() is what actually pushes
        // the new RemoteViews to the homescreen (mirrors SwitchTabAction pattern).
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = deserializeWidgetItems(prefs[ChecklistWidgetStateKeys.ITEMS_JSON] ?: "[]")
            val updated = current.map { if (it.id == itemId) it.copy(completed = newCompleted) else it }
            prefs[ChecklistWidgetStateKeys.ITEMS_JSON] = serializeWidgetItems(updated)
        }

        ChecklistWidget().update(context, glanceId)
    }
}
