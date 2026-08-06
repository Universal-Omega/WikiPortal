package org.wikitide.wikiportal.data.model

import kotlinx.serialization.Serializable

/**
 * A position key for ordering wikis and folders, see RankUtil. This
 * exists purely so the compiler catches a mixup, passing a wiki id or
 * a folder name where an order key was meant, rather than that only
 * surfacing later as a wrong sort order on screen. It's a thin wrapper
 * around the plain string RankUtil already produces, so it compiles
 * away to nothing extra at runtime.
 */
@Serializable
@JvmInline
value class Rank(val value: String) : Comparable<Rank> {
    override fun compareTo(other: Rank): Int = value.compareTo(other.value)
    override fun toString(): String = value
}
