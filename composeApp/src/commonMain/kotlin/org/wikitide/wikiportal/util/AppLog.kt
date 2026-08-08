package org.wikitide.wikiportal.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.wikitide.wikiportal.util.nowEpochMillis

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    /**
     * Preformatted time text to show as-is, used when this entry came
     * from a source that already reports a human-readable time, for
     * example Android's own logcat, rather than a raw epoch this app
     * recorded itself. Falls back to formatting [timestampEpochMillis]
     * when null.
     */
    val displayTime: String? = null,
    /**
     * True when this line actually came from an AppLog.d/i/w/e call,
     * as opposed to some other system-generated line that happened to
     * get swept up in a --pid filtered logcat dump, for example
     * ActivityManager noting this app was brought to the foreground.
     * Every entry in AppLog's own buffer is app-sourced by
     * construction, this only varies for entries DeviceLogReader
     * parses back out of Android's real logcat.
     */
    val isAppSource: Boolean = true,
)

/**
 * A small in-memory record of what the app has been doing this
 * session. On Android, readDeviceLogs pulls the real thing straight
 * out of logcat, so this buffer mostly just backs platformLog's own
 * mirroring there. On iOS, desktop, and the web build, where there's
 * no equivalent shell-accessible per-app log to shell out to,
 * readDeviceLogs falls back to reading this buffer directly, so it's
 * still the actual source of truth for the Logs screen on those
 * platforms. Capped at MAX_ENTRIES so a long session doesn't grow
 * this without bound, oldest entries drop off first.
 */
object AppLog {
    private const val MAX_ENTRIES = 1000
    private const val DEFAULT_TAG = "WikiPortal"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun d(tag: String = DEFAULT_TAG, message: String) = record(LogLevel.DEBUG, tag, message)

    fun i(tag: String = DEFAULT_TAG, message: String) = record(LogLevel.INFO, tag, message)

    fun w(tag: String = DEFAULT_TAG, message: String) = record(LogLevel.WARN, tag, message)

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message (${throwable::class.simpleName}: ${throwable.message})" else message
        record(LogLevel.ERROR, tag, full)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun record(level: LogLevel, tag: String, message: String) {
        val redacted = redactPii(message)
        val entry = LogEntry(nowEpochMillis(), level, tag, redacted)
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
        platformLog(level, tag, redacted)
    }
}

/** Writes one entry straight to whatever this platform's own console is. */
expect fun platformLog(level: LogLevel, tag: String, message: String)
