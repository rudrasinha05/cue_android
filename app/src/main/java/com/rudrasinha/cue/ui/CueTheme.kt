package com.rudrasinha.cue.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.rudrasinha.cue.settings.ColorTheme
import com.rudrasinha.cue.settings.ThemePreference

private data class Palette(
    val primary: Color,
    val lightContainer: Color,
    val lightBackground: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkBackground: Color
)

private fun palette(theme: ColorTheme): Palette = when (theme) {
    ColorTheme.DEFAULT -> Palette(
        Color(0xFF5340A0), Color(0xFFE9E1FF), Color(0xFFFAF9FC),
        Color(0xFFCBBEFF), Color(0xFF443287), Color(0xFF14121A)
    )
    ColorTheme.OCEAN -> Palette(
        Color(0xFF006878), Color(0xFFB8EAF4), Color(0xFFF4FAFC),
        Color(0xFF85D5E7), Color(0xFF174F5B), Color(0xFF101C20)
    )
    ColorTheme.FOREST -> Palette(
        Color(0xFF2A684C), Color(0xFFD0EDDA), Color(0xFFF6FBF7),
        Color(0xFF99D9AE), Color(0xFF2C513D), Color(0xFF121B16)
    )
    ColorTheme.SUNSET -> Palette(
        Color(0xFFA84F36), Color(0xFFFFDDD2), Color(0xFFFFF8F5),
        Color(0xFFFFB49E), Color(0xFF764131), Color(0xFF211713)
    )
    ColorTheme.ROSE -> Palette(
        Color(0xFFA23464), Color(0xFFFFD9E6), Color(0xFFFFF7FA),
        Color(0xFFFFAFCB), Color(0xFF74314F), Color(0xFF21141A)
    )
    ColorTheme.MIDNIGHT -> Palette(
        Color(0xFF374C88), Color(0xFFDCE4FF), Color(0xFFF6F8FF),
        Color(0xFFB4C5FF), Color(0xFF303F6A), Color(0xFF101522)
    )
}

fun themeSwatch(theme: ColorTheme): Color = palette(theme).primary

@Composable
fun CueTheme(preference: ThemePreference, colorTheme: ColorTheme, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = palette(colorTheme)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.darkPrimary,
            onPrimary = colors.darkBackground,
            primaryContainer = colors.darkContainer,
            onPrimaryContainer = colors.darkPrimary,
            secondary = lerp(colors.darkPrimary, Color(0xFF76C5E6), 0.46f),
            tertiary = lerp(colors.darkPrimary, Color(0xFFFFCF7A), 0.45f),
            secondaryContainer = colors.darkContainer,
            onSecondaryContainer = colors.darkPrimary,
            background = colors.darkBackground,
            surface = lerp(colors.darkBackground, colors.darkContainer, 0.30f),
            onSurface = Color(0xFFF8F6FF),
            onSurfaceVariant = Color(0xFFBFBDCA),
            outlineVariant = lerp(colors.darkBackground, colors.darkPrimary, 0.32f)
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.lightContainer,
            onPrimaryContainer = colors.primary,
            secondary = lerp(colors.primary, Color(0xFF117E9F), 0.40f),
            tertiary = lerp(colors.primary, Color(0xFFA36A13), 0.48f),
            secondaryContainer = colors.lightContainer,
            onSecondaryContainer = colors.primary,
            background = colors.lightBackground,
            surface = Color.White,
            onSurface = Color(0xFF23202B),
            onSurfaceVariant = Color(0xFF5E5B69),
            outlineVariant = lerp(colors.lightBackground, colors.primary, 0.22f)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
