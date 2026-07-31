package org.wikitide.wikiportal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.di.appModules
import org.wikitide.wikiportal.util.AppLogKoinLogger

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        logger(AppLogKoinLogger(Level.DEBUG))
        modules(appModules())
    }

    ComposeViewport(document.body!!) {
        WikiPortalApp()
    }
}
