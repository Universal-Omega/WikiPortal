package org.wikitide.wikiportal.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

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
        val entry = LogEntry(Clock.System.now().toEpochMilliseconds(), level, tag, message)
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
        platformLog(level, tag, message)
    }
}

/** Writes one entry straight to whatever this platform's own console is. */
expect fun platformLog(level: LogLevel, tag: String, message: String)
