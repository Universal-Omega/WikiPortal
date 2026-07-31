package org.wikitide.wikiportal.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDto(
    val code: String = "",
    val info: String = "",
)

/**
 * Implemented by every Action API, action=..., response shape in this
 * file, so [ActionApiClient] can check for an API-level error
 * generically. action=query succeeding at the HTTP level doesn't mean
 * MediaWiki didn't reject the request itself, for example bad params
 * or permission denied, and that always shows up the same way: an
 * error key alongside whatever the call's own data would have been.
 * Without this, every response type needs its own repeated
 * "if (response.error != null) ..." check at the call site.
 */
interface ActionApiResponse {
    val error: ApiErrorDto?
}

@Serializable
data class SiteInfoResponse(
    val query: SiteInfoQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class SiteInfoQuery(
    val general: SiteGeneral? = null,
    val skins: List<SkinInfoDto> = emptyList(),
    val extensions: List<ExtensionDto> = emptyList(),
)

@Serializable
data class ExtensionDto(
    val name: String? = null,
)

@Serializable
data class SiteGeneral(
    val sitename: String? = null,
    val lang: String? = null,
    val mainpage: String? = null,
    val base: String? = null,
    val favicon: String? = null,
)

/**
 * One entry from siprop=skins, every skin registered with the wiki's
 * MediaWiki install, not just ones actually meant to be used for page
 * rendering. See [unusable].
 */
@Serializable
data class SkinInfoDto(
    val code: String = "",
    val name: String = "",
    val default: Boolean = false,
    /**
     * True for skins that are registered but not real, selectable
     * page skins, for example SkinJSON, the auth popup skin, or
     * Fallback. This is always absent, defaulting to false, for
     * genuinely usable skins. MediaWiki only includes this key at all
     * when it is true.
     */
    val unusable: Boolean = false,
)

@Serializable
data class RandomPagesResponse(
    val query: RandomPagesQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class RandomPagesQuery(
    val pages: List<PageSummaryDto>? = null,
)

@Serializable
data class PageSummaryDto(
    val pageid: Int = 0,
    val title: String = "",
    val extract: String? = null,
    val thumbnail: ThumbnailDto? = null,
)

@Serializable
data class ThumbnailDto(
    val source: String,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class SearchPagesResponse(
    val query: SearchPagesQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class SearchPagesQuery(
    val pages: List<PageSummaryDto>? = null,
    // This is Cirrus-specific, gsrinfo=suggestion|rewrittenquery, and is
    // a harmless no-op on wikis without CirrusSearch, since MediaWiki
    // just ignores gsrinfo values the active backend doesn't understand.
    val searchinfo: SearchInfoDto? = null,
)

@Serializable
data class SearchInfoDto(
    val suggestion: String? = null,
    val rewrittenquery: String? = null,
)

@Serializable
data class CategoryMembersResponse(
    val query: CategoryMembersQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class CategoryMembersQuery(
    val categorymembers: List<CategoryMemberDto> = emptyList(),
)

@Serializable
data class CategoryMemberDto(
    val pageid: Int = 0,
    val title: String = "",
    val ns: Int = 0,
)

@Serializable
data class RecentChangesResponse(
    val query: RecentChangesQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class RecentChangesQuery(
    val recentchanges: List<RecentChangeDto> = emptyList(),
)

@Serializable
data class RecentChangeDto(
    val title: String = "",
    val user: String? = null,
)

@Serializable
data class CategorySearchResponse(
    val query: CategorySearchQuery? = null,
    override val error: ApiErrorDto? = null,
) : ActionApiResponse

@Serializable
data class CategorySearchQuery(
    val search: List<CategorySearchResultDto> = emptyList(),
)

@Serializable
data class CategorySearchResultDto(
    val title: String = "",
)
