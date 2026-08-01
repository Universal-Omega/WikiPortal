package org.wikitide.wikiportal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.SkinOption
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.COMMON_SCRIPT_PATHS
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.deriveArticlePathPrefix
import org.wikitide.wikiportal.network.deriveAvailableSkins
import org.wikitide.wikiportal.network.deriveUncuratedDefaultSkin
import org.wikitide.wikiportal.network.deriveWikiDefaultSkin
import org.wikitide.wikiportal.network.resolveDefaultSkin
import org.wikitide.wikiportal.network.resolveFaviconUrl
import org.wikitide.wikiportal.util.AppLog

data class AddWikiUiState(
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
    val showScriptPathField: Boolean = false,
)

class AddWikiViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
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

    fun submit(rawUrl: String, customScriptPath: String = "") {
        val normalizedInput = normalizeUrl(rawUrl)
        if (normalizedInput == null) {
            _state.value = AddWikiUiState(errorMessage = "Enter a valid wiki URL, e.g. https://mywiki.example.com")
            return
        }
        _state.value = AddWikiUiState(isChecking = true)
        viewModelScope.launch {
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
                return@launch
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
                lastError?.let { AppLog.e("AddWiki", "Couldn't resolve a MediaWiki API at $resolvedBaseUrl", it) }
                val message = buildString {
                    append("Couldn't find a MediaWiki API at that address.")
                    if (trimmedCustomPath.isBlank()) {
                        append(" Tried: ${COMMON_SCRIPT_PATHS.joinToString(", ") { it.ifBlank { "(root)" } }}.")
                        append(" If your wiki uses a different path, enter it above.")
                    }
                }
                _state.value = AddWikiUiState(errorMessage = message, showScriptPathField = true)
            } else {
                repository.setActiveWiki(
                    resolvedSite.copy(
                        id = "${resolvedSite.id}-${lang.orEmpty()}",
                        name = sitename,
                        articlePathPrefix = articlePathPrefix,
                        discoveredFaviconUrl = faviconUrl,
                        availableSkins = availableSkins,
                        uncuratedDefaultSkin = uncuratedDefaultSkin,
                        skin = resolveDefaultSkin(resolvedSite, wikiDefaultSkin, uncuratedDefaultSkin, availableSkins),
                        mainPageIsDomainRoot = mainPageIsDomainRoot,
                    ),
                )
                _state.value = AddWikiUiState(done = true)
            }
        }
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank() || !trimmed.contains(".")) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
