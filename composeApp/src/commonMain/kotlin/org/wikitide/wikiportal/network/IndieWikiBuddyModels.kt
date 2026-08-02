package org.wikitide.wikiportal.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One place a wiki used to live, or still does, that redirects or should redirect to [IwbSiteDto.destination]. */
@Serializable
data class IwbOriginDto(
    val origin: String = "",
    @SerialName("origin_base_url") val originBaseUrl: String = "",
    @SerialName("origin_content_path") val originContentPath: String = "",
)

/**
 * One entry from an Indie Wiki Buddy sites*.json file: a wiki that
 * moved, or forked, away from one or more [origins] onto its own
 * independent [destinationBaseUrl]. See
 * https://github.com/KevinPayravi/indie-wiki-buddy/tree/main/data for
 * the source data this is decoded from. Unlisted fields from the
 * source files, edit summaries and the like, aren't needed here and
 * are left to ignoreUnknownKeys.
 */
@Serializable
data class IwbSiteDto(
    val id: String = "",
    @SerialName("origins_label") val originsLabel: String = "",
    val origins: List<IwbOriginDto> = emptyList(),
    val destination: String = "",
    @SerialName("destination_base_url") val destinationBaseUrl: String = "",
    @SerialName("destination_platform") val destinationPlatform: String = "",
    @SerialName("destination_icon") val destinationIcon: String? = null,
    val tags: List<String> = emptyList(),
)
