package org.wikitide.wikiportal.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.IndieWikiDirectory
import org.wikitide.wikiportal.data.model.SkinOption
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.data.model.skinIsUnset
import org.wikitide.wikiportal.network.COMMON_SCRIPT_PATHS
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.deriveArticlePathPrefix
import org.wikitide.wikiportal.network.deriveAvailableSkins
import org.wikitide.wikiportal.network.deriveMainPageTitle
import org.wikitide.wikiportal.network.deriveUncuratedDefaultSkin
import org.wikitide.wikiportal.network.deriveWikiDefaultSkin
import org.wikitide.wikiportal.network.matchCuratedSkin
import org.wikitide.wikiportal.network.resolveDefaultSkin
import org.wikitide.wikiportal.network.resolveFaviconUrl
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.add_wiki_error_duplicate
import org.wikitide.wikiportal.resources.add_wiki_error_invalid_url
import org.wikitide.wikiportal.resources.add_wiki_error_no_api
import org.wikitide.wikiportal.resources.add_wiki_error_tried_paths
import org.wikitide.wikiportal.resources.add_wiki_error_try_different_path
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.isMobilePlatform

private const val TAG = "AddWikiViewModel"

/** An independent wiki Indie Wiki Buddy knows replaces whatever was actually typed in, offered before resolution proceeds. */
@Immutable
data class IndieWikiSuggestion(
    val originalUrl: String,
    val destinationName: String,
    val destinationBaseUrl: String,
)

@Immutable
data class AddWikiUiState(
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
    val showScriptPathField: Boolean = false,
    val indieWikiSuggestion: IndieWikiSuggestion? = null,
)

/** Everything resolveAndAdd needs out of a successful getSiteInfo probe, bundled since tryResolve tries several candidate paths and only the winning one's details matter. */
private data class ResolvedWikiInfo(
    val site: WikiSite,
    val sitename: String,
    val lang: String?,
    val articlePathPrefix: String?,
    val mainPageTitle: String?,
    val faviconUrl: String?,
    val availableSkins: List<SkinOption>?,
    val uncuratedDefaultSkin: SkinOption?,
    val wikiDefaultSkin: SkinOption?,
    val mainPageIsDomainRoot: Boolean,
)

class AddWikiViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
    private val indieWikiDirectory: IndieWikiDirectory,
) : ViewModel() {

    private val _state = MutableStateFlow(AddWikiUiState())
    val state: StateFlow<AddWikiUiState> = _state

    /**
     * The host and any meaningful path of a base URL, lowercased, with
     * no scheme or trailing slash. Used both for duplicate detection
     * and as the basis for a new custom wiki's id, so
     * "http://AllTheTropes.org" and "https://allthetropes.org/" are
     * recognized as the exact same wiki..
     */
    private fun hostOf(baseUrl: String): String =
        baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/').lowercase()

    private fun baseUrlPrefixes(baseUrl: String): List<String> {
        val hostStart = baseUrl.indexOf("://").let { if (it == -1) 0 else it + 3 }
        val prefixes = mutableListOf(baseUrl)
        var current = baseUrl
        while (true) {
            val lastSlash = current.lastIndexOf('/')
            if (lastSlash < hostStart) break
            current = current.substring(0, lastSlash)
            prefixes += current
        }
        return prefixes
    }

    /**
     * [skipIndieWikiCheck] is true for the two follow-up calls after a
     * suggestion has already been shown, see [useSuggestedIndieWiki]
     * and [continueWithOriginalUrl], so accepting or dismissing a
     * suggestion can never loop back into showing it again.
     */
    fun submit(rawUrl: String, customScriptPath: String = "", skipIndieWikiCheck: Boolean = false) {
        val normalizedInput = normalizeUrl(rawUrl)
        if (normalizedInput == null) {
            viewModelScope.launch {
                _state.value = AddWikiUiState(errorMessage = getString(Res.string.add_wiki_error_invalid_url))
            }
            return
        }
        _state.value = AddWikiUiState(isChecking = true)
        viewModelScope.launch {
            if (!skipIndieWikiCheck && repository.indieWikiSuggestionsEnabled.value) {
                val suggestion = indieWikiDirectory.findRedirectSuggestion(normalizedInput)
                if (suggestion != null && hostOf(suggestion.destinationBaseUrl) != hostOf(normalizedInput)) {
                    _state.value = AddWikiUiState(
                        indieWikiSuggestion = IndieWikiSuggestion(
                            originalUrl = normalizedInput,
                            destinationName = suggestion.destinationName,
                            destinationBaseUrl = suggestion.destinationBaseUrl,
                        ),
                    )
                    return@launch
                }
            }
            resolveAndAdd(normalizedInput, customScriptPath)
        }
    }

    /** Adds the independent wiki a suggestion pointed at instead of whatever was originally typed in. */
    fun useSuggestedIndieWiki() {
        val suggestion = _state.value.indieWikiSuggestion ?: return
        submit("https://${suggestion.destinationBaseUrl}", skipIndieWikiCheck = true)
    }

    /** Dismisses a suggestion and goes ahead with the originally typed URL anyway. */
    fun continueWithOriginalUrl() {
        val suggestion = _state.value.indieWikiSuggestion ?: return
        submit(suggestion.originalUrl, skipIndieWikiCheck = true)
    }

    /**
     * Tries every path in [candidatePaths] appended to [baseUrl] as a
     * script path, stopping at the first one whose getSiteInfo actually
     * comes back with a sitename. A failure on one path, for example a
     * 404 from a wrong /w/api.php, which throws a JSON parse exception,
     * says nothing about whether a different path would work, so this
     * doesn't short circuit on exceptions, only on success. Returns
     * whichever ran last if every path fails, so resolveAndAdd's own
     * error message reflects the most recent attempt either way.
     */
    private suspend fun tryResolve(host: String, baseUrl: String, candidatePaths: List<String>): Pair<ResolvedWikiInfo?, Throwable?> {
        var lastError: Throwable? = null
        for (path in candidatePaths) {
            val candidate = WikiSite(id = host, name = baseUrl, baseUrl = baseUrl, scriptPath = path, isCustom = true)
            val result = api.getSiteInfo(candidate)
            val info = result.getOrNull()?.general
            if (info?.sitename != null) {
                val skinsReported = result.getOrNull()?.skins.orEmpty()
                val resolved = ResolvedWikiInfo(
                    site = candidate,
                    sitename = info.sitename,
                    lang = info.lang,
                    articlePathPrefix = deriveArticlePathPrefix(candidate.baseUrl, info.articlepath),
                    mainPageTitle = deriveMainPageTitle(info.mainpage),
                    faviconUrl = resolveFaviconUrl(info.favicon, candidate.baseUrl) ?: api.getFaviconUrlFromHtml(candidate).getOrNull(),
                    availableSkins = deriveAvailableSkins(skinsReported),
                    uncuratedDefaultSkin = deriveUncuratedDefaultSkin(skinsReported),
                    wikiDefaultSkin = deriveWikiDefaultSkin(skinsReported),
                    mainPageIsDomainRoot = info.mainpageisdomainroot,
                )
                return resolved to null
            }
            result.exceptionOrNull()?.let { lastError = it }
        }
        return null to lastError
    }

    private suspend fun resolveAndAdd(normalizedInput: String, customScriptPath: String) {
        // Resolves wherever this URL actually, genuinely leads,
        // following both a wrong scheme or case correcting itself
        // and the wiki's own domain redirects, for example
        // wiki.miraheze.org landing on meta.miraheze.org, rather
        // than trusting the literal text typed in. Falls back to
        // the typed URL itself if this preliminary probe fails,
        // for example being offline, since the real validity check
        // is the api.php probing below regardless.
        val resolvedBaseUrl = api.resolveFinalBaseUrl(normalizedInput).getOrNull() ?: normalizedInput
        val host = hostOf(resolvedBaseUrl)

        val duplicate = repository.allWikisNow().firstOrNull { hostOf(it.baseUrl) == host }
        if (duplicate != null) {
            _state.value = AddWikiUiState(errorMessage = getString(Res.string.add_wiki_error_duplicate, duplicate.name))
            return
        }

        val trimmedCustomPath = customScriptPath.trim()
        val candidatePaths = if (trimmedCustomPath.isNotBlank()) {
            listOf(if (trimmedCustomPath.startsWith("/")) trimmedCustomPath else "/$trimmedCustomPath")
        } else {
            COMMON_SCRIPT_PATHS
        }

        var resolved: ResolvedWikiInfo? = null
        var lastError: Throwable? = null
        for (candidateBaseUrl in baseUrlPrefixes(resolvedBaseUrl)) {
            val (candidateResolved, candidateError) = tryResolve(hostOf(candidateBaseUrl), candidateBaseUrl, candidatePaths)
            if (candidateResolved != null) {
                resolved = candidateResolved
                break
            }
            candidateError?.let { lastError = it }
        }

        if (resolved == null) {
            lastError?.let { AppLog.e(TAG, "Couldn't resolve a MediaWiki API at $resolvedBaseUrl", it) }
            val message = buildString {
                append(getString(Res.string.add_wiki_error_no_api))
                if (trimmedCustomPath.isBlank()) {
                    append(" ")
                    append(getString(Res.string.add_wiki_error_tried_paths, COMMON_SCRIPT_PATHS.joinToString(", ") { it.ifBlank { "(root)" } }))
                    append(" ")
                    append(getString(Res.string.add_wiki_error_try_different_path))
                }
            }
            _state.value = AddWikiUiState(errorMessage = message, showScriptPathField = true)
        } else {
            val resolvedSite = resolved.site
            val resolvedHost = hostOf(resolvedSite.baseUrl)
            val lateDuplicate = repository.allWikisNow().firstOrNull { hostOf(it.baseUrl) == resolvedHost }
            if (lateDuplicate != null) {
                _state.value = AddWikiUiState(errorMessage = getString(Res.string.add_wiki_error_duplicate, lateDuplicate.name))
                return
            }
            // resolvedSite always still has the unset skin defaults
            // here, since this only runs once, the first time this
            // wiki is added. articlePathPrefix isn't on resolvedSite
            // itself yet, only on resolved above, hence the copy here
            // rather than passing resolvedSite as is. Also only worth
            // doing on a phone or tablet in the first place, see
            // isMobilePlatform. See MediaWikiApi.getMobileDefaultSkin.
            val detectedMobileSkinCode = if (resolvedSite.skinIsUnset && isMobilePlatform()) {
                val siteForMobileCheck = resolvedSite.copy(articlePathPrefix = resolved.articlePathPrefix)
                api.getMobileDefaultSkin(siteForMobileCheck, resolved.mainPageTitle).getOrNull()
            } else {
                null
            }
            val detectedMobileSkin = matchCuratedSkin(detectedMobileSkinCode, resolved.availableSkins)
            repository.addFreshCustomWiki(
                resolvedSite.copy(
                    id = "${resolvedSite.id}-${resolved.lang.orEmpty()}",
                    name = resolved.sitename,
                    articlePathPrefix = resolved.articlePathPrefix,
                    mainPageTitle = resolved.mainPageTitle,
                    discoveredFaviconUrl = resolved.faviconUrl,
                    availableSkins = resolved.availableSkins,
                    uncuratedDefaultSkin = resolved.uncuratedDefaultSkin,
                    skin = resolveDefaultSkin(resolvedSite, resolved.wikiDefaultSkin, resolved.uncuratedDefaultSkin, resolved.availableSkins, detectedMobileSkin),
                    mainPageIsDomainRoot = resolved.mainPageIsDomainRoot,
                ),
            )
            _state.value = AddWikiUiState(done = true)
        }
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank() || !trimmed.contains(".")) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
