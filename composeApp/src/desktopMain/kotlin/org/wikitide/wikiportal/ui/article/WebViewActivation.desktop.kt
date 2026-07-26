package org.wikitide.wikiportal.ui.article

import com.multiplatform.webview.web.NativeWebView

// Not available with KCEF.
actual fun setWebViewActive(nativeWebView: NativeWebView, isActive: Boolean) = Unit
