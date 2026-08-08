package org.wikitide.wikiportal.ui.theme

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Below Android 13 (API 33) there is no framework-level per-app
 * language to sync with in the first place, since the system's own
 * per-app language screen is itself a 13-and-up feature, see
 * https://developer.android.com/guide/topics/resources/app-languages.
 * Below that, this only updates the Configuration compose-resources
 * reads strings from, an in-app-only override; that alone is enough
 * here since this app has no legacy View-based UI reading
 * context.resources directly, only Compose.
 */
actual object LocalAppLocale {
    private var systemDefault: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (systemDefault == null) {
            systemDefault = Locale.getDefault()
        }
        val new = when (value) {
            null -> systemDefault!!
            else -> Locale.forLanguageTag(value)
        }
        Locale.setDefault(new)
        val updated = Configuration(configuration).apply { setLocale(new) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = LocalContext.current.getSystemService(LocaleManager::class.java)
            val newLocales = if (value == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
            // Guards against re-setting the same value on every
            // recomposition of this CompositionLocalProvider, since
            // LocaleManager.applicationLocales normally recreates the
            // Activity as a side effect of being set.
            if (localeManager?.applicationLocales != newLocales) {
                localeManager?.applicationLocales = newLocales
            }
        }

        return LocalConfiguration.provides(updated)
    }
}
