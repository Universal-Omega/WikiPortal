package org.wikitide.wikiportal.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun copyPlainText(clipboard: Clipboard, text: String): Boolean = runCatching {
    clipboard.setClipEntry(ClipEntry(StringSelection(text)))
}.isSuccess
