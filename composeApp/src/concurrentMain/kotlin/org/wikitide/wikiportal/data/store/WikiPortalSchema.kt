package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import org.wikitide.wikiportal.db.WikiPortalDatabase
import org.wikitide.wikiportal.util.RankUtil

private val synchronousSchema: SqlSchema<QueryResult.Value<Unit>> = WikiPortalDatabase.Schema.synchronous()

object WikiPortalSchema : SqlSchema<QueryResult.Value<Unit>> by synchronousSchema {
    override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): QueryResult.Value<Unit> =
        synchronousSchema.migrate(driver, oldVersion, newVersion, *SchemaMigrationCallbacks.all, *callbacks)
}

/**
 * Callbacks that run partway through a schema migration, right after
 * one specific version has finished applying and before the next one
 * starts. The shape this is meant for: a migration adds a new column
 * in some N.sqm, an entry here keyed to that same N fills it with real
 * data, then a later migration drops whatever it replaced, once every
 * install is guaranteed to have already run through this. Every
 * synchronous platform goes through [WikiPortalSchema], which always
 * includes this whole list, so a future migration shaped like this
 * only ever needs one new entry here, not something bolted onto each
 * platform's own driver setup.
 *
 * Each block runs synchronously and talks to [SqlDriver] directly in
 * plain SQL, not through WikiPortalStore, since the app's own
 * generated Queries wrapper isn't available yet this early in setup.
 */
object SchemaMigrationCallbacks {
    val all: Array<AfterVersion> = arrayOf(
        AfterVersion(4) { driver -> backfillRankColumn(driver, "Wiki") },
        AfterVersion(4) { driver -> backfillRankColumn(driver, "Folder") },
    )
}

/**
 * Gives every row in [table] still at rank's column default, an empty
 * string, a real one. RankUtil.initialRanksAfter spreads them out
 * evenly and keeps each one short, rather than what repeatedly calling
 * RankUtil.between would produce, landing after whatever in that same
 * table already has a real rank.
 */
private fun backfillRankColumn(driver: SqlDriver, table: String) {
    val watermark = driver.executeQuery(
        identifier = null,
        sql = "SELECT MAX(rank) FROM $table WHERE rank != ''",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0,
    ).value ?: ""

    val idsNeedingRank = driver.executeQuery(
        identifier = null,
        sql = "SELECT id FROM $table WHERE rank = ''",
        mapper = { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.next().value) ids.add(cursor.getString(0)!!)
            QueryResult.Value(ids)
        },
        parameters = 0,
    ).value

    idsNeedingRank.zip(RankUtil.initialRanksAfter(watermark, idsNeedingRank.size)).forEach { (id, rank) ->
        driver.execute(
            identifier = null,
            sql = "UPDATE $table SET rank = ? WHERE id = ?",
            parameters = 2,
            binders = {
                bindString(0, rank)
                bindString(1, id)
            },
        )
    }
}
