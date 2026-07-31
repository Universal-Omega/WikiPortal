package org.wikitide.wikiportal.android

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.di.appModules
import org.wikitide.wikiportal.util.AppLogKoinLogger

class WikiPortalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            logger(AppLogKoinLogger(Level.DEBUG))
            androidContext(this@WikiPortalApplication)
            modules(appModules())
        }
    }
}
