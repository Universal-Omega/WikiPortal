package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.runCatchingCancellable

/**
 * A shared transport for REST-style calls, both MediaWiki's own
 * rest.php modules, see [WikiSite.restUrl] and [MatomoAnalyticsApi],
 * and plain external REST and JSON services that aren't MediaWiki APIs
 * at all but are still shaped like this, not like the Action API,
 * for example [WikimediaPageviewsApi], which talks to the
 * Wikimedia Foundation's own metrics service. Every REST
 * call in this app should go through here rather than each repeating
 * its own "fetch, check HTTP status, decode, catch, log" boilerplate.
 * See [ActionApiClient] for the Action API equivalent. Adding a new
 * REST call should just be a response type plus a URL and params here,
 * not a new copy of this class's contents.
 */
class RestApiClient(
    @PublishedApi internal val httpClient: HttpClient,
) {

    companion object {
        @PublishedApi
        internal const val TAG = "RestApiClient"
    }

    /**
     * Unlike the Action API, a REST call's success or failure is a
     * normal HTTP status code, not a JSON-embedded error key, so that
     * is what this checks, explicitly. Ktor's expectSuccess = false,
     * see configureMediaWikiClient, means it won't throw on a non-2xx
     * response on its own. Without this check here, an actual HTTP
     * error would just fall through to decoding whatever error body
     * came back against the caller's expected success status, which,
     * for a default-valued response type, can silently produce an
     * empty "success" instead of a diagnosable failure.
     */
    suspend inline fun <reified T> get(
        url: String,
        params: Map<String, Any?> = emptyMap(),
    ): Result<T> = runCatchingCancellable {
        val httpResponse: HttpResponse = httpClient.get(url) {
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
        }
        if (!httpResponse.status.isSuccess()) {
            error("HTTP ${httpResponse.status.value} from $url")
        }
        httpResponse.body<T>()
    }.onFailure {
        AppLog.e(TAG, "get($url, $params) failed", it)
    }
}
