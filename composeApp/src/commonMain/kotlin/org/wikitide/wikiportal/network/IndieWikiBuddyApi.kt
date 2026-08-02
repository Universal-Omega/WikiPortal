package org.wikitide.wikiportal.network

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.wikitide.wikiportal.util.AppLog

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

/** A raw favicon Indie Wiki Buddy ships for a destination wiki, see [IwbSiteDto.destinationIcon]. */
fun iwbFaviconUrl(language: String, destinationIcon: String): String =
    "https://raw.githubusercontent.com/KevinPayravi/indie-wiki-buddy/main/favicons/${language.lowercase()}/$destinationIcon"

class IndieWikiBuddyApi(
    private val restApi: RestApiClient,
) {

    /**
     * Fetches every language's sites*.json file in parallel and tags
     * each entry with the language it came from, since [IwbSiteDto]
     * itself carries no language field, that's implicit in which file
     * it was in. A language file that fails to fetch, for example a
     * transient network error partway through, is just left out of the
     * result rather than failing the whole call, since a partial,
     * mostly-fresh directory is far more useful here than none at all.
     */
    suspend fun fetchAllSites(): List<Pair<String, IwbSiteDto>> = coroutineScope {
        IWB_LANGS.map { lang ->
            async {
                restApi.get<List<IwbSiteDto>>("$IWB_DATA_BASE_URL/sites$lang.json")
                    .onFailure { AppLog.w("IndieWikiBuddyApi", "Couldn't fetch sites$lang.json: ${it.message}") }
                    .getOrNull()
                    ?.map { lang to it }
                    .orEmpty()
            }
        }.flatMap { it.await() }
    }
}
