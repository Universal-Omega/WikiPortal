package org.wikitide.wikiportal.ui.theme

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.LocaleManagerCompat
import java.util.Locale

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

actual val appLocaleRemountRequired: Boolean = false

@Composable
actual fun languageNameInSystemLanguage(languageTag: String): String? {
    val systemLocale = LocaleManagerCompat.getSystemLocales(LocalContext.current).get(0) ?: return null
    return Locale.forLanguageTag(languageTag).getDisplayLanguage(systemLocale)
        .takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.titlecase(systemLocale) }
}
