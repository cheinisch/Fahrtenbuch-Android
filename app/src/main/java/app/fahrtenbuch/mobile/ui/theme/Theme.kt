package app.fahrtenbuch.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Auswahl für die Darstellung der App. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Die sieben zentralen Theme-Farben der Fahrtenbuch-App.
 * Für Light- und Dark-Mode existiert jeweils ein vollständiger Farbsatz.
 */
@Immutable
data class FahrtenbuchColors(
    val main: Color,
    val text: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentText: Color,
    val surface: Color,
    val border: Color
)

val FahrtenbuchLightColors = FahrtenbuchColors(
    main = Color(0xFFFFFFFF),
    text = Color(0xFF1A1A1A),
    accent = Color(0xFFF59E0B),
    accentSecondary = Color(0xFFFFE0A3),
    accentText = Color(0xFF111111),
    surface = Color(0xFFF6F6F6),
    border = Color(0xFFD9D9D9)
)

val FahrtenbuchDarkColors = FahrtenbuchColors(
    main = Color(0xFF121212),
    text = Color(0xFFF5F5F5),
    accent = Color(0xFFF59E0B),
    accentSecondary = Color(0xFF5C4308),
    accentText = Color(0xFF111111),
    surface = Color(0xFF1E1E1E),
    border = Color(0xFF3C3C3C)
)

private val LocalFahrtenbuchColors = staticCompositionLocalOf { FahrtenbuchLightColors }

/** Direkter Zugriff auf Main, Text, Accent, AccentSecondary, AccentText, Surface und Border. */
object FahrtenbuchThemeColors {
    val current: FahrtenbuchColors
        @Composable get() = LocalFahrtenbuchColors.current
}

private fun FahrtenbuchColors.toMaterialColorScheme(darkMode: Boolean): ColorScheme {
    val commonPrimary = accent
    val commonOnPrimary = accentText
    val commonPrimaryContainer = accentSecondary
    val commonOnPrimaryContainer = text
    val commonSecondary = accentSecondary
    val commonOnSecondary = accentText
    val commonBackground = main
    val commonOnBackground = text
    val commonSurface = surface
    val commonOnSurface = text
    val commonSurfaceVariant = surface
    val commonOnSurfaceVariant = text.copy(alpha = 0.72f)
    val commonOutline = border

    return if (darkMode) {
        darkColorScheme(
            primary = commonPrimary,
            onPrimary = commonOnPrimary,
            primaryContainer = commonPrimaryContainer,
            onPrimaryContainer = commonOnPrimaryContainer,
            secondary = commonSecondary,
            onSecondary = commonOnSecondary,
            background = commonBackground,
            onBackground = commonOnBackground,
            surface = commonSurface,
            onSurface = commonOnSurface,
            surfaceVariant = commonSurfaceVariant,
            onSurfaceVariant = commonOnSurfaceVariant,
            outline = commonOutline,
            outlineVariant = commonOutline,
            surfaceContainer = commonSurface,
            surfaceContainerLow = commonBackground,
            surfaceContainerHigh = commonSurface
        )
    } else {
        lightColorScheme(
            primary = commonPrimary,
            onPrimary = commonOnPrimary,
            primaryContainer = commonPrimaryContainer,
            onPrimaryContainer = commonOnPrimaryContainer,
            secondary = commonSecondary,
            onSecondary = commonOnSecondary,
            background = commonBackground,
            onBackground = commonOnBackground,
            surface = commonSurface,
            onSurface = commonOnSurface,
            surfaceVariant = commonSurfaceVariant,
            onSurfaceVariant = commonOnSurfaceVariant,
            outline = commonOutline,
            outlineVariant = commonOutline,
            surfaceContainer = commonSurface,
            surfaceContainerLow = commonBackground,
            surfaceContainerHigh = commonSurface
        )
    }
}

@Composable
fun FahrtenbuchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkMode = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkMode) FahrtenbuchDarkColors else FahrtenbuchLightColors

    androidx.compose.runtime.CompositionLocalProvider(LocalFahrtenbuchColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkMode),
            content = content
        )
    }
}
