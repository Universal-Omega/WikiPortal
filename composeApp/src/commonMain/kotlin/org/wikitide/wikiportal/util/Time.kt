package org.wikitide.wikiportal.util

import androidx.compose.runtime.Immutable
import kotlin.time.Clock

private const val MILLIS_PER_DAY = 86_400_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 1_440L

/** Current wall clock time in epoch milliseconds. */
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Days since the 1970-01-01 epoch, UTC, for [epochMillis]. */
fun epochDayFromMillis(epochMillis: Long): Long = epochMillis / MILLIS_PER_DAY

/**
 * Howard Hinnant's "civil_from_days" algorithm. Converts a day count
 * since the 1970-01-01 epoch into a year, month, day civil, Gregorian,
 * calendar date, UTC.
 */
fun civilDateFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
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

/** Which day bucket a history entry's timestamp falls into. */
enum class HistoryDayBucket { TODAY, YESTERDAY, OLDER }

/** Buckets [epochMillis] against [nowMillis] by UTC calendar day. */
fun historyDayBucket(epochMillis: Long, nowMillis: Long = nowEpochMillis()): HistoryDayBucket {
    val diffDays = epochDayFromMillis(nowMillis) - epochDayFromMillis(epochMillis)
    return when (diffDays) {
        0L -> HistoryDayBucket.TODAY
        1L -> HistoryDayBucket.YESTERDAY
        else -> HistoryDayBucket.OLDER
    }
}

private val MONTH_ABBREVIATIONS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * A short date like "Jan 3", or "Jan 3, 2026" once it's not the current
 * year, for an OLDER history section header.
 */
fun formatHistorySectionDate(epochMillis: Long, nowMillis: Long = nowEpochMillis()): String {
    val (year, month, day) = civilDateFromEpochDay(epochDayFromMillis(epochMillis))
    val (nowYear, _, _) = civilDateFromEpochDay(epochDayFromMillis(nowMillis))
    val label = "${MONTH_ABBREVIATIONS[month - 1]} $day"
    return if (year == nowYear) label else "$label, $year"
}

/** How long ago a history entry was visited, resolved by the UI into a localized string. */
@Immutable
sealed class RelativeTime {
    data object JustNow : RelativeTime()
    data class MinutesAgo(val minutes: Int) : RelativeTime()
    data class HoursAgo(val hours: Int) : RelativeTime()
    data class DaysAgo(val days: Int) : RelativeTime()
}

/**
 * [epochMillis] expressed as a duration before [nowMillis], for the
 * "last read" label on each history card.
 */
fun relativeTimeSince(epochMillis: Long, nowMillis: Long = nowEpochMillis()): RelativeTime {
    val diffMinutes = ((nowMillis - epochMillis).coerceAtLeast(0)) / MILLIS_PER_MINUTE
    return when {
        diffMinutes < 1 -> RelativeTime.JustNow
        diffMinutes < MINUTES_PER_HOUR -> RelativeTime.MinutesAgo(diffMinutes.toInt())
        diffMinutes < MINUTES_PER_DAY -> RelativeTime.HoursAgo((diffMinutes / MINUTES_PER_HOUR).toInt())
        else -> RelativeTime.DaysAgo((diffMinutes / MINUTES_PER_DAY).toInt())
    }
}
