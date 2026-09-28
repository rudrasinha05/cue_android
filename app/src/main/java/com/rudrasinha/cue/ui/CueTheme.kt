package com.rudrasinha.cue.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rudrasinha.cue.settings.ThemePreference

private val LightColors = lightColorScheme(
    primary = Color(0xFF5340A0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E1FF),
    onPrimaryContainer = Color(0xFF251754),
    secondaryContainer = Color(0xFFEDE8F5),
    background = Color(0xFFFAF9FC),
    surface = Color(0xFFFAF9FC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF35236D),
    primaryContainer = Color(0xFF443287),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondaryContainer = Color(0xFF393441),
    background = Color(0xFF14121A),
    surface = Color(0xFF14121A)
)

@Composable
fun CueTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
