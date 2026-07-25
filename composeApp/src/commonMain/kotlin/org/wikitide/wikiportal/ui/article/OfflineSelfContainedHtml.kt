package org.wikitide.wikiportal.ui.article

import kotlin.io.encoding.Base64
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Builds a fully self-contained HTML document. Every statically
 * referenced link, script src, or img src is fetched and inlined as a
 * base64 data URI directly in the markup, so the resulting single
 * string needs zero further network requests to render.
 */
suspend fun buildSelfContainedHtml(html: String, baseUrl: String, api: MediaWikiApi): String {
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
            val dataUri = "data:$contentType;base64," + Base64.encode(bytes)
            result = result.replace(match.value, "${match.groupValues[1]}$dataUri${match.groupValues[3]}")
        }
    }
    return result
}
