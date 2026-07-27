package org.wikitide.wikiportal.network

/**
 * A trending or most-viewed page, from whichever source applies to
 * the active wiki: Wikimedia's Pageviews API for Wikimedia-hosted
 * projects, see [wikimediaProjectDomain], or the wiki's own
 * MatomoAnalytics extension for any wiki that actually has it
 * installed.
 */
data class TrendingArticle(val title: String, val views: Long?, val url: String? = null)

/**
 * Wikimedia project domains this app knows how to ask the Pageviews
 * API about. This is not necessarily exhaustive of every Wikimedia
 * project.
 */
private val WIKIMEDIA_SUFFIXES = listOf(
    ".wikipedia.org", ".wiktionary.org", ".wikibooks.org", ".wikiquote.org",
    ".wikisource.org", ".wikinews.org", ".wikiversity.org", ".wikivoyage.org", ".wikidata.org",
    "mediawiki.org",
)

/**
 * Maps a wiki's baseUrl to the project domain form the Wikimedia
 * Pageviews API expects, for example "https://en.wikipedia.org"
 * becomes "en.wikipedia.org". This is null if this isn't a
 * Wikimedia-hosted project, since that API has no data for anything
 * else. This is host based, not tied to this app's own preset ids, so
 * a custom-added Wikimedia project.
 */
fun wikimediaProjectDomain(baseUrl: String): String? {
    val host = baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
    return host.takeIf { h -> WIKIMEDIA_SUFFIXES.any { h.endsWith(it) } }
}
