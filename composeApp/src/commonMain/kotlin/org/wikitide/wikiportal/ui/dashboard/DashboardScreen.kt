package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.network.TrendingArticle
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.OpenTabIndicator
import org.wikitide.wikiportal.ui.components.WikiSwitcherChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOpenWikiPicker: () -> Unit,
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
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load articles", style = MaterialTheme.typography.titleMedium)
                    state.errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    TextButton(onClick = onOpenWikiPicker) { Text("Switch wiki") }
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
                            onArticleClick = { title -> onArticleClick(state.wiki?.id.orEmpty(), title) },
                        )
                    }
                }
                item {
                    Text(
                        text = "Random articles from ${state.wiki?.name.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (state.trending.isNotEmpty()) 8.dp else 0.dp, bottom = 4.dp),
                    )
                }
                items(state.articles, key = { it.pageid }) { page ->
                    ArticleCard(
                        title = page.title,
                        extract = page.extract.orEmpty(),
                        thumbnailUrl = page.thumbnail?.source,
                        showImages = state.showImages,
                        onClick = { onArticleClick(state.wiki?.id.orEmpty(), page.title) },
                        trailingContent = if (page.title in openTitles) {
                            { OpenTabIndicator() }
                        } else {
                            null
                        },
                    )
                }
                item {
                    TextButton(onClick = onRefresh, modifier = Modifier.padding(vertical = 12.dp)) {
                        Text("Show more")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingCard(wikiName: String, trending: List<TrendingArticle>, onArticleClick: (title: String) -> Unit) {
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
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = trending.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trending.views?.let { views ->
                Text(
                    text = "${formatViewCount(views)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatViewCount(views: Long): String {
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
