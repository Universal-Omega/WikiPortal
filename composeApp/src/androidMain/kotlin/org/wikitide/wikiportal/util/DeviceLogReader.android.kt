package org.wikitide.wikiportal.util

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * threadtime lines look like:
 * 07-27 21:49:12.345  1234  1234 E WikiPortal    : [Ktor] some message
 * Captures pid and tid, not just to discard them, but because
 * Android's native logging layer, not just logcat's own display, really
 * does split a single Log.d/i/w/e call into several separate records
 * whenever its message contains an embedded newline, each one
 * repeating the exact same timestamp, pid, tid, level, and tag. Ktor's
 * own Logging plugin builds its messages exactly that way, one
 * embedded-newline-separated block per request or response phase, so
 * without accounting for this, every one of Ktor's own log lines shows
 * up here as its own disconnected entry. See mergeContinuations below,
 * which uses that repeated key to put them back together.
 */
private val threadTimeLine = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]*):\s?(.*)$""")

/**
 * Recovers the real tag AppLog was called with, see platformLog's
 * "[tag] message" convention. DOT_MATCHES_ALL so this still matches
 * once continuation lines have been rejoined with embedded newlines,
 * not just against the first physical line.
 */
private val appLogBracket = Regex("""^\[([^]]+)]\s?(.*)$""", RegexOption.DOT_MATCHES_ALL)

private data class RawLine(
    val time: String,
    val pid: String,
    val tid: String,
    val level: LogLevel,
    val androidTag: String,
    val message: String,
)

private fun parseRawLine(line: String): RawLine? {
    val match = threadTimeLine.matchEntire(line) ?: return null
    val (time, pid, tid, levelChar, rawTag, rawMessage) = match.destructured
    val level = when (levelChar) {
        "V", "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        else -> LogLevel.ERROR
    }
    return RawLine(time, pid, tid, level, rawTag.trim(), rawMessage)
}

/**
 * Rejoins consecutive raw lines that share the exact same timestamp,
 * pid, tid, level, and tag, Android's own fingerprint for "this was
 * really one Log call with an embedded newline in it", into a single
 * multi-line entry before recovering the real AppLog tag and message
 * from the combined text. A genuinely separate log call essentially
 * never matches all five of those at once, so this only ever combines
 * pieces that were truly one call to begin with.
 */
private fun mergeContinuations(rawLines: List<RawLine>): List<LogEntry> {
    val merged = mutableListOf<RawLine>()
    for (raw in rawLines) {
        val last = merged.lastOrNull()
        if (last != null && last.time == raw.time && last.pid == raw.pid && last.tid == raw.tid &&
            last.level == raw.level && last.androidTag == raw.androidTag
        ) {
            merged[merged.lastIndex] = last.copy(message = last.message + "\n" + raw.message)
        } else {
            merged.add(raw)
        }
    }
    return merged.map { raw ->
        val isAppSource = raw.androidTag == ANDROID_LOG_TAG
        val bracketMatch = if (isAppSource) appLogBracket.matchEntire(raw.message) else null
        val tag = bracketMatch?.groupValues?.get(1) ?: raw.androidTag
        val message = bracketMatch?.groupValues?.get(2) ?: raw.message
        LogEntry(
            timestampEpochMillis = 0L,
            level = raw.level,
            tag = tag,
            message = redactPii(message),
            displayTime = raw.time,
            isAppSource = isAppSource,
        )
    }
}

actual suspend fun readDeviceLogs(maxLines: Int): List<LogEntry> = withContext(Dispatchers.IO) {
    try {
        val pid = Process.myPid()
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"))
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        val rawLines = lines.mapNotNull { parseRawLine(it) }
        mergeContinuations(rawLines).takeLast(maxLines)
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
