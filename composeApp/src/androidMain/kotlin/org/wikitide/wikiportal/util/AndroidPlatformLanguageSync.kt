package org.wikitide.wikiportal.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import org.wikitide.wikiportal.data.PlatformLanguageSync
import org.wikitide.wikiportal.data.PlatformLanguageState

class AndroidPlatformLanguageSync(private val context: Context) : PlatformLanguageSync {
    override fun applyToPlatform(languageTag: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
        val newLocales = if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
        if (localeManager.applicationLocales != newLocales) {
            localeManager.applicationLocales = newLocales
        }
    }

    override fun currentPlatformOverride(): PlatformLanguageState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PlatformLanguageState.Unavailable
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return PlatformLanguageState.Unavailable
        val current = localeManager.applicationLocales
        return PlatformLanguageState.Known(if (current.isEmpty) null else current[0]?.toLanguageTag())
    }
}
