package org.wikitide.wikiportal.network

/** Which day-over-day direction a trending page's views are moving. */
enum class TrendDirection { UP, DOWN, FLAT }

/**
 * A trending or most-viewed page, from whichever source applies to
 * the active wiki: Wikipedia's own featured-content feed for
 * Wikipedia domains, see WikimediaFeaturedFeedApi, the plain
 * Pageviews API for other Wikimedia-hosted projects that feed doesn't
 * cover, see [wikimediaProjectDomain], or the wiki's own
 * MatomoAnalytics extension for any wiki that actually has it
 * installed. description, thumbnailUrl, trend, and isItalicized are
 * only ever populated by the featured feed. The other two sources
 * leave them null or false, since neither currently exposes anything
 * equivalent.
 */
data class TrendingArticle(
    val title: String,
    val views: Long?,
    val url: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val trend: TrendDirection? = null,
    val isItalicized: Boolean = false,
)

/**
 * The daily view floor a page needs to clear to count as genuinely
 * trending, rather than just whatever happens to be the highest of a
 * handful of views on a very small wiki or language.
 */
const val TRENDING_MIN_DAILY_VIEWS = 100L

/**
 * Wikimedia project domains this app knows how to ask the Pageviews
 * API about. This is not necessarily exhaustive of every Wikimedia
 * project.
 */
private val WIKIMEDIA_SUFFIXES = listOf(
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
