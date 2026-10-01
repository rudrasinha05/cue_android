package com.rudrasinha.cue.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
    private val floatingKey = booleanPreferencesKey("floating_cue")
    private val panelKey = booleanPreferencesKey("notification_panel")
    private val opacityKey = floatPreferencesKey("floating_opacity")
    val mode: Flow<ThemePreference> = context.cuePreferences.data.map { preferences ->
        ThemePreference.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemePreference.SYSTEM
    }
    val colorTheme: Flow<ColorTheme> = context.cuePreferences.data.map { preferences ->
        ColorTheme.entries.firstOrNull { it.name == preferences[colorKey] } ?: ColorTheme.DEFAULT
    }
    val floatingCue: Flow<Boolean> = context.cuePreferences.data.map { it[floatingKey] ?: false }
    val notificationPanel: Flow<Boolean> = context.cuePreferences.data.map { it[panelKey] ?: false }
    val floatingOpacity: Flow<Float> = context.cuePreferences.data.map {
        (it[opacityKey] ?: 0.82f).coerceIn(0.35f, 1f)
    }

    suspend fun set(value: ThemePreference) {
        context.cuePreferences.edit { it[themeKey] = value.name }
    }

    suspend fun setColorTheme(value: ColorTheme) {
        context.cuePreferences.edit { it[colorKey] = value.name }
    }

    suspend fun setFloatingCue(enabled: Boolean) {
        context.cuePreferences.edit { it[floatingKey] = enabled }
    }

    suspend fun setNotificationPanel(enabled: Boolean) {
        context.cuePreferences.edit { it[panelKey] = enabled }
    }

    suspend fun setFloatingOpacity(opacity: Float) {
        context.cuePreferences.edit { it[opacityKey] = opacity.coerceIn(0.35f, 1f) }
    }
}
