package org.wikitide.wikiportal.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository

class Navigator(
    private val repository: AppRepository,
    private val tabsRepository: TabsRepository,
) {
    lateinit var backStack: NavBackStack<NavKey>

    fun navigateTo(route: Route) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun switchTab(route: Route) {
        val survivors = backStack.filter { it in tabRoutes && it != route }
        backStack.clear()
        backStack.addAll(survivors + route)
    }

    /**
     * Opens a reading tab for wikiId and title, then makes sure we are on
     * the Article destination. If that article is already open in another
     * tab, found through findOpenTab, we just switch to that tab instead
     * of opening a duplicate, the same way clicking an already-open tab
     * works in a browser. A new tab is only created when no existing tab
     * matches. ArticleRoute is a singleton, so navigateTo does nothing if
     * we are already there.
     *
     * [openedFromOffline] only ever applies to a genuinely new tab. An
     * existing tab found through findOpenTab keeps whatever it already
     * committed to when it was first opened, rather than switching
     * partway through whatever a later, unrelated click happened to ask
     * for.
     */
    fun openArticle(wikiId: String, title: String, openedFromOffline: Boolean = false) {
        val site = repository.allWikisNow().firstOrNull { it.id == wikiId } ?: repository.activeWiki.value
        val existing = tabsRepository.findOpenTab(wikiId, title)
        if (existing != null) {
            tabsRepository.setActiveTab(existing.id)
        } else {
            tabsRepository.openTab(site, title, openedFromOffline)
        }
        navigateTo(ArticleRoute)
    }

    // The order here matters. First we close the tab switcher overlay if
    // it is open. Then we let the active tab's own in-page history handle
    // the back press, through tryGoBackInActiveTab.
    fun handleBack() {
        if (tabsRepository.isSwitcherOpen.value) {
            tabsRepository.setSwitcherOpen(false)
        } else if (tabsRepository.tryGoBackInActiveTab()) {
            // The active tab's own history already handled this.
        } else {
            backStack.removeLastOrNull()
        }
    }
}
