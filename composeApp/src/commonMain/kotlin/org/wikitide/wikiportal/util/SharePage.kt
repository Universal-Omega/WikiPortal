package org.wikitide.wikiportal.util

import androidx.compose.runtime.Composable

/**
 * Returns a function that hands a page's title and url to whatever the
 * platform offers for sharing. Where there is no native share sheet the
 * link is copied to the clipboard instead, and the returned function
 * reports which of the two happened so the caller can message it.
 */
@Composable
expect fun rememberPageSharer(): PageSharer

typealias PageSharer = suspend (title: String, url: String) -> ShareOutcome

enum class ShareOutcome { SHARED, COPIED_TO_CLIPBOARD, FAILED }
