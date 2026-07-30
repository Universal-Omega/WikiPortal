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
            val cleanContentType = contentType.replace(" ", "")
            val dataUri = "data:$cleanContentType;base64," + Base64.encode(bytes)
            result = result.replace(match.value, "${match.groupValues[1]}$dataUri${match.groupValues[3]}")
        }
    }
    return result
}

private const val OFFLINE_DEAD_LINK_CSS =
    "<style>.$OFFLINE_DEAD_LINK_CLASS{color:inherit!important;text-decoration:none!important;" +
        "font-weight:bold!important;cursor:default!important;pointer-events:none!important;}</style>"

private const val OFFLINE_COLLAPSIBLE_SECTIONS_JS = """<script>
document.querySelectorAll('.collapsible-heading').forEach(function(heading) {
  heading.addEventListener('click', function() {
    var expanded = heading.classList.toggle('open-block');
    heading.setAttribute('aria-expanded', expanded ? 'true' : 'false');
    var blockId = heading.getAttribute('aria-controls');
    var block = blockId ? document.getElementById(blockId) : null;
    if (block) block.classList.toggle('open-block', expanded);
  });
});
</script>"""

fun buildOfflineDocument(parse: ParseResult, site: WikiSite, api: MediaWikiApi): String {
    val styleLink = api.getModuleStylesheetUrl(site, parse.modulestyles)
        ?.let { href -> "<link rel=\"stylesheet\" href=\"$href\">" }
        .orEmpty()
    val heading = "<h1 id=\"firstHeading\" class=\"firstHeading\">${parse.displaytitle ?: parse.title}</h1>"
    val footer = parse.categorieshtml
        ?.takeIf { it.isNotBlank() }
        ?.let { "<div class=\"catlinks\">$it</div>" }
        .orEmpty()
    return "<html><head><meta charset=\"utf-8\">$styleLink$OFFLINE_DEAD_LINK_CSS</head>" +
        "<body class=\"mediawiki mw-body\">$heading" +
        "<div class=\"mw-parser-output\">${parse.text}</div>$footer$OFFLINE_COLLAPSIBLE_SECTIONS_JS</body></html>"
}
