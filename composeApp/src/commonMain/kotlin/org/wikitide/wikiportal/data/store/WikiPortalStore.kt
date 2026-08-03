package org.wikitide.wikiportal.data.store

import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite

/**
 * A single persistence contract for everything the app needs to
 * remember: settings, the user's custom wiki list, saved articles,
 * history, offline-saved article content, and open reading tabs.
 *
 * This is backed by SQLDelight everywhere. Android and JVM use their
 * normal synchronous drivers, and wasmJs uses the async Web
 * Worker driver, SQLite compiled to WASM through sql.js. See
 * [SqlDelightWikiPortalStore] and the per-platform
 * platformModule() implementations for how the driver
 * is constructed.
 */
interface WikiPortalStore {
    suspend fun getSetting(key: String): String?
    suspend fun setSetting(key: String, value: String)

    /**
     * All saved wiki rows, both wikis the person added by URL,
     * [WikiSite.isCustom] true, and saved preset rows, [isCustom] false,
     * seeded from
     * [PresetWikis]'s hardcoded
     * defaults on first run. Presets get a saved row too so they can
     * carry the same auto-detected fields, favicon, article path
     * prefix, and main page title, and user-editable ones, skin, that
     * custom wikis already do. See AppRepository's startup
     * reconciliation for how the saved set is kept in sync with
     * whatever the current app version's preset list is.
     */
    suspend fun allStoredWikis(): List<WikiSite>
    suspend fun upsertWiki(site: WikiSite)
    suspend fun removeWiki(id: String)

    /**
     * Folders the person created themselves to organize their custom
     * wikis, ordered the way they were created in. This does not
     * include [PresetFolders], which are not stored rows at all, just
     * fixed objects the app already knows about. Which wiki sits in
     * which folder is not stored here either. That lives on the wiki
     * row itself, see [WikiSite.folderId].
     */
    suspend fun allFolders(): List<WikiFolder>
    suspend fun upsertFolder(folder: WikiFolder, sortOrder: Int)
    suspend fun removeFolder(id: String)

    suspend fun savedPages(): List<SavedPage>
    suspend fun isSaved(wikiId: String, title: String): Boolean
    suspend fun toggleSaved(page: SavedPage)

    suspend fun history(limit: Int = 200): List<SavedPage>
    suspend fun recordVisit(page: SavedPage)
    suspend fun removeHistoryEntry(wikiId: String, title: String)
    suspend fun clearHistory()

    /**
     * Raw parsed article HTML, already wrapped as a document, see
     * buildOfflineHtmlDocument, stored so the article can be reopened
     * without a network connection. This is kept separate from
     * [savedPages] on purpose. Not every bookmark needs a multi hundred
     * kilobyte HTML blob attached, only ones the user explicitly
     * downloaded.
     */
    suspend fun saveOfflineArticle(page: SavedPage, html: String)
    suspend fun getOfflineArticleHtml(wikiId: String, title: String): String?
    suspend fun removeOfflineArticle(wikiId: String, title: String)
    suspend fun offlineArticleKeys(): Set<String>
    suspend fun offlineArticles(): List<SavedPage>

    suspend fun openTabs(): List<ArticleTab>
    suspend fun upsertOpenTab(tab: ArticleTab)
    suspend fun deleteOpenTab(id: String)
    suspend fun clearOpenTabs()
}

object SettingKeys {
    const val ACTIVE_WIKI_ID = "active_wiki_id"
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val TEXT_SCALE = "text_scale"
    const val SHOW_IMAGES = "show_images"
    const val OPEN_LINKS_EXTERNALLY = "open_links_externally"
    const val CONFIRM_EXTERNAL_NAVIGATION = "confirm_external_navigation"
    const val DISABLE_SAFE_MODE = "disable_safe_mode"
    const val OPEN_BLANK_IN_NEW_TAB = "open_blank_in_new_tab"
    const val INDIE_WIKI_SUGGESTIONS_ENABLED = "indie_wiki_suggestions_enabled"
    const val INDIE_WIKI_CACHE = "indie_wiki_cache"
    const val INDIE_WIKI_CACHE_UPDATED_AT = "indie_wiki_cache_updated_at"

    /** Which open tab, see [WikiPortalStore.openTabs], was active. */
    const val ACTIVE_TAB_ID = "active_tab_id"
}
