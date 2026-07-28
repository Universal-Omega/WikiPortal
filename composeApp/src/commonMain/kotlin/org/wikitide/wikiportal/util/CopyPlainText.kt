package org.wikitide.wikiportal.util

import androidx.compose.ui.platform.Clipboard

/** Can be removed once https://youtrack.jetbrains.com/projects/CMP/issues/CMP-7624/Design-ClipEntry-common-API is done. */
expect suspend fun copyPlainText(clipboard: Clipboard, text: String): Boolean
