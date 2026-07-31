package org.wikitide.wikiportal.util

import android.content.Context

class AndroidAppVersionProvider(context: Context) : AppVersionProvider() {
    override val versionName: String =
        context.packageManager.getPackageInfo(
            context.packageName, 0
        ).versionName ?: super.versionName
}
