package org.wikitide.wikiportal.ui.article

import com.multiplatform.webview.web.NativeWebView

/**
 * onPause()/onResume() are scoped to this one WebView instance. This
 * deliberately does not use WebView.pauseTimers()/resumeTimers(), which
 * pause JavaScript timers for every WebView in the process, active tab
 * included, since it is a process-wide call rather than a per-instance
 * one.
 */
actual fun setWebViewActive(nativeWebView: NativeWebView, isActive: Boolean) {
    if (isActive) {
        nativeWebView.onResume()
    } else {
        nativeWebView.onPause()
    }
}
