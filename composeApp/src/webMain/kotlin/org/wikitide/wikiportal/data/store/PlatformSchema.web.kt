package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import org.wikitide.wikiportal.db.WikiPortalDatabase

internal actual suspend fun ensurePlatformSchemaReady(driver: SqlDriver) {
    WikiPortalDatabase.Schema.create(driver).await()
}
