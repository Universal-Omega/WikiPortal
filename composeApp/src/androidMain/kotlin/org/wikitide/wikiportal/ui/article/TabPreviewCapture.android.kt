package org.wikitide.wikiportal.ui.article

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.multiplatform.webview.web.NativeWebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual suspend fun captureTabPreview(nativeWebView: NativeWebView): ImageBitmap? {
    return suspendCancellableCoroutine { continuation ->
        try {
            val width = nativeWebView.width
            val height = nativeWebView.height

            if (width <= 0 || height <= 0) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val activity = nativeWebView.context as? Activity
            val window = activity?.window

            if (window == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val location = IntArray(2)
            nativeWebView.getLocationInWindow(location)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            PixelCopy.request(
                window,
                Rect(
                    location[0],
                    location[1],
                    location[0] + width,
                    location[1] + height,
                ),
                bitmap,
                { result ->
                    if (!continuation.isActive) return@request

                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap.asImageBitmap())
                    } else {
                        println("TabPreview: PixelCopy failed with code $result")
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            println("TabPreview: capture failed: ${e::class.simpleName}: ${e.message}")
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}
