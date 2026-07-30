package org.wikitide.wikiportal.offline

private val KNOWN_JUNK_IDS = listOf("cookiewarning", "cookie-notice", "cookie-banner", "gdpr-notice")
private val KNOWN_JUNK_CLASSES = listOf("mw-cookiewarning-container")

fun stripKnownJunkElements(html: String): String {
    var result = html
    for (id in KNOWN_JUNK_IDS) {
        result = stripElement(result, idOpenTagPattern(id))
    }
    for (className in KNOWN_JUNK_CLASSES) {
        result = stripElement(result, classOpenTagPattern(className))
    }
    return result
}

private fun idOpenTagPattern(id: String) = Regex("""<([a-zA-Z0-9]+)\b[^>]*\bid="$id"[^>]*>""")

/** \b on both sides of $className, so a class list like "foo mw-cookiewarning-container bar" matches on the whole class name, not some other class that merely contains it as a substring. */
private fun classOpenTagPattern(className: String) = Regex("""<([a-zA-Z0-9]+)\b[^>]*\bclass="[^"]*\b$className\b[^"]*"[^>]*>""")

/**
 * Removes the first element matching [openTagPattern], and everything
 * inside it, by counting opening and closing tags of whatever element
 * type it is rather than a single regex match, since a naive `.*?`
 * would stop at the first closing tag it sees, which is very likely a
 * child element's, not this one's.
 */
private fun stripElement(html: String, openTagPattern: Regex): String {
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
