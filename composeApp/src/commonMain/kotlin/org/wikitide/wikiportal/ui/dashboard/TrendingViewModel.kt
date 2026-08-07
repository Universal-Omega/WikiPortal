package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TrendingLoader
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.util.refreshOnWikiChange

/** How many articles the expanded screen asks for, past the Dashboard card's top 5. */
private const val EXPANDED_LIMIT = 50

@Immutable
data class TrendingUiState(
    val isLoading: Boolean = true,
    val date: String? = null,
    val articles: List<TrendingArticle> = emptyList(),
    val wikiName: String = "",
)

class TrendingViewModel(
    private val repository: AppRepository,
    private val trendingLoader: TrendingLoader,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendingUiState())
    val state: StateFlow<TrendingUiState> = _state

    private var loadedForWikiId: String? = null

    // If the person switches wikis, through the picker or otherwise,
    // while this expanded screen is still open, this reloads for the
    // newly active one instead of silently continuing to show the
    // previous wiki's trending list.
    init {
        viewModelScope.refreshOnWikiChange(repository.activeWiki, { loadedForWikiId }, ::refresh)
    }

    fun refresh() {
        val wiki = repository.activeWiki.value
        loadedForWikiId = wiki.id
        viewModelScope.launch {
            _state.value = TrendingUiState(isLoading = true, wikiName = wiki.name)
            val result = trendingLoader.load(wiki, limit = EXPANDED_LIMIT)
            if (repository.activeWiki.value.id == wiki.id) {
                _state.value = TrendingUiState(
                    isLoading = false,
                    date = result.date,
                    articles = result.articles,
                    wikiName = wiki.name,
                )
            }
        }
    }
}
