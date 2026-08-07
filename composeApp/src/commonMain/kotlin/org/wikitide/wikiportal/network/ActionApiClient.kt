package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.runCatchingCancellable

/**
 * A shared transport for MediaWiki's classic query-string Action API,
 * api.php, action=.... Every Action API call in this app should go
 * through here rather than each repeating its own "build params, fetch,
 * check for an API-level error, catch, log" boilerplate. See
 * [RestApiClient] for the equivalent for REST-style calls, both
 * MediaWiki's own rest.php modules and plain external REST and JSON
 * services like Wikimedia's Pageviews API.
 */
class ActionApiClient(
    @PublishedApi internal val httpClient: HttpClient,
) {

    /**
     * [params] is the call-specific part: action, list, prop, meta,
     * whatever else that particular call needs. format=json and
     * formatversion=2 are always added here, so callers never need to
     * repeat those two. A null value in [params] is dropped rather than
     * sent as the literal string "null", so optional parameters can be
     * passed straight through without each call site needing its own
     * "only add this if non-null" branch. This returns [Result.failure]
     * both for a request that failed outright, for example a network,
     * TLS, or parse error, and for one that reached the wiki but got an
     * API-level error back, response.error != null, through
     * [ActionApiResponse]. Callers that need to tell those two apart,
     * see AddWikiViewModel, which validates a user-entered URL and
     * cares whether a MediaWiki API is even there, can still inspect
     * the returned Result's exception.
     */
    suspend inline fun <reified T : ActionApiResponse> get(
        url: String,
        params: Map<String, Any?>,
    ): Result<T> = runCatchingCancellable {
        val response = httpClient.get(url) {
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
            parameter("format", "json")
            parameter("formatversion", 2)
        }.body<T>()

        val error = response.error
        if (error != null) {
            throw ActionApiException(error)
        }
        response
    }.onFailure {
        AppLog.e("ActionApiClient", "get($url, $params) failed", it)
    }
}

/**
 * Thrown by [ActionApiClient.get] when the request succeeded at the
 * HTTP level but MediaWiki itself rejected it, for example bad params
 * or permission denied. This is kept as a real exception type, not a
 * generic Exception with a formatted message, so callers that care can
 * pattern-match on it instead of parsing the message string.
 */
class ActionApiException(val apiError: ApiErrorDto) : Exception("MediaWiki API error [${apiError.code}]: ${apiError.info}")
