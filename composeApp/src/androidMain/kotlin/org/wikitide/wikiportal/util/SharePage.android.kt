package org.wikitide.wikiportal.util

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPageSharer(): PageSharer {
    val context = LocalContext.current
    return { title, url ->
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }.fold(
            onSuccess = { ShareOutcome.SHARED },
            onFailure = { ShareOutcome.FAILED },
        )
    }
}
