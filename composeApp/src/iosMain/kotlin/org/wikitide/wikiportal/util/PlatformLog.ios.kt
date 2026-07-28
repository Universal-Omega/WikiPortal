package org.wikitide.wikiportal.util

import platform.Foundation.NSLog

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    NSLog("[$tag] ${level.name}: $message")
}
