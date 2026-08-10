package org.wikitide.wikiportal.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.SqlDelightWikiPortalStore
import org.wikitide.wikiportal.data.store.WikiPortalSchema
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.util.AndroidAppVersionProvider
import org.wikitide.wikiportal.util.AndroidLogExporter
import org.wikitide.wikiportal.util.AndroidOfflineArticleFileStore
import org.wikitide.wikiportal.util.AppVersionProvider
import org.wikitide.wikiportal.util.LogExporter

actual fun platformModule(): Module = module {
    single<SqlDriver> {
        AndroidSqliteDriver(WikiPortalSchema, get<Context>(), "wikiportal.db")
    }
    single<AppVersionProvider> { AndroidAppVersionProvider(get()) }
    single<LogExporter> { AndroidLogExporter(get()) }
    single<OfflineArticleFileStore> { AndroidOfflineArticleFileStore(get()) }
    single<WikiPortalStore> { SqlDelightWikiPortalStore(get(), get()) }
}
