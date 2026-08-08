package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

@Composable
expect fun languageNameInSystemLanguage(languageTag: String): String?

expect val appLocaleRemountRequired: Boolean

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun AppLocaleEnvironment(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        if (appLocaleRemountRequired) {
            key(languageTag) {
                content()
            }
        } else {
            content()
        }
    }
}
