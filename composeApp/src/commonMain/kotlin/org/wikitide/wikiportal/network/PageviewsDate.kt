package org.wikitide.wikiportal.network

import kotlin.time.Clock

/**
 * Howard Hinnant's "civil_from_days" algorithm. This
 * converts a day count since the 1970-01-01 epoch into a year, month,
 * day civil, Gregorian, calendar date, UTC. 
 */
private fun civilDateFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (if (month <= 2) y + 1 else y).toInt()
    return Triple(year, month, day)
}

/**
 * Wikimedia's REST APIs report by UTC calendar day, and "today" is
 * normally incomplete or not yet published. This is the UTC date
 * [daysAgo] days back from now, formatted "YYYY/MM/DD" to drop
 * straight into a URL path. [daysAgo] equal to 1 is "yesterday", the
 * usual first thing to try. Larger values exist so a caller,
 * WikimediaPageviewsApi or WikimediaFeaturedFeedApi, can step back
 * further when even yesterday's report isn't published yet.
 */
fun dateForPageviews(daysAgo: Int): String {
    val nowMillis = Clock.System.now().toEpochMilliseconds()
    // This is plain division, not floor division. nowMillis is always
    // non-negative for any real-world date, so this doesn't need the
    // negative-operand special casing floor division exists for, and
    // java.lang.Math.floorDiv isn't available in commonMain anyway.
    val epochDay = nowMillis / 86_400_000L - daysAgo
    val (year, month, day) = civilDateFromEpochDay(epochDay)
    return "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}
