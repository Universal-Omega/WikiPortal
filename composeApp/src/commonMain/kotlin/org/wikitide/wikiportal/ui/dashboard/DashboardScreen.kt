package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.network.RecentChangeEntry
import org.wikitide.wikiportal.network.TrendDirection
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.article_top_bar_close_search
import org.wikitide.wikiportal.resources.category_browse_title
import org.wikitide.wikiportal.resources.common_clear
import org.wikitide.wikiportal.resources.common_loading
import org.wikitide.wikiportal.resources.common_refresh
import org.wikitide.wikiportal.resources.common_retry
import org.wikitide.wikiportal.resources.common_search
import org.wikitide.wikiportal.resources.dashboard_continue_reading
import org.wikitide.wikiportal.resources.dashboard_could_not_load_wiki
import org.wikitide.wikiportal.resources.dashboard_did_you_mean
import org.wikitide.wikiportal.resources.dashboard_edited_by
import org.wikitide.wikiportal.resources.dashboard_go_to_main_page
import org.wikitide.wikiportal.resources.dashboard_more_trending
import org.wikitide.wikiportal.resources.dashboard_no_recent_activity
import org.wikitide.wikiportal.resources.dashboard_no_search_results
import org.wikitide.wikiportal.resources.dashboard_random_pick
import org.wikitide.wikiportal.resources.dashboard_recent_activity_on
import org.wikitide.wikiportal.resources.dashboard_relevant_default_label
import org.wikitide.wikiportal.resources.dashboard_relevant_empty
import org.wikitide.wikiportal.resources.dashboard_saved
import org.wikitide.wikiportal.resources.dashboard_search_wiki
import org.wikitide.wikiportal.resources.dashboard_showing_results_for
import org.wikitide.wikiportal.resources.dashboard_shuffle
import org.wikitide.wikiportal.resources.dashboard_switch_wiki
import org.wikitide.wikiportal.resources.dashboard_tab_feed
import org.wikitide.wikiportal.resources.dashboard_tab_relevant
import org.wikitide.wikiportal.resources.dashboard_title
import org.wikitide.wikiportal.resources.dashboard_trending_on
import org.wikitide.wikiportal.resources.dashboard_views_count
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.CompactArticleChip
import org.wikitide.wikiportal.ui.components.OpenTabIndicator
import org.wikitide.wikiportal.ui.components.WikiSwitcherChip

/** Index of the "Feed" tab in [DashboardScreen]'s [SecondaryTabRow]. */
private const val TAB_FEED = 0

/** Index of the "Relevant" tab in [DashboardScreen]'s [SecondaryTabRow]. */
private const val TAB_RELEVANT = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onOpenCategoryBrowse: () -> Unit,
    onOpenTrending: () -> Unit,
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel(),
    relevantLinksViewModel: RelevantLinksViewModel = koinViewModel(),
    tabsRepository: TabsRepository = koinInject(),
) {
    val feedState by feedViewModel.uiState.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val relevantState by relevantLinksViewModel.state.collectAsState()
    val tabs by tabsRepository.tabs.collectAsState()
    var tabIndex by remember { mutableStateOf(TAB_FEED) }
    var isFullScreenSearchOpen by remember { mutableStateOf(false) }

    // Titles within the current wiki that already have an open tab. This
    // drives the "Open" indicator, so tapping one of these jumps to the
    // existing tab, see App.kt's openArticle, instead of opening a
    // duplicate.
    val openTitles = remember(tabs, feedState.wiki?.id) {
        tabs.filter { it.wikiId == feedState.wiki?.id }.map { it.title }.toImmutableSet()
    }

    LaunchedEffect(tabIndex) {
        if (tabIndex == TAB_RELEVANT) relevantLinksViewModel.ensureLoaded()
    }

    Box(modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHeader(
                feedState = feedState,
                searchState = searchState,
                onArticleClick = onArticleClick,
                onOpenWikiPicker = onOpenWikiPicker,
                onQueryChange = searchViewModel::onQueryChange,
                onOpenFullScreenSearch = { isFullScreenSearchOpen = true },
            )

            if (searchState.query.isNotBlank()) {
                SearchResultsContent(searchState, onArticleClick, onSearchFor = searchViewModel::searchFor)
            } else {
                DashboardTabs(
                    tabIndex = tabIndex,
                    onTabIndexChange = { tabIndex = it },
                    feedState = feedState,
                    relevantState = relevantState,
                    openTitles = openTitles,
                    onArticleClick = onArticleClick,
                    onOpenWikiPicker = onOpenWikiPicker,
                    onRefreshFeed = feedViewModel::refresh,
                    onShuffleRandom = feedViewModel::shuffleRandomPick,
                    onOpenCategoryBrowse = onOpenCategoryBrowse,
                    onOpenTrending = onOpenTrending,
                    onRefreshRelevant = relevantLinksViewModel::refresh,
                )
            }
        }

        if (isFullScreenSearchOpen) {
            FullScreenSearchOverlay(
                searchState = searchState,
                onQueryChange = searchViewModel::onQueryChange,
                onSearchFor = searchViewModel::searchFor,
                onArticleClick = onArticleClick,
                onClose = { isFullScreenSearchOpen = false },
            )
        }
    }
}

