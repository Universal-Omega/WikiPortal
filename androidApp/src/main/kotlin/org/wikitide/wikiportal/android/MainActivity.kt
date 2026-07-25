package org.wikitide.wikiportal.android

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import org.wikitide.wikiportal.WikiPortalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Without this, a side display cutout in landscape, for example
        // a punch-hole or notch camera on the long edge, makes the
        // system letterbox that sliver of screen instead of letting our
        // content draw under it. The letterboxed sliver renders using
        // the Activity's theme window background, not ours.
        // Extending layout under the cutout hands that sliver back to
        // Compose, which handles it correctly through
        // WindowInsets.safeDrawing in App.kt.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent {
            WikiPortalApp(
                onDarkThemeResolved = { useDark ->
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !useDark
                    controller.isAppearanceLightNavigationBars = !useDark
                },
            )
        }
    }
}
