package org.wikitide.wikiportal.data.model

import kotlinx.serialization.Serializable
import org.wikitide.wikiportal.network.IwbSiteDto

/**
 * A wiki Indie Wiki Buddy tracks as having moved, or forked, off of one
 * or more [origins] onto its own [destinationBaseUrl]. See
 * IndieWikiDirectory, which loads and caches these, and
 * network/IndieWikiBuddyModels.kt for the raw shape this is built from.
 * Only wikis on MediaWiki itself are kept, see
 * IndieWikiDirectory.toDomainSites, since this app has no way to browse
 * or add anything else.
 */
@Serializable
data class IndieWikiSite(
    val id: String,
    val language: String,
    val originsLabel: String,
    val origins: List<IndieWikiOrigin>,
    val destinationName: String,
    val destinationBaseUrl: String,
    val destinationIcon: String?,
    val tags: List<String>,
) {
    val isOfficial: Boolean get() = "official" in tags
}

@Serializable
data class IndieWikiOrigin(
    val originBaseUrl: String,
    val originContentPath: String,
)

internal fun IwbSiteDto.toDomainOrNull(language: String): IndieWikiSite? {
    if (destinationPlatform != "mediawiki" || destinationBaseUrl.isBlank()) return null
    return IndieWikiSite(
        id = id,
        language = language,
        originsLabel = originsLabel,
        origins = origins.map { IndieWikiOrigin(it.originBaseUrl, it.originContentPath) },
        destinationName = destination,
        destinationBaseUrl = destinationBaseUrl,
        destinationIcon = destinationIcon,
        tags = tags,
    )
}

/**
 * A url or bare host someone typed into "Add a wiki", stripped of
 * scheme and lowercased, the same normalization
 * [findIndieWikiRedirect]'s candidates are compared against.
 */
private fun normalizedHostAndPath(input: String): String =
    input.trim().removePrefix("https://").removePrefix("http://").lowercase()

/**
 * Looks for a wiki in [sites] that [rawUrl] would actually redirect
 * away from, the same match Indie Wiki Buddy's browser extension makes
 * before rewriting a request. A prefix match against origin_base_url +
 * origin_content_path, or an exact match against the bare origin host
 * with no path at all. When more than one origin matches, for example
 * one wiki's origin host being a prefix of another's, the longest,
 * meaning most specific, origin host wins.
 */
fun findIndieWikiRedirect(rawUrl: String, sites: List<IndieWikiSite>): IndieWikiSite? {
    val normalized = normalizedHostAndPath(rawUrl)
    var bestMatch: IndieWikiSite? = null
    var bestOriginHost = ""
    for (site in sites) {
        for (origin in site.origins) {
            val originHost = origin.originBaseUrl.lowercase()
            val matches = normalized.startsWith("$originHost${origin.originContentPath.lowercase()}") ||
                normalized.trimEnd('/') == originHost
            if (matches && originHost.length > bestOriginHost.length) {
                bestMatch = site
                bestOriginHost = originHost
            }
        }
    }
    return bestMatch
}
