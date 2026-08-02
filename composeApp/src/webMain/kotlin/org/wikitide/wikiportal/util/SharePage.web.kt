package org.wikitide.wikiportal.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalClipboard

// Browser share-sheet support is inconsistent enough, and Kotlin/Wasm's
// JS interop for it clunky enough, that copying the link is the more
// reliable move here, same as the desktop target.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberPageSharer(): PageSharer {
    val clipboard = LocalClipboard.current
    return { title, url ->
        val copied = copyPlainText(clipboard, "$title\n$url")
        if (copied) ShareOutcome.COPIED_TO_CLIPBOARD else ShareOutcome.FAILED
    }
}
