package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.db.SqlDriver

internal expect suspend fun ensurePlatformSchemaReady(driver: SqlDriver)
