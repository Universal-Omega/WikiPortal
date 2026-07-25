package org.wikitide.wikiportal.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.RelevantLinksConfig
import org.wikitide.wikiportal.network.MediaWikiApi

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
            _state.value = RelevantLinksUiState(isLoading = true, wikiName = wiki.name)
            val source = RelevantLinksConfig.sourceByWikiId[wiki.id]
            val result = if (source != null) {
                api.getCategoryMembers(wiki, source.category)
            } else {
                api.getProjectNamespaceActivity(wiki)
            }
            val titles = result.getOrElse { emptyList() }
            _state.value = RelevantLinksUiState(
                titles = titles,
                isLoading = false,
                label = source?.label,
                wikiName = wiki.name,
            )
        }
    }
}
