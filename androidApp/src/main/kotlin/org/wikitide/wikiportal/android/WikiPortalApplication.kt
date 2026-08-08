package org.wikitide.wikiportal.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.data.AppRepository
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

        registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    GlobalContext.get().get<AppRepository>().reconcilePlatformLanguage()
                }

                override fun onLowMemory() = Unit

                override fun onTrimMemory(level: Int) = Unit
            },
        )
    }
}
