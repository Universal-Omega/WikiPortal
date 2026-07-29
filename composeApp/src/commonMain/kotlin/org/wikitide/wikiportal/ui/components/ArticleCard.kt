package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ArticleCard(
    title: String,
    extract: String,
    thumbnailUrl: String?,
    showImages: Boolean,
    modifier: Modifier = Modifier,
    previewBitmap: ImageBitmap? = null,
    wikiLabel: String? = null,
    onClick: () -> Unit,
    onClose: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val showThumbnail = showImages && (previewBitmap != null || !thumbnailUrl.isNullOrBlank())
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!wikiLabel.isNullOrBlank()) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        wikiLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
                if (extract.isNotBlank()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        extract,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailingContent?.let {
                    Spacer(Modifier.size(8.dp))
                    it()
                }
            }
            if (onClose != null || showThumbnail) {
                Column(horizontalAlignment = Alignment.End) {
                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(20.dp))
                        }
                    }
                    if (showThumbnail) {
                        if (onClose != null) Spacer(Modifier.size(4.dp))
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(84.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AsyncImage(
                                model = thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(84.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }
}
