package com.voicemind.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class NavPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val useSidebarKey = booleanPreferencesKey("use_sidebar_nav")
    private val summariesInfoShownKey = booleanPreferencesKey("summaries_info_shown")

    val useSidebar: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[useSidebarKey] ?: false
    }

    suspend fun setUseSidebar(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[useSidebarKey] = value
        }
    }

    val summariesInfoShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[summariesInfoShownKey] ?: false
    }

    suspend fun setSummariesInfoShown(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[summariesInfoShownKey] = value
        }
    }

    private val multiSelectHintShownKey = booleanPreferencesKey("multi_select_hint_shown")

    val multiSelectHintShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[multiSelectHintShownKey] ?: false
    }

    suspend fun setMultiSelectHintShown(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[multiSelectHintShownKey] = value
        }
    }

    private val folderSortKey = stringPreferencesKey("folder_sort")

    val folderSort: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[folderSortKey] ?: "recency"
    }

    suspend fun setFolderSort(value: String) {
        context.dataStore.edit { prefs ->
            prefs[folderSortKey] = value
        }
    }
}
