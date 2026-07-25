package org.wikitide.wikiportal.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedPage(
    val wikiId: String,
    val wikiName: String,
    val title: String,
    val extract: String = "",
    val thumbnailUrl: String? = null,
    val timestampEpochMillis: Long = 0L,
)
