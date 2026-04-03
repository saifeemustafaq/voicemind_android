package com.voicemind.widget.checklist

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class SwitchTabAction : ActionCallback {

    companion object {
        val TabIndexKey = ActionParameters.Key<Int>("switch_tab_index")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val tabIndex = parameters[TabIndexKey] ?: ChecklistWidgetStateKeys.TAB_TODO

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ChecklistWidgetStateKeys.SELECTED_TAB] = tabIndex
        }

        ChecklistWidget().update(context, glanceId)
    }
}
