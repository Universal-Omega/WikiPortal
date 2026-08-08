package org.wikitide.wikiportal.data.model

import org.jetbrains.compose.resources.StringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.app_language_english
import org.wikitide.wikiportal.resources.app_language_spanish
import org.wikitide.wikiportal.resources.theme_mode_system

/**
 * The languages this app ships its own strings.xml translations for,
 * offered as explicit choices in Settings. [tag] is the BCP 47 tag
 * passed to AppLocaleEnvironment, null for [SYSTEM] since that means
 * "don't override, just follow the platform's own language" rather
 * than a literal tag of its own. [labelRes] is what's shown on screen.
 */
enum class AppLanguage(val tag: String?, val labelRes: StringResource) {
    SYSTEM(null, Res.string.theme_mode_system),
    ENGLISH("en", Res.string.app_language_english),
    SPANISH("es", Res.string.app_language_spanish),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
