package org.wikitide.wikiportal.data.model

enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
