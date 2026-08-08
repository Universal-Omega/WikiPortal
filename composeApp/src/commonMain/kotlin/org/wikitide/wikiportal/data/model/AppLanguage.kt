package org.wikitide.wikiportal.data.model

/**
 * The languages this app ships its own strings.xml translations for,
 * offered as explicit choices in Settings. [tag] is the BCP 47 tag
 * passed to AppLocaleEnvironment, null for [SYSTEM] since that means
 * "don't override, just follow the platform's own language" rather
 * than a literal tag of its own.
 *
 * [nativeName] is deliberately not a translated string resource: a
 * language's own name is conventionally shown in that language itself
 * in a picker like this one, for example "Español" rather than
 * whatever "Spanish" happens to be in the app's current language, so
 * it reads the same regardless of which language is currently active.
 * [SYSTEM] has no language of its own to name this way; its label
 * comes from theme_mode_system instead, see AppLanguageDialog.
 */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
