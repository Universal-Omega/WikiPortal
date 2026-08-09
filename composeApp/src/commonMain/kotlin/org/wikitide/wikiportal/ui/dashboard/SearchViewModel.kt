package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.network.SearchResults

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<PageSummaryDto> = emptyList(),
    val suggestion: String? = null,
    val rewrittenQuery: String? = null,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val showImages: Boolean = true,
    val wikiId: String = "",
    val wikiName: String = "",
)

private data class SearchQueryState(
    val query: String,
    val results: List<PageSummaryDto>,
    val suggestion: String?,
    val rewrittenQuery: String?,
    val isSearching: Boolean,
)

private data class SearchContextState(
    val hasSearched: Boolean,
    val showImages: Boolean,
    val wiki: WikiSite,
)

private const val KEY_QUERY = "search_query"

class SearchViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
    private val _results = MutableStateFlow<List<PageSummaryDto>>(emptyList())
    private val _suggestion = MutableStateFlow<String?>(null)
    private val _rewrittenQuery = MutableStateFlow<String?>(null)
    private val _isSearching = MutableStateFlow(false)
    private val _hasSearched = MutableStateFlow(false)

    val state: StateFlow<SearchUiState> = combine(
        combine(
            _query,
            _results,
            _suggestion,
            _rewrittenQuery,
            _isSearching,
        ) { query, results, suggestion, rewrittenQuery, isSearching ->
            SearchQueryState(query, results, suggestion, rewrittenQuery, isSearching)
        },
        combine(_hasSearched, repository.showImages, repository.activeWiki) { hasSearched, showImages, wiki ->
            SearchContextState(hasSearched, showImages, wiki)
        },
    ) { queryState, context ->
        SearchUiState(
            query = queryState.query,
            results = queryState.results,
            suggestion = queryState.suggestion,
            rewrittenQuery = queryState.rewrittenQuery,
            isSearching = queryState.isSearching,
            hasSearched = context.hasSearched,
            showImages = context.showImages,
            wikiId = context.wiki.id,
            wikiName = context.wiki.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private var searchJob: Job? = null

    // Tracks which wiki the current results actually belong to, so a wiki
    // switch elsewhere in the app, for example the wiki picker, can be
    // told apart from this ViewModel's own initial startup value below.
    private var trackedWikiId: String? = null

    init {
        if (_query.value.isNotBlank()) runSearch(_query.value)
        viewModelScope.launch {
            repository.activeWiki.collect { wiki ->
                val previous = trackedWikiId
                trackedWikiId = wiki.id
                // Re-run whatever is currently typed against the newly
                // active wiki, so results never linger tagged to a wiki
                // the person has since switched away from. The very
                // first emission here is just the starting wiki, not a
                // real switch, so it's skipped.
                if (previous != null && previous != wiki.id && _query.value.isNotBlank()) {
                    searchJob?.cancel()
                    runSearch(_query.value)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
        _query.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            _suggestion.value = null
            _rewrittenQuery.value = null
            _hasSearched.value = false
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // this debounces so we don't hammer the API on every keystroke
            runSearch(query)
        }
    }

    /**
     * Re-runs the search using the suggested or rewritten query, for
     * example after the user taps a "did you mean" prompt.
     */
    fun searchFor(query: String) {
        savedStateHandle[KEY_QUERY] = query
        _query.value = query
        searchJob?.cancel()
        runSearch(query)
    }

    private fun runSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            val results = api.search(repository.activeWiki.value, query).getOrElse { SearchResults(emptyList()) }
            _results.value = results.pages
            _suggestion.value = results.suggestion
            _rewrittenQuery.value = results.rewrittenQuery
            _isSearching.value = false
            _hasSearched.value = true
        }
    }
}
