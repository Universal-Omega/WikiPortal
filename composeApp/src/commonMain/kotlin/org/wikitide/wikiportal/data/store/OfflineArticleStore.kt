package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.db.WikiPortalQueries

class OfflineArticleStore(
    private val queries: WikiPortalQueries,
    private val files: OfflineArticleFileStore,
) {
    suspend fun save(page: SavedPage, html: String) {
        val fileName = offlineArticleFileName(page.wikiId, page.title)
        files.write(fileName, html)
        queries.upsertOfflineArticle(page.wikiId, page.wikiName, page.title, page.thumbnailUrl, fileName, page.timestampEpochMillis)
    }

    suspend fun getHtml(wikiId: String, title: String): String? {
        val fileName = queries.getOfflineArticleFileName(wikiId, title).awaitAsOneOrNull() ?: return null
        return files.read(fileName)
    }

    suspend fun remove(wikiId: String, title: String) {
        val fileName = queries.getOfflineArticleFileName(wikiId, title).awaitAsOneOrNull()
        queries.deleteOfflineArticle(wikiId, title)
        if (fileName != null) files.delete(fileName)
    }

    suspend fun keys(): Set<String> =
        queries.selectAllOfflineArticleKeys().awaitAsList().map { "${it.wikiId}|${it.title}" }.toSet()

    suspend fun all(): List<SavedPage> =
        queries.selectAllOfflineArticles().awaitAsList().map {
            SavedPage(
                wikiId = it.wikiId,
                wikiName = it.wikiName,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                timestampEpochMillis = it.savedAtEpochMillis,
            )
        }
}
