package org.wikitide.wikiportal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBuilder.Settings.LogSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.wikitide.wikiportal.di.appModules
import org.wikitide.wikiportal.util.AppLogKoinLogger
import java.io.File

fun main() {
    startKoin {
        logger(AppLogKoinLogger(Level.DEBUG))
        modules(appModules())
    }

    application {
        var kcefInitialized by remember { mutableStateOf(false) }
        var kcefError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                runCatching {
                    KCEF.init(
                        builder = {
                            installDir(File("kcef-bundle"))
                            settings { logSeverity = LogSeverity.Disable }
                            progress { onInitialized { kcefInitialized = true } }
                        },
                        onError = { kcefError = true },
                        onRestartRequired = {},
                    )
                }.onFailure { kcefError = true }
            }
        }

        DisposableEffect(Unit) {
            onDispose { runCatching { KCEF.disposeBlocking() } }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "WikiPortal",
            state = rememberWindowState(width = 1100.dp, height = 760.dp),
        ) {
            when {
                kcefError -> Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Couldn't start the embedded browser engine. Please restart WikiPortal.")
                    }
                }
                !kcefInitialized -> Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> WikiPortalApp()
            }
        }
    }
}
