package org.wikitide.wikiportal.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import io.ktor.http.URLBuilder
import io.ktor.http.decodeURLQueryComponent
import kotlin.io.encoding.Base64
import org.wikitide.wikiportal.data.model.WikiSite

/** Snapshot of what the reader is currently showing, reported up to [ArticleHostScreen]. */
data class WikiPageState(
    /** Raw browser-reported title, always present, used as a fallback. */
    val title: String = "",
    /** Page identity used for save/history/tabs-list. */
    val canonicalTitle: String = "",
    val displaySiteName: String? = null,
    val url: String = "",
    val isLoading: Boolean = true,
    val progress: Int = 0,
)

@Composable
fun WikiArticleReader(
    site: WikiSite,
    /**
     * The title to load. This must be a stable value, for example
     * captured once at tab creation through remember(tab.id) { tab.title }
     * in the caller. This is not meant to track in-page navigation
     * reactively.
     */
    title: String,
    navigator: WebViewNavigator,
    textScale: Float,
    /**
     * Fully self-contained HTML, with all sub-resources inlined as
     * data URIs, built by buildSelfContainedHtml, or null for a live
     * page.
     */
    offlineHtml: String?,
    /**
     * Every wiki the app knows about, presets and custom. Used to look
     * up which one, if any, the currently loaded URL belongs to,
     * dynamically, rather than assuming it is always [site], the tab's
     * originally opened wiki. This includes [site] itself, so same-site
     * navigation is handled by the same lookup with no special casing
     * needed.
     */
    allWikis: List<WikiSite>,
    onWebViewReady: (NativeWebView) -> Unit = {},
    onStateChanged: (WikiPageState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialUrl = if (offlineHtml != null) {
        "data:text/html;charset=utf-8;base64," + Base64.encode(offlineHtml.encodeToByteArray())
    } else {
        site.articleUrl(title)
    }

    val webViewState = rememberWebViewState(initialUrl)

    LaunchedEffect(Unit) {
        webViewState.webSettings.apply {
            isJavaScriptEnabled = true
            androidWebSettings.apply {
                domStorageEnabled = true
            }
        }
    }

    LaunchedEffect(textScale) {
        webViewState.webSettings.androidWebSettings.textZoom = (textScale * 100).toInt()
    }

    // Tracks the last genuinely known good page state for this tab's
    // whole lifetime, not reset on every recomposition. title,
    // canonicalTitle, and displaySiteName only ever get overwritten when
    // we have real new information, meaning a successful URL-based
    // extraction, or a resolved evaluateJavaScript lookup for cross-site
    // content. During any "loading, nothing new yet" moment, isLoading,
    // progress, and url update right away, so the progress bar stays
    // responsive, but the title fields are left untouched.
    val lastKnown = remember {
        mutableStateOf(WikiPageState(title = title, canonicalTitle = title, displaySiteName = site.name, url = initialUrl))
    }

    LaunchedEffect(webViewState.pageTitle, webViewState.loadingState, webViewState.lastLoadedUrl) {
        val loading = webViewState.loadingState
        val url = webViewState.lastLoadedUrl
        val progress = (loading as? LoadingState.Loading)?.let { (it.progress * 100).toInt() } ?: 100
        val isLoading = loading is LoadingState.Loading

        if (offlineHtml != null) {
            lastKnown.value = lastKnown.value.copy(
                title = title,
                canonicalTitle = title,
                displaySiteName = site.name,
                url = url ?: lastKnown.value.url,
                isLoading = isLoading,
                progress = progress,
            )
            onStateChanged(lastKnown.value)
            return@LaunchedEffect
        }

        // Cross-site, unmatched, content has no URL-based title
        // extraction to fall back on. This asks the DOM directly
        // through evaluateJavaScript once loading has actually
        // finished. That call is async, so there is a real gap
        // between "finished loading" and "we know the real title."
        // During that gap, and during ordinary mid-load progress
        // updates, only isLoading, progress, and url are updated
        // below. The title fields stay whatever they last genuinely
        // were until the JS lookup resolves.
        if (loading is LoadingState.Finished) {
            navigator.evaluateJavaScript("window.location.href") { rawUrl ->
                val liveUrl = rawUrl.trim().removeSurrounding("\"").ifBlank { null } ?: url
                val matchedSite = liveUrl?.let { u -> allWikis.firstOrNull { u.startsWith(it.baseUrl) } }
                if (matchedSite != null) {
                    val extractedTitle = extractCanonicalTitle(liveUrl, matchedSite)
                    lastKnown.value = lastKnown.value.copy(
                        title = extractedTitle ?: lastKnown.value.title,
                        canonicalTitle = extractedTitle ?: lastKnown.value.canonicalTitle,
                        displaySiteName = matchedSite.name,
                        url = liveUrl,
                        isLoading = false,
                        progress = 100,
                    )
                    onStateChanged(lastKnown.value)
                } else {
                    navigator.evaluateJavaScript("document.title") { rawTitle ->
                        val cleaned = rawTitle.trim().removeSurrounding("\"")
                        lastKnown.value = lastKnown.value.copy(
                            title = cleaned.ifBlank { lastKnown.value.title },
                            canonicalTitle = cleaned.ifBlank { lastKnown.value.canonicalTitle },
                            displaySiteName = null,
                            url = liveUrl ?: lastKnown.value.url,
                            isLoading = false,
                            progress = 100,
                        )
                        onStateChanged(lastKnown.value)
                    }
                }
            }
            return@LaunchedEffect
        }

        val matchedSite = url?.let { u -> allWikis.firstOrNull { u.startsWith(it.baseUrl) } }
        if (matchedSite != null) {
            // Same site, or a different known wiki. This derives the
            // title from the URL, which has proven reliable around
            // reload(), unlike pageTitle, which goes stale or reverts
            // around that specific event. If extraction fails this pass,
            // this keeps the last known title rather than guessing from
            // pageTitle.
            val extractedTitle = extractCanonicalTitle(url, matchedSite)
            val isAwaitingSkinRewrite = matchedSite.id == site.id && !url.contains("useskin=${site.skin}")
            val canonicalTitle = extractedTitle?.takeUnless { isAwaitingSkinRewrite }
            lastKnown.value = lastKnown.value.copy(
                title = canonicalTitle ?: lastKnown.value.title,
                canonicalTitle = canonicalTitle ?: lastKnown.value.canonicalTitle,
                displaySiteName = matchedSite.name,
                url = url,
                isLoading = isLoading,
                progress = progress,
            )
            onStateChanged(lastKnown.value)
        } else if (url != null) {
            lastKnown.value = lastKnown.value.copy(
                url = url,
                isLoading = isLoading,
                progress = progress,
            )
            onStateChanged(lastKnown.value)
        }
    }

    WebView(
        state = webViewState,
        navigator = navigator,
        modifier = modifier,
        captureBackPresses = false,
        onCreated = { webView: NativeWebView -> onWebViewReady(webView) },
    )
}

fun extractCanonicalTitle(url: String?, site: WikiSite): String? {
    if (url == null) return null

    val queryTitle = url.substringAfter("title=", "").substringBefore("&")
    if (queryTitle.isNotBlank()) {
        return decodeTitle(queryTitle)
    }

    val wikiPrefix = site.cleanUrlPrefix()
    if (url.startsWith(wikiPrefix)) {
        val raw = url.removePrefix(wikiPrefix).substringBefore("?").substringBefore("#")
        if (raw.isNotBlank()) return decodeTitle(raw)
    }

    return null
}

fun looksLikeArticleRequest(url: String, site: WikiSite): Boolean {
    val withoutQuery = url.substringBefore("?")
    return withoutQuery.startsWith(site.indexUrl) || url.startsWith(site.cleanUrlPrefix())
}

fun withAppSkin(url: String, site: WikiSite): String? = runCatching {
    val builder = URLBuilder(url)
    builder.parameters.apply {
        set("useskin", site.skin)
        set("safemode", "1")
    }
    builder.buildString()
}.getOrNull()

private fun decodeTitle(raw: String): String {
    val spaced = raw.replace("_", " ")
    return runCatching { spaced.decodeURLQueryComponent(plusIsSpace = false) }.getOrDefault(spaced)
}
