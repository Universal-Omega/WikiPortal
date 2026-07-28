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
import org.wikitide.wikiportal.util.AppLog
import kotlin.coroutines.resume

private const val MAX_PREVIEW_WIDTH_PX = 320

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
                        continuation.resume(bitmap.toThumbnail().asImageBitmap())
                    } else {
                        AppLog.w("TabPreview", "PixelCopy failed with code $result")
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            AppLog.e("TabPreview", "capture failed", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}

private fun Bitmap.toThumbnail(): Bitmap {
    if (width <= MAX_PREVIEW_WIDTH_PX) return this
    val scale = MAX_PREVIEW_WIDTH_PX.toFloat() / width
    val targetWidth = MAX_PREVIEW_WIDTH_PX
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) recycle()
    return scaled
}
