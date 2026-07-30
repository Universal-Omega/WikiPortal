package org.wikitide.wikiportal.offline

/**
 * A short, known list of elements worth stripping from an offline
 * save: things that are either meaningless offline (a cookie consent
 * banner has nothing to ask consent for) or actively broken (most
 * fire off a network call the moment they render). This is
 * deliberately narrow. The point of OfflinePageCapture is keeping the
 * real page exactly as it is; this removes a small, specific list of
 * known irritants, not a general "clean up the page" pass.
 */
private val KNOWN_JUNK_IDS = listOf("cookiewarning", "cookie-notice", "cookie-banner", "gdpr-notice")

fun stripKnownJunkElements(html: String): String {
    var result = html
    for (id in KNOWN_JUNK_IDS) {
        result = stripElementById(result, id)
    }
    return result
}

/**
 * Removes the element with this exact id, and everything inside it,
 * by counting opening and closing tags of whatever element type it is
 * rather than a single regex match, since a naive `.*?` would stop at
 * the first closing tag it sees, which is very likely a child
 * element's, not this one's.
 */
private fun stripElementById(html: String, id: String): String {
    val openTagPattern = Regex("""<([a-zA-Z0-9]+)\b[^>]*\bid="$id"[^>]*>""")
    val openMatch = openTagPattern.find(html) ?: return html
    val tagName = openMatch.groupValues[1]
    val start = openMatch.range.first
    val searchFrom = openMatch.range.last + 1

    // depth 0 means "still inside the element we're removing, haven't
    // entered a nested element of the same tag name". A closing tag
    // seen at depth 0 is the one that actually matches our opening tag;
    // anything else adjusts depth for tags nested inside it.
    val tagPattern = Regex("""</?$tagName(?:\s[^>]*)?/?>""", RegexOption.IGNORE_CASE)
    var depth = 0
    var end = -1
    for (tagMatch in tagPattern.findAll(html, searchFrom)) {
        val isClosing = tagMatch.value.startsWith("</")
        val isSelfClosing = !isClosing && tagMatch.value.endsWith("/>")
        when {
            isClosing && depth == 0 -> {
                end = tagMatch.range.last
                break
            }
            isClosing -> depth--
            !isSelfClosing -> depth++
        }
    }
    if (end == -1) return html

    return html.removeRange(start, end + 1)
}
