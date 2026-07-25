package org.wikitide.wikiportal.android

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.wikitide.wikiportal.di.appModules

class WikiPortalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@WikiPortalApplication)
            modules(appModules())
        }
    }
}
