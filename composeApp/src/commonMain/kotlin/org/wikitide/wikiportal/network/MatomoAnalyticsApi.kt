package org.wikitide.wikiportal.network

import kotlinx.serialization.Serializable
import org.wikitide.wikiportal.data.model.WikiSite

/**
 * One trending page the way MatomoAnalytics's REST module, TopPagesHandler,
 * reports it. This is a flat JSON array of these, not wrapped in an
 * action=query style envelope, since this is a genuine REST endpoint,
 * rest.php, rather than the classic action= Action API. This carries a
 * little more than [TrendingArticle] needs today, [pageId], so a later
 * addition on the PHP side, say a thumbnail URL, is just a new optional
 * field here, not a reshape of the whole response. [pageId] itself is
 * always null right now, since TopPagesHandler doesn't fill it in.
 * Matomo's "Actions.getPageTitles" report has no concept of a MediaWiki
 * page ID, only the title Matomo itself tracked the pageview against.
 */
@Serializable
data class MatomoTrendingPageDto(
    val title: String = "",
    val views: Long = 0,
    val pageId: Int? = null,
)

class MatomoAnalyticsApi(
    private val restApi: RestApiClient,
    private val mediaWikiApi: MediaWikiApi,
) {
    private val ROUTE_PATH = "/matomoanalytics/v0/top_pages"

    suspend fun getTopPages(site: WikiSite, period: Int = 7, limit: Int = 10): Result<List<MatomoTrendingPageDto>> {
        val extensions = mediaWikiApi.getSiteInfo(site).getOrNull()?.extensions.orEmpty()
        if (extensions.none { it.name == "MatomoAnalytics" }) return Result.success(emptyList())

        val url = "${site.restUrl}$ROUTE_PATH"
        return restApi.get<List<MatomoTrendingPageDto>>(url, mapOf("period" to period, "limit" to limit))
            .map { it.take(limit) }
    }
}
