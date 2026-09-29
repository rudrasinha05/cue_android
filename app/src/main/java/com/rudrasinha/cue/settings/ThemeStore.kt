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

enum class ColorTheme(val label: String) {
    DEFAULT("Cue Default"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    ROSE("Rose"),
    MIDNIGHT("Midnight")
}

private val Context.cuePreferences by preferencesDataStore(name = "cue_preferences")

class ThemeStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val colorKey = stringPreferencesKey("color_theme")
    val mode: Flow<ThemePreference> = context.cuePreferences.data.map { preferences ->
        ThemePreference.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemePreference.SYSTEM
    }
    val colorTheme: Flow<ColorTheme> = context.cuePreferences.data.map { preferences ->
        ColorTheme.entries.firstOrNull { it.name == preferences[colorKey] } ?: ColorTheme.DEFAULT
    }

    suspend fun set(value: ThemePreference) {
        context.cuePreferences.edit { it[themeKey] = value.name }
    }

    suspend fun setColorTheme(value: ColorTheme) {
        context.cuePreferences.edit { it[colorKey] = value.name }
    }
}
