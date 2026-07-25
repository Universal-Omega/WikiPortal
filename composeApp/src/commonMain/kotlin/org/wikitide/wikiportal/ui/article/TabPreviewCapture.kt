package org.wikitide.wikiportal.ui.article

import androidx.compose.ui.graphics.ImageBitmap
import com.multiplatform.webview.web.NativeWebView

expect suspend fun captureTabPreview(nativeWebView: NativeWebView): ImageBitmap?
