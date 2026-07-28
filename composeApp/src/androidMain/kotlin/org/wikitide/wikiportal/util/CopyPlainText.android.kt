package org.wikitide.wikiportal.util

import android.content.ClipData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun copyPlainText(clipboard: Clipboard, text: String): Boolean = runCatching {
    clipboard.setClipEntry(ClipData.newPlainText(text, text).toClipEntry())
}.isSuccess
