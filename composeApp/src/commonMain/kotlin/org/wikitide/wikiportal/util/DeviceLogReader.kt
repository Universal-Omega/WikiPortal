package org.wikitide.wikiportal.util

/**
 * Pulls this app's own recent log output straight from the platform,
 * Android's logcat via `logcat -d`, filtered to this process, rather
 * than only replaying whatever AppLog happened to buffer in memory.
 * Platforms without an equivalent shell-accessible per-app log, iOS,
 * desktop, and the web build, fall back to that in-memory buffer
 * instead, see AppLog.
 */
expect suspend fun readDeviceLogs(maxLines: Int = 2000): List<LogEntry>

/** Clears whatever readDeviceLogs is currently reading from. */
expect suspend fun clearDeviceLogs()
