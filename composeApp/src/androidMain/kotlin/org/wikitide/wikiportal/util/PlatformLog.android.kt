package org.wikitide.wikiportal.util

import android.util.Log

/**
 * Every AppLog call writes to logcat under this single Android tag,
 * with the caller's own tag folded into the message as a "[tag] "
 * prefix instead of used as the real Log tag. That way, DeviceLogReader
 * can tell this app's own AppLog output apart from any other line the
 * system happens to log under this same process, an ActivityManager
 * entry noting the app came to the foreground, for example, just by
 * checking the Android tag, rather than needing to track every string
 * tag AppLog has ever been called with.
 */
const val ANDROID_LOG_TAG = "WikiPortal"

actual fun platformLog(level: LogLevel, tag: String, message: String) {
    val line = "[$tag] $message"
    when (level) {
        LogLevel.DEBUG -> Log.d(ANDROID_LOG_TAG, line)
        LogLevel.INFO -> Log.i(ANDROID_LOG_TAG, line)
        LogLevel.WARN -> Log.w(ANDROID_LOG_TAG, line)
        LogLevel.ERROR -> Log.e(ANDROID_LOG_TAG, line)
    }
}
