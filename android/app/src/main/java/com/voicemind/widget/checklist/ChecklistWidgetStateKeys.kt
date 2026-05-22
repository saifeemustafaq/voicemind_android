package com.voicemind.widget.checklist

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object ChecklistWidgetStateKeys {
    val IS_SIGNED_IN = booleanPreferencesKey("checklist_is_signed_in")
    val SHOW_COMPLETED = booleanPreferencesKey("checklist_show_completed")
    val SELECTED_TAB = intPreferencesKey("checklist_selected_tab")
    val ITEMS_JSON = stringPreferencesKey("checklist_items_json")

    const val TAB_TODO = 0
    const val TAB_DONE = 1
}
