package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
    onDismiss: (() -> Unit)? = null,
    dismissIcon: ImageVector = Icons.Filled.Close,
    dismissContentDescription: String = "Close",
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
            if (showThumbnail) {
                // The dismiss button sits on top of the thumbnail itself
                // here, same as the grid switcher's TabCard, with a dark
                // scrim behind it so it stays legible over a light
                // screenshot or lead image. Without a thumbnail there's
                // nothing under it to obscure, so it's just pinned plainly
                // to that corner of the row instead, below.
                Box(Modifier.size(84.dp)) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (onDismiss != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.matchParentSize()) {
                                Icon(
                                    dismissIcon,
                                    contentDescription = dismissContentDescription,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            } else if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(dismissIcon, contentDescription = dismissContentDescription, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
