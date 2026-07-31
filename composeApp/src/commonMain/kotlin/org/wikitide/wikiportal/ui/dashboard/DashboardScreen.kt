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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.network.RecentChangeEntry
import org.wikitide.wikiportal.network.TrendDirection
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.CompactArticleChip
import org.wikitide.wikiportal.ui.components.OpenTabIndicator
import org.wikitide.wikiportal.ui.components.WikiSwitcherChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
    onOpenCategoryBrowse: () -> Unit,
    onOpenTrending: () -> Unit,
    exploreViewModel: ExploreViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel(),
    relevantLinksViewModel: RelevantLinksViewModel = koinViewModel(),
    tabsRepository: TabsRepository = koinInject(),
) {
    val exploreState by exploreViewModel.uiState.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val relevantState by relevantLinksViewModel.state.collectAsState()
    val tabs by tabsRepository.tabs.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }
    var isFullScreenSearchOpen by remember { mutableStateOf(false) }

    // Titles within the current wiki that already have an open tab. This
    // drives the "Open" indicator, so tapping one of these jumps to the
    // existing tab, see App.kt's openArticle, instead of opening a
    // duplicate. This works the same way the old Explore screen did it.
    val openTitles = remember(tabs, exploreState.wiki?.id) {
        tabs.filter { it.wikiId == exploreState.wiki?.id }.map { it.title }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val isCompactHeight = maxHeight < 500.dp
                val showSearchBar = !isCompactHeight || searchState.query.isNotBlank()

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top))
                            .heightIn(min = 64.dp)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Dashboard",
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
                                IconButton(onClick = { isFullScreenSearchOpen = true }) {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                                }
                            }
                            // Jumps straight to the wiki's own main page. The
                            // title comes from that wiki's siteinfo and is
                            // never assumed, since custom wikis can rename it.
                            // This reuses the normal article-open path so it
                            // lands in a tab exactly like any other link.
                            IconButton(
                                onClick = {
                                    val wikiId = exploreState.wiki?.id ?: return@IconButton
                                    val mainPage = exploreState.mainPageTitle ?: return@IconButton
                                    onArticleClick(wikiId, mainPage)
                                },
                                enabled = exploreState.wiki != null && exploreState.mainPageTitle != null,
                            ) {
                                Icon(imageVector = Icons.Filled.Home, contentDescription = "Go to main page")
                            }
                            WikiSwitcherChip(
                                wikiName = exploreState.wiki?.name.orEmpty(),
                                onClick = onOpenWikiPicker,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    if (showSearchBar) {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = searchState.query,
                                onValueChange = searchViewModel::onQueryChange,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text("Search ${searchState.wikiName}") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchState.query.isNotEmpty()) {
                                        IconButton(onClick = { searchViewModel.onQueryChange("") }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Clear")
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
                                        .clickable(onClick = { isFullScreenSearchOpen = true }),
                                ) {}
                            }
                        }
                    }
                }
            }

            if (searchState.query.isNotBlank()) {
                SearchResultsContent(searchState, onArticleClick, onSearchFor = searchViewModel::searchFor)
            } else {
                SecondaryTabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Feed") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Relevant") })
                }
                when (tabIndex) {
                    0 -> FeedTabContent(
                        state = exploreState,
                        openTitles = openTitles,
                        onArticleClick = onArticleClick,
                        onOpenWikiPicker = onOpenWikiPicker,
                        onRefresh = exploreViewModel::refresh,
                        onShuffleRandom = exploreViewModel::shuffleRandomPick,
                        onOpenCategoryBrowse = onOpenCategoryBrowse,
                        onOpenTrending = onOpenTrending,
                    )
                    else -> RelevantLinksTabContent(
                        state = relevantState,
                        onArticleClick = { title -> exploreState.wiki?.id?.let { onArticleClick(it, title) } },
                        onRefresh = relevantLinksViewModel::refresh,
                    )
                }
            }
        }

        if (isFullScreenSearchOpen) {
            FullScreenSearchOverlay(
                searchState = searchState,
                searchViewModel = searchViewModel,
                onArticleClick = onArticleClick,
                onClose = { isFullScreenSearchOpen = false },
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
                        "No results for \"${state.query}\"",
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
                            "Showing results for \"$rewritten\"",
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
        text = "Did you mean: $suggestion?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun FullScreenSearchOverlay(
    searchState: SearchUiState,
    searchViewModel: SearchViewModel,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
            OutlinedTextField(
                value = searchState.query,
                onValueChange = searchViewModel::onQueryChange,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                placeholder = { Text("Search ${searchState.wikiName}") },
                singleLine = true,
                trailingIcon = {
                    if (searchState.query.isNotEmpty()) {
                        IconButton(onClick = { searchViewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                shape = MaterialTheme.shapes.large,
            )
        }
        Box(Modifier.weight(1f).imePadding()) {
            SearchResultsContent(searchState, onArticleClick, onSearchFor = searchViewModel::searchFor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTabContent(
    state: ExploreUiState,
    openTitles: Set<String>,
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
            fullyFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load this wiki", style = MaterialTheme.typography.titleMedium)
                    state.errorMessage.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    Row {
                        TextButton(onClick = onRefresh) { Text("Retry") }
                        TextButton(onClick = onOpenWikiPicker) { Text("Switch wiki") }
                    }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.trending.isNotEmpty()) {
                    item {
                        TrendingCard(
                            wikiName = state.wiki?.name.orEmpty(),
                            trending = state.trending,
                            expandable = state.trendingExpandable,
                            onArticleClick = { title -> onArticleClick(wikiId, title) },
                            onOpenTrending = onOpenTrending,
                        )
                    }
                }
                if (state.continueReading.isNotEmpty()) {
                    item {
                        HorizontalArticleRow(
                            title = "Continue reading",
                            pages = state.continueReading,
                            showImages = state.showImages,
                            onClick = { title -> onArticleClick(wikiId, title) },
                        )
                    }
                }
                if (state.savedPages.isNotEmpty()) {
                    item {
                        HorizontalArticleRow(
                            title = "Saved",
                            pages = state.savedPages,
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
                        text = "Recent activity on ${state.wiki?.name.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                if (state.recentChanges.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            "No recent activity to show here",
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
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalArticleRow(
    title: String,
    pages: List<SavedPage>,
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
            Text("Browse by category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                    "Random pick",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    page?.title ?: "Loading...",
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
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle")
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
                    "Edited by ${change.user}",
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
    trending: List<TrendingArticle>,
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
                    text = "Trending on $wikiName",
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
                    Text("More trending")
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
                        text = "${formatViewCount(views)} views",
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
                    "Nothing to show here yet for ${state.wikiName}",
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
                        text = state.label ?: "Recent community/project activity on ${state.wikiName}",
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
