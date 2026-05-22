package com.voicemind.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
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

    private val multiSelectHintDismissCountKey = intPreferencesKey("multi_select_hint_dismiss_count")

    val multiSelectHintDismissCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[multiSelectHintDismissCountKey] ?: 0
    }

    suspend fun incrementMultiSelectHintDismissCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[multiSelectHintDismissCountKey] ?: 0
            prefs[multiSelectHintDismissCountKey] = current + 1
        }
    }

    private val defaultLandingPageKey = stringPreferencesKey("default_landing_page")

    val defaultLandingPage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[defaultLandingPageKey] ?: "recordings"
    }

    suspend fun setDefaultLandingPage(route: String) {
        context.dataStore.edit { prefs ->
            prefs[defaultLandingPageKey] = route
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

    private val navOrderKey = stringPreferencesKey("nav_order")
    private val defaultNavOrder = "folders,summaries,checklist,recordings"

    val navOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        (prefs[navOrderKey] ?: defaultNavOrder).split(",")
    }

    suspend fun setNavOrder(routes: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[navOrderKey] = routes.joinToString(",")
        }
    }

    // ── Initial sync ─────────────────────────────────────────────────────────

    private val initialSyncCompleteKey = booleanPreferencesKey("initial_sync_complete")

    val isInitialSyncComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[initialSyncCompleteKey] ?: false
    }

    suspend fun setInitialSyncComplete(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[initialSyncCompleteKey] = value }
    }

    // ── Timezone ─────────────────────────────────────────────────────────────

    private val timezoneKey = stringPreferencesKey("timezone")

    /** IANA timezone ID; falls back to device default when the user hasn't overridden it. */
    val appTimezone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[timezoneKey]?.takeIf { it.isNotEmpty() } ?: TimeZone.getDefault().id
    }

    val isAutoTimezone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[timezoneKey].isNullOrEmpty()
    }

    /** Pass `null` to reset to device-automatic. */
    suspend fun setTimezone(timezoneId: String?) {
        context.dataStore.edit { prefs ->
            if (timezoneId == null) prefs.remove(timezoneKey)
            else prefs[timezoneKey] = timezoneId
        }
    }

    // ── Device setup ──────────────────────────────────────────────────────────

    private val deviceSyncStrategyKey = stringPreferencesKey("device_sync_strategy")
    private val deviceSetupCompleteKey = booleanPreferencesKey("device_setup_complete")

    /** One of: "full", "on_demand", "metadata_only". Defaults to "full". */
    val deviceSyncStrategy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[deviceSyncStrategyKey] ?: "full"
    }

    val isDeviceSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[deviceSetupCompleteKey] ?: false
    }

    suspend fun setDeviceSyncStrategy(strategy: String) {
        context.dataStore.edit { prefs -> prefs[deviceSyncStrategyKey] = strategy }
    }

    suspend fun setDeviceSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[deviceSetupCompleteKey] = complete }
    }

    // ── Local storage consent ─────────────────────────────────────────────────

    private val localStorageConsentShownKey = booleanPreferencesKey("local_storage_consent_shown")

    val isLocalStorageConsentShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[localStorageConsentShownKey] ?: false
    }

    suspend fun setLocalStorageConsentShown(shown: Boolean) {
        context.dataStore.edit { prefs -> prefs[localStorageConsentShownKey] = shown }
    }
}
