package org.wikitide.wikiportal.util

import kotlinx.browser.console

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    val line = "[$tag] $message"
    when (level) {
        LogLevel.DEBUG -> console.debug(line)
        LogLevel.INFO -> console.info(line)
        LogLevel.WARN -> console.warn(line)
        LogLevel.ERROR -> console.error(line)
    }
}
