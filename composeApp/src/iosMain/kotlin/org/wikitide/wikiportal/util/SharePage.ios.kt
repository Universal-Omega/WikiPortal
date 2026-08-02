package org.wikitide.wikiportal.util

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberPageSharer(): PageSharer = { title, url ->
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (rootViewController == null) {
        ShareOutcome.FAILED
    } else {
        runCatching {
            val items = listOf(title, NSURL(string = url))
            val activityController = UIActivityViewController(activityItems = items, applicationActivities = null)
            rootViewController.presentViewController(activityController, animated = true, completion = null)
        }.fold(
            onSuccess = { ShareOutcome.SHARED },
            onFailure = { ShareOutcome.FAILED },
        )
    }
}
