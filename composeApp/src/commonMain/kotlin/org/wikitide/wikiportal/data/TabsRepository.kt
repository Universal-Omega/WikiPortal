package org.wikitide.wikiportal.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.WikiSite
import kotlin.random.Random
import kotlin.time.Clock

private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Tracks open reading tabs, similar to a browser's tab strip. This is kept
 * in memory only for now and is not saved across app restarts.
 *
 * Every open tab's WebView stays mounted at the same time inside
 * ArticleHostScreen. See that file's comment for why.
 */
class TabsRepository {

    private val _tabs = MutableStateFlow<List<ArticleTab>>(emptyList())
    val tabs: StateFlow<List<ArticleTab>> = _tabs

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId

    /**
     * Whether the tab switcher overlay is showing. This lives here
     * instead of as local Composable state so the top-level Nav3 onBack
     * handler in App.kt can check it.
     */
    private val _isSwitcherOpen = MutableStateFlow(false)
    val isSwitcherOpen: StateFlow<Boolean> = _isSwitcherOpen

    fun setSwitcherOpen(open: Boolean) {
        _isSwitcherOpen.value = open
    }

    /** Real captured content previews, Android only, keyed by tab id. */
    private val _previews = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val previews: StateFlow<Map<String, ImageBitmap>> = _previews

    /**
     * Each tab registers its own in-page back history handler once, when
     * the tab is created. It is keyed by tab id and stays registered for
     * the tab's whole lifetime. It is not re-registered every time the
     * tab becomes active.
     */
    private val backHandlers = mutableMapOf<String, () -> Boolean>()

    fun registerBackHandler(tabId: String, handler: () -> Boolean) {
        backHandlers[tabId] = handler
    }

    fun unregisterBackHandler(tabId: String) {
        backHandlers.remove(tabId)
    }

    /**
     * Whether the currently active tab has in-page history to go back to
     * right now. This is kept reactive, unlike backHandlers which is a
     * plain map, so App.kt can use it to decide ahead of time whether to
     * intercept the system back gesture before Android's predictive back
     * preview renders. See SystemBackInterceptor. Only the active tab
     * should ever set this. SingleArticleTab does so through
     * setActiveTabCanGoBack whenever its own isActive is true, and this
     * is reset to false on every tab switch, open, or close so a stale
     * true value from whichever tab was previously active can't linger
     * and wrongly intercept a back press for a moment before the newly
     * active tab's own effect has run.
     */
    private val _activeTabCanGoBack = MutableStateFlow(false)
    val activeTabCanGoBack: StateFlow<Boolean> = _activeTabCanGoBack

    fun setActiveTabCanGoBack(canGoBack: Boolean) {
        _activeTabCanGoBack.value = canGoBack
    }

    /**
     * Returns true if the active tab's own in-page history handled the
     * back press and nothing further should happen. Returns false if
     * there was nothing to go back to, meaning the caller should fall
     * through to normal navigation.
     */
    fun tryGoBackInActiveTab(): Boolean {
        val activeId = _activeTabId.value ?: return false
        return backHandlers[activeId]?.invoke() ?: false
    }

    fun openTab(site: WikiSite, title: String): String {
        val id = "tab-${nowEpochMillis()}-${Random.nextInt(10_000)}"
        _tabs.update { it + ArticleTab(id, site.id, site.name, title, createdAtEpochMillis = nowEpochMillis()) }
        _activeTabId.value = id
        _activeTabCanGoBack.value = false
        return id
    }

    fun setActiveTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            _activeTabCanGoBack.value = false
        }
    }

    /**
     * [wikiName] here reflects whatever site the tab's current URL
     * actually matches. This is looked up dynamically and may differ
     * from the site it was originally opened on. Pass an empty string if
     * the current page doesn't match any known wiki, rather than the
     * tab's original site.
     */
    fun updateTab(tabId: String, title: String, thumbnailUrl: String?, wikiName: String, extract: String? = null) {
        _tabs.update { list ->
            list.map {
                if (it.id == tabId) {
                    it.copy(
                        title = title,
                        thumbnailUrl = thumbnailUrl ?: it.thumbnailUrl,
                        wikiName = wikiName,
                        extract = extract ?: it.extract,
                    )
                } else {
                    it
                }
            }
        }
    }

    fun updatePreview(tabId: String, bitmap: ImageBitmap) {
        _previews.update { it + (tabId to bitmap) }
    }

    fun closeTab(tabId: String) {
        val remaining = _tabs.value.filterNot { it.id == tabId }
        _tabs.value = remaining
        _previews.update { it - tabId }
        backHandlers.remove(tabId)
        if (_activeTabId.value == tabId) {
            _activeTabId.value = remaining.lastOrNull()?.id
            _activeTabCanGoBack.value = false
        }
        // If that was the last tab, ArticleHostScreen is about to tear
        // itself down, since tabs.isEmpty() there triggers onBack().
        // Without this, isSwitcherOpen could be left stuck at true, for
        // example if the tab was closed from inside the switcher overlay
        // itself. Then the next tab opened later, a fresh
        // ArticleHostScreen composition, would read that stale flag and
        // render straight into the switcher instead of the article.
        if (remaining.isEmpty()) {
            _isSwitcherOpen.value = false
        }
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _previews.value = emptyMap()
        backHandlers.clear()
        _activeTabId.value = null
        _activeTabCanGoBack.value = false
        _isSwitcherOpen.value = false
    }

    fun tab(tabId: String): ArticleTab? = _tabs.value.firstOrNull { it.id == tabId }

    /**
     * Looks for an already-open tab pointing at the same wiki and title,
     * so callers, for example tapping an article from Dashboard, Search,
     * or Saved, can jump to it instead of opening a duplicate. This
     * matches on the tab's current title, which tracks in-page
     * navigation through updateTab, not just its original opening title,
     * so a tab that has since navigated back to this same article is
     * still found.
     */
    fun findOpenTab(wikiId: String, title: String): ArticleTab? =
        _tabs.value.firstOrNull { it.wikiId == wikiId && it.title == title }
}
