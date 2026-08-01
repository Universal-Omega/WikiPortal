package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.db.SqlDriver

/**
 * Brings [driver]'s database up to date, however this platform
 * actually does that. Android, iOS, and desktop each pass the schema
 * straight into their own driver constructor, see platformModule() on
 * each of those, so by the time a driver reaches this function there
 * it's already fully migrated, and the actual there is a no-op. Only
 * wasmJs's WebWorkerDriver has no such constructor, so its actual does
 * the create-or-migrate check by hand instead.
 */
internal expect suspend fun ensurePlatformSchemaReady(driver: SqlDriver)
