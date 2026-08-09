package org.wikitide.wikiportal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import org.wikitide.wikiportal.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF3457D5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF), onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF1C7C77), onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F1EA), onSecondaryContainer = Color(0xFF00201F),
    background = Color(0xFFFBFAF7), onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBFAF7), onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE5E1EC), onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680), error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C3FF), onPrimary = Color(0xFF0A2596),
    primaryContainer = Color(0xFF1C3AAB), onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFF8AD4CD), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF004F47), onSecondaryContainer = Color(0xFFB9F1EA),
    background = Color(0xFF131316), onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF131316), onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF47464F), onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF918F99), error = Color(0xFFFFB4AB),
)

expect fun dynamicColorSchemeAvailable(): Boolean

@Composable
@ReadOnlyComposable
expect fun platformDynamicColorScheme(useDark: Boolean): ColorScheme?

/**
 * @param onDarkThemeResolved Reports the app's actual resolved theme,
 * accounting for ThemeMode.SYSTEM, so a caller can keep OS chrome like
 * the status and navigation bar icons legible against it. This is
 * different from the device's raw dark mode setting, since the person
 * may have picked "Light" or "Dark" in Settings rather than "System
 * default". This is a plain callback rather than a platform expect or
 * actual, since desktop windows and the browser don't have OS status
 * or nav bars to theme in the first place, and Android's own
 * implementation lives in MainActivity, plain Android code outside the
 * KMP source sets, where it has direct access to the window. So there
 * is nothing for a shared cross-platform layer to abstract here.
 */
@Composable
fun WikiPortalTheme(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    onDarkThemeResolved: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SideEffect { onDarkThemeResolved(useDark) }
    val dynamicScheme = if (useDynamicColor && dynamicColorSchemeAvailable()) platformDynamicColorScheme(
            useDark
        ) else null
    val colorScheme = dynamicScheme ?: if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WikiPortalTypography,
        shapes = WikiPortalShapes,
        content = content
    )
}
