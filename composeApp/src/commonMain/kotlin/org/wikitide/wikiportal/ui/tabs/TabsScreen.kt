package org.wikitide.wikiportal.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.ArticleTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    onBack: () -> Unit,
    onSelectTab: (String) -> Unit,
    tabsRepository: TabsRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val previews by tabsRepository.previews.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("${tabs.size} tab${if (tabs.size == 1) "" else "s"}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (tabs.isNotEmpty()) {
                        IconButton(onClick = { tabsRepository.closeAllTabs(); onBack() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Close all tabs")
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        if (tabs.isEmpty()) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        preview = previews[tab.id],
                        onClick = { onSelectTab(tab.id) },
                        onClose = { tabsRepository.closeTab(tab.id) },
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: ArticleTab,
    preview: ImageBitmap?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(0.85f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    // Real captured tab content, Android and IOS only,
                    // takes priority.
                    preview != null -> Image(
                        bitmap = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                    // Falls back to the article's lead image where no
                    // capture is available, Desktop and Web, or hasn't
                    // happened yet.
                    tab.thumbnailUrl != null -> AsyncImage(
                        model = tab.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val outlineColor = Color.Black.copy(alpha = 0.75f)
                    val outlineOffsets = listOf(
                        -1.dp to -1.dp, 0.dp to -1.dp, 1.dp to -1.dp,
                        -1.dp to 0.dp, 1.dp to 0.dp,
                        -1.dp to 1.dp, 0.dp to 1.dp, 1.dp to 1.dp,
                    )
                    outlineOffsets.forEach { (x, y) ->
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = outlineColor,
                            modifier = Modifier.size(18.dp).offset(x = x, y = y),
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.matchParentSize()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close tab",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    tab.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tab.wikiName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
