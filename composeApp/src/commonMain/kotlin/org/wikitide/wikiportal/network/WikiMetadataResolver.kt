package org.wikitide.wikiportal.network

import org.wikitide.wikiportal.data.model.SkinOption
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.data.model.WikiSkins
import org.wikitide.wikiportal.data.model.skinIsUnset

/**
 * Script paths tried, in order, when probing a wiki whose script path
 * isn't already known, for a new custom wiki being added, or has
 * stopped working, for an existing custom wiki being revalidated. See
 * WikiMetadataRefresher. This covers the large majority of real-world
 * MediaWiki installs:
 *  - "/w" is the short URL pattern used by WMF wikis, Miraheze, and others
 *  - "" is a root install, with api.php directly in the web root
 *  - "/wiki" serves both articles and the API under /wiki/
 *  - "/mediawiki" is common on shared hosting, where MediaWiki lives in a subdirectory
 * Anything outside these four needs the manual "Script path" override in
 * the Add wiki UI. There is no way to guess an arbitrary custom layout.
 */
val COMMON_SCRIPT_PATHS = listOf("/w", "", "/wiki", "/mediawiki")

/**
 * Works out the wiki's real article path prefix, the part that goes
 * before a page title to build a working article URL, from
 * general.articlepath, something like "/w/$1". Returns null if
 * articlepath is missing or doesn't contain "$1", in which case callers
 * fall back to the "/wiki/" convention, see WikiSite.cleanUrlPrefix.
 */
