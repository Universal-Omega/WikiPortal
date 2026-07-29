package org.wikitide.wikiportal.ui.article

import org.wikitide.wikiportal.data.model.WikiSite

/**
 * An offline copy has no network to send a tapped link to. A link to
 * another article that's also saved still works, see the offline
 * branch in ArticleHostScreen's RequestInterceptor, which swaps the tab
 * over to that other saved copy instead of trying to fetch it. Every
 * other link, internal or not, gets turned into plain bold text here,
 * so it stops looking clickable when it isn't. In-page anchors, plain
 * #fragment links to somewhere later in the same document, are left
 * alone either way.
 *
 * [offlineTitlesForSite] is title only, not the wikiId|title keys
 * AppRepository.offlineKeys stores, since every link found here is
 * necessarily on [site] already.
 */
fun rewriteOfflineLinks(html: String, site: WikiSite, offlineTitlesForSite: Set<String>): String {
    val linkPattern = Regex("""<a\b[^>]*\shref="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    return linkPattern.replace(html) { match ->
        val href = match.groupValues[1]
        if (href.startsWith("#")) return@replace match.value
        val inner = match.groupValues[2]
        val title = resolveOfflineLinkTitle(href, site)
        if (title != null && title in offlineTitlesForSite) match.value else "<b>$inner</b>"
    }
}

private fun resolveOfflineLinkTitle(href: String, site: WikiSite): String? {
    val absolute = when {
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "${site.baseUrl}$href"
        href.startsWith("http") -> href
        else -> return null
    }
    return extractCanonicalTitle(absolute, site)
}

/** offlineKeys stores "wikiId|title". Only [wikiId]'s own titles are ever relevant to a page from that wiki. */
fun offlineTitlesForWiki(offlineKeys: Set<String>, wikiId: String): Set<String> =
    offlineKeys.mapNotNullTo(mutableSetOf()) { key ->
        val separatorIndex = key.indexOf('|')
        if (separatorIndex < 0) return@mapNotNullTo null
        val keyWikiId = key.substring(0, separatorIndex)
        if (keyWikiId != wikiId) return@mapNotNullTo null
        key.substring(separatorIndex + 1)
    }
