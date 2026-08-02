package org.wikitide.wikiportal.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalClipboard

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberPageSharer(): PageSharer {
    val clipboard = LocalClipboard.current
    return { title, url ->
        val copied = copyPlainText(clipboard, "$title\n$url")
        if (copied) ShareOutcome.COPIED_TO_CLIPBOARD else ShareOutcome.FAILED
    }
}
