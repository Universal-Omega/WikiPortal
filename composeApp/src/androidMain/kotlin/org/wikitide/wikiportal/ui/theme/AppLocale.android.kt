package org.wikitide.wikiportal.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Updates the Configuration compose-resources actually reads strings
 * from, so switching language here takes effect immediately, no
 * restart needed, and also calls through to AppCompatDelegate's own
 * per-app language support, so Android's system Settings > Apps >
 * Language screen and other system UI, like share sheets, agree with
 * what this app is showing. See this module's AndroidManifest.xml for
 * the AppLocalesMetadataHolderService entry that lets
 * AppCompatDelegate persist and restore this on its own, even though
 * MainActivity itself is a plain ComponentActivity rather than an
 * AppCompatActivity. That persistence is what makes the language
 * still correct the next time the app is launched, before
 * AppRepository's own saved setting has finished loading and this
 * actual has had a chance to run again.
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
        configuration.setLocale(new)
        val resources = LocalContext.current.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)

        AppCompatDelegate.setApplicationLocales(
            if (value == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(value),
        )

        return LocalConfiguration.provides(configuration)
    }
}
