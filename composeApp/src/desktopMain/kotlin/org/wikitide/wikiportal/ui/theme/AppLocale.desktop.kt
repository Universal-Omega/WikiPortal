package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * The JVM has no separate "per app" vs "system" language, only
 * Locale.getDefault()'s own process-wide default, but since nothing
 * else shares this process, overriding that default amounts to the
 * same thing as a genuine per-app override. Compose Multiplatform's
 * own resource loader reads this same default, so setting it here is
 * enough on its own for strings to update; the private LocalAppLocale
 * below only exists so AppLocaleEnvironment's key() has a value to
 * remount on.
 */
actual object LocalAppLocale {
    private var systemDefault: Locale? = null
    private val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = LocalAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (systemDefault == null) {
            systemDefault = Locale.getDefault()
        }
        val new = when (value) {
            null -> systemDefault!!
            else -> Locale.forLanguageTag(value)
        }
        Locale.setDefault(new)
        return LocalAppLocale.provides(new.toLanguageTag())
    }
}
