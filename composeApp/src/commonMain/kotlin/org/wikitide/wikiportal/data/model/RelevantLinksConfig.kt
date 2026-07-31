package org.wikitide.wikiportal.data.model

/**
 * A per-wiki curated source for the Dashboard's "Relevant" tab. There
 * is no MediaWiki-protocol-level concept of "important announcements"
 * or "open RFCs" to query generically, so this has to be hand curated
 * per wiki, the same way [WikiSkins] is a curated list rather than
 * something derived. Wikis with no entry here fall back to a generic
 * heuristic instead, recent activity in the Project namespace. See
 * MediaWikiApi.getRecentChanges and RelevantLinksViewModel.
 */
data class RelevantLinksSource(val category: String, val label: String)

object RelevantLinksConfig {
    val sourceByWikiId: Map<String, RelevantLinksSource> = mapOf(
        PresetWikis.MIRAHEZE_META.id to RelevantLinksSource(
            category = "Category:Current Requests for Comment",
            label = "Open Requests for Comment",
        ),
    )
}
