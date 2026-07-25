package org.wikitide.wikiportal.ui.article

import androidx.compose.ui.graphics.ImageBitmap
import com.multiplatform.webview.web.NativeWebView

// Not available with KCEF.
actual suspend fun captureTabPreview(nativeWebView: NativeWebView): ImageBitmap? = null
