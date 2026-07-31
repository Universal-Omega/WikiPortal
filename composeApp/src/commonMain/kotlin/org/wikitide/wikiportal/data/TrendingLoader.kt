package org.wikitide.wikiportal.data

import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.TRENDING_MIN_DAILY_VIEWS
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.network.WikimediaFeaturedFeedApi
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.wikimediaProjectDomain

data class TrendingResult(
    val articles: List<TrendingArticle>,
    val expandable: Boolean,
    val date: String? = null,
)

class TrendingLoader(
    private val wikimediaFeaturedFeedApi: WikimediaFeaturedFeedApi,
    private val wikimediaPageviewsApi: WikimediaPageviewsApi,
    private val matomoAnalyticsApi: MatomoAnalyticsApi,
) {

    suspend fun load(wiki: WikiSite, limit: Int): TrendingResult {
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
