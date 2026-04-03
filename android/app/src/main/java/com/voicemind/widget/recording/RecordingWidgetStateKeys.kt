package com.voicemind.widget.recording

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

object RecordingWidgetStateKeys {
    val IS_SIGNED_IN = booleanPreferencesKey("is_signed_in")
    val NEEDS_MIC_PERMISSION = booleanPreferencesKey("needs_mic_permission")
    val IS_RECORDING = booleanPreferencesKey("is_recording")
    val IS_PAUSED = booleanPreferencesKey("is_paused")
    val ELAPSED_SECONDS = longPreferencesKey("elapsed_seconds")
}
