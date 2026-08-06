package org.wikitide.wikiportal.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.data.store.SettingKeys
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.util.nowEpochMillis
import kotlin.random.Random

/**
 * Tracks open reading tabs, similar to a browser's tab strip. Each tab's
 * own row, see [store]'s OpenTab table, is upserted or deleted
 * individually as tabs open, navigate, and close, and the whole set is
 * loaded back at startup, see [init], so restarting the app doesn't lose
 * open tabs. Which tab was active is saved separately as a single
 * setting, the same way AppRepository saves the active wiki id. Real
 * captured preview bitmaps, see [_previews], are not part of any of that
 * and start out empty again each launch. That's an in-memory-only visual
 * nicety, not the tab's identity, and are quick to recapture as each
 * restored tab is actually viewed again.
 *
 * Every open tab's WebView stays mounted at the same time inside
 * ArticleHostScreen. See that file's comment for why.
 */
class TabsRepository(
    private val store: WikiPortalStore,
    private val appScope: CoroutineScope,
) {

    private val _tabs = MutableStateFlow<List<ArticleTab>>(emptyList())
    val tabs: StateFlow<List<ArticleTab>> = _tabs

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId

    /**
     * Tabs that have actually been selected at least once this app
     * session, meaning ArticleHostScreen should give them a real,
     * mounted WebView. See ArticleHostScreen's render loop. This is
     * deliberately not persisted and starts empty every launch: a
     * restored tab list from a previous session, potentially a couple
     * dozen tabs, has no business all spinning up their own WebView and
     * firing off network requests the moment the person opens any one of
     * them. A tab only materializes once it's the one actually tapped,
     * in TabsListScreen, TabsScreen, or by name from Dashboard or Saved.
     */
    private val _materializedTabIds = MutableStateFlow<Set<String>>(emptySet())
    val materializedTabIds: StateFlow<Set<String>> = _materializedTabIds

    init {
        appScope.launch {
            val restoredTabs = store.openTabs()
            val restoredActiveId = store.getSetting(SettingKeys.ACTIVE_TAB_ID)?.takeIf { it.isNotBlank() }
            // Only apply this if nothing has already opened a tab in the
            // moments since this repository was constructed, so a very
            // early openTab() call can't get wiped out by this finishing
            // its disk read afterward.
            if (_tabs.value.isEmpty()) {
                _tabs.value = restoredTabs
                _activeTabId.value = restoredTabs.firstOrNull { it.id == restoredActiveId }?.id ?: restoredTabs.lastOrNull()?.id
            }
        }
    }

    private fun persistActiveTabId(id: String?) {
        appScope.launch { store.setSetting(SettingKeys.ACTIVE_TAB_ID, id.orEmpty()) }
    }

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

    fun openTab(site: WikiSite, title: String, openedFromOffline: Boolean = false): String {
        val id = "tab-${nowEpochMillis()}-${Random.nextInt(10_000)}"
        val tab = ArticleTab(id, site.id, site.name, title, createdAtEpochMillis = nowEpochMillis(), openedFromOffline = openedFromOffline)
        insertNewTab(tab)
        return id
    }

    /**
     * Opens a tab pointed straight at [url] rather than an article
     * title, for a link that isn't necessarily this wiki's own article
     * URL shape, for example a target="_blank" link followed from
     * within another tab. [site] is the wiki [url] itself actually
     * belongs to, resolved by the caller, not necessarily whichever tab
     * the link was clicked from. Null means [url] doesn't match any
     * saved wiki at all, in which case the new tab opens ungrouped
     * rather than being attributed to the source tab's wiki.
     */
    fun openTabForUrl(site: WikiSite?, url: String, title: String): String {
        val id = "tab-${nowEpochMillis()}-${Random.nextInt(10_000)}"
        val tab = ArticleTab(id, site?.id.orEmpty(), site?.name.orEmpty(), title, createdAtEpochMillis = nowEpochMillis(), currentUrl = url)
        insertNewTab(tab)
        return id
    }

    private fun insertNewTab(tab: ArticleTab) {
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        _activeTabCanGoBack.value = false
        _materializedTabIds.update { it + tab.id }
        appScope.launch { store.upsertOpenTab(tab) }
        persistActiveTabId(tab.id)
    }

    fun setActiveTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            _activeTabCanGoBack.value = false
            _materializedTabIds.update { it + tabId }
            persistActiveTabId(tabId)
        }
    }

    /**
     * [wikiName] here reflects whatever site the tab's current URL
     * actually matches. This is looked up dynamically and may differ
     * from the site it was originally opened on. Pass an empty string if
     * the current page doesn't match any known wiki, rather than the
     * tab's original site.
     */
    fun updateTab(
        tabId: String,
        title: String,
        thumbnailUrl: String?,
        wikiName: String,
        extract: String? = null,
        currentUrl: String? = null,
        clearSummary: Boolean = false,
    ) {
        var updatedTab: ArticleTab? = null
        _tabs.update { list ->
            list.map {
                if (it.id == tabId) {
                    it.copy(
                        title = title,
                        thumbnailUrl = if (clearSummary) null else thumbnailUrl ?: it.thumbnailUrl,
                        wikiName = wikiName,
                        extract = if (clearSummary) null else extract ?: it.extract,
                        currentUrl = currentUrl ?: it.currentUrl,
                    ).also { updated -> updatedTab = updated }
                } else {
                    it
                }
            }
        }
        updatedTab?.let { tab -> appScope.launch { store.upsertOpenTab(tab) } }
    }

    fun updatePreview(tabId: String, bitmap: ImageBitmap) {
        _previews.update { it + (tabId to bitmap) }
    }

    fun closeTab(tabId: String) {
        val remaining = _tabs.value.filterNot { it.id == tabId }
        _tabs.value = remaining
        _previews.update { it - tabId }
        _materializedTabIds.update { it - tabId }
        backHandlers.remove(tabId)
        appScope.launch { store.deleteOpenTab(tabId) }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = remaining.lastOrNull()?.id
            _activeTabCanGoBack.value = false
            persistActiveTabId(_activeTabId.value)
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
        _materializedTabIds.value = emptySet()
        backHandlers.clear()
        _activeTabId.value = null
        _activeTabCanGoBack.value = false
        _isSwitcherOpen.value = false
        appScope.launch { store.clearOpenTabs() }
        persistActiveTabId(null)
    }

    fun tab(tabId: String): ArticleTab? = _tabs.value.firstOrNull { it.id == tabId }

    /**
     * Looks for an already-open tab pointing at the same wiki, title,
     * and viewing mode, so callers, for example tapping an article
     * from Dashboard, Search, Saved, or Offline, can jump to it instead
     * of opening a duplicate. This matches on the tab's current title,
     * which tracks in-page navigation through updateTab, not just its
     * original opening title, so a tab that has since navigated back
     * to this same article is still found.
     *
     * [openedFromOffline] is part of the match, not just wikiId and
     * title. A tab already open live and a tab already open offline
     * for the same title are two different things, not one tab that
     * gets mutated depending on whichever click happened to land on it
     * last. Tapping Saved for a title that's already open offline in
     * some other tab opens or switches to a separate live tab, and
     * tapping Offline for a title that's already open live does the
     * same in reverse, rather than either one silently taking over
     * whatever tab already existed.
     */
    fun findOpenTab(wikiId: String, title: String, openedFromOffline: Boolean): ArticleTab? =
        _tabs.value.firstOrNull { it.wikiId == wikiId && it.title == title && it.openedFromOffline == openedFromOffline }
}
