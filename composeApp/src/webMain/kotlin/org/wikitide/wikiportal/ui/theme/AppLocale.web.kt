package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * A browser tab's Navigator.languages is read-only, so there is no
 * property here to assign directly the way the other platforms do.
 * window.__customLocale instead is read by a small patch in
 * wasmJsMain/resources/index.html that overrides
 * Navigator.prototype.languages to return it once set, which is what
 * lets compose-resources actually see a language different from the
 * browser's own; see that file for the matching half of this.
 */
external object window {
    var __customLocale: String?
}

actual object LocalAppLocale {
    private val LocalAppLocale = staticCompositionLocalOf { Locale.current.toLanguageTag() }

    actual val current: String
        @Composable get() = LocalAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        window.__customLocale = value?.replace('_', '-')
        return LocalAppLocale.provides(value ?: Locale.current.toLanguageTag())
    }
}
