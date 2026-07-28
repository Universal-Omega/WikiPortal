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
  window.__wpDirty = false;
  window.__wpObserver = null;

  window.__wpPauseObserver = function() {
    if (window.__wpObserver) window.__wpObserver.disconnect();
  };

  window.__wpResumeObserver = function() {
    if (!window.__wpObserver) {
      window.__wpObserver = new MutationObserver(function() { window.__wpDirty = true; });
    }
    window.__wpObserver.observe(document.body, {
      attributes: true,
      childList: true,
      subtree: true,
      attributeFilter: ['style', 'class', 'hidden', 'open', 'aria-expanded', 'aria-hidden'],
    });
  };

  window.__wpClearInternal = function() {
    window.__wpMatches.forEach(function(mark) {
      var parent = mark.parentNode;
      if (!parent) return;
      parent.replaceChild(document.createTextNode(mark.textContent), mark);
      parent.normalize();
    });
    window.__wpMatches = [];
    window.__wpActive = -1;
  };

  window.__wpClear = function() {
    window.__wpPauseObserver();
    window.__wpClearInternal();
    window.__wpResumeObserver();
  };

  window.__wpIsControl = function(startEl) {
    var el = startEl;
    while (el && el !== document.body) {
      var role = el.getAttribute ? el.getAttribute('role') : null;
      if (
        el.tagName === 'BUTTON' ||
        el.tagName === 'LABEL' ||
        el.tagName === 'SUMMARY' ||
        role === 'button' ||
        role === 'menuitem' ||
        role === 'menu' ||
        (el.hasAttribute && (el.hasAttribute('aria-haspopup') || el.hasAttribute('aria-expanded')))
      ) {
        return true;
      }
      el = el.parentElement;
    }
    return false;
  };

  window.__wpFindBoundary = function(fromEl) {
    var el = fromEl;
    while (el && el !== document.body) {
      var isDetailsClosed = el.tagName === 'DETAILS' && !el.open;
      var isUntilFound = el.getAttribute && el.getAttribute('hidden') === 'until-found';
      if ((isDetailsClosed || isUntilFound) && !window.__wpIsChrome(el)) return el;
      el = el.parentElement;
    }
    return null;
  };

  window.__wpIsExcluded = function(startEl) {
    var el = startEl;
    while (el && el !== document.body) {
      var boundary = window.__wpFindBoundary(el);
      if (boundary) {
        el = boundary.parentElement;
        continue;
      }
      while (el && el !== document.body) {
        var style = window.getComputedStyle(el);
        if (
          style.display === 'none' ||
          style.visibility === 'hidden' ||
          style.opacity === '0' ||
          el.getClientRects().length === 0
        ) {
          return true;
        }
        el = el.parentElement;
      }
      return false;
    }
    return false;
  };

  window.__wpRevealAncestors = function(markEl) {
    var el = markEl.parentElement;
    while (el && el !== document.body) {
      if (el.tagName === 'DETAILS' && !el.open) el.open = true;
      if (el.getAttribute && el.getAttribute('hidden') === 'until-found') el.removeAttribute('hidden');
      el = el.parentElement;
    }
  };

  window.__wpPaint = function(scrollToActive) {
    window.__wpMatches.forEach(function(mark, i) {
      mark.style.backgroundColor = i === window.__wpActive ? '#ff9d2f' : '#ffe066';
      mark.style.color = '#000000';
    });
    if (!scrollToActive) return;
    var active = window.__wpMatches[window.__wpActive];
    if (active) {
      window.__wpRevealAncestors(active);
      active.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  };

  window.__wpReport = function() {
    return JSON.stringify({
      matchCount: window.__wpMatches.length,
      activeIndex: window.__wpMatches.length > 0 ? window.__wpActive + 1 : 0,
    });
  };

  window.__wpIsChrome = function(startEl) {
    var el = startEl;
    while (el && el !== document.body) {
      var tag = el.tagName;
      var role = el.getAttribute ? el.getAttribute('role') : null;
      if (
        tag === 'NAV' ||
        tag === 'HEADER' ||
        tag === 'FOOTER' ||
        role === 'navigation' ||
        role === 'banner' ||
        role === 'contentinfo' ||
        role === 'search'
      ) {
        return true;
      }
      el = el.parentElement;
    }
    return false;
  };

  window.__wpRun = function(query, scrollToActive) {
    window.__wpPauseObserver();
    window.__wpClearInternal();
    if (!query) {
      window.__wpDirty = false;
      window.__wpResumeObserver();
      return window.__wpReport();
    }
    var needle = query.toLowerCase();
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function(node) {
        var parent = node.parentElement;
        var tag = parent ? parent.nodeName : '';
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'MARK' || tag === 'NOSCRIPT') return NodeFilter.FILTER_REJECT;
        if (!node.nodeValue || node.nodeValue.toLowerCase().indexOf(needle) === -1) return NodeFilter.FILTER_SKIP;
        if (parent && window.__wpIsControl(parent)) return NodeFilter.FILTER_REJECT;
        if (parent && window.__wpIsExcluded(parent)) return NodeFilter.FILTER_REJECT;
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
    window.__wpPaint(scrollToActive !== false);
    window.__wpDirty = false;
    window.__wpResumeObserver();
    return window.__wpReport();
  };

  window.__wpStep = function(delta) {
    if (window.__wpMatches.length === 0) return window.__wpReport();
    window.__wpPauseObserver();
    var count = window.__wpMatches.length;
    window.__wpActive = (window.__wpActive + delta + count) % count;
    window.__wpPaint(true);
    window.__wpResumeObserver();
    return window.__wpReport();
  };

  window.__wpResumeObserver();
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
suspend fun runPageSearch(navigator: WebViewNavigator, query: String, scrollToActive: Boolean = true): PageSearchResult =
    parseSearchResult(
        navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpRun(${jsStringLiteral(query)}, $scrollToActive);"),
    )

/** Moves to the next match, wrapping around to the first after the last. */
suspend fun stepPageSearch(navigator: WebViewNavigator, forward: Boolean): PageSearchResult =
    parseSearchResult(navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpStep(${if (forward) 1 else -1});"))

/** Strips all highlight markup back out, restoring the page's original text nodes. */
suspend fun clearPageSearch(navigator: WebViewNavigator) {
    navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpClear(); window.__wpReport();")
}

/**
 * Whether anything on the page has changed since the last search or step,
 * a menu opening, content loading in, and so on. Cheap to call often since
 * it just reads a flag rather than touching the DOM.
 */
suspend fun isPageSearchDirty(navigator: WebViewNavigator): Boolean =
    navigator.evalForResult(SEARCH_RUNTIME_SCRIPT + "window.__wpDirty ? 'true' : 'false';")
        .trim().removeSurrounding("\"") == "true"
