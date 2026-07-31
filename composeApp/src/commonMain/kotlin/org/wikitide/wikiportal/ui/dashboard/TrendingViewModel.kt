package org.wikitide.wikiportal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.network.WikimediaFeaturedFeedApi
import org.wikitide.wikiportal.network.friendlyNetworkErrorMessage
import org.wikitide.wikiportal.network.wikimediaProjectDomain

/** How many articles the expanded screen asks for, past the Dashboard card's top 5. */
private const val EXPANDED_LIMIT = 50

data class TrendingUiState(
    val isLoading: Boolean = true,
    val date: String? = null,
    val articles: List<TrendingArticle> = emptyList(),
    val wikiName: String = "",
    val errorMessage: String? = null,
)

class TrendingViewModel(
    private val repository: AppRepository,
    private val wikimediaFeaturedFeedApi: WikimediaFeaturedFeedApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendingUiState())
    val state: StateFlow<TrendingUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        val wiki = repository.activeWiki.value
        viewModelScope.launch {
            _state.value = TrendingUiState(isLoading = true, wikiName = wiki.name)
            val project = wikimediaProjectDomain(wiki.baseUrl)
            if (project == null) {
                _state.value = TrendingUiState(isLoading = false, wikiName = wiki.name, errorMessage = "Not available for this wiki")
                return@launch
            }
            wikimediaFeaturedFeedApi.getMostRead(project, limit = EXPANDED_LIMIT)
                .onSuccess { result ->
                    _state.value = TrendingUiState(
                        isLoading = false,
                        date = result.resolvedDate,
                        articles = result.articles,
                        wikiName = wiki.name,
                    )
                }
                .onFailure { e ->
                    _state.value = TrendingUiState(isLoading = false, wikiName = wiki.name, errorMessage = friendlyNetworkErrorMessage(e))
                }
        }
    }
}
