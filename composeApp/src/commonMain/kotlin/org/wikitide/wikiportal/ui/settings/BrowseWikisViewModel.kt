package org.wikitide.wikiportal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.IndieWikiDirectory
import org.wikitide.wikiportal.data.model.IndieWikiSite

class BrowseWikisViewModel(
    private val directory: IndieWikiDirectory,
) : ViewModel() {

    val sites: StateFlow<List<IndieWikiSite>> = directory.sites
    val isRefreshing: StateFlow<Boolean> = directory.isRefreshing

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        viewModelScope.launch { directory.ensureLoaded() }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch { directory.refresh() }
    }
}
