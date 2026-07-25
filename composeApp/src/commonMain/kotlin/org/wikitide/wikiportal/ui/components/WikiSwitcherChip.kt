package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WikiSwitcherChip(wikiName: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(wikiName) },
        trailingIcon = { Icon(Icons.Filled.UnfoldMore, contentDescription = "Switch wiki") },
        modifier = Modifier.padding(end = 12.dp),
    )
}
