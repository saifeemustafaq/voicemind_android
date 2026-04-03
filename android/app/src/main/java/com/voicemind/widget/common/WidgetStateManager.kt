package com.voicemind.widget.common

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.voicemind.widget.recording.RecordingWidget
import com.voicemind.widget.recording.RecordingWidgetStateKeys
import timber.log.Timber

object WidgetStateManager {

    suspend fun pushAuthState(
        context: Context,
        isSignedIn: Boolean,
        needsMicPermission: Boolean,
    ) {
        updateWidgetState(context, RecordingWidget()) { prefs ->
            prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
            prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] = needsMicPermission
            // Reset recording state so the widget never shows stale "Recording" UI
            // after sign-out or app restart.
            prefs[RecordingWidgetStateKeys.IS_RECORDING] = false
            prefs[RecordingWidgetStateKeys.IS_PAUSED] = false
            prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] = 0L
        }
    }

    suspend fun pushRecordingState(
        context: Context,
        isSignedIn: Boolean,
        needsMicPermission: Boolean,
        isRecording: Boolean,
        isPaused: Boolean,
        elapsedSeconds: Long,
    ) {
        updateWidgetState(context, RecordingWidget()) { prefs ->
            prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
            prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] = needsMicPermission
            prefs[RecordingWidgetStateKeys.IS_RECORDING] = isRecording
            prefs[RecordingWidgetStateKeys.IS_PAUSED] = isPaused
            prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] = elapsedSeconds
        }
    }

    private suspend fun updateWidgetState(
        context: Context,
        widget: GlanceAppWidget,
        block: (MutablePreferences) -> Unit,
    ) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(widget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs -> block(prefs) }
                widget.update(context, id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget state")
        }
    }
}
