package org.wikitide.wikiportal.data

interface PlatformLanguageSync {
    fun applyToPlatform(languageTag: String?)
    fun currentPlatformOverride(): PlatformLanguageState
}

sealed interface PlatformLanguageState {
    data object Unavailable : PlatformLanguageState
    data class Known(val tag: String?) : PlatformLanguageState
}

object NoOpPlatformLanguageSync : PlatformLanguageSync {
    override fun applyToPlatform(languageTag: String?) = Unit
    override fun currentPlatformOverride(): PlatformLanguageState = PlatformLanguageState.Unavailable
}
