package org.wikitide.wikiportal.offline

import kotlin.io.encoding.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Rewrites every statically referenced link href, script src, img src,
 * and CSS @import in [html] into a base64 data URI, fetched through
 * [api], so the result needs zero further network requests to render.
 * An already-inlined data: URI is left alone. This is the one step
 * every offline document ultimately depends on, whatever built the
 * HTML it runs on, see OfflinePageCapture.
 */
suspend fun inlineResourcesAsDataUris(html: String, baseUrl: String, api: MediaWikiApi): String {
    var result = inlineLinkTags(html, baseUrl, api)
    result = inlineScriptTags(result, baseUrl, api)
    result = inlineImgTags(result, baseUrl, api)
    result = inlineCssImports(result, baseUrl, api)
    return result
}

private val LINK_TAG = Regex("""<link\b[^>]*>""")
private val SCRIPT_OPEN_TAG = Regex("""<script\b[^>]*>""")
private val IMG_TAG = Regex("""<img\b[^>]*>""")
private val HREF_ATTR = Regex("""\shref="([^"]*)"""")
private val SRC_ATTR = Regex("""\ssrc="([^"]*)"""")
private val REL_ATTR = Regex("""\srel="([^"]*)"""")

/** A browser fetches a page's own resources several at once, not one at a time; matched here rather than fetching truly unbounded, which for a page with hundreds of images would just as easily overwhelm a small wiki's server or this app's own connection pool as help. */
private const val MAX_CONCURRENT_FETCHES = 8

/**
 * Some skins load their real stylesheet as
 * `<link rel="preload" as="style" href="..." onload="this.rel='stylesheet'">`,
 * a real, fast page's own performance trick: the stylesheet is fetched
 * early but only actually activates as CSS once that onload swaps
 * rel to stylesheet. A data URI does not reliably fire onload the
 * same way a real network fetch does, so that swap can just never
 * happen, and the styling never applies even though the CSS itself
 * inlined successfully. Detecting that shape and forcing rel straight
 * to stylesheet up front skips the swap entirely instead of depending
 * on it. Harmless either way for a tag that was already a plain
 * rel="stylesheet" to begin with, which is what most MediaWiki
 * installs actually ship; this only ever changes anything for the
 * preload shape specifically.
 */
private suspend fun inlineLinkTags(html: String, baseUrl: String, api: MediaWikiApi): String =
    inlineMatches(
        html, LINK_TAG, baseUrl, api,
        extractUrl = { match -> HREF_ATTR.find(match.value)?.groupValues?.get(1) },
        buildReplacement = { match, dataUri ->
            val tag = match.value
            val hrefMatch = HREF_ATTR.find(tag)!!
            var newTag = tag.replaceRange(hrefMatch.range, " href=\"$dataUri\"")

            val relMatch = REL_ATTR.find(newTag)
            if (relMatch != null && relMatch.groupValues[1].contains("preload", ignoreCase = true)) {
                newTag = newTag.replaceRange(REL_ATTR.find(newTag)!!.range, " rel=\"stylesheet\"")
            }
            // A stale integrity hash or a crossorigin requirement has
            // no meaning for content that is now inline, and a browser
            // treating either as still binding would only reject the
            // resource for no real reason.
            newTag = stripAttribute(newTag, "as")
            newTag = stripAttribute(newTag, "onload")
            newTag = stripAttribute(newTag, "integrity")
            newTag = stripAttribute(newTag, "crossorigin")
            newTag
        },
    )

private suspend fun inlineScriptTags(html: String, baseUrl: String, api: MediaWikiApi): String =
    inlineMatches(
        html, SCRIPT_OPEN_TAG, baseUrl, api,
        extractUrl = { match -> SRC_ATTR.find(match.value)?.groupValues?.get(1) },
        buildReplacement = { match, dataUri ->
            var newTag = match.value.replaceRange(SRC_ATTR.find(match.value)!!.range, " src=\"$dataUri\"")
            newTag = stripAttribute(newTag, "integrity")
            newTag = stripAttribute(newTag, "crossorigin")
            newTag
        },
    )

private suspend fun inlineImgTags(html: String, baseUrl: String, api: MediaWikiApi): String =
    inlineMatches(
        html, IMG_TAG, baseUrl, api,
        extractUrl = { match -> SRC_ATTR.find(match.value)?.groupValues?.get(1) },
        buildReplacement = { match, dataUri -> match.value.replaceRange(SRC_ATTR.find(match.value)!!.range, " src=\"$dataUri\"") },
    )

/**
 * @import inside a literal `<style>` block already sitting in the
 * page's own HTML. An @import buried inside an *external* stylesheet
 * this function is about to fetch and inline is out of scope: by the
 * time that content is a data URI it is no longer regex-addressable
 * text, and unwinding a second level of imports out of it isn't worth
 * the complexity it would add here.
 */
private val CSS_IMPORT = Regex("""@import\s+(?:url\()?["']?([^"')]+)["']?\)?\s*;""")

private suspend fun inlineCssImports(html: String, baseUrl: String, api: MediaWikiApi): String =
    inlineMatches(
        html, CSS_IMPORT, baseUrl, api,
        extractUrl = { match -> match.groupValues[1] },
        buildReplacement = { _, dataUri -> "@import url(\"$dataUri\");" },
    )

private suspend fun inlineMatches(
    html: String,
    tagPattern: Regex,
    baseUrl: String,
    api: MediaWikiApi,
    extractUrl: (MatchResult) -> String?,
    buildReplacement: (match: MatchResult, dataUri: String) -> String,
): String {
    val matches = tagPattern.findAll(html).toList()
    if (matches.isEmpty()) return html

    val dataUris = coroutineScope {
        val fetchLimiter = Semaphore(MAX_CONCURRENT_FETCHES)
        matches.map { match ->
            async {
                val rawUrl = extractUrl(match)
                if (rawUrl == null || rawUrl.startsWith("data:")) {
                    null
                } else {
                    fetchLimiter.withPermit { fetchAsDataUri(rawUrl, baseUrl, api) }
                }
            }
        }.awaitAll()
    }

    val result = StringBuilder(html.length)
    var cursor = 0
    matches.forEachIndexed { index, match ->
        result.append(html, cursor, match.range.first)
        val dataUri = dataUris[index]
        result.append(if (dataUri != null) buildReplacement(match, dataUri) else match.value)
        cursor = match.range.last + 1
    }
    result.append(html, cursor, html.length)
    return result.toString()
}

private suspend fun fetchAsDataUri(rawUrl: String, baseUrl: String, api: MediaWikiApi): String? {
    val decodedUrl = rawUrl.decodeHtmlEntities()
    val absoluteUrl = when {
        decodedUrl.startsWith("//") -> "https:$decodedUrl"
        decodedUrl.startsWith("/") -> "$baseUrl$decodedUrl"
        decodedUrl.startsWith("http") -> decodedUrl
        else -> return null
    }
    val (contentType, bytes) = api.getRawBytes(absoluteUrl).getOrNull() ?: return null
    // "text/css; charset=utf-8", a completely normal Content-Type
    // header, has a raw space in it that a data URI's own syntax
    // doesn't allow. Left in, some WebViews just silently refuse the
    // resource instead of erroring loudly about it.
    val cleanContentType = contentType.replace(" ", "")
    return "data:$cleanContentType;base64," + Base64.encode(bytes)
}

private fun stripAttribute(tag: String, name: String): String {
    val pattern = Regex("""\s$name="[^"]*"""", RegexOption.IGNORE_CASE)
    return pattern.replace(tag, "")
}
