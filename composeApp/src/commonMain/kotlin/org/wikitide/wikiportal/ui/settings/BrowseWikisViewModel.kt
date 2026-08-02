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

    /** null means every language, otherwise one of IndieWikiSite.language's codes, see IndieWikiLanguages. */
    private val _languageFilter = MutableStateFlow<String?>(null)
    val languageFilter: StateFlow<String?> = _languageFilter

    private val _officialOnly = MutableStateFlow(false)
    val officialOnly: StateFlow<Boolean> = _officialOnly

    init {
        viewModelScope.launch { directory.ensureLoaded() }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLanguageFilter(language: String?) {
        _languageFilter.value = language
    }

    fun setOfficialOnly(officialOnly: Boolean) {
        _officialOnly.value = officialOnly
    }

    fun refresh() {
        viewModelScope.launch { directory.refresh() }
    }
}
