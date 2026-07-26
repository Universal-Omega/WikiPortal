package org.wikitide.wikiportal.ui.article

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.multiplatform.webview.web.NativeWebView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.WebKit.WKSnapshotConfiguration
import kotlin.coroutines.resume

private const val MAX_PREVIEW_WIDTH_PX = 320.0

@OptIn(ExperimentalForeignApi::class)
actual suspend fun captureTabPreview(nativeWebView: NativeWebView): ImageBitmap? {
    val snapshotImage: UIImage? = suspendCancellableCoroutine { continuation ->
        val configuration = WKSnapshotConfiguration().apply {
            snapshotWidth = NSNumber(double = MAX_PREVIEW_WIDTH_PX)
        }
        nativeWebView.takeSnapshotWithConfiguration(
            snapshotConfiguration = configuration,
        ) { image, error ->
            if (!continuation.isActive) return@takeSnapshotWithConfiguration
            if (error != null) {
                println("TabPreview: WKWebView snapshot failed: ${error.localizedDescription}")
            }
            continuation.resume(image)
        }
    }

    if (snapshotImage == null) return null
    val pngData = UIImagePNGRepresentation(snapshotImage) ?: run {
        println("TabPreview: UIImagePNGRepresentation returned null")
        return null
    }

    return try {
        Image.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
    } catch (e: Exception) {
        println("TabPreview: decoding snapshot PNG failed: ${e::class.simpleName}: ${e.message}")
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}
