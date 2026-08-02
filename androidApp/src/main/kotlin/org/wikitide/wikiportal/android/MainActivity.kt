package org.wikitide.wikiportal.android

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import org.wikitide.wikiportal.WikiPortalApp
import org.wikitide.wikiportal.util.AndroidLogExportBridge

class MainActivity : ComponentActivity() {
    // Registered here, as a property, rather than lazily inside
    // onCreate, since registerForActivityResult must be called before
    // the Activity reaches the started state. AndroidLogExportBridge
    // is what lets AndroidLogExporter, a plain suspend function with
    // no Activity reference of its own, actually trigger this and get
    // the chosen Uri back.
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> AndroidLogExportBridge.onDocumentCreated(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidLogExportBridge.launcher = { suggestedName ->
            createDocumentLauncher.launch(suggestedName)
        }

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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                        uiModeManager.setApplicationNightMode(
                            if (useDark) {
                                UiModeManager.MODE_NIGHT_YES
                            } else {
                                UiModeManager.MODE_NIGHT_NO
                            }
                        )
                    }

                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !useDark
                    controller.isAppearanceLightNavigationBars = !useDark
                },
            )
        }
    }
}
