package org.wikitide.wikiportal.offline

import kotlin.io.encoding.Base64
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Rewrites every statically referenced link href, script src, or img
 * src in [html] into a base64 data URI, fetched through [api], so the
 * result needs zero further network requests to render. An
 * already-inlined data: URI is left alone. This is the one step every
 * offline document ultimately depends on, whatever built the HTML it
 * runs on, see OfflinePageCapture.
 */
suspend fun inlineResourcesAsDataUris(html: String, baseUrl: String, api: MediaWikiApi): String {
    val patterns = listOf(
        Regex("""(<link[^>]+href=")([^"]+)(")"""),
        Regex("""(<script[^>]+src=")([^"]+)(")"""),
        Regex("""(<img[^>]+src=")([^"]+)(")"""),
    )

    var result = html
    for (pattern in patterns) {
        val matches = pattern.findAll(result).toList()
        for (match in matches) {
            val rawUrl = match.groupValues[2]
            if (rawUrl.startsWith("data:")) continue
            val absoluteUrl = when {
                rawUrl.startsWith("//") -> "https:$rawUrl"
                rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                rawUrl.startsWith("http") -> rawUrl
                else -> continue
            }
            val fetched = api.getRawBytes(absoluteUrl).getOrNull() ?: continue
            val (contentType, bytes) = fetched
            // "text/css; charset=utf-8", a completely normal Content-Type
            // header, has a raw space in it that a data URI's own syntax
            // doesn't allow. Left in, some WebViews just silently refuse
            // the resource instead of erroring loudly about it.
            val cleanContentType = contentType.replace(" ", "")
            val dataUri = "data:$cleanContentType;base64," + Base64.encode(bytes)
            result = result.replace(match.value, "${match.groupValues[1]}$dataUri${match.groupValues[3]}")
        }
    }
    return result
}
