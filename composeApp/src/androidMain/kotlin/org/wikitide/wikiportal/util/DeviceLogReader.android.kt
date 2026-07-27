package org.wikitide.wikiportal.util

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * threadtime lines look like:
 * 07-27 21:49:12.345  1234  1234 E Ktor    : some message here
 * The pid and tid columns are dropped since callers only care about
 * time, level, tag, and the message itself.
 */
private val threadTimeLine = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]*):\s?(.*)$""")

private fun parseThreadTimeLine(line: String): LogEntry? {
    val match = threadTimeLine.matchEntire(line) ?: return null
    val (time, levelChar, tag, message) = match.destructured
    val level = when (levelChar) {
        "V", "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        else -> LogLevel.ERROR
    }
    return LogEntry(timestampEpochMillis = 0L, level = level, tag = tag.trim(), message = message, displayTime = time)
}

actual suspend fun readDeviceLogs(maxLines: Int): List<LogEntry> = withContext(Dispatchers.IO) {
    try {
        val pid = Process.myPid()
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"))
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        lines.mapNotNull { parseThreadTimeLine(it) }.takeLast(maxLines)
    } catch (e: Exception) {
        // A device or ROM that doesn't expose the logcat binary the
        // normal way, or denies exec() outright, falls back to
        // whatever this session's own AppLog buffer already has,
        // rather than showing nothing at all.
        AppLog.e("DeviceLogReader", "logcat -d failed, falling back to the in-app log", e)
        AppLog.entries.value.takeLast(maxLines)
    }
}

actual suspend fun clearDeviceLogs() {
    withContext(Dispatchers.IO) {
        try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
        } catch (e: Exception) {
            AppLog.e("DeviceLogReader", "logcat -c failed", e)
        }
    }
    AppLog.clear()
}
