package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.runtime.Immutable
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
import org.wikitide.wikiportal.network.MediaWikiApi

@Immutable
data class CategoryBrowseUiState(
    val query: String = "",
    val matches: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val selectedCategory: String? = null,
    val members: List<String> = emptyList(),
    val isLoadingMembers: Boolean = false,
    val wikiId: String = "",
    val wikiName: String = "",
)

private data class SearchState(val query: String, val matches: List<String>, val isSearching: Boolean)
private data class SelectionState(
    val selectedCategory: String?,
    val members: List<String>,
    val isLoadingMembers: Boolean,
)

class CategoryBrowseViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _matches = MutableStateFlow<List<String>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _members = MutableStateFlow<List<String>>(emptyList())
    private val _isLoadingMembers = MutableStateFlow(false)

    val state: StateFlow<CategoryBrowseUiState> = combine(
        combine(
            _query,
            _matches,
            _isSearching
        ) { query, matches, isSearching -> SearchState(query, matches, isSearching) },
        combine(
            _selectedCategory,
            _members,
            _isLoadingMembers
        ) { selected, members, loading -> SelectionState(selected, members, loading) },
        repository.activeWiki,
    ) { search, selection, wiki ->
        CategoryBrowseUiState(
            query = search.query,
            matches = search.matches,
            isSearching = search.isSearching,
            selectedCategory = selection.selectedCategory,
            members = selection.members,
            isLoadingMembers = selection.isLoadingMembers,
            wikiId = wiki.id,
            wikiName = wiki.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryBrowseUiState())

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _query.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _matches.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounces so typing doesn't hammer the API on every keystroke
            _isSearching.value = true
            _matches.value = api.searchCategories(repository.activeWiki.value, query).getOrElse { emptyList() }
            _isSearching.value = false
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        viewModelScope.launch {
            _isLoadingMembers.value = true
            _members.value = api.getCategoryMembers(repository.activeWiki.value, category).getOrElse { emptyList() }
            _isLoadingMembers.value = false
        }
    }

    fun clearSelection() {
        _selectedCategory.value = null
        _members.value = emptyList()
    }
}
