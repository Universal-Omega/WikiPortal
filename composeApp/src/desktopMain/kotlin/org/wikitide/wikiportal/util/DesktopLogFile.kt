package org.wikitide.wikiportal.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Clock

@Serializable
private data class DesktopLogLine(val epochMillis: Long, val level: String, val tag: String, val message: String)

private val json = Json { ignoreUnknownKeys = true }

internal object DesktopLogFile {
    private const val MAX_LINES = 5000

    private val file: File by lazy {
        val dir = File(System.getProperty("user.home"), ".wikiportal/logs").apply { mkdirs() }
        File(dir, "wikiportal.log")
    }

    @Synchronized
    fun append(level: LogLevel, tag: String, message: String) {
        val entry = DesktopLogLine(Clock.System.now().toEpochMilliseconds(), level.name, tag, message)
        file.appendText(json.encodeToString(DesktopLogLine.serializer(), entry) + "\n")
        trimIfNeeded()
    }

    @Synchronized
    fun readAll(): List<LogEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { raw ->
            runCatching { json.decodeFromString(DesktopLogLine.serializer(), raw) }.getOrNull()?.let {
                LogEntry(it.epochMillis, LogLevel.valueOf(it.level), it.tag, it.message)
            }
        }
    }

    @Synchronized
    fun clear() {
        file.writeText("")
    }

    private fun trimIfNeeded() {
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }
}
