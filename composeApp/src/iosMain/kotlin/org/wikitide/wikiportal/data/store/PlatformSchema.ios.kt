package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.db.SqlDriver

internal actual suspend fun ensurePlatformSchemaReady(driver: SqlDriver) = Unit
