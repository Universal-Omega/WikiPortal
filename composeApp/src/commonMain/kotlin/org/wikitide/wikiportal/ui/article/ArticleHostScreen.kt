package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import io.ktor.http.Url
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.AuthDomains
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.ui.tabs.TabsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Composable
fun ArticleHostScreen(
    onBack: () -> Unit,
    tabsRepository: TabsRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val activeTabId by tabsRepository.activeTabId.collectAsState()
    val isSwitcherOpen by tabsRepository.isSwitcherOpen.collectAsState()
    val materializedTabIds by tabsRepository.materializedTabIds.collectAsState()

    if (tabs.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(Modifier.fillMaxSize()) {
        tabs.forEach { tab ->
            // A tab that was restored from a previous session but hasn't
            // actually been tapped yet this launch has no WebView at
            // all, not just a paused one. It still shows up fine in
            // TabsListScreen and TabsScreen, which only read the tab's
            // own metadata, not a live view of it. See
            // TabsRepository.materializedTabIds.
            if (tab.id !in materializedTabIds && tab.id != activeTabId) return@forEach
            key(tab.id) {
                val isActive = tab.id == activeTabId && !isSwitcherOpen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isActive) 1f else 0f }
                        .zIndex(if (isActive) 1f else 0f),
                ) {
                    SingleArticleTab(tab = tab, isActive = isActive, onBack = onBack)
                }
            }
        }

        if (isSwitcherOpen) {
            TabsScreen(
                onBack = { tabsRepository.setSwitcherOpen(false) },
                onSelectTab = { tabId ->
                    tabsRepository.setActiveTab(tabId)
                    tabsRepository.setSwitcherOpen(false)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleArticleTab(
    tab: ArticleTab,
    isActive: Boolean,
    onBack: () -> Unit,
    repository: AppRepository = koinInject(),
    api: MediaWikiApi = koinInject(),
    tabsRepository: TabsRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val presetWikis by repository.presetWikis.collectAsState()
    val customWikis by repository.customWikis.collectAsState()
    // Reactive, not a one-time snapshot, so a metadata refresh that
    // resolves after this tab was already opened, for example this
    // wiki's main page title for the home button below, actually reaches
    // this tab instead of being frozen out by a remember() taken before
    // it existed.
    val allWikis = remember(presetWikis, customWikis) { presetWikis + customWikis }
    val site = remember(tab.wikiId, allWikis) { allWikis.firstOrNull { it.id == tab.wikiId } ?: repository.activeWiki.value }
    val initialTitle = remember(tab.id) { tab.title }
    val textScale by repository.textScale.collectAsState()
    val offlineKeys by repository.offlineKeys.collectAsState()
    val confirmExternalNavigation by repository.confirmExternalNavigation.collectAsState()
    val scope = rememberCoroutineScope()

    // A link the WebView tried to load that points somewhere outside
    // every wiki this app knows about, waiting on the person to decide
    // whether to actually follow it. Null means no such prompt is
    // showing. See the RequestInterceptor below and the AlertDialog
    // near the end of this composable.
    var pendingExternalUrl by remember(tab.id) { mutableStateOf<String?>(null) }

    // Whether the tab's live navigation currently sits outside every
    // saved wiki. Seeded from the tab's own saved currentUrl on
    // resume, so a tab reopened straight into an external page starts
    // out already knowing that, rather than assuming it's on site
    // until the next navigation proves otherwise.
    var isOnExternalSite by remember(tab.id) {
        mutableStateOf(
            tab.currentUrl?.let { url -> !AuthDomains.matches(url) && allWikis.none { url.startsWith(it.baseUrl) } } ?: false,
        )
    }

    // Read through rememberUpdatedState rather than as a remember(site)
    // key, so toggling the setting mid-session is picked up by the
    // interceptor right away without tearing down and recreating the
    // navigator, which owns the WebView's actual navigation history.
    val confirmExternalNavigationState = rememberUpdatedState(confirmExternalNavigation)
    val allWikisState = rememberUpdatedState(allWikis)
    val isOnExternalSiteState = rememberUpdatedState(isOnExternalSite)

    val navigator = rememberWebViewNavigator(
        requestInterceptor = remember(site) {
            object : RequestInterceptor {
                override fun onInterceptUrlRequest(
                    request: WebRequest,
                    navigator: WebViewNavigator,
                ): WebRequestInterceptResult {
                    val url = request.url
                    if (url.startsWith("data:")) return WebRequestInterceptResult.Allow
                    val isAuthRequest = AuthDomains.matches(url)
                    val targetSite = when {
                        isAuthRequest -> site
                        else -> allWikisState.value.firstOrNull { url.startsWith(it.baseUrl) }
                    }

                    if (targetSite == null) {
                        if (isOnExternalSiteState.value || !confirmExternalNavigationState.value) {
                            isOnExternalSite = true
                            return WebRequestInterceptResult.Allow
                        }

                        pendingExternalUrl = url
                        return WebRequestInterceptResult.Reject
                    }

                    isOnExternalSite = false
                    if (url.contains("useskin=${targetSite.skin}")) return WebRequestInterceptResult.Allow
                    // looksLikeArticleRequest only recognizes this site's
                    // own article URL shapes, which an auth host's login
                    // and callback pages don't match at all, so that
                    // check is skipped for those. There isn't a wide
                    // variety of request types to worry about missing
                    // there the way there is on the wiki's own domain,
                    // where skipping it would risk rewriting API calls
                    // and static assets that have no business carrying a
                    // skin param.
                    if (!isAuthRequest && !looksLikeArticleRequest(url, targetSite)) return WebRequestInterceptResult.Allow
                    val rewritten = withAppSkin(url, targetSite) ?: return WebRequestInterceptResult.Allow
                    scope.launch { navigator.loadUrl(rewritten) }
                    return WebRequestInterceptResult.Reject
                }
            }
        },
    )

    var historyNavTrigger by remember(tab.id) { mutableStateOf(0) }

    // Registered once, at tab creation, keyed by tab.id and not isActive.
    // See TabsRepository's comment on backHandlers for why this must not
    // be tied to activation timing.
    DisposableEffect(tab.id) {
        tabsRepository.registerBackHandler(tab.id) {
            if (navigator.canGoBack) {
                navigator.navigateBack()
                historyNavTrigger++
                true
            } else {
                false
            }
        }
        onDispose { tabsRepository.unregisterBackHandler(tab.id) }
    }

    // Keeps TabsRepository.activeTabCanGoBack in sync with this tab's own
    // in-page history, but only while this tab is actually the active
    // one.
    LaunchedEffect(isActive, navigator.canGoBack) {
        if (isActive) tabsRepository.setActiveTabCanGoBack(navigator.canGoBack)
    }

    var pageState by remember(tab.id) {
        mutableStateOf(
            WikiPageState(title = initialTitle, canonicalTitle = initialTitle, displaySiteName = site.name, url = tab.currentUrl.orEmpty()),
        )
    }

    // The interceptor above only ever sees genuinely new top-level
    // requests, so it correctly flips isOnExternalSite back to false
    // whenever a link takes the tab to a known wiki. It never sees the
    // system back gesture or the "Refresh" menu item, though, since
    // navigator.navigateBack() and navigator.reload() replay existing
    // history rather than issuing a fresh request.
    LaunchedEffect(pageState.url, pageState.isLoading) {
        if (pageState.isLoading) return@LaunchedEffect
        val settledUrl = pageState.url
        if (settledUrl.isBlank()) return@LaunchedEffect
        isOnExternalSite = allWikis.none { settledUrl.startsWith(it.baseUrl) } && !AuthDomains.matches(settledUrl)
    }

    var isSearchBarOpen by remember(tab.id) { mutableStateOf(false) }
    var searchQuery by remember(tab.id) { mutableStateOf("") }
    var searchResult by remember(tab.id) { mutableStateOf(PageSearchResult()) }
    val searchFocusRequester = remember(tab.id) { FocusRequester() }

    LaunchedEffect(isSearchBarOpen, searchQuery) {
        if (!isSearchBarOpen) return@LaunchedEffect
        if (searchQuery.isBlank()) {
            searchResult = PageSearchResult()
            clearPageSearch(navigator)
            return@LaunchedEffect
        }
        delay(250)
        searchResult = runPageSearch(navigator, searchQuery)
    }

    LaunchedEffect(isSearchBarOpen, pageState.isLoading) {
        if (!isSearchBarOpen || !pageState.isLoading) return@LaunchedEffect
        isSearchBarOpen = false
        searchQuery = ""
        searchResult = PageSearchResult()
    }

    LaunchedEffect(isSearchBarOpen) {
        while (isSearchBarOpen) {
            delay(700)
            if (searchQuery.isNotBlank() && isPageSearchDirty(navigator)) {
                searchResult = runPageSearch(navigator, searchQuery)
            }
        }
    }

    var pageSummary by remember(tab.id) { mutableStateOf<PageSummaryDto?>(null) }
    var offlineHtml by remember(tab.id) { mutableStateOf<String?>(null) }
    var isSavingOffline by remember(tab.id) { mutableStateOf(false) }
    var isRefreshing by remember(tab.id) { mutableStateOf(false) }
    var isOverflowMenuOpen by remember(tab.id) { mutableStateOf(false) }
    // The title a summary was last fetched for, so reactivating a tab
    // that hasn't navigated anywhere new doesn't re-fetch and re-record a
    // visit it already has.
    var summarizedTitle by remember(tab.id) { mutableStateOf<String?>(null) }
    var nativeWebViewRef by remember(tab.id) { mutableStateOf<NativeWebView?>(null) }
    val savedPages by repository.savedPages.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    // Every open tab's WebView stays mounted the whole time it's open, so
    // switching tabs never reloads one, but an inactive tab has no
    // business still running its page's JS and timers in the background.
    // This pauses the underlying engine, see setWebViewActive, without
    // touching navigation state, so reactivating a tab shows it exactly
    // as it was left.
    LaunchedEffect(isActive, nativeWebViewRef) {
        nativeWebViewRef?.let { setWebViewActive(it, isActive) }
    }

    val currentTitle = pageState.canonicalTitle.ifBlank { initialTitle }
    val isSaved = savedPages.any { it.wikiId == site.id && it.title == currentTitle }
    val isOfflineSaved = repository.isOfflineSaved(site.id, currentTitle)

    // Gated on isActive so a background tab doesn't do this local lookup
    // every time any tab's offline status changes. It re-runs the moment
    // the tab becomes active again, which is early enough to still land
    // before the person would notice.
    LaunchedEffect(isActive, tab.id, currentTitle, offlineKeys) {
        if (!isActive) return@LaunchedEffect
        offlineHtml = if (isOfflineSaved) repository.getOfflineArticleHtml(site.id, currentTitle) else null
    }

    // Gated on isActive, and deduped by summarizedTitle, so background
    // tabs never make their own network calls or history writes, and a
    // tab reactivating on the same title it was last summarized for
    // doesn't redundantly repeat either.
    LaunchedEffect(isActive, currentTitle, pageState.isLoading) {
        if (!isActive || pageState.isLoading || currentTitle.isBlank()) return@LaunchedEffect
        // This has to happen before the summarizedTitle dedup check
        // below, not after it. A pull-to-refresh reloads the page at its
        // current title, so currentTitle == summarizedTitle is exactly
        // the common case for a refresh finishing, and returning early
        // before reaching this used to leave isRefreshing stuck true,
        // and the indicator stuck visible, forever.
        if (isRefreshing) {
            isRefreshing = false
            pullToRefreshState.animateToHidden()
        }
        if (currentTitle == summarizedTitle) return@LaunchedEffect
        val isOffSiteContent = isOnExternalSite || AuthDomains.matches(pageState.url)
        // getPageSummary assumes currentTitle is a real article on
        // site, which isn't true for an auth page or an external link
        // followed in this tab, so that call is skipped for those.
        // The tab and history are still updated either way, using
        // pageState.url as the actual address to return to, rather
        // than reconstructing one from title and wikiId that would
        // 404 on a page that was never really an article here.
        val freshSummary = if (isOffSiteContent) null else api.getPageSummary(site, currentTitle).getOrNull()
        summarizedTitle = currentTitle
        pageSummary = freshSummary
        tabsRepository.updateTab(
            tab.id, currentTitle, freshSummary?.thumbnail?.source, pageState.displaySiteName.orEmpty(), freshSummary?.extract,
            pageState.url, clearSummary = isOffSiteContent,
        )
        repository.recordVisit(
            SavedPage(
                wikiId = site.id,
                wikiName = site.name,
                title = currentTitle,
                extract = pageSummary?.extract.orEmpty(),
                thumbnailUrl = pageSummary?.thumbnail?.source,
                timestampEpochMillis = nowEpochMillis(),
                url = pageState.url,
            ),
        )
    }

    suspend fun capturePreviewAndRun(action: () -> Unit) {
        nativeWebViewRef?.let { webView ->
            captureTabPreview(webView)?.let { bitmap ->
                tabsRepository.updatePreview(tab.id, bitmap)
            }
        }
        action()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    capturePreviewAndRun(onBack)
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    title = {
                        // Show "Title - SiteName" only when the current
                        // page matches a wiki we actually know about.
                        // Otherwise, for example an external link followed
                        // in this tab, just show the page's own raw
                        // title, the same way an ordinary browser would.
                        // There is no reconstructed suffix for a site we
                        // don't have a name for. A page whose title is
                        // already exactly the site's name, for example a
                        // wiki's own main page sharing the wiki's name,
                        // skips the suffix entirely rather than
                        // repeating it, e.g. "MediaWiki" rather than
                        // "MediaWiki - MediaWiki".
                        val siteName = pageState.displaySiteName
                        val displayedTitle = when {
                            siteName == null -> pageState.title.ifBlank { currentTitle }
                            currentTitle.equals(siteName, ignoreCase = true) -> siteName
                            currentTitle.endsWith("- $siteName", ignoreCase = true) -> currentTitle
                            else -> "$currentTitle - $siteName"
                        }
                        Text(
                            text = displayedTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    actions = {
                        BadgedBox(
                            badge = { if (tabs.isNotEmpty()) Badge { Text("${tabs.size}") } },
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        capturePreviewAndRun {
                                            tabsRepository.setSwitcherOpen(true)
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Tab, contentDescription = "Tabs")
                            }
                        }

                        IconButton(onClick = { isOverflowMenuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = isOverflowMenuOpen,
                            onDismissRequest = { isOverflowMenuOpen = false },
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 6.dp,
                        ) {
                            if (navigator.canGoForward) {
                                DropdownMenuItem(
                                    text = { Text("Forward") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                                    onClick = {
                                        isOverflowMenuOpen = false
                                        navigator.navigateForward()
                                        historyNavTrigger++
                                    },
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuOpen = false
                                    navigator.reload()
                                },
                            )

                            DropdownMenuItem(
                                text = { Text("Find on page") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuOpen = false
                                    isSearchBarOpen = true
                                },
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(if (isSaved) "Unsave" else "Save for later") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                },
                                onClick = {
                                    isOverflowMenuOpen = false
                                    repository.toggleSaved(
                                        SavedPage(
                                            wikiId = site.id,
                                            wikiName = site.name,
                                            title = currentTitle,
                                            extract = pageSummary?.extract.orEmpty(),
                                            thumbnailUrl = pageSummary?.thumbnail?.source,
                                            timestampEpochMillis = nowEpochMillis(),
                                            url = pageState.url,
                                        ),
                                    )
                                },
                            )

                            DropdownMenuItem(
                                text = { Text(if (isOfflineSaved) "Remove offline copy" else "Save for offline reading") },
                                leadingIcon = {
                                    if (isSavingOffline) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isOfflineSaved) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = if (isOfflineSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                },
                                onClick = {
                                    isOverflowMenuOpen = false
                                    if (isOfflineSaved) {
                                        repository.removeOfflineArticle(site.id, currentTitle)
                                    } else {
                                        isSavingOffline = true
                                    }
                                },
                            )
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                )

                if (isSearchBarOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Find on page") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { scope.launch { searchResult = stepPageSearch(navigator, forward = true) } },
                            ),
                        )

                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = if (searchResult.matchCount > 0) {
                                    "${searchResult.activeIndex}/${searchResult.matchCount}"
                                } else {
                                    "0/0"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }

                        IconButton(
                            enabled = searchResult.matchCount > 0,
                            onClick = { scope.launch { searchResult = stepPageSearch(navigator, forward = false) } },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                        }

                        IconButton(
                            enabled = searchResult.matchCount > 0,
                            onClick = { scope.launch { searchResult = stepPageSearch(navigator, forward = true) } },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                        }

                        IconButton(
                            onClick = {
                                isSearchBarOpen = false
                                searchQuery = ""
                                searchResult = PageSearchResult()
                                scope.launch { clearPageSearch(navigator) }
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    }

                    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                }

                if (pageState.isLoading) {
                    LinearProgressIndicator(
                        progress = { pageState.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            WikiArticleReader(
                site = site,
                title = initialTitle,
                navigator = navigator,
                textScale = textScale,
                offlineHtml = offlineHtml,
                allWikis = allWikis,
                historyNavTrigger = historyNavTrigger,
                restoreUrl = tab.currentUrl,
                onWebViewReady = { nativeWebViewRef = it },
                onStateChanged = { newState -> pageState = newState },
                modifier = Modifier.fillMaxSize(),
            )

            // A manual swipe-to-refresh: a thin invisible strip at the
            // very top edge that tracks a downward drag starting there,
            // driving a real PullToRefreshState by hand rather than the
            // usual Modifier.pullToRefresh. That modifier depends on the
            // nested scroll protocol, which a native WebView does not
            // take part in, so it would never receive the drag at all.
            var dragAmount by remember(tab.id) { mutableStateOf(0f) }
            val pullThresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.TopCenter)
                    .pointerInput(tab.id) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val triggered = dragAmount >= pullThresholdPx && !isRefreshing
                                dragAmount = 0f
                                scope.launch {
                                    if (triggered) {
                                        pullToRefreshState.animateToThreshold()
                                        isRefreshing = true
                                        navigator.reload()
                                    } else {
                                        pullToRefreshState.animateToHidden()
                                    }
                                }
                            },
                            onVerticalDrag = { change, delta ->
                                if (!isRefreshing) {
                                    dragAmount = (dragAmount + delta).coerceIn(0f, pullThresholdPx * 1.5f)
                                    change.consume()
                                    scope.launch {
                                        pullToRefreshState.snapTo((dragAmount / pullThresholdPx).coerceIn(0f, 1f))
                                    }
                                }
                            },
                        )
                    },
            )

            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    LaunchedEffect(isSavingOffline) {
        if (!isSavingOffline) return@LaunchedEffect

        val parsed = api.parsePage(site, currentTitle).getOrNull()
        if (parsed != null && parsed.text.isNotBlank()) {
            val selfContained = buildSelfContainedHtml(parsed.text, site.baseUrl, api)
            repository.saveOfflineArticle(
                SavedPage(
                    wikiId = site.id,
                    wikiName = site.name,
                    title = currentTitle,
                    thumbnailUrl = pageSummary?.thumbnail?.source,
                    timestampEpochMillis = nowEpochMillis(),
                ),
                selfContained,
            )
        }

        isSavingOffline = false
    }

    pendingExternalUrl?.let { url ->
        val host = runCatching { Url(url).host }.getOrNull()?.ifBlank { null }
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("Leave ${site.name}?") },
            text = { Text("This link goes to ${host ?: "an outside site"}, not ${site.name}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExternalUrl = null
                        isOnExternalSite = true
                        scope.launch { navigator.loadUrl(url) }
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("Stay here") }
            },
        )
    }
}
