package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.wikitide.wikiportal.BuildKonfig
import org.wikitide.wikiportal.data.model.WikiSite

val mediaWikiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Shared client configuration applied on top of whichever engine each
 * platform picks. See the platform-specific createHttpClient() actuals.
 */
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureMediaWikiClient() {
    expectSuccess = false
    install(ContentNegotiation) { json(mediaWikiJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 15_000
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                println("Ktor: $message")
            }
        }
        level = LogLevel.INFO
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "WikiPortal/${BuildKonfig.VERSION_NAME} (https://wikitide.org)")
    }
}

expect fun createHttpClient(): HttpClient

/**
 * The result of a search, including CirrusSearch's spelling suggestion
 * or query rewrite if the wiki has it enabled. This is null on wikis
 * without CirrusSearch.
 */
data class SearchResults(
    val pages: List<PageSummaryDto>,
    val suggestion: String? = null,
    val rewrittenQuery: String? = null,
)

/**
 * A thin wrapper around the MediaWiki Action API. Every method here just
 * describes its own request, meaning URL, params, and response type, and
 * hands that to [ActionApiClient], which owns the actual fetch, decode,
 * error check, catch, and log steps once, in one place, instead of each
 * method repeating them.
 *
 * Every public method here should return [Result].
 */
class MediaWikiApi(
    private val httpClient: HttpClient,
    private val actionApi: ActionApiClient,
) {

    /**
     * Unlike most other calls below, this one's result is inspected for
     * its exception, not just discarded on failure. It is used to
     * validate a user-entered wiki URL, see AddWikiViewModel, which needs
     * to know whether the failure was "no MediaWiki API here", a valid
     * null general response, or "the request itself failed", a network,
     * TLS, or parse error. Those are very different problems from the
     * person's point of view. [ActionApiClient.get] preserves that
     * distinction for every call, not just this one. This comment is
     * here because this is the one place that actually relies on it.
     *
     * This returns the whole query object, meaning general, skins, and
     * extensions, not just general. It is the same request either way,
     * and callers that don't need skins or extensions, for example
     * ExploreViewModel, which only wants mainpage, just ignore them.
     */
    suspend fun getSiteInfo(site: WikiSite): Result<SiteInfoQuery?> =
        actionApi.get<SiteInfoResponse>(
            site.apiUrl,
            mapOf("action" to "query", "meta" to "siteinfo", "siprop" to "general|skins|extensions"),
        ).map { it.query }

    /**
     * A fallback for when siteinfo's own general.favicon is missing or
     * unusable. See resolveFaviconUrl's comment for the un-interpolated
     * $wgUploadPath quirk this specifically works around. This fetches
     * the wiki's own rendered HTML and pulls the real, already resolved
     * favicon URL out of its link rel icon tag instead. The skin's PHP
     * template resolves $wgFavicon properly at render time regardless of
     * how LocalSettings.php quoted it, so parsing the actual HTML output
     * avoids the bug entirely instead of guessing at it. This is only
     * called when siteinfo already failed to give something usable, see
     * AddWikiViewModel and WikiMetadataRefresher, since an extra full
     * page fetch on every wiki, every revalidation, regardless of need,
     * would be real bandwidth for no benefit on the more common wikis
     * whose siteinfo favicon already just works. This is not routed
     * through ActionApiClient or RestApiClient, since it is neither an
     * Action API call nor a JSON REST one, just a raw HTML page fetch,
     * but it still returns Result, wrapping its own
     * runCatchingCancellable directly, see the class comment. A
     * successful fetch with no link rel icon found is still a success,
     * Result.success(null), which is different from the fetch itself
     * failing.
     */
    suspend fun getFaviconUrlFromHtml(site: WikiSite): Result<String?> = runCatchingCancellable {
        val html = httpClient.get(site.indexUrl).bodyAsText()
        parseFaviconFromHtml(html, site.baseUrl)
    }.onFailure {
        println("Ktor: getFaviconUrlFromHtml(${site.indexUrl}) failed: ${it::class.simpleName}: ${it.message}")
    }

    /**
     * Follows [url] exactly the way a browser, or the wiki's own domain
     * redirects, actually would, and returns the scheme and host it
     * genuinely resolves to, lowercased. Used when adding a custom
     * wiki, so a mistyped scheme, a wrong case, for example
     * http://AllTheTropes.org, or an outdated subdomain the wiki itself
     * 30x-redirects elsewhere, for example wiki.miraheze.org landing on
     * meta.miraheze.org, gets stored as wherever it actually resolves
     * to, not the literal text typed in.
     */
    suspend fun resolveFinalBaseUrl(url: String): Result<String> = runCatchingCancellable {
        val response = httpClient.get(url)
        val finalUrl = response.request.url
        "${finalUrl.protocol.name}://${finalUrl.host.lowercase()}"
    }

    suspend fun getRandomArticles(site: WikiSite, count: Int = 15): Result<List<PageSummaryDto>> =
        actionApi.get<RandomPagesResponse>(
            site.apiUrl,
            mapOf(
                "action" to "query",
                "generator" to "random",
                "grnnamespace" to 0,
                "grnlimit" to count,
                "prop" to "extracts|pageimages",
                "exintro" to true,
                "explaintext" to true,
                "exchars" to 280,
                "piprop" to "thumbnail",
                "pithumbsize" to 400,
            ),
        ).map { it.query?.pages.orEmpty() }

    suspend fun search(site: WikiSite, query: String, limit: Int = 20): Result<SearchResults> {
        if (query.isBlank()) return Result.success(SearchResults(emptyList()))
        return actionApi.get<SearchPagesResponse>(
            site.apiUrl,
            mapOf(
                "action" to "query",
                "generator" to "search",
                "gsrsearch" to query,
                "gsrlimit" to limit,
                "gsrnamespace" to 0,
                "gsrinfo" to "suggestion|rewrittenquery",
                "prop" to "extracts|pageimages",
                "exintro" to true,
                "explaintext" to true,
                "exchars" to 200,
                "piprop" to "thumbnail",
                "pithumbsize" to 200,
            ),
        ).map { response ->
            SearchResults(
                pages = response.query?.pages.orEmpty(),
                suggestion = response.query?.searchinfo?.suggestion,
                rewrittenQuery = response.query?.searchinfo?.rewrittenquery,
            )
        }
    }

    /**
     * Titles currently in [category], most recently added first. Used
     * for the Dashboard's Relevant Links tab on wikis with a curated
     * category configured, see RelevantLinksConfig. This is a standard,
     * fully generic MediaWiki API call. There is nothing wiki specific
     * about the call itself, only about which category, if any, a given
     * wiki has configured.
     */
    suspend fun getCategoryMembers(site: WikiSite, category: String, limit: Int = 20): Result<List<String>> =
        actionApi.get<CategoryMembersResponse>(
            site.apiUrl,
            mapOf(
                "action" to "query",
                "list" to "categorymembers",
                "cmtitle" to category,
                "cmlimit" to limit,
                "cmsort" to "timestamp",
                "cmdir" to "desc",
            ),
        ).map { it.query?.categorymembers?.map { member -> member.title }.orEmpty() }

    suspend fun getProjectNamespaceActivity(site: WikiSite, limit: Int = 20): Result<List<String>> =
        actionApi.get<RecentChangesResponse>(
            site.apiUrl,
            mapOf(
                "action" to "query",
                "list" to "recentchanges",
                "rcnamespace" to 4,
                "rclimit" to limit,
                "rcprop" to "title",
                "rctype" to "edit|new",
            ),
        ).map { response ->
            // recentchanges can list the same page more than once, for
            // repeat edits. This removes duplicates while keeping recency
            // order.
            response.query?.recentchanges?.map { it.title }?.distinct().orEmpty()
        }

    suspend fun parsePage(site: WikiSite, title: String): Result<ParseResult?> =
        actionApi.get<ParseResponse>(
            site.apiUrl,
            mapOf("action" to "parse", "page" to title, "prop" to "text|displaytitle"),
        ).map { it.parse }

    /**
     * Fetches an extract and thumbnail for exactly one page. Used by
     * ArticleScreen to fill in a thumbnail and extract for saved or
     * history entries. The WebView reader itself never makes an API
     * call, so without this every saved or recently viewed article would
     * have no image no matter what the "show images" setting says, since
     * there would simply be no thumbnailUrl to show.
     */
    suspend fun getPageSummary(site: WikiSite, title: String): Result<PageSummaryDto?> =
        actionApi.get<RandomPagesResponse>(
            site.apiUrl,
            mapOf(
                "action" to "query",
                "titles" to title,
                "prop" to "extracts|pageimages",
                "exintro" to true,
                "explaintext" to true,
                "exchars" to 200,
                "piprop" to "thumbnail",
                "pithumbsize" to 200,
            ),
        ).map { it.query?.pages?.firstOrNull() }

    /**
     * Raw bytes and content type for any URL. Used to inline an offline
     * article's CSS, JS, and image sub-resources as data URIs. See
     * OfflineSelfContainedHtml.kt.
     */
    suspend fun getRawBytes(url: String): Result<Pair<String, ByteArray>> = runCatchingCancellable {
        val response = httpClient.get(url)
        val contentType = response.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
        contentType to response.body<ByteArray>()
    }
}
