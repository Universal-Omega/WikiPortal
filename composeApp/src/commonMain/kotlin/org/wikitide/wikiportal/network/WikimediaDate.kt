package org.wikitide.wikiportal.network

import org.wikitide.wikiportal.util.civilDateFromEpochDay
import org.wikitide.wikiportal.util.nowEpochMillis

/**
 * Wikimedia's REST APIs report by UTC calendar day, and "today" is
 * normally incomplete or not yet published. This is the UTC date
 * [daysAgo] days back from now, formatted "YYYY/MM/DD" to drop
 * straight into a URL path. [daysAgo] equal to 1 is "yesterday", the
 * usual first thing to try. Larger values exist so a caller,
 * WikimediaPageviewsApi or WikimediaFeaturedFeedApi, can step back
 * further when even yesterday's report isn't published yet.
 */
fun wikimediaDatePath(daysAgo: Int): String {
    // This is plain division, not floor division. nowMillis is always
    // non-negative for any real-world date, so this doesn't need the
    // negative-operand special casing floor division exists for, and
    // java.lang.Math.floorDiv isn't available in commonMain anyway.
    val epochDay = nowEpochMillis() / 86_400_000L - daysAgo
    val (year, month, day) = civilDateFromEpochDay(epochDay)
    return "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}
