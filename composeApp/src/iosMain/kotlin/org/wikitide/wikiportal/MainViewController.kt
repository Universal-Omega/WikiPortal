package org.wikitide.wikiportal

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.wikitide.wikiportal.di.appModules
import platform.UIKit.UIViewController

private var koinStarted = false

fun initKoin() {
    if (koinStarted) return
    startKoin { modules(appModules()) }
    koinStarted = true
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    WikiPortalApp()
}
