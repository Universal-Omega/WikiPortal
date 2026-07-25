package org.wikitide.wikiportal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual fun dynamicColorSchemeAvailable(): Boolean = false

@Composable
actual fun platformDynamicColorScheme(useDark: Boolean): ColorScheme? = null
