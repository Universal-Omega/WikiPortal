package org.wikitide.wikiportal.ui.article

import com.multiplatform.webview.web.WebViewNavigator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Result of a find-on-page action, as reported back from the injected script. */
data class PageSearchResult(
    val matchCount: Int = 0,
    /** 1-based position of the active match, or 0 when there are no matches. */
    val activeIndex: Int = 0,
)

// Everything here lives on window under a wp_ prefix so it doesn't collide
// with whatever the article page itself defines. The guard at the top means
// this can be sent down on every call, not just the first one, since a fresh
// page load wipes it out and re-running it on a page that already has it is
// a no-op.
private const val SEARCH_RUNTIME_SCRIPT = """
if (!window.__wpSearchReady) {
  window.__wpSearchReady = true;
  window.__wpMatches = [];
  window.__wpActive = -1;

  window.__wpClear = function() {
    window.__wpMatches.forEach(function(mark) {
      var parent = mark.parentNode;
      if (!parent) return;
      parent.replaceChild(document.createTextNode(mark.textContent), mark);
      parent.normalize();
    });
    window.__wpMatches = [];
    window.__wpActive = -1;
  };

  window.__wpPaint = function() {
    window.__wpMatches.forEach(function(mark, i) {
      mark.style.backgroundColor = i === window.__wpActive ? '#ff9d2f' : '#ffe066';
      mark.style.color = '#000000';
    });
    var active = window.__wpMatches[window.__wpActive];
    if (active) active.scrollIntoView({ block: 'center', behavior: 'smooth' });
  };

  window.__wpReport = function() {
    return JSON.stringify({
      matchCount: window.__wpMatches.length,
      activeIndex: window.__wpMatches.length > 0 ? window.__wpActive + 1 : 0,
    });
  };

  window.__wpRun = function(query) {
    window.__wpClear();
    if (!query) return window.__wpReport();
    var needle = query.toLowerCase();
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function(node) {
        var tag = node.parentNode ? node.parentNode.nodeName : '';
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'MARK' || tag === 'NOSCRIPT') return NodeFilter.FILTER_REJECT;
        if (!node.nodeValue || node.nodeValue.toLowerCase().indexOf(needle) === -1) return NodeFilter.FILTER_SKIP;
        return NodeFilter.FILTER_ACCEPT;
      },
    });
    var targets = [];
    var n;
    while ((n = walker.nextNode())) targets.push(n);

    targets.forEach(function(node) {
      var text = node.nodeValue;
      var lower = text.toLowerCase();
      var frag = document.createDocumentFragment();
      var cursor = 0;
      var idx = lower.indexOf(needle, cursor);
      while (idx !== -1) {
        if (idx > cursor) frag.appendChild(document.createTextNode(text.slice(cursor, idx)));
        var mark = document.createElement('mark');
        mark.textContent = text.slice(idx, idx + query.length);
        frag.appendChild(mark);
        window.__wpMatches.push(mark);
        cursor = idx + query.length;
        idx = lower.indexOf(needle, cursor);
      }
      if (cursor < text.length) frag.appendChild(document.createTextNode(text.slice(cursor)));
      node.parentNode.replaceChild(frag, node);
    });

    window.__wpActive = window.__wpMatches.length > 0 ? 0 : -1;
    window.__wpPaint();
    return window.__wpReport();
  };

  window.__wpStep = function(delta) {
    if (window.__wpMatches.length === 0) return window.__wpReport();
    var count = window.__wpMatches.length;
    window.__wpActive = (window.__wpActive + delta + count) % count;
    window.__wpPaint();
    return window.__wpReport();
  };
}
"""

/**
 * Runs evaluateJavaScript and resumes with its raw string result. The
 * library's callback isn't itself suspending, so this just bridges it.
 */
private suspend fun WebViewNavigator.evalForResult(script: String): String =
    suspendCancellableCoroutine { continuation ->
        evaluateJavaScript(script) { raw -> if (continuation.isActive) continuation.resume(raw) }
    }

private fun parseSearchResult(raw: String): PageSearchResult {
    // The bridge always returns a JSON-encoded string, itself wrapped in an
    // extra layer of quoting by evaluateJavaScript's own string result, so
    // that outer layer is unwrapped before parsing.
    val unwrapped = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
    val count = Regex(""""matchCount":(\d+)""").find(unwrapped)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val active = Regex(""""activeIndex":(\d+)""").find(unwrapped)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return PageSearchResult(matchCount = count, activeIndex = active)
}

private fun jsStringLiteral(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    return "\"$escaped\""
}

/** Highlights every match of [query] in the current page and jumps to the first one. */
suspend fun runPageSearch(navigator: WebViewNavigator, query: String): PageSearchResult =
    parseSearchResult(navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpRun(${jsStringLiteral(query)});"))

/** Moves to the next match, wrapping around to the first after the last. */
suspend fun stepPageSearch(navigator: WebViewNavigator, forward: Boolean): PageSearchResult =
    parseSearchResult(navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpStep(${if (forward) 1 else -1});"))

/** Strips all highlight markup back out, restoring the page's original text nodes. */
suspend fun clearPageSearch(navigator: WebViewNavigator) {
    navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpClear(); window.__wpReport();")
}
