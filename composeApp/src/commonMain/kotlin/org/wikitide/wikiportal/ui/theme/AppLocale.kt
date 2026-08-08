package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Bridges AppRepository's persisted "App language" setting, see
 * AppRepository.appLanguageTag, to whatever each platform offers for
 * overriding just this app's resource language, separate from the
 * device's overall system language. There is no shared cross-platform
 * API for this yet, see
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html,
 * so this is expect/actual rather than a single common implementation.
 *
 * [current] reads back whatever language this app is effectively
 * running under right now, as a BCP 47 tag: the active override where
 * [provides] has applied one, otherwise the platform's own system
 * default. [provides] is what actually switches it, called from
 * [AppLocaleEnvironment] below with such a tag, or null to drop back to
 * following the system. Each actual's own comment says exactly what
 * switching it does and does not affect on that platform, since none of
 * them treat this quite like a genuine second system setting.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Wraps [content] so every stringResource lookup inside it, and
 * [LocalAppLocale.current] itself, reflects [languageTag] rather than
 * whatever the platform's own default locale is. This should sit as
 * close to the root of the app as possible, above anything that might
 * call stringResource, see App.kt.
 *
 * [key] forces a full remount of [content] whenever [languageTag]
 * changes. Some platforms resolve compose-resources strings against a
 * process-wide default that [provides] updates as a side effect, for
 * example desktop's Locale.setDefault, rather than against this
 * CompositionLocal directly, so without this remount already-composed
 * screens would keep showing the old language until something else
 * happened to recompose them.
 */
@Composable
fun AppLocaleEnvironment(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) {
            content()
        }
    }
}
