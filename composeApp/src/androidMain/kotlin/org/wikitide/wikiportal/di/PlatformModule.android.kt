package org.wikitide.wikiportal.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.SqlDelightWikiPortalStore
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.util.AndroidLogExporter
import org.wikitide.wikiportal.util.AndroidOfflineArticleFileStore
import org.wikitide.wikiportal.util.LogExporter

actual fun platformModule(): Module = module {
    single<SqlDriver> {
        val context = get<Context>()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("wikiportal.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        AndroidSqliteDriver(openHelper)
    }
    single<LogExporter> { AndroidLogExporter(get()) }
    single<OfflineArticleFileStore> { AndroidOfflineArticleFileStore(get()) }
    single<WikiPortalStore> { SqlDelightWikiPortalStore(get(), get()) }
}
