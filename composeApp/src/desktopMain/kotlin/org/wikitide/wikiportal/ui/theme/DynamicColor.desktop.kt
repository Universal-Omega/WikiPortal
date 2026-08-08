package org.wikitide.wikiportal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

actual fun dynamicColorSchemeAvailable(): Boolean = false

@Composable
@ReadOnlyComposable
actual fun platformDynamicColorScheme(useDark: Boolean): ColorScheme? = null
