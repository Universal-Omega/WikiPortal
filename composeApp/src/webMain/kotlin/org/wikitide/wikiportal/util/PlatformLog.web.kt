package org.wikitide.wikiportal.util

import kotlin.js.console

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    val line = "[$tag] $message"
    when (level) {
        LogLevel.DEBUG -> console.log(line)
        LogLevel.INFO -> console.info(line)
        LogLevel.WARN -> console.warn(line)
        LogLevel.ERROR -> console.error(line)
    }
}