fun deriveArticlePathPrefix(baseUrl: String, articlepath: String?): String? {
    val trimmed = articlepath?.takeIf { it.isNotBlank() } ?: return null
    if (!trimmed.contains("\$1")) return null
    val prefix = trimmed.substringBefore("\$1")
    if (prefix.contains("://")) return prefix
    return if (prefix.startsWith("/")) "$baseUrl$prefix" else "$baseUrl/$prefix"
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

private fun toSkinOption(dto: SkinInfoDto): SkinOption = SkinOption(dto.code, dto.name.ifBlank { dto.code })

/**
 * Which of this app's curated skins, see [WikiSkins], this wiki
 * genuinely has installed, according to its own siteinfo,
 * siprop=skins. [skipUnusable] controls whether a skin
 * the wiki itself marks [SkinInfoDto.unusable] still counts: see
 * [deriveAvailableSkins] and [deriveAllCuratedSkins], the two public
 * entry points, for when each is the right call.
 *
 * Returns null, rather than an empty list, if [skins] itself is empty.
 * That only happens when the siprop=skins probe genuinely came back
 * empty or failed outright.
 */
private fun deriveCuratedSkins(skins: List<SkinInfoDto>, skipUnusable: Boolean): List<SkinOption>? {
    if (skins.isEmpty()) return null
    val byCode = (if (skipUnusable) skins.filterNot { it.unusable } else skins).associateBy { it.code }
    // This iterates WikiSkins.options, not `skins`, so the picker's
    // ordering stays stable and curated rather than following whatever
    // order this particular wiki's siteinfo happens to list skins in.
    return WikiSkins.options.mapNotNull { code -> byCode[code]?.let(::toSkinOption) }
}

/**
 * [deriveCuratedSkins] with a skin the wiki marks [SkinInfoDto.unusable]
 * excluded. This backs the "Change skin" picker, and offering
 * something the wiki has deliberately hidden from its own preferences
 * page would be a strange, inconsistent choice to put in front of a
 * person. This app's own default resolution has its own reasons to see
 * past that flag instead, see [deriveAllCuratedSkins] and
 * [deriveWikiDefaultSkin]'s own comment.
 */
fun deriveAvailableSkins(skins: List<SkinInfoDto>): List<SkinOption>? = deriveCuratedSkins(skins, skipUnusable = true)

/**
 * [deriveCuratedSkins] with nothing excluded, [SkinInfoDto.unusable]
 * skins included. That flag means an admin listed a skin in
 * $wgSkipSkins to keep it out of that wiki's own preferences page, not
 * that it's actually missing or broken, see [SkinInfoDto.unusable].
 *
 * Used where this app's own default resolution needs to see past that
 * flag, since it isn't offering the skin as a person's choice the way
 * [deriveAvailableSkins] is, only trusting that the wiki genuinely has
 * it installed and rendering, currently just
 * MediaWikiApi.getMobileDefaultSkin's raw detected code, see
 * WikiMetadataRefresher and AddWikiViewModel. A wiki legitimately
 * hiding minerva from its preferences page is still a wiki this app
 * should be able to fall back to minerva on for that.
 */
fun deriveAllCuratedSkins(skins: List<SkinInfoDto>): List<SkinOption>? = deriveCuratedSkins(skins, skipUnusable = false)

/**
 * The wiki's own reported default skin, the siprop=skins entry with
 * default: true, if it is one of this app's curated and allowed skins.
 * This is null if the wiki didn't report a default at all, or its
 * default isn't something this app supports, in which case
 * [resolveDefaultSkin] just leaves this app's own blanket fallback in
 * place.
 *
 * This deliberately never looks at [SkinInfoDto.unusable] either way.
 * MediaWiki computes the two completely independently: default: true
 * comes from matching $wgDefaultSkin against the skin's own name,
 * unusable from a separate check against $wgSkipSkins, so a wiki
 * genuinely can report both true on the same entry, an admin hiding
 * their own site's actual default from its own preferences page. If
 * that happens this still trusts default: true over that flag.
 */
fun deriveWikiDefaultSkin(skins: List<SkinInfoDto>): SkinOption? {
    val reportedDefault = skins.firstOrNull { it.default } ?: return null
    if (reportedDefault.code !in WikiSkins.options) return null
    return toSkinOption(reportedDefault)
}

/**
 * The same lookup as [deriveWikiDefaultSkin], but without the curated
 * check. This backs [WikiSite.uncuratedDefaultSkin], the skin picker's
 * own last-resort fallback display, see [WikiSite.skinChoices]. It is
 * also the one exception to [resolveDefaultSkin] otherwise always
 * requiring a curated match before touching [WikiSite.skin]: see that
 * function's own comment for why a wiki with nothing curated installed
 * at all is different.
 */
fun deriveUncuratedDefaultSkin(skins: List<SkinInfoDto>): SkinOption? =
    skins.firstOrNull { it.default }?.let(::toSkinOption)

/**
 * The wiki's real main page title, straight off general.mainpage,
 * falling back to MediaWiki's own "Main Page" default whenever
 * siteinfo reports it blank or missing entirely.
 */
fun deriveMainPageTitle(mainpage: String?): String = mainpage?.takeIf { it.isNotBlank() } ?: "Main Page"

/**
 * [rawSkinCode] looked up against [curatedSkins] by code, or null if
 * it isn't one of them. Shared by WikiMetadataRefresher and
 * AddWikiViewModel's use of MediaWikiApi.getMobileDefaultSkin, since
 * both need the same "only keep it if this app actually supports it"
 * check on the raw code that call reads back out of a page's body
 * class, before it's fit to hand to [resolveDefaultSkin].
 */
fun matchCuratedSkin(rawSkinCode: String?, curatedSkins: List<SkinOption>?): SkinOption? =
    rawSkinCode?.let { code -> curatedSkins?.firstOrNull { it.code == code } }

private val BODY_TAG_REGEX = Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE)
private val CLASS_ATTR_REGEX = Regex("""class\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SKIN_CLASS_TOKEN_REGEX = Regex("""^skin-(.+)$""")

/**
 * The skin actually rendering this page, read back out of the body
 * tag's own skin-$name class, which is the class every MediaWiki skin's
 * base template writes regardless of which skin it is. This is the
 * only place a wiki's real active skin is observable at all. Siteinfo
 * only reports the desktop default, not whatever a phone visiting the
 * same page might genuinely get switched to by MobileFrontend, see
 * MediaWikiApi.getMobileDefaultSkin. Returns null if there is no body
 * tag, no class attribute on it, or no skin-$name token inside that
 * attribute.
 */
fun parseSkinFromBodyClass(html: String): String? {
    val bodyTag = BODY_TAG_REGEX.find(html)?.value ?: return null
    val classAttr = CLASS_ATTR_REGEX.find(bodyTag)?.groupValues?.get(1) ?: return null
    return classAttr.trim().split(Regex("""\s+"""))
        .firstNotNullOfOrNull { token -> SKIN_CLASS_TOKEN_REGEX.find(token)?.groupValues?.get(1) }
}

/**
 * What [site]'s skin should be, given what the wiki itself reports,
 * and, when it was actually checked, what the wiki genuinely renders
 * for a phone. This only ever touches [site.skin] at all when nobody
 * has ever actually chosen one, meaning neither a preset author, still
 * at [WikiSite.DEFAULT_SKIN] and never overridden, nor the person
 * themselves, see [WikiSite.skinIsUserSet].
 *
 * In that case, [detectedMobileSkin], from
 * MediaWikiApi.getMobileDefaultSkin and [parseSkinFromBodyClass], wins
 * first. It arrives here already matched against every curated skin
 * the wiki actually has installed, unusable ones included, see
 * [deriveAllCuratedSkins], not just [curatedSkins]. A wiki with
 * MobileFrontend installed and autodetection on can serve a
 * completely different skin to a phone than whatever it declares as
 * its desktop default, minerva being the common case, and there is no
 * siteinfo field for that, only this actual observed render.
 *
 * Failing that, this falls back to prefer the wiki's own reported
 * default, [wikiDefaultSkin], whenever it's one of this app's
 * curated skins, over this app's own generic fallback.
 * [curatedSkins] is the wiki's full curated intersection,
 * see [deriveAvailableSkins]. When that comes back genuinely empty,
 * meaning the wiki has nothing curated installed at all, not even
 * [WikiSite.DEFAULT_SKIN] itself, this instead falls back to
 * [uncuratedDefaultSkin], uncurated as it is.
 *
 * With still nothing to go on, a [curatedSkins] list of exactly one
 * entry is trusted as the default too, even without siteinfo ever
 * marking it default: true, since a wiki that only has one skin this
 * app curates installed clearly isn't rendering [WikiSite.DEFAULT_SKIN]
 * at all.
 */
fun resolveDefaultSkin(
    site: WikiSite,
    wikiDefaultSkin: SkinOption?,
    uncuratedDefaultSkin: SkinOption?,
    curatedSkins: List<SkinOption>?,
    detectedMobileSkin: SkinOption? = null,
): String {
    if (!site.skinIsUnset) return site.skin
    if (detectedMobileSkin != null) return detectedMobileSkin.code
    if (wikiDefaultSkin != null) return wikiDefaultSkin.code
    if (curatedSkins != null && curatedSkins.isEmpty() && uncuratedDefaultSkin != null) return uncuratedDefaultSkin.code
    if (curatedSkins != null && curatedSkins.size == 1) return curatedSkins[0].code
    return site.skin
}
