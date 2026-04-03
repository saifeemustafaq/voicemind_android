package com.voicemind.widget.checklist

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
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
        entryPoint.actionItemDao().updateCompleted(itemId, newCompleted, SyncStatus.PENDING_UPDATE)

        ChecklistWidget().update(context, glanceId)
    }
}
