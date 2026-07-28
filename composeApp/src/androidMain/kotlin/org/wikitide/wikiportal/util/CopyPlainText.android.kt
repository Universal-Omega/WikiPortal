package org.wikitide.wikiportal.util

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry

actual suspend fun copyPlainText(clipboard: Clipboard, text: String): Boolean = runCatching {
    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("WikiPortal logs", text)))
}.isSuccess
