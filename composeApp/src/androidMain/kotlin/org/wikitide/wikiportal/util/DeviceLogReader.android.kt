package org.wikitide.wikiportal.util

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * threadtime lines look like:
 * 07-27 21:49:12.345  1234  1234 E WikiPortal    : [Ktor] some message
 * The pid and tid columns are dropped since callers only care about
 * time, level, tag, and the message itself.
 */
private val threadTimeLine = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]*):\s?(.*)$""")

/** Recovers the real tag AppLog was called with, see platformLog's "[tag] message" convention. */
private val appLogBracket = Regex("""^\[([^]]+)]\s?(.*)$""")

private fun parseThreadTimeLine(line: String): LogEntry? {
    val match = threadTimeLine.matchEntire(line) ?: return null
    val (time, levelChar, rawTag, rawMessage) = match.destructured
    val level = when (levelChar) {
        "V", "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        else -> LogLevel.ERROR
    }
    val androidTag = rawTag.trim()
    val isAppSource = androidTag == ANDROID_LOG_TAG
    val bracketMatch = if (isAppSource) appLogBracket.matchEntire(rawMessage) else null
    val displayTag = bracketMatch?.groupValues?.get(1) ?: androidTag
    val displayMessage = bracketMatch?.groupValues?.get(2) ?: rawMessage
    return LogEntry(
        timestampEpochMillis = 0L,
        level = level,
        tag = displayTag,
        message = displayMessage,
        displayTime = time,
        isAppSource = isAppSource,
    )
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
