package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
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
 * .await() needed on those. .await() is only for schema creation and
 * migration, which go through the lower-level SqlSchema.create(driver)
 * and SqlSchema.migrate(driver, old, new), both of which return
 * QueryResult.AsyncValue<Unit>.
 *
 * Schema setup happens lazily, on first use, rather than being
 * choreographed across four different driver-construction call sites,
 * one per platform. See [ensureSchema].
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
    private val offlineArticleStore = OfflineArticleStore(queries, offlineFiles)

    private val schemaMutex = Mutex()
    private var schemaReady = false

    /**
     * A fresh database, [queries.wikiTableExists] false, gets
     * [WikiPortalDatabase.Schema.create], which builds the latest
     * schema directly, nothing to migrate yet.
     *
     * An existing database gets [WikiPortalDatabase.Schema.migrate]
     * instead, starting from whatever PRAGMA user_version currently
     * reads. That pragma is SQLite's own field for exactly this,
     * stored in the database file's header rather than in any table
     * of ours, so there's nothing here for a future schema change to
     * accidentally disturb. A database that predates this versioning
     * setup reads 0 there too, same as a genuinely empty one, which is
     * the one thing the pragma alone can't tell apart, hence still
     * checking wikiTableExists first. SQLDelight's own numbering
     * starts at 1, so that case treats 0 as 1, migrations/1.sqm is
     * what brings it up to date from there.
     *
     * Either way, the resulting version is written back to the same
     * pragma before returning, so later launches skip straight past
     * both branches.
     */
    private suspend fun ensureSchema() {
        if (schemaReady) return
        schemaMutex.withLock {
            if (schemaReady) return@withLock
            val latestVersion = WikiPortalDatabase.Schema.version
            if (queries.wikiTableExists().awaitAsOne()) {
                val currentVersion = driver.readUserVersion().takeIf { it > 0L } ?: 1L
                if (currentVersion < latestVersion) {
                    WikiPortalDatabase.Schema.migrate(driver, currentVersion, latestVersion).await()
                }
            } else {
                WikiPortalDatabase.Schema.create(driver).await()
            }
            driver.writeUserVersion(latestVersion)
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
            )
        }
    }

    override suspend fun upsertWiki(site: WikiSite) {
        ensureSchema()
        queries.upsertWiki(
            site.id, site.name, site.description, site.baseUrl, site.scriptPath, site.skin,
            site.articlePathPrefix, site.discoveredFaviconUrl, site.isCustom, site.availableSkins, site.skinIsUserSet,
            site.mainPageTitle, site.folderId, site.mainPageIsDomainRoot,
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

/** Reads SQLite's own schema version field. 0 if it was never set. */
private suspend fun SqlDriver.readUserVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.AsyncValue { if (cursor.next().await()) cursor.getLong(0) ?: 0L else 0L } },
        parameters = 0,
    ).await()

/**
 * PRAGMA doesn't support bind parameters for the value in SQLite, so
 * this has to inline the number into the SQL text directly rather than
 * passing it as a `?`. That's fine here since it only ever comes from
 * WikiPortalDatabase.Schema.version, never from anything a person typed.
 */
private suspend fun SqlDriver.writeUserVersion(version: Long) {
    execute(identifier = null, sql = "PRAGMA user_version = $version", parameters = 0).await()
}
