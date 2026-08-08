package org.wikitide.wikiportal.ui.article

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.ktor.http.Url

/**
 * Confirms leaving [currentWikiName] before following a link to [url],
 * which matched no wiki this app knows about. See ArticleHostScreen's
 * RequestInterceptor for when this is shown at all: only once per
 * external host, not on every subsequent link within that same
 * outside site.
 */
@Composable
fun ExternalSiteDialog(
    url: String,
    currentWikiName: String,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val host = runCatching { Url(url).host }.getOrNull()?.ifBlank { null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave $currentWikiName?") },
        text = { Text("This link goes to ${host ?: "an outside site"}, not $currentWikiName.") },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay here") }
        },
    )
}
