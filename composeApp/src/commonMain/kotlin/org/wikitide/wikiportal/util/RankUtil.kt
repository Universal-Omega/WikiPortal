package org.wikitide.wikiportal.util

/**
 * Sortable string keys for ordering rows without ever renumbering a
 * whole table. Moving one row means generating one new key for that row
 * alone, no matter how many other rows exist around it, so this scales
 * the same way at 10 rows or 10,000. This is the same "fractional
 * indexing" approach Figma and a few other realtime list editors use.
 *
 * A key is just a string over [ALPHABET], compared the normal, plain
 * lexicographic way, so any two keys already sort correctly against
 * each other with no parsing needed. [between] is the one operation
 * everything else is built on: given a row's new lower and upper
 * neighbor, it returns a fresh key that sorts strictly between them.
 */
object RankUtil {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val BASE = 36

    private fun valueOf(c: Char) = ALPHABET.indexOf(c)

    /**
     * A key that sorts after [lo] and, when [hi] isn't null, before it
     * too. Pass "" for [lo] to mean "no lower neighbor, this is the new
     * first item", and null for [hi] to mean "no upper neighbor, this
     * is the new last item". This is the operation a single insert,
     * append, or drag reorder needs, touching only the one row being
     * placed.
     */
    fun between(lo: String, hi: String?): String {
        val result = StringBuilder()
        var i = 0
        var hiBounds = hi != null
        while (true) {
            val l = if (i < lo.length) valueOf(lo[i]) else 0
            val h = when {
                hiBounds && i < hi!!.length -> valueOf(hi[i])
                hiBounds -> error("no room between \"$lo\" and \"$hi\"")
                else -> BASE
            }
            if (h - l >= 2) {
                result.append(ALPHABET[l + (h - l) / 2])
                return result.toString()
            }
            // Not enough room to fit a distinct character at this
            // position, so borrow lo's own digit here and look one
            // character deeper for the room instead. Once a digit is
            // chosen that's strictly less than hi's, everything below
            // it is already guaranteed less than hi, so hi stops
            // constraining anything further down.
            result.append(ALPHABET[l])
            if (l < h) hiBounds = false
            i++
        }
    }

    /**
     * [count] keys, evenly spread out and each one short, rather than
     * whatever calling [between] end to end [count] times in a row
     * would produce, which drifts longer with every step. Meant for
     * seeding a table fresh or backfilling a batch of existing rows at
     * once, not for a single everyday insert, since it needs the full
     * count up front to know how to space them.
     */
    fun initialRanks(count: Int): List<String> {
        if (count <= 0) return emptyList()
        var width = 1
        var span = BASE.toLong()
        while (span <= count) {
            width++
            span *= BASE
        }
        return (1..count).map { toBase36((it.toLong() * span) / (count + 1), width) }
    }

    private fun toBase36(value: Long, width: Int): String {
        val digits = CharArray(width)
        var remaining = value
        for (pos in width - 1 downTo 0) {
            digits[pos] = ALPHABET[(remaining % BASE).toInt()]
            remaining /= BASE
        }
        return String(digits)
    }

    /**
     * Same idea as [initialRanks], but every key is guaranteed to sort
     * after [after] rather than starting from scratch. Any nonempty
     * suffix appended to [after] already sorts after it on its own, a
     * longer string sharing a prefix always sorts after the shorter
     * one, so this is really just [initialRanks] with [after] stuck on
     * the front of each result. For backfilling a batch of existing
     * rows in after whatever already has real ranks, for example every
     * preset, rather than mixing in among them.
     */
    fun initialRanksAfter(after: String, count: Int): List<String> = initialRanks(count).map { after + it }
}
