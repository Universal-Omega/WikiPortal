package org.wikitide.wikiportal.data.model

/**
 * A single open reading tab, like a browser tab. This is deliberately
 * lightweight, with no WebView state. See TabsRepository for why.
 * thumbnailUrl is the article's own lead image, from pageimages, used
 * as a stand-in preview in the tab switcher, not a real screenshot of
 * the WebView's scroll state, which this library doesn't expose a way
 * to capture. extract is that same page's own summary text, also from
 * pageimages and extracts, see MediaWikiApi.getPageSummary, used by
 * TabsListScreen. Both are null until the backfill fetch in
 * ArticleHostScreen resolves, which happens once per title change.
 * Being briefly null on a freshly-opened tab is expected, not a bug.
 * currentUrl is the tab's actual live address, used to reopen it
 * exactly where it was, since title alone isn't enough to rebuild a
 * url for a page that isn't really an article on wikiId, like an
 * auth flow or an external link followed in the tab.
 */
data class ArticleTab(
    val id: String,
    val wikiId: String,
    val wikiName: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val extract: String? = null,
    val createdAtEpochMillis: Long,
    val currentUrl: String? = null,
)
