package org.wikitide.wikiportal.util

actual suspend fun readDeviceLogs(maxLines: Int): List<LogEntry> = AppLog.entries.value.takeLast(maxLines)
actual suspend fun clearDeviceLogs() = AppLog.clear()
