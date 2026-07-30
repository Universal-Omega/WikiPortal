package org.wikitide.wikiportal.offline

import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Captures [title] for offline reading: the real rendered page, the
 * exact same HTML this app's own WebView already shows while browsing
 * live, since WikiSite.articleUrl is the one URL both paths use, with
 * a short, known list of irrelevant elements removed (OfflineJunkStripper),
 * its own ResourceLoader modules prefetched so real page scripts like
 * collapsible sections keep working offline (OfflineModuleBundle), and
 * every remaining external resource inlined as a data URI
 * (OfflineResourceInliner). The result needs nothing from the network
 * to render.
 *
 * Link deactivation, rewriteOfflineLinks, deliberately isn't done here.
 * Which links should still work depends on which other articles are
 * saved right now, not what was saved when this article was captured,
 * so it's re-applied every time a saved copy is loaded instead, see
 * ArticleHostScreen.
 */
suspend fun captureArticleForOffline(site: WikiSite, title: String, api: MediaWikiApi): Result<String> {
    val rendered = api.getRenderedPage(site, title).getOrElse { return Result.failure(it) }

    val withoutJunk = stripKnownJunkElements(rendered)
    val moduleBundle = fetchOfflineModuleBundle(withoutJunk, site, api).orEmpty()
    val withHeadExtras = injectBeforeFirst(withoutJunk, "</head>", OFFLINE_DEAD_LINK_CSS + moduleBundle)
    val withFallback = injectBeforeFirst(withHeadExtras, "</body>", OFFLINE_COLLAPSIBLE_FALLBACK_SCRIPT)

    return Result.success(inlineResourcesAsDataUris(withFallback, site.baseUrl, api))
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
