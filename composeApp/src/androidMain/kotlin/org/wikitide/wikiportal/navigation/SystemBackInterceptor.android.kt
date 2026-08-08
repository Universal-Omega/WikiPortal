package org.wikitide.wikiportal.navigation

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun SystemBackInterceptor(enabled: Boolean, onBack: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Before Android 13 there is no predictive-back gestures to handle.
        BackHandler(enabled = enabled, onBack = onBack)
        return
    }

    val activity = LocalContext.current as? Activity
    val currentOnBack = rememberUpdatedState(onBack)

    DisposableEffect(activity, enabled) {
        val dispatcher = activity?.onBackInvokedDispatcher
        if (dispatcher == null || !enabled) {
            onDispose {}
        } else {
            // Predictive back gestures don't display properly in WebView, so we don't show them there.
            val callback = OnBackInvokedCallback { currentOnBack.value() }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            onDispose { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }
}
