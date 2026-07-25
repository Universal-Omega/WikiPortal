package org.wikitide.wikiportal.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import org.koin.core.module.Module
import org.koin.dsl.module
import org.wikitide.wikiportal.data.store.SqlDelightWikiPortalStore
import org.wikitide.wikiportal.data.store.WikiPortalStore

actual fun platformModule(): Module = module {
    single<SqlDriver> {
        NativeSqliteDriver(
            DatabaseConfiguration(
                name = "wikiportal.db",
                version = 1,
                create = { /* handled by SqlDelightWikiPortalStore.ensureSchema() */ },
                upgrade = { _, _, _ -> },
            ),
        )
    }
    single<WikiPortalStore> { SqlDelightWikiPortalStore(get()) }
}
