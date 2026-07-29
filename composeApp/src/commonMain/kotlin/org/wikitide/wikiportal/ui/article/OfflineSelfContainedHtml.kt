package org.wikitide.wikiportal.ui.article

import kotlin.io.encoding.Base64
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.ParseResult

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

fun buildOfflineDocument(parse: ParseResult, site: WikiSite, api: MediaWikiApi): String {
    val styleLink = api.getModuleStylesheetUrl(site, parse.modulestyles)
        ?.let { href -> "<link rel=\"stylesheet\" href=\"$href\">" }
        .orEmpty()
    val heading = "<h1 id=\"firstHeading\" class=\"firstHeading\">${parse.displaytitle ?: parse.title}</h1>"
    val footer = parse.categorieshtml
        ?.takeIf { it.isNotBlank() }
        ?.let { "<div class=\"catlinks\">$it</div>" }
        .orEmpty()
    return "<html><head><meta charset=\"utf-8\">$styleLink</head>" +
        "<body class=\"mediawiki mw-body\">$heading" +
        "<div class=\"mw-parser-output\">${parse.text}</div>$footer</body></html>"
}
