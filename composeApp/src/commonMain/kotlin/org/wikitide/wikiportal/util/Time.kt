package org.wikitide.wikiportal.util

import kotlin.time.Clock

/** Current wall clock time in epoch milliseconds. */
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
