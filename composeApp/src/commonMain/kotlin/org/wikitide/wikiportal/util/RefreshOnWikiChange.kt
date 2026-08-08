package org.wikitide.wikiportal.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.model.WikiSite

/**
 * Calls [refresh] once right away for whatever wiki is currently
 * active, then again every time [activeWiki] changes to a different
 * wiki. [refresh] is expected to record which wiki it just ran for
 * itself, through [loadedForWikiId].
 */
fun CoroutineScope.refreshOnWikiChange(
    activeWiki: StateFlow<WikiSite>,
    loadedForWikiId: () -> String?,
    refresh: () -> Unit,
) {
    launch {
        activeWiki.collect { wiki ->
            if (wiki.id != loadedForWikiId()) refresh()
        }
    }
}
