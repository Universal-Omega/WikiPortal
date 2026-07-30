package org.wikitide.wikiportal.util

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.SkinOptionListColumnAdapter
import org.wikitide.wikiportal.db.Wiki
import org.wikitide.wikiportal.db.WikiPortalDatabase

/**
 * Backed by the OfflineArticleFile table, not real files: the web
 * target's sql.js driver runs entirely in a Web Worker with no
 * filesystem access to speak of, but it also has none of Android
 * Cursor's row-size ceiling that made real files necessary there in
 * the first place, see OfflineArticleFileStore's own doc comment, so
 * a plain table is a perfectly safe backing store here.
 *
 * A second, lightweight WikiPortalDatabase instance over the same
 * driver SqlDelightWikiPortalStore itself uses. SQLDelight's generated
 * database class is just a thin typed wrapper around a driver, so two
 * instances sharing one driver is safe, and it avoids restructuring
 * SqlDelightWikiPortalStore just to hand this one table's queries out.
 */
class WebOfflineArticleFileStore(driver: SqlDriver) : OfflineArticleFileStore {
    private val queries = WikiPortalDatabase(driver, WikiAdapter = Wiki.Adapter(availableSkinsAdapter = SkinOptionListColumnAdapter)).wikiPortalQueries

    override suspend fun write(fileName: String, content: String) {
        queries.upsertOfflineArticleFile(fileName, content)
    }

    override suspend fun read(fileName: String): String? =
        queries.getOfflineArticleFile(fileName).awaitAsOneOrNull()

    override suspend fun delete(fileName: String) {
        queries.deleteOfflineArticleFile(fileName)
    }
}
