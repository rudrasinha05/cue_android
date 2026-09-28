package com.rudrasinha.cue.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePreference(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

private val Context.cuePreferences by preferencesDataStore(name = "cue_preferences")

class ThemeStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    val mode: Flow<ThemePreference> = context.cuePreferences.data.map { preferences ->
        ThemePreference.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemePreference.SYSTEM
    }

    suspend fun set(value: ThemePreference) {
        context.cuePreferences.edit { it[themeKey] = value.name }
    }
}
