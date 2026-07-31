package org.wikitide.wikiportal

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.di.appModules
import org.wikitide.wikiportal.util.AppLogKoinLogger
import platform.UIKit.UIViewController

private var koinStarted = false

fun initKoin() {
    if (koinStarted) return
    startKoin {
        logger(AppLogKoinLogger(Level.DEBUG))
        modules(appModules())
    }

    koinStarted = true
}

fun MainViewController(): UIViewController {
    ComposeUIViewController {
        WikiPortalApp()
    }
}
