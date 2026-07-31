package org.wikitide.wikiportal.util

import org.wikitide.wikiportal.BuildKonfig

open class AppVersionProvider {
    open val versionName: String = BuildKonfig.VERSION_NAME
}
