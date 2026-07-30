package org.wikitide.wikiportal.offline

import kotlin.io.encoding.Base64
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

/**
 * A modern skin, Citizen here, commonly loads its real stylesheet as
 * `<link rel="preload" as="style" href="..." onload="this.rel='stylesheet'">`,
 * a real, fast page's own performance trick: the stylesheet is fetched
 * early but only actually activates as CSS once that onload swaps
 * rel to stylesheet. A data URI does not reliably fire onload the
 * same way a real network fetch does, so that swap can just never
 * happen, and the styling never applies even though the CSS itself
 * inlined successfully. Detecting that shape and forcing rel straight
 * to stylesheet up front skips the swap entirely instead of depending
 * on it.
 */
private suspend fun inlineLinkTags(html: String, baseUrl: String, api: MediaWikiApi): String {
    var result = html
    for (match in LINK_TAG.findAll(html).toList()) {
        val tag = match.value
        val hrefMatch = HREF_ATTR.find(tag) ?: continue
        val rawUrl = hrefMatch.groupValues[1]
        if (rawUrl.startsWith("data:")) continue
        val dataUri = fetchAsDataUri(rawUrl, baseUrl, api) ?: continue

        var newTag = tag.replaceRange(hrefMatch.range, " href=\"$dataUri\"")

        val relMatch = REL_ATTR.find(newTag)
        if (relMatch != null && relMatch.groupValues[1].contains("preload", ignoreCase = true)) {
            newTag = newTag.replaceRange(REL_ATTR.find(newTag)!!.range, " rel=\"stylesheet\"")
        }
        // A stale integrity hash or a crossorigin requirement has no
        // meaning for content that is now inline, and a browser
        // treating either as still binding would only reject the
        // resource for no real reason.
        newTag = stripAttribute(newTag, "as")
        newTag = stripAttribute(newTag, "onload")
        newTag = stripAttribute(newTag, "integrity")
        newTag = stripAttribute(newTag, "crossorigin")

        result = result.replace(tag, newTag)
    }
    return result
}

private suspend fun inlineScriptTags(html: String, baseUrl: String, api: MediaWikiApi): String {
    var result = html
    for (match in SCRIPT_OPEN_TAG.findAll(html).toList()) {
        val tag = match.value
        val srcMatch = SRC_ATTR.find(tag) ?: continue
        val rawUrl = srcMatch.groupValues[1]
        if (rawUrl.startsWith("data:")) continue
        val dataUri = fetchAsDataUri(rawUrl, baseUrl, api) ?: continue

        var newTag = tag.replaceRange(srcMatch.range, " src=\"$dataUri\"")
        newTag = stripAttribute(newTag, "integrity")
        newTag = stripAttribute(newTag, "crossorigin")

        result = result.replace(tag, newTag)
    }
    return result
}

private suspend fun inlineImgTags(html: String, baseUrl: String, api: MediaWikiApi): String {
    var result = html
    for (match in IMG_TAG.findAll(html).toList()) {
        val tag = match.value
        val srcMatch = SRC_ATTR.find(tag) ?: continue
        val rawUrl = srcMatch.groupValues[1]
        if (rawUrl.startsWith("data:")) continue
        val dataUri = fetchAsDataUri(rawUrl, baseUrl, api) ?: continue
        result = result.replace(tag, tag.replaceRange(srcMatch.range, " src=\"$dataUri\""))
    }
    return result
}

/**
 * @import inside a literal `<style>` block already sitting in the
 * page's own HTML. An @import buried inside an *external* stylesheet
 * this function is about to fetch and inline is out of scope: by the
 * time that content is a data URI it is no longer regex-addressable
 * text, and unwinding a second level of imports out of it isn't worth
 * the complexity it would add here.
 */
private val CSS_IMPORT = Regex("""@import\s+(?:url\()?["']?([^"')]+)["']?\)?\s*;""")

private suspend fun inlineCssImports(html: String, baseUrl: String, api: MediaWikiApi): String {
    var result = html
    for (match in CSS_IMPORT.findAll(html).toList()) {
        val rawUrl = match.groupValues[1]
        if (rawUrl.startsWith("data:")) continue
        val dataUri = fetchAsDataUri(rawUrl, baseUrl, api) ?: continue
        result = result.replace(match.value, "@import url(\"$dataUri\");")
    }
    return result
}

private suspend fun fetchAsDataUri(rawUrl: String, baseUrl: String, api: MediaWikiApi): String? {
    val absoluteUrl = when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
        rawUrl.startsWith("http") -> rawUrl
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
