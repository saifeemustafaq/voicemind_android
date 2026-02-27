package com.voicemind.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    val useSidebar: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[useSidebarKey] ?: true
    }

    suspend fun setUseSidebar(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[useSidebarKey] = value
        }
    }
}
