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
        updateWidgetState<RecordingWidget>(context) { prefs ->
            prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
            prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] = needsMicPermission
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
        updateWidgetState<RecordingWidget>(context) { prefs ->
            prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
            prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] = needsMicPermission
            prefs[RecordingWidgetStateKeys.IS_RECORDING] = isRecording
            prefs[RecordingWidgetStateKeys.IS_PAUSED] = isPaused
            prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] = elapsedSeconds
        }
    }

    private suspend inline fun <reified T : GlanceAppWidget> updateWidgetState(
        context: Context,
        crossinline block: (MutablePreferences) -> Unit,
    ) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(T::class.java)
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs -> block(prefs) }
                T::class.java.getDeclaredConstructor().newInstance().update(context, id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget state")
        }
    }
}
