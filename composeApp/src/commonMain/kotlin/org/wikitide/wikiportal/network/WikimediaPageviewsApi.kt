package org.wikitide.wikiportal.network

import kotlinx.serialization.Serializable

@Serializable
data class WikimediaPageviewsResponse(
    val items: List<WikimediaPageviewsItem> = emptyList(),
)

@Serializable
data class WikimediaPageviewsItem(
    val articles: List<WikimediaPageviewsArticle> = emptyList(),
)

@Serializable
data class WikimediaPageviewsArticle(
    val article: String = "",
    val views: Long = 0,
)

class WikimediaPageviewsApi(
    private val restApi: RestApiClient,
) {

    suspend fun getTopArticles(
        project: String,
        limit: Int = 10,
    ): Result<List<WikimediaPageviewsArticle>> {
        var lastFailure: Result<List<WikimediaPageviewsArticle>>? = null
        for (daysAgo in 1..3) {
            val result = fetchForDate(project, wikimediaDatePath(daysAgo), limit)
            if (result.isSuccess) return result
            lastFailure = result
        }

        // All attempts failed. This returns the most recent failure,
        // meaning yesterday's, the one whose failure reason is most
        // likely to still be relevant, rather than an empty success, so
        // callers still see a real error instead of silently treating
        // "every date we tried is missing" the same as "there's just
        // nothing trending".
        return lastFailure ?: Result.failure(IllegalStateException("no dates attempted"))
    }

    private suspend fun fetchForDate(
        project: String,
        date: String,
        limit: Int,
    ): Result<List<WikimediaPageviewsArticle>> {
        val url = "https://wikimedia.org/api/rest_v1/metrics/pageviews/top/$project/all-access/$date"
        return restApi.get<WikimediaPageviewsResponse>(url).map { response ->
            response.items.firstOrNull()?.articles
                // Main_Page and Special: pages are always at the top of
                // any wiki's raw pageview counts and aren't interesting
                // as "trending". They are just always there.
                ?.filterNot { it.article == "Main_Page" || it.article.startsWith("Special:") }
                ?.filter { it.views >= TRENDING_MIN_DAILY_VIEWS }
                ?.take(limit)
                .orEmpty()
        }
    }
}
