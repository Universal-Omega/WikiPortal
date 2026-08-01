package org.wikitide.wikiportal.di

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.SqlDelightWikiPortalStore
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.db.WikiPortalDatabase
import org.wikitide.wikiportal.util.DesktopOfflineArticleFileStore
import java.io.File

actual fun platformModule(): Module = module {
    single<SqlDriver> {
        val dir = File(System.getProperty("user.home"), ".wikiportal").apply {
        JdbcSqliteDriver(
            "jdbc:sqlite:${File(dir, "wikiportal.db").absolutePath}",
            schema = WikiPortalDatabase.Schema.synchronous(),
        )
    }
    single<OfflineArticleFileStore> { DesktopOfflineArticleFileStore() }
    single<WikiPortalStore> { SqlDelightWikiPortalStore(get(), get()) }
}