/**
 * The dashboard's top area: title row with the home and wiki-switcher
 * actions, plus the search field below it (hidden behind a search icon
 * in compact-height layouts).
 */
@Composable
private fun DashboardHeader(
    feedState: FeedUiState,
    searchState: SearchUiState,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenFullScreenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val isCompactHeight = maxHeight < 500.dp
        val showSearchBar = !isCompactHeight || searchState.query.isNotBlank()

        Column(Modifier.fillMaxWidth()) {
            DashboardTitleRow(
                feedState = feedState,
                isCompactHeight = isCompactHeight,
                showSearchBar = showSearchBar,
                onArticleClick = onArticleClick,
                onOpenWikiPicker = onOpenWikiPicker,
                onOpenFullScreenSearch = onOpenFullScreenSearch,
            )

            if (showSearchBar) {
                DashboardSearchField(
                    query = searchState.query,
                    wikiName = searchState.wikiName,
                    isCompactHeight = isCompactHeight,
                    onQueryChange = onQueryChange,
                    onOpenFullScreenSearch = onOpenFullScreenSearch,
                )
            }
        }
    }
}

/** The "Dashboard" title, home button, and wiki switcher chip. */
@Composable
private fun DashboardTitleRow(
    feedState: FeedUiState,
    isCompactHeight: Boolean,
    showSearchBar: Boolean,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onOpenFullScreenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top))
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            softWrap = false,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCompactHeight && !showSearchBar) {
                IconButton(onClick = onOpenFullScreenSearch) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = stringResource(Res.string.common_search))
                }
            }
            // Jumps straight to the wiki's own main page. The title
            // comes from that wiki's siteinfo and is never assumed,
            // since custom wikis can rename it. This reuses the normal
            // article-open path so it lands in a tab exactly like any
            // other link.
            IconButton(
                onClick = {
                    val wikiId = feedState.wiki?.id ?: return@IconButton
                    val mainPage = feedState.mainPageTitle ?: return@IconButton
                    onArticleClick(wikiId, mainPage)
                },
                enabled = feedState.wiki != null && feedState.mainPageTitle != null,
            ) {
                Icon(imageVector = Icons.Filled.Home, contentDescription = stringResource(Res.string.dashboard_go_to_main_page))
            }
            WikiSwitcherChip(
                wikiName = feedState.wiki?.name.orEmpty(),
                onClick = onOpenWikiPicker,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** The search field beneath the title row, tappable-through to the full-screen overlay in compact-height layouts. */
@Composable
private fun DashboardSearchField(
    query: String,
    wikiName: String,
    isCompactHeight: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenFullScreenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(Res.string.dashboard_search_wiki, wikiName)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_clear))
                    }
                }
            },
            readOnly = isCompactHeight,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.large,
        )
        if (isCompactHeight) {
            Box(
                Modifier.matchParentSize()
                    .clickable(onClick = onOpenFullScreenSearch),
            ) {}
        }
    }
}

