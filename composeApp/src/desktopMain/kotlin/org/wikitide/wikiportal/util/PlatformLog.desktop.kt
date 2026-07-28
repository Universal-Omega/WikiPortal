package org.wikitide.wikiportal.util

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    val line = "[$tag] ${level.name}: $message"
    println(line)
    DesktopLogFile.append(level, tag, message)
}
