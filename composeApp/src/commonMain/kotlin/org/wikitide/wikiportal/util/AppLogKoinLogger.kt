package org.wikitide.wikiportal.util

import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

private const val KOIN_TAG = "Koin"

class AppLogKoinLogger(level: Level = Level.DEBUG) : Logger(level) {
    override fun display(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> AppLog.d(KOIN_TAG, msg)
            Level.INFO -> AppLog.i(KOIN_TAG, msg)
            Level.WARNING -> AppLog.w(KOIN_TAG, msg)
            Level.ERROR, Level.NONE -> AppLog.e(KOIN_TAG, msg)
        }
    }
}
