package org.wikitide.wikiportal.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual fun dynamicColorSchemeAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun platformDynamicColorScheme(useDark: Boolean): ColorScheme? {
    if (!dynamicColorSchemeAvailable()) return null
    val context = LocalContext.current
    return if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
