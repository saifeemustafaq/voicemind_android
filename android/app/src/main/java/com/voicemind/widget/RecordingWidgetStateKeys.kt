package com.voicemind.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

object RecordingWidgetStateKeys {
    val IS_RECORDING = booleanPreferencesKey("is_recording")
    val IS_PAUSED = booleanPreferencesKey("is_paused")
    val ELAPSED_SECONDS = longPreferencesKey("elapsed_seconds")
}
