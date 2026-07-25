package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.ui.tabs.TabsScreen
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

    if (tabs.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(Modifier.fillMaxSize()) {
        tabs.forEach { tab ->
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
    val site = remember(tab.wikiId) { repository.allWikisNow().firstOrNull { it.id == tab.wikiId } ?: repository.activeWiki.value }
    val allWikis = remember { repository.allWikisNow() }
    val initialTitle = remember(tab.id) { tab.title }
    val textScale by repository.textScale.collectAsState()
    val offlineKeys by repository.offlineKeys.collectAsState()
    val scope = rememberCoroutineScope()

    val navigator = rememberWebViewNavigator(
        requestInterceptor = remember(site) {
            object : RequestInterceptor {
                override fun onInterceptUrlRequest(
                    request: WebRequest,
                    navigator: WebViewNavigator,
                ): WebRequestInterceptResult {
                    val url = request.url
                    if (url.contains("useskin=${site.skin}")) return WebRequestInterceptResult.Allow
                    if (url.startsWith("data:")) return WebRequestInterceptResult.Allow
                    if (!url.startsWith(site.baseUrl)) return WebRequestInterceptResult.Allow
                    if (!looksLikeArticleRequest(url, site)) return WebRequestInterceptResult.Allow
                    val rewritten = withAppSkin(url, site) ?: return WebRequestInterceptResult.Allow
                    scope.launch { navigator.loadUrl(rewritten) }
                    return WebRequestInterceptResult.Reject
                }
            }
        },
    )

    // Registered once, at tab creation, keyed by tab.id and not isActive.
    // See TabsRepository's comment on backHandlers for why this must not
    // be tied to activation timing.
    DisposableEffect(tab.id) {
        tabsRepository.registerBackHandler(tab.id) {
            if (navigator.canGoBack) {
                navigator.navigateBack()
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

    var pageState by remember(tab.id) { mutableStateOf(WikiPageState(title = initialTitle, canonicalTitle = initialTitle, displaySiteName = site.name)) }
    var pageSummary by remember(tab.id) { mutableStateOf<PageSummaryDto?>(null) }
    var offlineHtml by remember(tab.id) { mutableStateOf<String?>(null) }
    var isSavingOffline by remember(tab.id) { mutableStateOf(false) }
    var isRefreshing by remember(tab.id) { mutableStateOf(false) }
    var isOverflowMenuOpen by remember(tab.id) { mutableStateOf(false) }
    var nativeWebViewRef by remember(tab.id) { mutableStateOf<NativeWebView?>(null) }
    val savedPages by repository.savedPages.collectAsState()

    val currentTitle = pageState.canonicalTitle.ifBlank { initialTitle }
    val isSaved = savedPages.any { it.wikiId == site.id && it.title == currentTitle }
    val isOfflineSaved = repository.isOfflineSaved(site.id, currentTitle)

    LaunchedEffect(tab.id, currentTitle, offlineKeys) {
        offlineHtml = if (isOfflineSaved) repository.getOfflineArticleHtml(site.id, currentTitle) else null
    }

    LaunchedEffect(currentTitle, pageState.isLoading) {
        if (pageState.isLoading || currentTitle.isBlank()) return@LaunchedEffect
        isRefreshing = false
        // Fetch first, then fill in both pageSummary and the tab record
        // with the result.
        val freshSummary = api.getPageSummary(site, currentTitle).getOrNull()
        pageSummary = freshSummary
        tabsRepository.updateTab(
            tab.id, currentTitle, freshSummary?.thumbnail?.source, pageState.displaySiteName.orEmpty(), freshSummary?.extract,
        )
        repository.recordVisit(
            SavedPage(
                wikiId = site.id,
                wikiName = site.name,
                title = currentTitle,
                extract = pageSummary?.extract.orEmpty(),
                thumbnailUrl = pageSummary?.thumbnail?.source,
                timestampEpochMillis = nowEpochMillis(),
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
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        capturePreviewAndRun(onBack)
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }

                            if (navigator.canGoForward) {
                                IconButton(onClick = { navigator.navigateForward() }) {
                                    Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
                                }
                            }
                        }
                    },
                    title = {
                        // Show "Title - SiteName" only when the current
                        // page matches a wiki we actually know about.
                        // Otherwise, for example an external link followed
                        // in this tab, just show the page's own raw
                        // title, the same way an ordinary browser would.
                        // There is no reconstructed suffix for a site we
                        // don't have a name for.
                        Text(
                            text = pageState.displaySiteName?.let { "$currentTitle - $it" } ?: pageState.title.ifBlank { currentTitle },
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
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isSaved) "Unsave" else "Save for later") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = null,
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

                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuOpen = false
                                    navigator.reload()
                                },
                            )
                        }
                    },
                )

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
                onWebViewReady = { nativeWebViewRef = it },
                onStateChanged = { newState -> pageState = newState },
                modifier = Modifier.fillMaxSize(),
            )

            // A simple swipe-to-refresh: a thin invisible strip at the
            // very top edge that triggers a refresh on a downward drag
            // starting there. This is not the same as native pull to
            // refresh. Compose's PullToRefreshBox relies on the nested
            // scroll protocol, which a native WebView does not take part
            // in.
            var dragAmount by remember(tab.id) { mutableStateOf(0f) }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.TopCenter)
                    .pointerInput(tab.id) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAmount > 120f && !isRefreshing) {
                                    isRefreshing = true
                                    navigator.reload()
                                }
                                dragAmount = 0f
                            },
                            onVerticalDrag = { change, delta ->
                                if (delta > 0f) {
                                    dragAmount += delta
                                    change.consume()
                                }
                            },
                        )
                    },
            )

            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(28.dp),
                )
            }
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
}
