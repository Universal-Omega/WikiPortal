package org.wikitide.wikiportal.data.model

import io.ktor.http.Url

object WikimediaDomains {
    private val suffixes = listOf(
        ".wikibooks.org",
        ".wikidata.org",
        ".wikifunctions.org",
        ".wikimedia.org",
        ".wikinews.org",
        ".wikipedia.org",
        ".wikiquote.org",
        ".wikiversity.org",
        ".wikivoyage.org",
        ".wiktionary.org",
        "mediawiki.org",
        "wikisource.org",
    )

    fun matches(baseUrl: String): Boolean {
        val host = runCatching { Url(baseUrl).host }.getOrNull() ?: return false
        return suffixes.any { host.endsWith(it) }
    }
}