/** The Feed/Relevant tab row and its selected content. */
@Composable
private fun DashboardTabs(
    tabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    feedState: FeedUiState,
    relevantState: RelevantLinksUiState,
    openTitles: ImmutableSet<String>,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onRefreshFeed: () -> Unit,
    onShuffleRandom: () -> Unit,
    onOpenCategoryBrowse: () -> Unit,
    onOpenTrending: () -> Unit,
    onRefreshRelevant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SecondaryTabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == TAB_FEED, onClick = { onTabIndexChange(TAB_FEED) }, text = { Text(stringResource(Res.string.dashboard_tab_feed)) })
            Tab(selected = tabIndex == TAB_RELEVANT, onClick = { onTabIndexChange(TAB_RELEVANT) }, text = { Text(stringResource(Res.string.dashboard_tab_relevant)) })
        }
        when (tabIndex) {
            TAB_FEED -> FeedTabContent(
                state = feedState,
                openTitles = openTitles,
                onArticleClick = onArticleClick,
                onOpenWikiPicker = onOpenWikiPicker,
                onRefresh = onRefreshFeed,
                onShuffleRandom = onShuffleRandom,
                onOpenCategoryBrowse = onOpenCategoryBrowse,
                onOpenTrending = onOpenTrending,
            )
            else -> RelevantLinksTabContent(
                state = relevantState,
                onArticleClick = { title -> feedState.wiki?.id?.let { onArticleClick(it, title) } },
                onRefresh = onRefreshRelevant,
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    state: SearchUiState,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onSearchFor: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.results.isEmpty() && state.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.dashboard_no_search_results, state.query),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.suggestion?.let { suggestion ->
                        DidYouMeanRow(suggestion) { onSearchFor(suggestion) }
                    }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.rewrittenQuery?.let { rewritten ->
                    item {
                        Text(
                            stringResource(Res.string.dashboard_showing_results_for, rewritten),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.suggestion?.let { suggestion ->
                    item {
                        Box(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            DidYouMeanRow(suggestion) { onSearchFor(suggestion) }
                        }
                    }
                }
                items(state.results, key = { it.pageid }) { page ->
                    ArticleCard(
                        title = page.title,
                        extract = page.extract.orEmpty(),
                        thumbnailUrl = page.thumbnail?.source,
                        showImages = state.showImages,
                        onClick = { onArticleClick(state.wikiId, page.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DidYouMeanRow(suggestion: String, onClick: () -> Unit) {
    Text(
        text = stringResource(Res.string.dashboard_did_you_mean, suggestion),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun FullScreenSearchOverlay(
    searchState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearchFor: (String) -> Unit,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        FullScreenSearchField(
            query = searchState.query,
            wikiName = searchState.wikiName,
            onQueryChange = onQueryChange,
            onClose = onClose,
        )

        Box(Modifier.weight(1f).imePadding()) {
            SearchResultsContent(searchState, onArticleClick, onSearchFor = onSearchFor)
        }
    }
}

/** The back button and search field row at the top of [FullScreenSearchOverlay]. */
@Composable
private fun FullScreenSearchField(query: String, wikiName: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.article_top_bar_close_search))
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            placeholder = { Text(stringResource(Res.string.dashboard_search_wiki, wikiName)) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = MaterialTheme.shapes.large,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTabContent(
    state: FeedUiState,
    openTitles: ImmutableSet<String>,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onRefresh: () -> Unit,
    onShuffleRandom: () -> Unit,
    onOpenCategoryBrowse: () -> Unit,
    onOpenTrending: () -> Unit,
) {
    val wikiId = state.wiki?.id.orEmpty()
    val nothingToShowYet = state.isLoading && state.recentChanges.isEmpty() && state.continueReading.isEmpty() &&
        state.savedPages.isEmpty() && state.trending.isEmpty()
    val fullyFailed = !state.isLoading && state.recentChanges.isEmpty() && state.continueReading.isEmpty() &&
        state.savedPages.isEmpty() && state.trending.isEmpty() && state.errorMessage != null

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            nothingToShowYet -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            fullyFailed -> FeedErrorContent(
                errorMessage = state.errorMessage.orEmpty(),
                onRefresh = onRefresh,
                onOpenWikiPicker = onOpenWikiPicker,
                modifier = Modifier.fillMaxSize(),
            )
            else -> FeedListContent(
                state = state,
                wikiId = wikiId,
                openTitles = openTitles,
                onArticleClick = onArticleClick,
                onRefresh = onRefresh,
                onShuffleRandom = onShuffleRandom,
                onOpenCategoryBrowse = onOpenCategoryBrowse,
                onOpenTrending = onOpenTrending,
            )
        }
    }
}

/** The "could not load wiki" error state, shown by [FeedTabContent] when the feed has nothing at all to show. */
@Composable
private fun FeedErrorContent(errorMessage: String, onRefresh: () -> Unit, onOpenWikiPicker: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        FeedErrorContentBody(errorMessage = errorMessage, onRefresh = onRefresh, onOpenWikiPicker = onOpenWikiPicker)
    }
}

/** The text and retry/switch-wiki buttons inside [FeedErrorContent], split out to keep that function's own nesting shallow. */
@Composable
private fun FeedErrorContentBody(errorMessage: String, onRefresh: () -> Unit, onOpenWikiPicker: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.dashboard_could_not_load_wiki), style = MaterialTheme.typography.titleMedium)
        Text(
            errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Row {
            TextButton(onClick = onRefresh) { Text(stringResource(Res.string.common_retry)) }
            TextButton(onClick = onOpenWikiPicker) { Text(stringResource(Res.string.dashboard_switch_wiki)) }
        }
    }
}

/** The normal, fully-loaded feed list: trending, continue reading, saved, and recent activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListContent(
    state: FeedUiState,
    wikiId: String,
    openTitles: ImmutableSet<String>,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onRefresh: () -> Unit,
    onShuffleRandom: () -> Unit,
    onOpenCategoryBrowse: () -> Unit,
    onOpenTrending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.trending.isNotEmpty()) {
            item {
                TrendingCard(
                    wikiName = state.wiki?.name.orEmpty(),
                    trending = state.trending.toImmutableList(),
                    expandable = state.trendingExpandable,
                    onArticleClick = { title -> onArticleClick(wikiId, title) },
                    onOpenTrending = onOpenTrending,
                )
            }
        }
        if (state.continueReading.isNotEmpty()) {
            item {
                HorizontalArticleRow(
                    title = stringResource(Res.string.dashboard_continue_reading),
                    pages = state.continueReading.toImmutableList(),
                    showImages = state.showImages,
                    onClick = { title -> onArticleClick(wikiId, title) },
                )
            }
        }
        if (state.savedPages.isNotEmpty()) {
            item {
                HorizontalArticleRow(
                    title = stringResource(Res.string.dashboard_saved),
                    pages = state.savedPages.toImmutableList(),
                    showImages = state.showImages,
                    onClick = { title -> onArticleClick(wikiId, title) },
                )
            }
        }
        item {
            CategoryBrowseEntry(onClick = onOpenCategoryBrowse)
        }
        item {
            RandomPickCard(
                page = state.randomPick,
                showImages = state.showImages,
                onClick = { title -> onArticleClick(wikiId, title) },
                onShuffle = onShuffleRandom,
            )
        }
        item {
            Text(
                text = stringResource(Res.string.dashboard_recent_activity_on, state.wiki?.name.orEmpty()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (state.recentChanges.isEmpty() && !state.isLoading) {
            item {
                Text(
                    stringResource(Res.string.dashboard_no_recent_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(state.recentChanges, key = { it.title }) { change ->
            RecentChangeRow(
                change = change,
                onClick = { onArticleClick(wikiId, change.title) },
                trailingContent = if (change.title in openTitles) {
                    { OpenTabIndicator() }
                } else {
                    null
                },
            )
        }
        item {
            TextButton(onClick = onRefresh, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(stringResource(Res.string.common_refresh))
            }
        }
    }
}

@Composable
private fun HorizontalArticleRow(
    title: String,
    pages: ImmutableList<SavedPage>,
    showImages: Boolean,
    onClick: (title: String) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pages, key = { it.title }) { page ->
                CompactArticleChip(
                    title = page.title,
                    thumbnailUrl = page.thumbnailUrl,
                    showImage = showImages,
                    onClick = { onClick(page.title) },
                )
            }
        }
    }
}

@Composable
private fun CategoryBrowseEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(stringResource(Res.string.category_browse_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RandomPickCard(
    page: PageSummaryDto?,
    showImages: Boolean,
    onClick: (title: String) -> Unit,
    onShuffle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = page != null) { page?.let { onClick(it.title) } },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.dashboard_random_pick),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    page?.title ?: stringResource(Res.string.common_loading),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showImages && !page?.thumbnail?.source.isNullOrBlank()) {
                AsyncImage(
                    model = page.thumbnail.source,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = stringResource(Res.string.dashboard_shuffle))
            }
        }
    }
}

@Composable
private fun RecentChangeRow(
    change: RecentChangeEntry,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(change.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!change.user.isNullOrBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(
                    stringResource(Res.string.dashboard_edited_by, change.user.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            trailingContent?.let {
                Spacer(Modifier.size(6.dp))
                it()
            }
        }
    }
}

@Composable
private fun TrendingCard(
    wikiName: String,
    trending: ImmutableList<TrendingArticle>,
    expandable: Boolean,
    onArticleClick: (title: String) -> Unit,
    onOpenTrending: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                )
                Text(
                    text = stringResource(Res.string.dashboard_trending_on, wikiName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            trending.forEachIndexed { index, article ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                TrendingRow(rank = index + 1, trending = article) { onArticleClick(article.title) }
            }
            if (expandable) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                TextButton(onClick = onOpenTrending, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.dashboard_more_trending))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TrendingRow(rank: Int, trending: TrendingArticle, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                text = trending.title,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (trending.isItalicized) FontStyle.Italic else FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trending.views?.let { views ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.dashboard_views_count, formatViewCount(views)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TrendArrow(trending.trend)
                }
            }
        }
        if (!trending.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = trending.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** The small green up / red down arrow next to a trending row's view count. Null trend, no arrow. */
@Composable
private fun TrendArrow(trend: TrendDirection?) {
    val (icon, tint) = when (trend) {
        TrendDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF2E7D32)
        TrendDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown to Color(0xFFC62828)
        TrendDirection.FLAT, null -> return
    }
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.padding(start = 4.dp).size(14.dp),
    )
}

internal fun formatViewCount(views: Long): String {
    val (divisor, suffix) = when {
        views >= 1_000_000 -> 1_000_000.0 to "M"
        views >= 1_000 -> 1_000.0 to "K"
        else -> return views.toString()
    }
    val scaled = (views / divisor * 10).toLong()
    val whole = scaled / 10
    val tenth = scaled % 10
    return if (tenth == 0L) "$whole$suffix" else "$whole.$tenth$suffix"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelevantLinksTabContent(
    state: RelevantLinksUiState,
    onArticleClick: (title: String) -> Unit,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.titles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.titles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.dashboard_relevant_empty, state.wikiName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = state.label ?: stringResource(Res.string.dashboard_relevant_default_label, state.wikiName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(state.titles, key = { it }) { title ->
                    ArticleCard(
                        title = title,
                        extract = "",
                        thumbnailUrl = null,
                        showImages = false,
                        onClick = { onArticleClick(title) },
                    )
                }
            }
        }
    }
}
