package org.wikitide.wikiportal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults

/**
 * iOS has no live per-app locale switch: Foundation and UIKit read
 * AppleLanguages once at process launch. Writing it here changes what
 * the next launch picks up, not this running process, so on its own
 * this would have no visible effect until the app is relaunched.
 * AppLocaleEnvironment's CompositionLocal, held in the private
 * LocalAppLocale below, is what actually makes this app's own UI
 * reflect [value] right away. Anything outside Compose's own resource
 * lookups, system share sheets or dialogs, still follows the device's
 * real setting until that relaunch happens.
 */
@OptIn(InternalComposeUiApi::class)
actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"
    private val systemDefault = NSLocale.preferredLanguages.first() as String
    private val LocalAppLocale = staticCompositionLocalOf { systemDefault }

    actual val current: String
        @Composable get() = LocalAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val new = value ?: systemDefault
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANG_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(new), LANG_KEY)
        }
        return LocalAppLocale.provides(new)
    }
}
