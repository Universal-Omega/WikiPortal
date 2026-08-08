package org.wikitide.wikiportal.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SavedPage(
    val wikiId: String,
    val wikiName: String,
    val title: String,
    val extract: String = "",
    val thumbnailUrl: String? = null,
    val timestampEpochMillis: Long = 0L,
    val url: String? = null,
)
