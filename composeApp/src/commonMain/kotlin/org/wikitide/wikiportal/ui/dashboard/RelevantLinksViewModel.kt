package org.wikitide.wikiportal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.RelevantLinksConfig
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.util.refreshOnWikiChange

data class RelevantLinksUiState(
    val titles: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val label: String? = null,
    val wikiName: String = "",
)

class RelevantLinksViewModel(
    private val repository: AppRepository,
    private val api: MediaWikiApi,
) : ViewModel() {

    private val _state = MutableStateFlow(RelevantLinksUiState())
    val state: StateFlow<RelevantLinksUiState> = _state

    private var loadedForWikiId: String? = null

    /** Set by [ensureLoaded], so the wiki-change collector below stays a no-op until this tab has actually been visited once. */
    private var hasBeenRequested = false

    init {
        viewModelScope.refreshOnWikiChange(repository.activeWiki, { loadedForWikiId }) {
            if (hasBeenRequested) refresh()
        }
    }

    /**
     * Loads this tab's content the first time it's actually opened,
     * see DashboardScreen's tabIndex effect. Cheap to call again on
     * every subsequent visit, it only does anything the first time, or
     * after the active wiki has changed since the last load.
     */
    fun ensureLoaded() {
        hasBeenRequested = true
        if (repository.activeWiki.value.id != loadedForWikiId) refresh()
    }

    fun refresh() {
        val wiki = repository.activeWiki.value
        loadedForWikiId = wiki.id
        viewModelScope.launch {
            _state.value = RelevantLinksUiState(isLoading = true, wikiName = wiki.name)
            val source = RelevantLinksConfig.sourceByWikiId[wiki.id]
            val titles = if (source != null) {
                api.getCategoryMembers(wiki, source.category).getOrElse { emptyList() }
            } else {
                api.getRecentChanges(wiki, namespace = 4).getOrElse { emptyList() }.map { it.title }
            }
            if (repository.activeWiki.value.id == wiki.id) {
                _state.value = RelevantLinksUiState(
                    titles = titles,
                    isLoading = false,
                    label = source?.label,
                    wikiName = wiki.name,
                )
            }
        }
    }
}
