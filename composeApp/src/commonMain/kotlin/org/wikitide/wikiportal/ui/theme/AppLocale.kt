package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

expect fun languageNameInSystemLanguage(languageTag: String): String?

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun AppLocaleEnvironment(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) {
            content()
        }
    }
}
