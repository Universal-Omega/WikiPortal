package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.await
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
import org.wikitide.wikiportal.db.Wiki
import org.wikitide.wikiportal.db.WikiPortalDatabase

/**
 * Wraps the generated SQLDelight [WikiPortalDatabase]. The schema uses
 * generateAsync = true, so SELECT queries return QueryResult.AsyncValue,
 * read with awaitAsList(), awaitAsOne(), or awaitAsOneOrNull(). Generated
 * INSERT, UPDATE, and DELETE functions are already plain suspend funs
 * under generateAsync, so they are just called directly, with no
 * .await() needed on those. .await() is only for schema creation, which
 * goes through the lower-level SqlSchema.create(driver), which returns
 * QueryResult.AsyncValue<Unit>.
 *
 * Schema creation is lazy and idempotent, using CREATE TABLE IF NOT
 * EXISTS, so it is simplest to just do it here on first use rather than
 * choreograph it across four different driver-construction call sites.
 */
class SqlDelightWikiPortalStore(
    private val driver: SqlDriver,
    private val offlineFiles: OfflineArticleFileStore,
) : WikiPortalStore {

    // WikiAdapter only needs an entry for availableSkins. isCustom and
    // skinIsUserSet are typed INTEGER AS Boolean in WikiPortal.sq, which
    // SQLDelight handles natively without a ColumnAdapter.
    private val database = WikiPortalDatabase(driver, WikiAdapter = Wiki.Adapter(availableSkinsAdapter = SkinOptionListColumnAdapter))
    private val queries = database.wikiPortalQueries

    private val schemaMutex = Mutex()
    private var schemaReady = false

    private suspend fun ensureSchema() {
        if (schemaReady) return
        schemaMutex.withLock {
            if (!schemaReady) {
                WikiPortalDatabase.Schema.create(driver).await()
                schemaReady = true
            }
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
                folderId = it.folderId,
            )
        }
    }

    override suspend fun upsertWiki(site: WikiSite) {
        ensureSchema()
        queries.upsertWiki(
            site.id, site.name, site.description, site.baseUrl, site.scriptPath, site.skin,
            site.articlePathPrefix, site.discoveredFaviconUrl, site.isCustom, site.availableSkins, site.skinIsUserSet,
            site.mainPageTitle, site.folderId,
        )
    }

    override suspend fun removeWiki(id: String) {
        ensureSchema()
        queries.deleteWiki(id)
    }

    override suspend fun allFolders(): List<WikiFolder> {
        ensureSchema()
        return queries.selectAllFolders().awaitAsList().map {
            WikiFolder(id = it.id, name = it.name, isCustom = true)
        }
    }

    override suspend fun upsertFolder(folder: WikiFolder, sortOrder: Int) {
        ensureSchema()
        queries.upsertFolder(folder.id, folder.name, sortOrder.toLong())
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

    override suspend fun clearHistory() {
        ensureSchema()
        queries.clearHistory()
    }

    override suspend fun saveOfflineArticle(page: SavedPage, html: String) {
        ensureSchema()
        val fileName = offlineArticleFileName(page.wikiId, page.title)
        offlineFiles.write(fileName, html)
        queries.upsertOfflineArticle(page.wikiId, page.wikiName, page.title, page.thumbnailUrl, fileName, page.timestampEpochMillis)
    }

    override suspend fun getOfflineArticleHtml(wikiId: String, title: String): String? {
        ensureSchema()
        val fileName = queries.getOfflineArticleHtml(wikiId, title).awaitAsOneOrNull() ?: return null
        return offlineFiles.read(fileName)
    }

    override suspend fun removeOfflineArticle(wikiId: String, title: String) {
        ensureSchema()
        val fileName = queries.getOfflineArticleHtml(wikiId, title).awaitAsOneOrNull()
        queries.deleteOfflineArticle(wikiId, title)
        if (fileName != null) offlineFiles.delete(fileName)
    }

    override suspend fun offlineArticleKeys(): Set<String> {
        ensureSchema()
        return queries.selectAllOfflineArticleKeys().awaitAsList().map { "${it.wikiId}|${it.title}" }.toSet()
    }

    override suspend fun offlineArticles(): List<SavedPage> {
        ensureSchema()
        return queries.selectAllOfflineArticles().awaitAsList().map {
            SavedPage(
                wikiId = it.wikiId,
                wikiName = it.wikiName,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                timestampEpochMillis = it.savedAtEpochMillis,
            )
        }
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
