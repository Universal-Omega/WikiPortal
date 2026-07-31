package org.wikitide.wikiportal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TrendingLoader
import org.wikitide.wikiportal.network.TrendingArticle

/** How many articles the expanded screen asks for, past the Dashboard card's top 5. */
private const val EXPANDED_LIMIT = 50

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

    init {
        refresh()
    }

    fun refresh() {
        val wiki = repository.activeWiki.value
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
