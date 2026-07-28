package org.wikitide.wikiportal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.friendlyNetworkErrorMessage
import org.wikitide.wikiportal.network.wikimediaProjectDomain
import org.wikitide.wikiportal.util.AppLog

data class ExploreUiState(
    val wiki: WikiSite? = null,
    val articles: List<PageSummaryDto> = emptyList(),
    val showImages: Boolean = true,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mainPageTitle: String? = null,
    val trending: List<TrendingArticle> = emptyList(),
)

private data class ExploreCoreState(
    val wiki: WikiSite,
    val articles: List<PageSummaryDto>,
    val showImages: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
)

class ExploreViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
    private val wikimediaPageviewsApi: WikimediaPageviewsApi,
    private val matomoAnalyticsApi: MatomoAnalyticsApi,
) : ViewModel() {

    private val _articles = MutableStateFlow<List<PageSummaryDto>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trending = MutableStateFlow<List<TrendingArticle>>(emptyList())

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(
            repository.activeWiki, _articles, repository.showImages, _isLoading, _errorMessage,
        ) { wiki, articles, showImages, isLoading, errorMessage ->
            ExploreCoreState(wiki, articles, showImages, isLoading, errorMessage)
        },
        _trending,
    ) { core, trending ->
        ExploreUiState(
            wiki = core.wiki,
            articles = core.articles,
            showImages = core.showImages,
            isLoading = core.isLoading,
            errorMessage = core.errorMessage,
            mainPageTitle = core.wiki.mainPageTitle,
            trending = trending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    private var loadedForWikiId: String? = null

    init {
        viewModelScope.launch {
            repository.activeWiki.collect { wiki ->
                if (wiki.id != loadedForWikiId) refresh()
            }
        }
    }

    fun refresh() {
        val wiki = repository.activeWiki.value
        loadedForWikiId = wiki.id
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = api.getRandomArticles(wiki)
            // Only apply this if the person hasn't already switched to a
            // different wiki while the request was in flight. Otherwise a
            // slow response for the wiki we've left can land after a
            // newer, faster one and silently show the wrong wiki's
            // articles. Same guard as the trending fetch below.
            if (repository.activeWiki.value.id == wiki.id) {
                result.onSuccess { articles ->
                    _articles.value = articles
                }.onFailure { e ->
                    _articles.value = emptyList()
                    AppLog.e("ExploreViewModel", "getRandomArticles failed for ${wiki.id}", e)
                    _errorMessage.value = friendlyNetworkErrorMessage(e)
                }
                _isLoading.value = false
            }
        }
        // This only hits the network if this wiki's main page title
        // isn't already cached, see WikiSite.mainPageTitle. It's saved
        // across sessions, and also kept fresh by WikiMetadataRefresher
        // during its own revalidation. In practice this fires at most
        // once ever per wiki, not once per Explore visit or
        // pull-to-refresh like before.
        if (wiki.mainPageTitle == null) {
            viewModelScope.launch {
                val info = api.getSiteInfo(wiki).getOrNull()?.general
                val title = info?.mainpage?.takeIf { it.isNotBlank() } ?: "Main Page"
                repository.updateMainPageTitle(wiki.id, title)
            }
        }
        // Whichever trending source, if any, applies to this wiki. See
        // TrendingArticle's comment. Both branches are best effort and
        // silent. A wiki with neither, the common case for custom or
        // arbitrary wikis, or a non-Wikimedia wiki without the
        // MatomoAnalytics extension, just gets an empty trending
        // section, not an error. Unlike the Wikimedia check, there is
        // no cheap way to pre-filter which wikis are even worth trying
        // Matomo for. MatomoAnalyticsApi checks the wiki's own siteinfo
        // itself before doing anything else.
        viewModelScope.launch {
            _trending.value = emptyList()
            val wikimediaProject = wikimediaProjectDomain(wiki.baseUrl)
            val trending = if (wikimediaProject != null) {
                wikimediaPageviewsApi.getTopArticles(wikimediaProject).getOrNull()
                    ?.map { TrendingArticle(title = it.article.replace('_', ' '), views = it.views) }
            } else {
                matomoAnalyticsApi.getTopPages(wiki).getOrNull()
                    ?.map { TrendingArticle(title = it.title, views = it.views, url = it.url.takeIf { u -> u.isNotBlank() }) }
            }
            // This only applies if we're still looking at the same
            // wiki. It guards against a slow trending fetch landing
            // after a quick back-to-back wiki switch and overwriting
            // the newer wiki's still-loading, correctly empty, trending
            // state.
            if (repository.activeWiki.value.id == wiki.id) {
                _trending.value = trending.orEmpty()
            }
        }
    }
}
