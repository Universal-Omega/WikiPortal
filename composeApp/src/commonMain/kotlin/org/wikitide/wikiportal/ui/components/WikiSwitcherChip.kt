package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WikiSwitcherChip(wikiName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                wikiName,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        },
        trailingIcon = { Icon(Icons.Filled.UnfoldMore, contentDescription = "Switch wiki") },
        modifier = modifier.padding(end = 12.dp),
    )
}
