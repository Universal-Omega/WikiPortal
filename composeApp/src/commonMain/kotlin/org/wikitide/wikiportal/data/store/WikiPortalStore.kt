package org.wikitide.wikiportal.data.store

import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.WikiSite

/**
 * A single persistence contract for everything the app needs to
 * remember: settings, the user's custom wiki list, saved articles,
 * history, and offline-saved article content.
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
     * carry the same auto-detected fields, favicon and article path
     * prefix, and user-editable ones, skin, that custom wikis already
     * do. See AppRepository's startup reconciliation for how the saved
     * set is kept in sync with whatever the current app version's
     * preset list is.
     */
    suspend fun allStoredWikis(): List<WikiSite>
    suspend fun upsertWiki(site: WikiSite)
    suspend fun removeWiki(id: String)

    /**
     * Cached lookups of each wiki's actual main page title, keyed by
     * wikiId. This is backed by the generic [Setting] table, with the
     * key "$MAIN_PAGE_TITLE_KEY_PREFIX$wikiId", rather than a dedicated
     * table, since this is cached page content, not part of a wiki's
     * own configuration the way [WikiSite.articlePathPrefix] or
     * [WikiSite.discoveredFaviconUrl] are. This applies to every wiki,
     * presets included, not just custom ones, because it is just as
     * wasteful to re-fetch Wikipedia's main page title on every Dashboard
     * visit as a custom wiki's.
     */
    suspend fun mainPageTitles(): Map<String, String>
    suspend fun setMainPageTitle(wikiId: String, title: String)

    suspend fun savedPages(): List<SavedPage>
    suspend fun isSaved(wikiId: String, title: String): Boolean
    suspend fun toggleSaved(page: SavedPage)

    suspend fun history(limit: Int = 200): List<SavedPage>
    suspend fun recordVisit(page: SavedPage)
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
}

object SettingKeys {
    const val ACTIVE_WIKI_ID = "active_wiki_id"
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val TEXT_SCALE = "text_scale"
    const val SHOW_IMAGES = "show_images"
    const val OPEN_LINKS_EXTERNALLY = "open_links_externally"
}

/**
 * The prefix for the per-wiki main page title cache keys stored in
 * [Setting], see [WikiPortalStore.mainPageTitles]. This is not in
 * [SettingKeys] itself since these aren't single fixed keys but one per
 * wiki id.
 */
const val MAIN_PAGE_TITLE_KEY_PREFIX = "mainpage:"
