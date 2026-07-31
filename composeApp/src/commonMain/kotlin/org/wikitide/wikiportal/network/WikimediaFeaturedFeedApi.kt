package org.wikitide.wikiportal.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeaturedFeedResponse(
    val mostread: MostReadBlockDto? = null,
)

@Serializable
data class MostReadBlockDto(
    val date: String? = null,
    val articles: List<MostReadArticleDto> = emptyList(),
)

@Serializable
data class MostReadArticleDto(
    val titles: MostReadTitlesDto? = null,
    val description: String? = null,
    val thumbnail: ThumbnailDto? = null,
    val views: Long = 0,
    val rank: Int = 0,
    @SerialName("view_history") val viewHistory: List<MostReadViewHistoryEntryDto> = emptyList(),
)

@Serializable
data class MostReadTitlesDto(
    val normalized: String = "",
    // MediaWiki's own rendered display title for the page, with any
    // DISPLAYTITLE markup, most relevantly <i> for creative works
    // like films or albums, already applied server side. This is used
    // to decide isItalicized below rather than guessing from the
    // plain title.
    val display: String? = null,
)

@Serializable
data class MostReadViewHistoryEntryDto(
    val date: String = "",
    val views: Long = 0,
)

/** [resolvedDate] is whichever day the feed actually had data for, see getMostRead. */
data class MostReadResult(val resolvedDate: String?, val articles: List<TrendingArticle>)

class WikimediaFeaturedFeedApi(
    private val restApi: RestApiClient,
) {

    suspend fun getMostRead(project: String, limit: Int = 5): Result<MostReadResult> {
        var lastFailure: Result<MostReadResult>? = null
        // 0 is "today", which the feed itself already answers with
        // the previous day's most-read list, see the mostread date
        // field. The extra days back exist only for when even that
        // hasn't been published yet, the same fallback shape as
        // WikimediaPageviewsApi.
        for (daysAgo in 0..2) {
            val result = fetchForDate(project, dateForPageviews(daysAgo), limit)
            if (result.isSuccess) return result
            lastFailure = result
        }
        return lastFailure ?: Result.failure(IllegalStateException("no dates attempted"))
    }

    private suspend fun fetchForDate(project: String, date: String, limit: Int): Result<MostReadResult> {
        val url = "https://$project/api/rest_v1/feed/featured/$date"
        return restApi.get<FeaturedFeedResponse>(url).map { response ->
            val block = response.mostread
            val articles = block?.articles.orEmpty()
                .filter { it.views >= TRENDING_MIN_DAILY_VIEWS }
                .sortedBy { it.rank }
                .take(limit)
                .map { it.toTrendingArticle(project) }
            MostReadResult(resolvedDate = block?.date, articles = articles)
        }
    }
}

private fun MostReadArticleDto.toTrendingArticle(project: String): TrendingArticle {
    val title = titles?.normalized.orEmpty()
    return TrendingArticle(
        title = title,
        views = views,
        url = "https://$project/wiki/${title.replace(' ', '_')}",
        description = description,
        thumbnailUrl = thumbnail?.source,
        trend = trendFromHistory(viewHistory),
        isItalicized = titles?.display?.contains("<i>") == true,
    )
}

private fun trendFromHistory(history: List<MostReadViewHistoryEntryDto>): TrendDirection? {
    if (history.size < 2) return null
    val latest = history.last().views
    val previous = history[history.size - 2].views
    return when {
        latest > previous -> TrendDirection.UP
        latest < previous -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }
}
