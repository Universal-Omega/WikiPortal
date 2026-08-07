package org.wikitide.wikiportal.util

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.RankColumnAdapter
import org.wikitide.wikiportal.data.store.SkinOptionListColumnAdapter
import org.wikitide.wikiportal.db.Folder
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
 * Both adapters, not just Wiki's, are required here even though this
 * class never touches Folder rows itself, since WikiPortalDatabase's
 * constructor needs one for every table with an adapted column, rank
 * on both Wiki and Folder, see SqlDelightWikiPortalStore's
 * construction of the same database.
 */
class WebOfflineArticleFileStore(driver: SqlDriver) : OfflineArticleFileStore {
    private val queries = WikiPortalDatabase(
        driver,
        FolderAdapter = Folder.Adapter(rankAdapter = RankColumnAdapter),
        WikiAdapter = Wiki.Adapter(
            availableSkinsAdapter = SkinOptionListColumnAdapter,
            rankAdapter = RankColumnAdapter,
        ),
    ).wikiPortalQueries

    override suspend fun write(fileName: String, content: String) {
        queries.upsertOfflineArticleFile(fileName, content)
    }

    override suspend fun read(fileName: String): String? =
        queries.getOfflineArticleFile(fileName).awaitAsOneOrNull()

    override suspend fun delete(fileName: String) {
        queries.deleteOfflineArticleFile(fileName)
    }
}
