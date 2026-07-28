package org.wikitide.wikiportal.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun copyPlainText(clipboard: Clipboard, text: String): Boolean = runCatching {
    clipboard.setClipEntry(ClipEntry.withPlainText(text))
}.isSuccess
