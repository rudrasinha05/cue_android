package com.rudrasinha.cue.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val sizeKey = intPreferencesKey("floating_size_dp")
    private val dayPlanKey = booleanPreferencesKey("daily_plan_enabled")
    private val notificationIntelligenceKey = booleanPreferencesKey("notification_intelligence_enabled")
    private val wakeKey = intPreferencesKey("wake_minute")
    private val bedKey = intPreferencesKey("bed_minute")
    private val reminderToneKey = stringPreferencesKey("reminder_tone")
    private val toneRepeatsKey = intPreferencesKey("tone_repeats")
    private val cloudAnalysisOwnerKey = stringPreferencesKey("cloud_analysis_owner")
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
    val floatingSize: Flow<Int> = context.cuePreferences.data.map { (it[sizeKey] ?: 64).coerceIn(48, 88) }
    val dailyPlanEnabled: Flow<Boolean> = context.cuePreferences.data.map { it[dayPlanKey] ?: false }
    val notificationIntelligence: Flow<Boolean> = context.cuePreferences.data.map {
        it[notificationIntelligenceKey] ?: false
    }
    val wakeMinute: Flow<Int> = context.cuePreferences.data.map { (it[wakeKey] ?: 420).coerceIn(0, 1080) }
    val bedMinute: Flow<Int> = context.cuePreferences.data.map { (it[bedKey] ?: 1320).coerceIn(480, 1439) }
    val reminderTone: Flow<String> = context.cuePreferences.data.map {
        it[reminderToneKey] ?: "default"
    }
    val toneRepeats: Flow<Int> = context.cuePreferences.data.map { (it[toneRepeatsKey] ?: 3).coerceIn(1, 5) }
    val cloudAnalysisOwner: Flow<String?> = context.cuePreferences.data.map { it[cloudAnalysisOwnerKey] }

    suspend fun setCloudAnalysisOwner(owner: String?) {
        context.cuePreferences.edit {
            if (owner == null) it.remove(cloudAnalysisOwnerKey) else it[cloudAnalysisOwnerKey] = owner
        }
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

    suspend fun setFloatingSize(sizeDp: Int) {
        context.cuePreferences.edit { it[sizeKey] = sizeDp.coerceIn(48, 88) }
    }

    suspend fun setDailyPlanEnabled(enabled: Boolean) {
        context.cuePreferences.edit { it[dayPlanKey] = enabled }
    }

    suspend fun setNotificationIntelligence(enabled: Boolean) {
        context.cuePreferences.edit { it[notificationIntelligenceKey] = enabled }
    }

    suspend fun setDayHours(wake: Int, bed: Int) {
        require(wake in 0..1080 && bed in 480..1439 && bed - wake >= 360)
        context.cuePreferences.edit { it[wakeKey] = wake; it[bedKey] = bed }
    }

    suspend fun setReminderTone(id: String) {
        context.cuePreferences.edit { it[reminderToneKey] = id }
    }

    suspend fun setToneRepeats(count: Int) {
        context.cuePreferences.edit { it[toneRepeatsKey] = count.coerceIn(1, 5) }
    }
}
