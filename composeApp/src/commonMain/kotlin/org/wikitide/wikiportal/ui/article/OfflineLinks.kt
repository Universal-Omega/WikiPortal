package org.wikitide.wikiportal.ui.article

import org.wikitide.wikiportal.data.model.WikiSite

/** The class rewriteOfflineLinks marks a deactivated link with. See OFFLINE_DEAD_LINK_CSS in OfflineSelfContainedHtml.kt for what it actually does. */
const val OFFLINE_DEAD_LINK_CLASS = "wp-offline-dead-link"

/**
 * An offline copy has no network to send a tapped link to. A link to
 * another article that's also saved still works, see the offline
 * branch in ArticleHostScreen's RequestInterceptor, which swaps the tab
 * over to that other saved copy instead of trying to fetch it. Every
 * other link, internal or not, gets its href stripped here and
 * [OFFLINE_DEAD_LINK_CLASS] added, so it stops looking clickable
 * without losing whatever box, border, or button styling its own
 * class already gave it, the way discarding the tag entirely would.
 * In-page anchors, plain #fragment links to somewhere later in the
 * same document, are left alone either way.
 *
 * Only the opening `<a ...>` tag is ever touched, never its content or
 * closing tag, on purpose: this runs against the whole saved document,
 * images and all, already inlined as data URIs, and a pattern that had
 * to scan into that content looking for a matching `</a>` would be both
 * slower and far easier to get wrong.
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

/**
 * The baseUrl to pass alongside a given loadHtml call. Every offline
 * article for the same wiki loading with the exact same baseUrl, plain
 * site.baseUrl, is what let one saved article's WebView history entry
 * get served back for a completely different one: some WebView engines
 * key cached rendering and back-forward history off the baseUrl alone,
 * not the actual HTML given alongside it. Folding the article's own
 * title and a hash of its current HTML into that URL gives every
 * distinct load, different article or the same article re-saved with
 * different content, its own identity instead of colliding with
 * whatever was last loaded at that same bare site.baseUrl.
 */
fun offlineLoadIdentityUrl(site: WikiSite, title: String, html: String): String =
    "${site.articleUrl(title)}#offline-${html.hashCode()}"
