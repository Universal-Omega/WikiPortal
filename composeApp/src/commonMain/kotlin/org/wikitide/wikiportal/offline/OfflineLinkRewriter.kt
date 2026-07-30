package org.wikitide.wikiportal.offline

import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.ui.article.extractCanonicalTitle

/** The class rewriteOfflineLinks marks a deactivated link with. The matching CSS rule lives right alongside the page's own real stylesheet, injected by OfflinePageCapture. */
const val OFFLINE_DEAD_LINK_CLASS = "wp-offline-dead-link"

/** The one CSS rule OFFLINE_DEAD_LINK_CLASS needs: bold, no link color, no underline, no pointer affordance. !important so it wins over whatever box, border, or button styling the wiki's own real CSS already gave that element by class, which should otherwise stay exactly as it was. */
const val OFFLINE_DEAD_LINK_CSS =
    "<style>.$OFFLINE_DEAD_LINK_CLASS{color:inherit!important;text-decoration:none!important;" +
        "font-weight:bold!important;cursor:default!important;pointer-events:none!important;}</style>"

/**
 * An offline copy has no network to send a tapped link to. A link to
 * another article that's also saved still works, navigated to within
 * the same tab, see ArticleHostScreen's RequestInterceptor. Every
 * other link, anywhere on the page, not just the article body, gets
 * its href stripped here and [OFFLINE_DEAD_LINK_CLASS] added, so it
 * stops looking clickable without losing whatever box, border, or
 * button styling its own class already gave it. In-page anchors,
 * plain #fragment links to somewhere later in the same document, are
 * left alone either way.
 *
 * Only the opening `<a ...>` tag is ever touched, never its content or
 * closing tag: this runs against the whole saved document, already
 * inlined images and all, and a pattern that had to scan into that
 * content looking for a matching `</a>` would be both slower and far
 * easier to get wrong.
 *
 * [offlineTitlesForSite] is title only, not the wikiId|title keys
 * AppRepository.offlineKeys stores, since every link found here is
 * necessarily on [site] already.
 */
fun rewriteOfflineLinks(html: String, site: WikiSite, offlineTitlesForSite: Set<String>): String {
    val openingTag = Regex("""<a\b([^>]*)>""")
    val hrefAttr = Regex("""\shref="([^"]*)"""")
    val classAttr = Regex("""\sclass="([^"]*)"""")

    return openingTag.replace(html) { match ->
        val attrs = match.groupValues[1]
        val hrefMatch = hrefAttr.find(attrs) ?: return@replace match.value
        val href = hrefMatch.groupValues[1]
        if (href.startsWith("#")) return@replace match.value
        val title = resolveOfflineLinkTitle(href, site)
        if (title != null && title in offlineTitlesForSite) return@replace match.value

        val withoutHref = attrs.removeRange(hrefMatch.range)
        val classMatch = classAttr.find(withoutHref)
        val withDeadClass = if (classMatch != null) {
            withoutHref.replaceRange(classMatch.range, " class=\"${classMatch.groupValues[1]} $OFFLINE_DEAD_LINK_CLASS\"")
        } else {
            "$withoutHref class=\"$OFFLINE_DEAD_LINK_CLASS\""
        }
        "<a$withDeadClass>"
    }
}

private fun resolveOfflineLinkTitle(href: String, site: WikiSite): String? {
    val decodedHref = href.decodeHtmlEntities()
    val absolute = when {
        decodedHref.startsWith("//") -> "https:$decodedHref"
        decodedHref.startsWith("/") -> "${site.baseUrl}$decodedHref"
        decodedHref.startsWith("http") -> decodedHref
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

/**
 * The identity to load offline HTML with. Every offline article for a
 * wiki loading with the exact same baseUrl, plain site.baseUrl, is
 * what let one saved article's cached rendering get served back for a
 * completely different one on some platforms: several WebView engines
 * key cached rendering and back-forward history off that identity
 * alone, not the actual HTML given alongside it. Folding the article's
 * own title and a hash of its current HTML into the URL gives every
 * distinct load, different article or the same article reloaded with
 * different content, its own identity instead of colliding with
 * whatever was last loaded at that same bare address.
 */
fun offlineLoadIdentityUrl(site: WikiSite, title: String, html: String): String =
    "${site.articleUrl(title)}#offline-${html.hashCode()}"
