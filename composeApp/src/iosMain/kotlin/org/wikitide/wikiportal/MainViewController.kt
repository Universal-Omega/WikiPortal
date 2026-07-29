package org.wikitide.wikiportal

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.di.appModules
import platform.UIKit.UIViewController

private var koinStarted = false

fun initKoin() {
    if (koinStarted) return
    try {
        println("STARTING KOIN INITIALIZATION...")
        
        startKoin {
            printLogger(Level.DEBUG)
            modules(appModules())
        }
        koinStarted = true
        
        println("KOIN INITIALIZED SUCCESSFULLY")
    } catch (e: Throwable) {
        println("KOIN INITIALIZATION FAILED!")
        println("Exception Type: ${e::class.simpleName}")
        println("Message: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

fun MainViewController(): UIViewController {
    return try {
        println("CREATING COMPOSE VIEW CONTROLLER...")
        ComposeUIViewController {
            WikiPortalApp()
        }
    } catch (e: Throwable) {
        println("COMPOSE UI RENDERING FAILED!")
        println("Exception Type: ${e::class.simpleName}")
        println("Message: ${e.message}")
        e.printStackTrace()
        throw e
    }
}
