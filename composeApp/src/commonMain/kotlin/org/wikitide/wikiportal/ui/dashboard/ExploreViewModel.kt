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
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.network.RecentChangeEntry
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.friendlyNetworkErrorMessage
import org.wikitide.wikiportal.network.wikimediaProjectDomain
import org.wikitide.wikiportal.util.AppLog

/** How many recently visited or saved pages to surface on the Dashboard. */
private const val ROW_LIMIT = 12

data class ExploreUiState(
    val wiki: WikiSite? = null,
    val recentChanges: List<RecentChangeEntry> = emptyList(),
    val continueReading: List<SavedPage> = emptyList(),
    val savedPages: List<SavedPage> = emptyList(),
    val randomPick: PageSummaryDto? = null,
    val showImages: Boolean = true,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mainPageTitle: String? = null,
    val trending: List<TrendingArticle> = emptyList(),
)

private data class ExploreCoreState(
    val wiki: WikiSite,
    val recentChanges: List<RecentChangeEntry>,
    val showImages: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
)

private data class ExploreLocalState(
    val continueReading: List<SavedPage>,
    val savedPages: List<SavedPage>,
)

class ExploreViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
    private val wikimediaPageviewsApi: WikimediaPageviewsApi,
    private val matomoAnalyticsApi: MatomoAnalyticsApi,
) : ViewModel() {

    private val _recentChanges = MutableStateFlow<List<RecentChangeEntry>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trending = MutableStateFlow<List<TrendingArticle>>(emptyList())
    private val _randomPick = MutableStateFlow<PageSummaryDto?>(null)

    private var randomBacklog: List<PageSummaryDto> = emptyList()

    val uiState: StateFlow<ExploreUiState> = combine(
        combine(
            repository.activeWiki, _recentChanges, repository.showImages, _isLoading, _errorMessage,
        ) { wiki, recentChanges, showImages, isLoading, errorMessage ->
            ExploreCoreState(wiki, recentChanges, showImages, isLoading, errorMessage)
        },
        combine(repository.history, repository.savedPages) { history, saved ->
            ExploreLocalState(history, saved)
        },
        _trending,
        _randomPick,
    ) { core, local, trending, randomPick ->
        val wikiId = core.wiki.id
        ExploreUiState(
            wiki = core.wiki,
            recentChanges = core.recentChanges,
            continueReading = local.continueReading.filter { it.wikiId == wikiId }.take(ROW_LIMIT),
            savedPages = local.savedPages.filter { it.wikiId == wikiId }.take(ROW_LIMIT),
            randomPick = randomPick,
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
            val result = api.getRecentChanges(wiki, namespace = 0, limit = 25)
            // Only apply this if the person hasn't already switched to a
            // different wiki while the request was in flight. Otherwise a
            // slow response for the wiki we've left can land after a
            // newer, faster one and silently show the wrong wiki's
            // activity. Same guard as the trending and random fetches
            // below.
            if (repository.activeWiki.value.id == wiki.id) {
                result.onSuccess { changes ->
                    _recentChanges.value = changes
                }.onFailure { e ->
                    _recentChanges.value = emptyList()
                    AppLog.e("ExploreViewModel", "getRecentChanges failed for ${wiki.id}", e)
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
            if (repository.activeWiki.value.id == wiki.id) {
                _trending.value = trending.orEmpty()
            }
        }
        randomBacklog = emptyList()
        _randomPick.value = null
        viewModelScope.launch { fetchRandomBatch(wiki) }
    }

    /**
     * Swaps in a new random pick for the hero card. Hands one out of
     * the local backlog when there is one, so most taps feel instant,
     * and only refetches once that backlog is empty.
     */
    fun shuffleRandomPick() {
        val wiki = repository.activeWiki.value
        val next = randomBacklog.firstOrNull()
        if (next != null) {
            randomBacklog = randomBacklog.drop(1)
            _randomPick.value = next
        } else {
            viewModelScope.launch { fetchRandomBatch(wiki) }
        }
    }

    private suspend fun fetchRandomBatch(wiki: WikiSite) {
        val articles = api.getRandomArticles(wiki, count = 10).getOrElse { e ->
            AppLog.e("ExploreViewModel", "getRandomArticles failed for ${wiki.id}", e)
            emptyList()
        }
        if (repository.activeWiki.value.id != wiki.id) return
        randomBacklog = articles.drop(1)
        _randomPick.value = articles.firstOrNull() ?: _randomPick.value
    }
}
