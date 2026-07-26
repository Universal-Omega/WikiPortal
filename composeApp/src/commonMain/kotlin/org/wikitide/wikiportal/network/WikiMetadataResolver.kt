package org.wikitide.wikiportal.network

import org.wikitide.wikiportal.data.model.SkinOption
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.data.model.WikiSkins

/**
 * Script paths tried, in order, when probing a wiki whose script path
 * isn't already known, for a new custom wiki being added, or has
 * stopped working, for an existing custom wiki being revalidated. See
 * org.wikitide.wikiportal.data.WikiMetadataRefresher. This covers the
 * large majority of real-world MediaWiki installs:
 *  - "/w" is the short URL pattern used by WMF wikis, Miraheze, and others
 *  - "" is a root install, with api.php directly in the web root
 *  - "/wiki" serves both articles and the API under /wiki/
 *  - "/mediawiki" is common on shared hosting, where MediaWiki lives in a subdirectory
 * Anything outside these four needs the manual "Script path" override in
 * the Add wiki UI. There is no way to guess an arbitrary custom layout.
 */
val COMMON_SCRIPT_PATHS = listOf("/w", "", "/wiki", "/mediawiki")

/**
 * Strips the encoded main page title off the end of `base` to recover
 * the wiki's real article path prefix. For example base equal to
 * "https://x/wiki/Main_Page" and mainpage equal to "Main Page" becomes
 * "https://x/wiki/". This returns null if it can't be derived, in which
 * case callers fall back to the "/wiki/" convention
 */
fun deriveArticlePathPrefix(base: String?, mainpage: String?): String? {
    if (base.isNullOrBlank() || mainpage.isNullOrBlank()) return null
    val underscored = mainpage.replace(" ", "_")
    if (base.endsWith(underscored)) return base.removeSuffix(underscored)
    if (base.endsWith(mainpage)) return base.removeSuffix(mainpage)
    return null
}

fun resolveFaviconUrl(favicon: String?, baseUrl: String): String? {
    val trimmed = favicon?.takeIf { it.isNotBlank() } ?: return null
    if (trimmed.contains("\$wg")) return null
    return resolveMaybeRelativeUrl(trimmed, baseUrl)
}

fun parseFaviconFromHtml(html: String, baseUrl: String): String? {
    val href = LINK_TAG_REGEX.findAll(html)
        .map { it.value }
        .firstOrNull { REL_ICON_REGEX.containsMatchIn(it) }
        ?.let { HREF_REGEX.find(it)?.groupValues?.get(1) }
        ?: return null
    if (href.contains("\$wg")) return null
    return resolveMaybeRelativeUrl(href, baseUrl)
}

private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
private val REL_ICON_REGEX = Regex("""rel\s*=\s*["'](?:shortcut icon|icon)["']""", RegexOption.IGNORE_CASE)
private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

/**
 * Shared by [resolveFaviconUrl] and [parseFaviconFromHtml]. Both need
 * to turn a possibly root-relative or scheme-relative path into an
 * absolute URL against the wiki's own base.
 */
private fun resolveMaybeRelativeUrl(path: String, baseUrl: String): String {
    if (path.contains("://")) return path
    return if (path.startsWith("/")) "$baseUrl$path" else "$baseUrl/$path"
}

/**
 * Which of this app's curated skins, see [WikiSkins], are actually
 * usable on this wiki, according to its own siteinfo, siprop=skins.
 * This is deliberately an intersection, not just "whatever the wiki
 * reports".
 *
 * This returns null, rather than an empty list, if [skins] itself is
 * empty. That only happens when the siprop=skins probe genuinely came
 * back empty or failed outright.
 */
fun deriveAvailableSkins(skins: List<SkinInfoDto>): List<SkinOption>? {
    if (skins.isEmpty()) return null
    val usableByCode = skins.filterNot { it.unusable }.associateBy { it.code }
    // This iterates WikiSkins.options, not `skins`, so the picker's
    // ordering stays stable and curated rather than following whatever
    // order this particular wiki's siteinfo happens to list skins in.
    return WikiSkins.options.mapNotNull { code -> usableByCode[code]?.let { SkinOption(code, it.name.ifBlank { code }) } }
}

/**
 * The wiki's own reported default skin, the siprop=skins entry with
 * default: true, if it is one of this app's curated and allowed skins.
 * This is null if the wiki didn't report a default at all, or its
 * default isn't something this app supports, in which case
 * [resolveDefaultSkin] just leaves this app's own blanket fallback in
 * place.
 */
fun deriveWikiDefaultSkin(skins: List<SkinInfoDto>): SkinOption? {
    val reportedDefault = skins.firstOrNull { it.default } ?: return null
    if (reportedDefault.code !in WikiSkins.options) return null
    return SkinOption(reportedDefault.code, reportedDefault.name.ifBlank { reportedDefault.code })
}

/**
 * The same lookup as [deriveWikiDefaultSkin], but without the curated
 * check, used purely for the skin picker's own last-resort fallback,
 * see [WikiSite.uncuratedDefaultSkin] and [WikiSite.skinChoices]. This
 * is never used to decide what [WikiSite.skin] actually renders with.
 * [resolveDefaultSkin] below is the only thing that does that, and it
 * deliberately keeps requiring a curated match, so the app never
 * silently starts rendering a wiki with a skin it has never been
 * tested against.
 */
fun deriveUncuratedDefaultSkin(skins: List<SkinInfoDto>): SkinOption? {
    val reportedDefault = skins.firstOrNull { it.default } ?: return null
    return SkinOption(reportedDefault.code, reportedDefault.name.ifBlank { reportedDefault.code })
}

/**
 * What [site]'s skin should be, given what the wiki itself reports as
 * its own default. If nobody has ever actually chosen a skin for
 * [site], meaning neither a preset author, still at
 * [WikiSite.DEFAULT_SKIN] and never overridden, nor the person
 * themselves, see [WikiSite.skinIsUserSet], and the wiki's own default
 * is one of this app's allowed skins, this prefers that over this app's
 * own generic fallback.
 */
fun resolveDefaultSkin(site: WikiSite, wikiDefaultSkin: SkinOption?): String {
    val stillUnset = !site.skinIsUserSet && site.skin == WikiSite.DEFAULT_SKIN
    return if (stillUnset && wikiDefaultSkin != null) wikiDefaultSkin.code else site.skin
}
