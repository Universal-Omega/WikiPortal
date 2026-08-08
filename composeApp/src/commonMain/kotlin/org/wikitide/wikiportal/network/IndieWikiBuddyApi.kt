package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.runCatchingCancellable

private const val TAG = "IndieWikiBuddyApi"

/**
 * Every language Indie Wiki Buddy publishes a sites*.json file for. See
 * https://github.com/KevinPayravi/indie-wiki-buddy/tree/main/data.
 * Matching a wiki someone is adding doesn't depend on the app's own
 * language, unlike the browser extension this data is sourced from, so
 * every language is fetched and searched, not just whichever locale
 * the person happens to be using.
 */
private val IWB_LANGS = listOf(
    "CA", "DE", "EN", "ES", "FI", "FR", "HR", "HU", "IT", "JA", "KO", "LZH",
    "NL", "PL", "PT", "RU", "SV", "TH", "TOK", "TR", "UK", "ZH",
)

private const val IWB_DATA_BASE_URL = "https://raw.githubusercontent.com/KevinPayravi/indie-wiki-buddy/main/data"

private val iwbJson = Json { ignoreUnknownKeys = true }

/** A raw favicon Indie Wiki Buddy ships for a destination wiki, see [IwbSiteDto.destinationIcon]. */
fun iwbFaviconUrl(language: String, destinationIcon: String): String =
    "https://raw.githubusercontent.com/KevinPayravi/indie-wiki-buddy/main/favicons/${language.lowercase()}/$destinationIcon"

class IndieWikiBuddyApi(
    private val httpClient: HttpClient,
) {

    /**
     * Fetches every language's sites*.json file in parallel and tags
     * each entry with the language it came from, since [IwbSiteDto]
     * itself carries no language field, that's implicit in which file
     * it was in.
     */
    suspend fun fetchAllSites(): List<Pair<String, IwbSiteDto>> = coroutineScope {
        IWB_LANGS.map { lang ->
            async { fetchOneLanguage(lang) }
        }.flatMap { it.await() }
    }

    private suspend fun fetchOneLanguage(lang: String): List<Pair<String, IwbSiteDto>> {
        val url = "$IWB_DATA_BASE_URL/sites$lang.json"
        return runCatchingCancellable {
            val response: HttpResponse = httpClient.get(url)
            if (!response.status.isSuccess()) error("HTTP ${response.status.value} from $url")
            iwbJson.decodeFromString<List<IwbSiteDto>>(response.bodyAsText())
        }.onFailure {
            AppLog.w(TAG, "Couldn't fetch sites$lang.json: ${it.message}")
        }.getOrNull()?.map { lang to it }.orEmpty()
    }
}
