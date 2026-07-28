package org.wikitide.wikiportal.util

external interface Console {
    fun debug(message: String)
    fun log(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

external val console: Console

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    val line = "[$tag] $message"
    when (level) {
        LogLevel.DEBUG -> console.debug(line)
        LogLevel.INFO -> console.info(line)
        LogLevel.WARN -> console.warn(line)
        LogLevel.ERROR -> console.error(line)
    }
}
