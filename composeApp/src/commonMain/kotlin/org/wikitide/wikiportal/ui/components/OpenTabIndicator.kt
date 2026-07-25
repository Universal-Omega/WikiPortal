package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small label shown on an ArticleCard when that article already has
 * an open tab. Tapping the card jumps to the existing tab instead of
 * opening a duplicate one, see App.kt's openArticle and
 * TabsRepository's findOpenTab. This is shared across Dashboard, Saved,
 * Offline, and History so the "already open" affordance is consistent
 * everywhere an ArticleCard can link back into the tab switcher.
 */
@Composable
fun OpenTabIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Tab,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Open in a tab",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
