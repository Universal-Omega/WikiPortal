package org.wikitide.wikiportal.ui.article

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.http.Url
import org.jetbrains.compose.resources.stringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.external_site_dialog_body
import org.wikitide.wikiportal.resources.external_site_dialog_continue
import org.wikitide.wikiportal.resources.external_site_dialog_outside_site
import org.wikitide.wikiportal.resources.external_site_dialog_stay
import org.wikitide.wikiportal.resources.external_site_dialog_title

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
    modifier: Modifier = Modifier,
) {
    val host = runCatching { Url(url).host }.getOrNull()?.ifBlank { null }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.external_site_dialog_title, currentWikiName)) },
        text = { Text(stringResource(Res.string.external_site_dialog_body, host ?: stringResource(Res.string.external_site_dialog_outside_site), currentWikiName)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(Res.string.external_site_dialog_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.external_site_dialog_stay)) }
        },
    )
}
