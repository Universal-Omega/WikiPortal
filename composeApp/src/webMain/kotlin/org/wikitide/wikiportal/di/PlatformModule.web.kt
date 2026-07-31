package org.wikitide.wikiportal.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import org.w3c.dom.Worker
import org.wikitide.wikiportal.BuildKonfig
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import org.wikitide.wikiportal.data.store.SqlDelightWikiPortalStore
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.util.AppVersionProvider
import org.wikitide.wikiportal.util.WebOfflineArticleFileStore
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSqlJsWorker(): Worker =
    js("new Worker(new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url))")

actual fun platformModule(): Module = module {
    single<SqlDriver> { WebWorkerDriver(createSqlJsWorker()) }
    single<OfflineArticleFileStore> { WebOfflineArticleFileStore(get()) }
    single<WikiPortalStore> { SqlDelightWikiPortalStore(get(), get()) }
    single<AppVersionProvider> {
        object : AppVersionProvider {
            override val versionName = BuildKonfig.VERSION_NAME
        }
    }
}
