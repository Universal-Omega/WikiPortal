package org.wikitide.wikiportal.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.isMobilePlatform

private const val TAG = "AddWiki"

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

class AddWikiViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
    private val indieWikiDirectory: IndieWikiDirectory,
) : ViewModel() {

    private val _state = MutableStateFlow(AddWikiUiState())
    val state: StateFlow<AddWikiUiState> = _state

    /**
     * The host component of a base URL, lowercased, with no scheme or
     * trailing path. Used both for duplicate detection and as the
     * basis for a new custom wiki's id, so "http://AllTheTropes.org"
     * and "https://allthetropes.org/" are recognized as the exact same
     * wiki.
     */
    private fun hostOf(baseUrl: String): String =
        baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/").lowercase()

    /**
     * [skipIndieWikiCheck] is true for the two follow-up calls after a
     * suggestion has already been shown, see [useSuggestedIndieWiki]
     * and [continueWithOriginalUrl], so accepting or dismissing a
     * suggestion can never loop back into showing it again.
     */
    fun submit(rawUrl: String, customScriptPath: String = "", skipIndieWikiCheck: Boolean = false) {
        val normalizedInput = normalizeUrl(rawUrl)
        if (normalizedInput == null) {
            _state.value = AddWikiUiState(errorMessage = "Enter a valid wiki URL, e.g. https://mywiki.example.com")
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
            _state.value = AddWikiUiState(errorMessage = "${duplicate.name} is already in your wiki list.")
            return
        }

        val trimmedCustomPath = customScriptPath.trim()
        val candidatePaths = if (trimmedCustomPath.isNotBlank()) {
            listOf(if (trimmedCustomPath.startsWith("/")) trimmedCustomPath else "/$trimmedCustomPath")
        } else {
            COMMON_SCRIPT_PATHS
        }

        var resolvedSite: WikiSite? = null
        var sitename: String? = null
        var lang: String? = null
        var articlePathPrefix: String? = null
        var mainPageTitle: String? = null
        var faviconUrl: String? = null
        var availableSkins: List<SkinOption>? = null
        var uncuratedDefaultSkin: SkinOption? = null
        var wikiDefaultSkin: SkinOption? = null
        var mainPageIsDomainRoot = false
        var lastError: Throwable? = null

        // This always tries every candidate path. A failure on one
        // path, for example a 404 from a wrong /w/api.php, which
        // throws a JSON parse exception, says nothing about whether
        // a different path would work, so this doesn't short
        // circuit on exceptions here. It only stops early on
        // success.
        for (path in candidatePaths) {
            val candidate = WikiSite(
                id = host,
                name = resolvedBaseUrl,
                baseUrl = resolvedBaseUrl,
                scriptPath = path,
                isCustom = true,
            )
            val result = api.getSiteInfo(candidate)
            val info = result.getOrNull()?.general
            if (info?.sitename != null) {
                resolvedSite = candidate
                sitename = info.sitename
                lang = info.lang
                articlePathPrefix = deriveArticlePathPrefix(candidate.baseUrl, info.articlepath)
                mainPageTitle = deriveMainPageTitle(info.mainpage)
                mainPageIsDomainRoot = info.mainpageisdomainroot
                faviconUrl = resolveFaviconUrl(info.favicon, candidate.baseUrl)
                    ?: api.getFaviconUrlFromHtml(candidate).getOrNull()
                val skinsReported = result.getOrNull()?.skins.orEmpty()
                availableSkins = deriveAvailableSkins(skinsReported)
                uncuratedDefaultSkin = deriveUncuratedDefaultSkin(skinsReported)
                wikiDefaultSkin = deriveWikiDefaultSkin(skinsReported)
                break
            }
            result.exceptionOrNull()?.let { lastError = it }
        }

        if (resolvedSite == null || sitename == null) {
            lastError?.let { AppLog.e(TAG, "Couldn't resolve a MediaWiki API at $resolvedBaseUrl", it) }
            val message = buildString {
                append("Couldn't find a MediaWiki API at that address.")
                if (trimmedCustomPath.isBlank()) {
                    append(" Tried: ${COMMON_SCRIPT_PATHS.joinToString(", ") { it.ifBlank { "(root)" } }}.")
                    append(" If your wiki uses a different path, enter it above.")
                }
            }
            _state.value = AddWikiUiState(errorMessage = message, showScriptPathField = true)
        } else {
            // resolvedSite always still has the unset skin defaults
            // here, since this only runs once, the first time this
            // wiki is added. articlePathPrefix isn't on resolvedSite
            // itself yet, only in the local var above, hence the copy
            // here rather than passing resolvedSite as is. Also only
            // worth doing on a phone or tablet in the first place, see
            // isMobilePlatform. See MediaWikiApi.getMobileDefaultSkin.
            val detectedMobileSkinCode = if (resolvedSite.skinIsUnset && isMobilePlatform()) {
                val siteForMobileCheck = resolvedSite.copy(articlePathPrefix = articlePathPrefix)
                api.getMobileDefaultSkin(siteForMobileCheck, mainPageTitle).getOrNull()
            } else {
                null
            }
            val detectedMobileSkin = matchCuratedSkin(detectedMobileSkinCode, availableSkins)
            repository.addFreshCustomWiki(
                resolvedSite.copy(
                    id = "${resolvedSite.id}-${lang.orEmpty()}",
                    name = sitename,
                    articlePathPrefix = articlePathPrefix,
                    mainPageTitle = mainPageTitle,
                    discoveredFaviconUrl = faviconUrl,
                    availableSkins = availableSkins,
                    uncuratedDefaultSkin = uncuratedDefaultSkin,
                    skin = resolveDefaultSkin(resolvedSite, wikiDefaultSkin, uncuratedDefaultSkin, availableSkins, detectedMobileSkin),
                    mainPageIsDomainRoot = mainPageIsDomainRoot,
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
