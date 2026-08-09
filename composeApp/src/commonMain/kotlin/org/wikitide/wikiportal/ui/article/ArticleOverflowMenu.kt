package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.article_overflow_find_on_page
import org.wikitide.wikiportal.resources.article_overflow_forward
import org.wikitide.wikiportal.resources.article_overflow_remove_offline_copy
import org.wikitide.wikiportal.resources.article_overflow_save_offline
import org.wikitide.wikiportal.resources.article_overflow_share
import org.wikitide.wikiportal.resources.common_refresh

@Composable
fun ArticleOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showForward: Boolean,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onFindOnPage: () -> Unit,
    onShare: () -> Unit,
    isOfflineSaved: Boolean,
    isSavingOffline: Boolean,
    onToggleOfflineSave: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
    ) {
        if (showForward) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.article_overflow_forward)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onForward()
                },
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.common_refresh)) },
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            onClick = {
                onDismiss()
                onRefresh()
            },
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.article_overflow_find_on_page)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = {
                onDismiss()
                onFindOnPage()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.article_overflow_share)) },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            },
        )

        DropdownMenuItem(
            text = {
                Text(
                if (isOfflineSaved) stringResource(
                        Res.string.article_overflow_remove_offline_copy
                    ) else stringResource(Res.string.article_overflow_save_offline)
            )
            },
            leadingIcon = {
                if (isSavingOffline) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isOfflineSaved) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (isOfflineSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            },
            onClick = {
                onDismiss()
                onToggleOfflineSave()
            },
        )
    }
}
