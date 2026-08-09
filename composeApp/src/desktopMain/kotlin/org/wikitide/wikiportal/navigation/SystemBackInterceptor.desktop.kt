package org.wikitide.wikiportal.navigation

import androidx.compose.runtime.Composable

// Nothing to handle with this on Desktop.
@Composable
actual fun SystemBackInterceptor(enabled: Boolean, onBack: () -> Unit) = Unit
