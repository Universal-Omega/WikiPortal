package org.wikitide.wikiportal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.koin.core.context.startKoin
import org.wikitide.wikiportal.di.appModules

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(appModules()) }
    ComposeViewport(document.body!!) {
        WikiPortalApp()
    }
}
