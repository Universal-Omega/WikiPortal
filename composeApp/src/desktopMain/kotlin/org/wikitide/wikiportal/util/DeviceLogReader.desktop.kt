package org.wikitide.wikiportal.util

actual suspend fun readDeviceLogs(maxLines: Int): List<LogEntry> = DesktopLogFile.readAll().takeLast(maxLines)

actual suspend fun clearDeviceLogs() {
    DesktopLogFile.clear()
    AppLog.clear()
}
