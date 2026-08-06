package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.db.Folder
import org.wikitide.wikiportal.db.Wiki
import org.wikitide.wikiportal.db.WikiPortalDatabase

/**
 * Wraps the generated SQLDelight [WikiPortalDatabase]. The schema uses
 * generateAsync = true, so SELECT queries return QueryResult.AsyncValue,
 * read with awaitAsList(), awaitAsOne(), or awaitAsOneOrNull(). Generated
 * INSERT, UPDATE, and DELETE functions are already plain suspend funs
 * under generateAsync, so they are just called directly, with no
 * .await() needed on those.
 *
 * Schema setup, creating tables on a fresh database or migrating an
 * existing one, happens per platform, see [ensurePlatformSchemaReady],
 * since Android and iOS can do this more reliably through their own
 * driver constructors, while desktop and web still need it done by
 * hand to keep it lazy. Either way, [ensureSchema] only ever triggers
 * it once, behind a mutex, on first real use.
 */
class SqlDelightWikiPortalStore(
    private val driver: SqlDriver,
    private val offlineFiles: OfflineArticleFileStore,
) : WikiPortalStore {

    // Wiki needs adapters for availableSkins and rank, Folder just for
    // rank. isCustom and skinIsUserSet are typed INTEGER AS Boolean in
    // WikiPortal.sq, which SQLDelight handles natively without one.
    private val database = WikiPortalDatabase(
        driver,
        WikiAdapter = Wiki.Adapter(availableSkinsAdapter = SkinOptionListColumnAdapter, rankAdapter = RankColumnAdapter),
        FolderAdapter = Folder.Adapter(rankAdapter = RankColumnAdapter),
    )

    private val queries = database.wikiPortalQueries
    private val offlineArticleStore = OfflineArticleStore(queries, offlineFiles)

    private val schemaMutex = Mutex()
    private var schemaReady = false

    /**
     * Delegates to [ensurePlatformSchemaReady] and only ever runs
     * that once per store instance.
     */
    private suspend fun ensureSchema() {
        if (schemaReady) return
        schemaMutex.withLock {
            if (schemaReady) return@withLock
            ensurePlatformSchemaReady(driver)
            schemaReady = true
        }
    }

    override suspend fun getSetting(key: String): String? {
        ensureSchema()
        return queries.getSetting(key).awaitAsOneOrNull()
    }

    override suspend fun setSetting(key: String, value: String) {
        ensureSchema()
        queries.upsertSetting(key, value)
    }

    override suspend fun allStoredWikis(): List<WikiSite> {
        ensureSchema()
        return queries.selectAllWikis().awaitAsList().map {
            WikiSite(
                id = it.id,
                name = it.name,
                description = it.description,
                baseUrl = it.baseUrl,
                scriptPath = it.scriptPath,
                skin = it.skin,
                isCustom = it.isCustom,
                articlePathPrefix = it.articlePathPrefix,
                discoveredFaviconUrl = it.faviconUrl,
                availableSkins = it.availableSkins,
                skinIsUserSet = it.skinIsUserSet,
                mainPageTitle = it.mainPageTitle,
                mainPageIsDomainRoot = it.mainPageIsDomainRoot,
                folderId = it.folderId,
                disableSafeMode = it.disableSafeMode,
                rank = it.rank,
            )
        }
    }

    override suspend fun upsertWiki(site: WikiSite) {
        ensureSchema()
        queries.upsertWiki(
            site.id, site.name, site.description, site.baseUrl, site.scriptPath, site.skin,
            site.articlePathPrefix, site.discoveredFaviconUrl, site.isCustom, site.availableSkins, site.skinIsUserSet,
            site.mainPageTitle, site.folderId, site.mainPageIsDomainRoot, site.disableSafeMode, site.rank,
        )
    }

    override suspend fun removeWiki(id: String) {
        ensureSchema()
        queries.deleteWiki(id)
    }

    override suspend fun allFolders(): List<WikiFolder> {
        ensureSchema()
        return queries.selectAllFolders().awaitAsList().map {
            WikiFolder(id = it.id, name = it.name, isCustom = true, rank = it.rank)
        }
    }

    override suspend fun upsertFolder(folder: WikiFolder) {
        ensureSchema()
        queries.upsertFolder(folder.id, folder.name, folder.rank)
    }

    override suspend fun removeFolder(id: String) {
        ensureSchema()
        queries.deleteFolder(id)
    }

    override suspend fun savedPages(): List<SavedPage> {
        ensureSchema()
        return queries.selectAllSavedPages().awaitAsList().map {
            SavedPage(
                wikiId = it.wikiId,
                wikiName = it.wikiName,
                title = it.title,
                extract = it.extract,
                thumbnailUrl = it.thumbnailUrl,
                timestampEpochMillis = it.savedAtEpochMillis,
                url = it.url,
            )
        }
    }

    override suspend fun isSaved(wikiId: String, title: String): Boolean {
        ensureSchema()
        return queries.isSaved(wikiId, title).awaitAsOne()
    }

    override suspend fun toggleSaved(page: SavedPage) {
        ensureSchema()
        if (queries.isSaved(page.wikiId, page.title).awaitAsOne()) {
            queries.deleteSavedPage(page.wikiId, page.title)
        } else {
            queries.upsertSavedPage(
                page.wikiId, page.wikiName, page.title, page.extract, page.thumbnailUrl, page.timestampEpochMillis, page.url,
            )
        }
    }

    override suspend fun history(limit: Int): List<SavedPage> {
        ensureSchema()
        return queries.selectRecentHistory(limit.toLong()).awaitAsList().map {
            SavedPage(
                wikiId = it.wikiId,
                wikiName = it.wikiName,
                title = it.title,
                extract = it.extract,
                thumbnailUrl = it.thumbnailUrl,
                timestampEpochMillis = it.visitedAtEpochMillis,
                url = it.url,
            )
        }
    }

    override suspend fun recordVisit(page: SavedPage) {
        ensureSchema()
        queries.upsertHistoryEntry(page.wikiId, page.wikiName, page.title, page.extract, page.thumbnailUrl, page.timestampEpochMillis, page.url)
    }

    override suspend fun removeHistoryEntry(wikiId: String, title: String) {
        ensureSchema()
        queries.deleteHistoryEntry(wikiId, title)
    }

    override suspend fun clearHistory() {
        ensureSchema()
        queries.clearHistory()
    }

    override suspend fun saveOfflineArticle(page: SavedPage, html: String) {
        ensureSchema()
        offlineArticleStore.save(page, html)
    }

    override suspend fun getOfflineArticleHtml(wikiId: String, title: String): String? {
        ensureSchema()
        return offlineArticleStore.getHtml(wikiId, title)
    }

    override suspend fun removeOfflineArticle(wikiId: String, title: String) {
        ensureSchema()
        offlineArticleStore.remove(wikiId, title)
    }

    override suspend fun offlineArticleKeys(): Set<String> {
        ensureSchema()
        return offlineArticleStore.keys()
    }

    override suspend fun offlineArticles(): List<SavedPage> {
        ensureSchema()
        return offlineArticleStore.all()
    }

    override suspend fun openTabs(): List<ArticleTab> {
        ensureSchema()
        return queries.selectAllOpenTabs().awaitAsList().map {
            ArticleTab(
                id = it.id,
                wikiId = it.wikiId,
                wikiName = it.wikiName,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                extract = it.extract,
                createdAtEpochMillis = it.createdAtEpochMillis,
                currentUrl = it.currentUrl,
                openedFromOffline = it.openedFromOffline,
            )
        }
    }

    override suspend fun upsertOpenTab(tab: ArticleTab) {
        ensureSchema()
        queries.upsertOpenTab(
            tab.id, tab.wikiId, tab.wikiName, tab.title, tab.thumbnailUrl, tab.extract, tab.createdAtEpochMillis, tab.currentUrl,
            tab.openedFromOffline,
        )
    }

    override suspend fun deleteOpenTab(id: String) {
        ensureSchema()
        queries.deleteOpenTab(id)
    }

    override suspend fun clearOpenTabs() {
        ensureSchema()
        queries.clearOpenTabs()
    }
}
