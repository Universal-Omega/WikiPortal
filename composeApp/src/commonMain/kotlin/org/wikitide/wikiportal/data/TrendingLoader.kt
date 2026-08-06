package org.wikitide.wikiportal.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.TRENDING_MIN_DAILY_VIEWS
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.network.WikimediaFeaturedFeedApi
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.wikimediaProjectDomain
import org.wikitide.wikiportal.util.nowEpochMillis

data class TrendingResult(
    val articles: List<TrendingArticle>,
    val expandable: Boolean,
    val date: String? = null,
)

/** How long a fetched result is reused before asking the network again. Trending lists don't move within this window. */
private const val CACHE_MAX_AGE_MILLIS = 15L * 60 * 1000

/**
 * How many articles a fetch asks for and caches, regardless of what the
 * caller itself needs right now. The Dashboard card only shows a handful
 * and the expanded Trending screen shows many more, see FeedViewModel
 * and TrendingViewModel, so fetching this many up front lets one cache
 * entry answer both instead of the expanded screen re-hitting the
 * network for a wiki whose trending list the Dashboard already just
 * loaded.
 */
private const val CACHE_FETCH_LIMIT = 50

private data class CacheEntry(val result: TrendingResult, val fetchedAtEpochMillis: Long)

class TrendingLoader(
    private val wikimediaFeaturedFeedApi: WikimediaFeaturedFeedApi,
    private val wikimediaPageviewsApi: WikimediaPageviewsApi,
    private val matomoAnalyticsApi: MatomoAnalyticsApi,
) {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    suspend fun load(wiki: WikiSite, limit: Int): TrendingResult {
        val cached = cacheMutex.withLock { cache[wiki.id] }
        if (cached != null && nowEpochMillis() - cached.fetchedAtEpochMillis < CACHE_MAX_AGE_MILLIS) {
            return cached.result.withLimit(limit)
        }
        val fetched = fetchFresh(wiki, CACHE_FETCH_LIMIT)
        cacheMutex.withLock { cache[wiki.id] = CacheEntry(fetched, nowEpochMillis()) }
        return fetched.withLimit(limit)
    }

    private fun TrendingResult.withLimit(limit: Int) = copy(articles = articles.take(limit))

    private suspend fun fetchFresh(wiki: WikiSite, limit: Int): TrendingResult {
        val wikimediaProject = wikimediaProjectDomain(wiki.baseUrl)
        if (wikimediaProject != null) {
            val featured = wikimediaFeaturedFeedApi.getMostRead(wikimediaProject, limit).getOrNull()
            val featuredArticles = featured?.articles.orEmpty()
            if (featuredArticles.isNotEmpty()) {
                return TrendingResult(articles = featuredArticles, expandable = true, date = featured?.resolvedDate)
            }
            val plain = wikimediaPageviewsApi.getTopArticles(wikimediaProject, limit).getOrNull()
                ?.map { TrendingArticle(title = it.article.replace('_', ' '), views = it.views) }
                .orEmpty()
            return TrendingResult(articles = plain, expandable = plain.isNotEmpty())
        }

        val matomo = matomoAnalyticsApi.getTopPages(wiki, limit = limit).getOrNull()
            ?.filter { it.views >= TRENDING_MIN_DAILY_VIEWS }
            ?.map { TrendingArticle(title = it.title, views = it.views, url = it.url.takeIf { u -> u.isNotBlank() }) }
            .orEmpty()
        return TrendingResult(articles = matomo, expandable = matomo.isNotEmpty())
    }
}
