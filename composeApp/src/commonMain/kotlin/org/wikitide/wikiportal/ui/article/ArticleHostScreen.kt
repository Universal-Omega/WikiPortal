package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.data.model.AuthDomains
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.effectiveDisableSafeMode
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.PageSummaryDto
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.article_host_link_copied
import org.wikitide.wikiportal.resources.article_host_share_failed
import org.wikitide.wikiportal.ui.tabs.TabsScreen
import org.wikitide.wikiportal.util.ShareOutcome
import org.wikitide.wikiportal.util.nowEpochMillis
import org.wikitide.wikiportal.util.offline.captureArticleForOffline
import org.wikitide.wikiportal.util.offline.offlineLoadIdentityUrl
import org.wikitide.wikiportal.util.offline.offlineTitlesForWiki
import org.wikitide.wikiportal.util.offline.rewriteOfflineLinks
import org.wikitide.wikiportal.util.rememberPageSharer

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
    val openOfflineFromStart = tab.openedFromOffline
    val textScale by repository.textScale.collectAsState()
    val offlineKeys by repository.offlineKeys.collectAsState()
    val confirmExternalNavigation by repository.confirmExternalNavigation.collectAsState()
    val disableSafeMode by repository.disableSafeMode.collectAsState()
    // Off unless the app-wide setting is on, or this wiki has its own
    // override on, see WikiSite.disableSafeMode. Either one is enough.
    val effectiveDisableSafeMode = site.effectiveDisableSafeMode(disableSafeMode)
    val openBlankInNewTab by repository.openBlankInNewTab.collectAsState()
    val openLinksExternally by repository.openLinksExternally.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val linkCopiedMessage = stringResource(Res.string.article_host_link_copied)
    val shareFailedMessage = stringResource(Res.string.article_host_share_failed)

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

    // Whether this tab is currently showing a saved offline copy
    // rather than a live page.
    var offlineHtml by remember(tab.id) { mutableStateOf<String?>(null) }
    // Whether the lookup below has actually run at least once, so a
    // null offlineHtml downstream can be told apart from "haven't
    // checked yet" versus "checked, and there's genuinely nothing
    // there". See WikiArticleReader's matching param.
    var offlineLookupSettled by remember(tab.id) { mutableStateOf(false) }

    // True once a tab that opened straight into an offline copy has
    // genuinely fallen back to live browsing, meaning the lookup above
    // came back settled with nothing to show, either because the copy
    // was deleted mid-session or there never really was one. This is
    // a MutableState object, not a plain val, specifically so the back
    // handler below, registered once at tab creation and never
    // re-registered, can still read its up to date value on every
    // invocation rather than whatever it was at registration time.
    val offlineFellBackToLiveState = remember(tab.id) { mutableStateOf(false) }
    LaunchedEffect(offlineHtml, offlineLookupSettled) {
        if (openOfflineFromStart && offlineLookupSettled && offlineHtml == null) {
            offlineFellBackToLiveState.value = true
        }
    }
    val isShowingOfflineSnapshot = openOfflineFromStart && !offlineFellBackToLiveState.value

    var pageState by remember(tab.id) {
        mutableStateOf(
            WikiPageState(title = initialTitle, canonicalTitle = initialTitle, displaySiteName = site.name, url = tab.currentUrl.orEmpty()),
        )
    }

    // Offline navigation stays inside this one tab, exactly like
    // ordinary browsing: clicking a link to another saved article
    // changes what this tab shows, not a new tab for every article
    // clicked through, and back/forward move through that history.
    // WebView's own navigateBack/navigateForward aren't used for this,
    // on purpose, see the back handler below for why, so this tracks
    // the titles visited in this tab itself instead.
    var offlineBackStack by remember(tab.id) { mutableStateOf(listOf<String>()) }
    var offlineForwardStack by remember(tab.id) { mutableStateOf(listOf<String>()) }

    fun currentOfflineTitle() = pageState.canonicalTitle.ifBlank { initialTitle }

    /** Clicking a link inside an offline tab to another saved article. Pushes history and clears forward, same as following any new link would. */
    fun navigateWithinOfflineTab(newTitle: String) {
        if (newTitle == currentOfflineTitle()) return
        offlineBackStack = offlineBackStack + currentOfflineTitle()
        offlineForwardStack = emptyList()
        pageState = pageState.copy(title = newTitle, canonicalTitle = newTitle, isLoading = true)
    }

    fun goBackOffline(): Boolean {
        val previous = offlineBackStack.lastOrNull() ?: return false
        offlineForwardStack = offlineForwardStack + currentOfflineTitle()
        offlineBackStack = offlineBackStack.dropLast(1)
        pageState = pageState.copy(title = previous, canonicalTitle = previous, isLoading = true)
        return true
    }

    fun goForwardOffline(): Boolean {
        val next = offlineForwardStack.lastOrNull() ?: return false
        offlineBackStack = offlineBackStack + currentOfflineTitle()
        offlineForwardStack = offlineForwardStack.dropLast(1)
        pageState = pageState.copy(title = next, canonicalTitle = next, isLoading = true)
        return true
    }

    // Read through rememberUpdatedState rather than as a remember(site)
    // key, so toggling the setting mid-session is picked up by the
    // interceptor right away without tearing down and recreating the
    // navigator, which owns the WebView's actual navigation history.
    val confirmExternalNavigationState = rememberUpdatedState(confirmExternalNavigation)
    val openLinksExternallyState = rememberUpdatedState(openLinksExternally)
    val openBlankInNewTabState = rememberUpdatedState(openBlankInNewTab)
    val disableSafeModeState = rememberUpdatedState(disableSafeMode)
    val allWikisState = rememberUpdatedState(allWikis)
    val isOnExternalSiteState = rememberUpdatedState(isOnExternalSite)
    val offlineHtmlState = rememberUpdatedState(offlineHtml)
    val offlineKeysState = rememberUpdatedState(offlineKeys)
    val openOfflineFromStartState = rememberUpdatedState(isShowingOfflineSnapshot)

    val navigator = rememberWebViewNavigator(
        requestInterceptor = remember(site) {
            object : RequestInterceptor {
                override fun onInterceptUrlRequest(
                    request: WebRequest,
                    navigator: WebViewNavigator,
                ): WebRequestInterceptResult {
                    val url = request.url
                    if (url.startsWith("data:")) return WebRequestInterceptResult.Allow

                    if (openOfflineFromStartState.value && offlineHtmlState.value != null) {
                        // Showing a saved snapshot, there's no network
                        // to send this to. A link to another saved
                        // article navigates within this same tab,
                        // ordinary browsing, not a new tab for every
                        // article clicked through. Everything else is
                        // dropped, including anything rewriteOfflineLinks
                        // should already have turned into plain text
                        // before this point.
                        val targetTitle = extractCanonicalTitle(url, site)
                        if (targetTitle != null && targetTitle in offlineTitlesForWiki(offlineKeysState.value, site.id)) {
                            navigateWithinOfflineTab(targetTitle)
                        }
                        return WebRequestInterceptResult.Reject
                    }

                    val isAuthRequest = AuthDomains.matches(url)
                    val targetSite = when {
                        isAuthRequest -> site
                        else -> allWikisState.value.firstOrNull { url.startsWith(it.baseUrl) }
                    }

                    if (openBlankInNewTabState.value && requestsNewTab(url)) {
                        val cleanUrl = withoutNewTabMarker(url)
                        val fallbackTitle = targetSite?.name ?: runCatching { Url(cleanUrl).host }.getOrNull().orEmpty()
                        val newTabTitle = targetSite?.let { extractCanonicalTitle(cleanUrl, it) } ?: fallbackTitle
                        tabsRepository.openTabForUrl(targetSite, cleanUrl, newTabTitle)
                        return WebRequestInterceptResult.Reject
                    }

                    if (targetSite == null) {
                        if (isOnExternalSiteState.value || !confirmExternalNavigationState.value) {
                            isOnExternalSite = true
                            if (openLinksExternallyState.value) {
                                uriHandler.openUri(url)
                            } else {
                                return WebRequestInterceptResult.Allow
                            }
                            return WebRequestInterceptResult.Reject
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
                    val rewritten = targetSite.withSkinParams(url, safeMode = !targetSite.effectiveDisableSafeMode(disableSafeModeState.value)) ?: return WebRequestInterceptResult.Allow
                    scope.launch { navigator.loadUrl(rewritten) }
                    return WebRequestInterceptResult.Reject
                }
            }
        },
    )

    var historyNavTrigger by remember(tab.id) { mutableIntStateOf(0) }

    // Registered once, at tab creation, keyed by tab.id and not isActive.
    // See TabsRepository's comment on backHandlers for why this must not
    // be tied to activation timing.
    //
    // openOfflineFromStart tabs never call navigator.navigateBack() at
    // all, on purpose. Offline content loads through loadHtml, which on
    // Android goes straight to loadDataWithBaseURL, and each such call,
    // an ordinary refresh included, counts as one more entry in the
    // WebView's own back-forward list. Android WebView is known not to
    // reliably restore loadDataWithBaseURL content when navigating back
    // into one of those entries, rendering blank instead. offlineBackStack
    // above is what actually gives these tabs working back navigation
    // without touching that.
    DisposableEffect(tab.id) {
        tabsRepository.registerBackHandler(tab.id) {
            if (openOfflineFromStart && !offlineFellBackToLiveState.value) {
                goBackOffline()
            } else if (navigator.canGoBack) {
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
    // history, WebView's for a live tab, offlineBackStack for an offline
    // one, but only while this tab is actually the active one.
    LaunchedEffect(isActive, navigator.canGoBack, offlineBackStack, isShowingOfflineSnapshot) {
        if (isActive) {
            tabsRepository.setActiveTabCanGoBack(if (isShowingOfflineSnapshot) offlineBackStack.isNotEmpty() else navigator.canGoBack)
        }
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
                searchResult = runPageSearch(navigator, searchQuery, scrollToActive = false)
            }
        }
    }

    var pageSummary by remember(tab.id) { mutableStateOf<PageSummaryDto?>(null) }
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

    // Reapplies a changed skin or safe mode setting to whatever this
    // tab is already showing, so neither one requires closing and
    // reopening every open tab to take effect.
    LaunchedEffect(site.skin, effectiveDisableSafeMode, isShowingOfflineSnapshot) {
        if (isShowingOfflineSnapshot || isOnExternalSite) return@LaunchedEffect
        val currentUrl = pageState.url.ifBlank { return@LaunchedEffect }
        val rewritten = site.withSkinParams(currentUrl, safeMode = !effectiveDisableSafeMode) ?: return@LaunchedEffect
        if (rewritten != currentUrl) navigator.loadUrl(rewritten)
    }

    val currentTitle = pageState.canonicalTitle.ifBlank { initialTitle }
    val isSaved = savedPages.any { it.wikiId == site.id && it.title == currentTitle }
    val isOfflineSaved = repository.isOfflineSaved(site.id, currentTitle)

    // navigator.reload() re-requests whatever URL the WebView thinks
    // it's on, which for offline content loaded through loadHtml isn't
    // a real, re-fetchable address, so reload() on it just clears the
    // page instead of refreshing anything. There's nothing to refresh
    // on a saved snapshot anyway, so this re-runs the same loadHtml
    // instead of touching the network.
    fun refreshCurrentPage() {
        val savedHtml = offlineHtml
        if (openOfflineFromStart && savedHtml != null) {
            navigator.loadHtml(savedHtml, baseUrl = offlineLoadIdentityUrl(site, currentTitle, savedHtml))
        } else {
            navigator.reload()
        }
    }

    // Gated on isActive so a background tab doesn't do this local lookup
    // every time any tab's offline status changes. It re-runs the moment
    // the tab becomes active again, which is early enough to still land
    // before the person would notice.
    LaunchedEffect(isActive, tab.id, currentTitle, offlineKeys) {
        if (!isActive) return@LaunchedEffect
        val savedHtml = if (isOfflineSaved) repository.getOfflineArticleHtml(site.id, currentTitle) else null
        offlineHtml = savedHtml?.let { rewriteOfflineLinks(it, site, offlineTitlesForWiki(offlineKeys, site.id)) }
        offlineLookupSettled = true
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
        // getPageSummary assumes currentTitle is a real, currently
        // reachable article on site. That's not true for an auth page
        // or an external link followed in this tab, and there's no
        // network to ask at all while reading offline, in-tab
        // navigation between saved articles included. The tab and
        // history are still updated either way, using pageState.url as
        // the actual address to return to, rather than reconstructing
        // one from title and wikiId that would 404 on a page that was
        // never really an article here.
        val freshSummary = if (isOffSiteContent || isShowingOfflineSnapshot) null else api.getPageSummary(site, currentTitle).getOrNull()
        summarizedTitle = currentTitle
        pageSummary = freshSummary
        tabsRepository.updateTab(
            tab.id, currentTitle, freshSummary?.thumbnail?.source, pageState.displaySiteName.orEmpty(), freshSummary?.extract,
            pageState.url, clearSummary = isOffSiteContent,
        )
        // Attributed to whichever wiki the page actually is right now,
        // not necessarily this tab's own site. A page has genuinely
        // navigated to a different saved wiki when it matches one of
        // allWikis other than site, and Continue reading on Dashboard
        // should point back at that wiki, not the one this tab was
        // originally opened on. Content that matches no known wiki at
        // all, an outside site or an auth flow, isn't recorded as a
        // visit to any of them.
        val visitSite = when {
            isShowingOfflineSnapshot -> site
            isOffSiteContent -> null
            else -> allWikis.firstOrNull { pageState.url.startsWith(it.baseUrl) } ?: site
        }
        if (visitSite != null) {
            repository.recordVisit(
                SavedPage(
                    wikiId = visitSite.id,
                    wikiName = visitSite.name,
                    title = currentTitle,
                    extract = pageSummary?.extract.orEmpty(),
                    thumbnailUrl = pageSummary?.thumbnail?.source,
                    timestampEpochMillis = nowEpochMillis(),
                    url = pageState.url,
                ),
            )
        }
    }

    suspend fun capturePreviewAndRun(action: () -> Unit) {
        nativeWebViewRef?.let { webView ->
            captureTabPreview(webView)?.let { bitmap ->
                tabsRepository.updatePreview(tab.id, bitmap)
            }
        }
        action()
    }

    // Show "Title - SiteName" only when the current page matches a wiki
    // we actually know about. Otherwise, for example an external link
    // followed in this tab, just show the page's own raw title, the
    // same way an ordinary browser would. There is no reconstructed
    // suffix for a site we don't have a name for. A page whose title is
    // already exactly the site's name, for example a wiki's own main
    // page sharing the wiki's name, skips the suffix entirely rather
    // than repeating it, e.g. "MediaWiki" rather than "MediaWiki -
    // MediaWiki".
    val siteName = pageState.displaySiteName
    val displayedTitle = when {
        siteName == null -> pageState.title.ifBlank { currentTitle }
        currentTitle.equals(siteName, ignoreCase = true) -> siteName
        currentTitle.endsWith("- $siteName", ignoreCase = true) -> currentTitle
        else -> "$currentTitle - $siteName"
    }
    val sharePage = rememberPageSharer()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box {
                if (isSearchBarOpen) {
                    PageSearchTopBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        matchCount = searchResult.matchCount,
                        activeIndex = searchResult.activeIndex,
                        focusRequester = searchFocusRequester,
                        onClose = {
                            isSearchBarOpen = false
                            searchQuery = ""
                            searchResult = PageSearchResult()
                            scope.launch { clearPageSearch(navigator) }
                        },
                        onSearchSubmit = { scope.launch { searchResult = stepPageSearch(navigator, forward = true) } },
                        onPreviousMatch = { scope.launch { searchResult = stepPageSearch(navigator, forward = false) } },
                        onNextMatch = { scope.launch { searchResult = stepPageSearch(navigator, forward = true) } },
                    )
                } else {
                    ArticleTopBar(
                        displayedTitle = displayedTitle,
                        openTabCount = tabs.size,
                        isSaved = isSaved,
                        onClose = { scope.launch { capturePreviewAndRun(onBack) } },
                        onToggleSaved = {
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
                        onOpenTabSwitcher = {
                            scope.launch { capturePreviewAndRun { tabsRepository.setSwitcherOpen(true) } }
                        },
                        onOpenOverflowMenu = { isOverflowMenuOpen = true },
                        overflowMenu = {
                            ArticleOverflowMenu(
                                expanded = isOverflowMenuOpen,
                                onDismiss = { isOverflowMenuOpen = false },
                                showForward = if (isShowingOfflineSnapshot) offlineForwardStack.isNotEmpty() else navigator.canGoForward,
                                onForward = {
                                    if (isShowingOfflineSnapshot) {
                                        goForwardOffline()
                                    } else {
                                        navigator.navigateForward()
                                        historyNavTrigger++
                                    }
                                },
                                onRefresh = { refreshCurrentPage() },
                                onFindOnPage = { isSearchBarOpen = true },
                                onShare = {
                                    scope.launch {
                                        val outcome = sharePage(displayedTitle, pageState.url)
                                        if (outcome == ShareOutcome.COPIED_TO_CLIPBOARD) {
                                            snackbarHostState.showSnackbar(linkCopiedMessage)
                                        } else if (outcome == ShareOutcome.FAILED) {
                                            snackbarHostState.showSnackbar(shareFailedMessage)
                                        }
                                    }
                                },
                                isOfflineSaved = isOfflineSaved,
                                isSavingOffline = isSavingOffline,
                                onToggleOfflineSave = {
                                    if (isOfflineSaved) {
                                        repository.removeOfflineArticle(site.id, currentTitle)
                                    } else {
                                        isSavingOffline = true
                                    }
                                },
                            )
                        },
                    )
                }

                if (pageState.isLoading) {
                    // openOfflineFromStart, still no offlineHtml: reading
                    // and rewriting a saved article's HTML off disk, not a
                    // real, percentage-trackable load. pageState.progress
                    // would just be stuck reporting 0 through this whole
                    // phase, which looks like a stall rather than
                    // something actually happening.
                    val barModifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    if (openOfflineFromStart && offlineHtml == null) {
                        LinearProgressIndicator(modifier = barModifier)
                    } else {
                        LinearProgressIndicator(
                            progress = { pageState.progress.coerceIn(0, 100) / 100f },
                            modifier = barModifier,
                        )
                    }
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
                offlineLookupSettled = offlineLookupSettled,
                offlineDisplayTitle = currentTitle,
                openOfflineFromStart = openOfflineFromStart,
                allWikis = allWikis,
                historyNavTrigger = historyNavTrigger,
                restoreUrl = tab.currentUrl,
                disableSafeMode = effectiveDisableSafeMode,
                openBlankInNewTab = openBlankInNewTab,
                onWebViewReady = { nativeWebViewRef = it },
                onStateChanged = { newState -> pageState = newState },
                modifier = Modifier.fillMaxSize(),
            )

            ArticlePullToRefreshOverlay(
                tabKey = tab.id,
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                onRefreshingChange = { isRefreshing = it },
                onRefresh = { refreshCurrentPage() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    LaunchedEffect(isSavingOffline) {
        if (!isSavingOffline) return@LaunchedEffect
        val captured = captureArticleForOffline(site, currentTitle, api).getOrNull()

        if (!captured.isNullOrBlank()) {
            repository.saveOfflineArticle(
                SavedPage(
                    wikiId = site.id,
                    wikiName = site.name,
                    title = currentTitle,
                    extract = pageSummary?.extract.orEmpty(),
                    thumbnailUrl = pageSummary?.thumbnail?.source,
                    timestampEpochMillis = nowEpochMillis(),
                ),
                captured,
            )
        }

        isSavingOffline = false
    }

    pendingExternalUrl?.let { url ->
        ExternalSiteDialog(
            url = url,
            currentWikiName = siteName ?: site.name,
            onDismiss = { pendingExternalUrl = null },
            onContinue = {
                pendingExternalUrl = null
                isOnExternalSite = true
                if (openLinksExternally) {
                    uriHandler.openUri(url)
                } else {
                    scope.launch { navigator.loadUrl(url) }
                }
            },
        )
    }
}
