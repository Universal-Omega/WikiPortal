package org.wikitide.wikiportal.util.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Captures [title] for offline reading: the real rendered page, the
 * exact same HTML this app's own WebView already shows while browsing
 * live, since WikiSite.articleUrl is the one URL both paths use, with
 * a short, known list of irrelevant elements removed (OfflineJunkStripper),
 * its own ResourceLoader modules prefetched, styling guaranteed by
 * fetching it as plain CSS text rather than trusting the real
 * ResourceLoader JS runtime to apply it, scripts like collapsible
 * sections best-effort on top of that (OfflineModuleBundle), and every
 * remaining external resource inlined as a data URI
 * (OfflineResourceInliner). The result needs nothing from the network
 * to render.
 *
 * Runs on Dispatchers.Default, not whatever dispatcher the caller
 * happened to be on. This does a real amount of CPU work, regex
 * scanning and rebuilding strings that can run into the megabytes for
 * a large page, and none of that has any business running on
 * Compose's UI dispatcher, which is exactly where the LaunchedEffect
 * that kicks this off otherwise runs. Without this a big save could
 * visibly freeze the whole app, not just the tab doing the saving,
 * for as long as the save took.
 */
suspend fun captureArticleForOffline(site: WikiSite, title: String, api: MediaWikiApi): Result<String> = withContext(Dispatchers.Default) {
    val rendered = api.getRenderedPage(site, title).getOrElse { return@withContext Result.failure(it) }

    val withoutJunk = stripKnownJunkElements(rendered)
    val modules = fetchOfflineModules(withoutJunk, site, api)
    val moduleStyleTag = modules.css?.let { "<style>$it</style>" }.orEmpty()
    val moduleScriptTag = modules.js?.let { "<script>$it</script>" }.orEmpty()

    val withHeadExtras = injectBeforeFirst(withoutJunk, "</head>", OFFLINE_DEAD_LINK_CSS + moduleStyleTag)
    val withScripts = injectBeforeFirst(withHeadExtras, "</body>", moduleScriptTag)

    Result.success(inlineResourcesAsDataUris(withScripts, site.baseUrl, api))
}

/**
 * Inserts [insert] right before the first case-insensitive [closingTag],
 * or prepends it if that tag is missing from otherwise-valid-enough
 * HTML. Deliberately plain indexOf/substring, not Regex.replaceFirst:
 * [insert] is real JS and CSS content, jQuery's own `$` included, and
 * passing that through as a replacement pattern crashes the moment it
 * contains a `$` Matcher.replaceFirst can't parse as a group
 * reference, which real script content does constantly.
 */
private fun injectBeforeFirst(html: String, closingTag: String, insert: String): String {
    if (insert.isBlank()) return html
    val index = html.indexOf(closingTag, ignoreCase = true)
    return if (index >= 0) html.substring(0, index) + insert + html.substring(index) else insert + html
}
