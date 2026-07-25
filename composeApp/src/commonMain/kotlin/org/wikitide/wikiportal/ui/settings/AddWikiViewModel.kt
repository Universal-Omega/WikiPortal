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
import org.wikitide.wikiportal.network.deriveWikiDefaultSkin
import org.wikitide.wikiportal.network.resolveDefaultSkin
import org.wikitide.wikiportal.network.resolveFaviconUrl

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

    fun submit(rawUrl: String, customScriptPath: String = "") {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            _state.value = AddWikiUiState(errorMessage = "Enter a valid wiki URL, e.g. https://mywiki.example.com")
            return
        }
        _state.value = AddWikiUiState(isChecking = true)
        viewModelScope.launch {
            val baseId = normalized.removePrefix("https://").removePrefix("http://").substringBefore("/")

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
            var wikiDefaultSkin: SkinOption? = null
            var lastError: Throwable? = null

            // This always tries every candidate path. A failure on one
            // path, for example a 404 from a wrong /w/api.php, which
            // throws a JSON parse exception, says nothing about whether
            // a different path would work, so this doesn't short
            // circuit on exceptions here. It only stops early on
            // success.
            for (path in candidatePaths) {
                val candidate = WikiSite(
                    id = baseId,
                    name = normalized,
                    baseUrl = normalized,
                    scriptPath = path,
                    isCustom = true,
                )
                val result = api.getSiteInfo(candidate)
                val info = result.getOrNull()?.general
                if (info?.sitename != null) {
                    resolvedSite = candidate
                    sitename = info.sitename
                    lang = info.lang
                    articlePathPrefix = deriveArticlePathPrefix(info.base, info.mainpage)
                    faviconUrl = resolveFaviconUrl(info.favicon, candidate.baseUrl)
                        ?: api.getFaviconUrlFromHtml(candidate).getOrNull()
                    val skinsReported = result.getOrNull()?.skins.orEmpty()
                    availableSkins = deriveAvailableSkins(skinsReported)
                    wikiDefaultSkin = deriveWikiDefaultSkin(skinsReported)
                    break
                }
                result.exceptionOrNull()?.let { lastError = it }
            }

            if (resolvedSite == null || sitename == null) {
                val debug = lastError?.let { "${it::class.simpleName}: ${it.message}" }
                val message = buildString {
                    append("Couldn't find a MediaWiki API at that address.")
                    if (trimmedCustomPath.isBlank()) {
                        append(" Tried: ${COMMON_SCRIPT_PATHS.joinToString(", ") { it.ifBlank { "(root)" } }}.")
                        append(" If your wiki uses a different path, enter it above.")
                    }
                    if (debug != null) append("\n\nDebug: $debug")
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
                        skin = resolveDefaultSkin(resolvedSite, wikiDefaultSkin),
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
