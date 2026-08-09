package org.wikitide.wikiportal.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.model.ArticleTab
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.tabs_close_all
import org.wikitide.wikiportal.resources.tabs_close_all_body
import org.wikitide.wikiportal.resources.tabs_close_all_confirm
import org.wikitide.wikiportal.resources.tabs_close_all_title
import org.wikitide.wikiportal.resources.tabs_close_tab
import org.wikitide.wikiportal.resources.tabs_grid_title_n
import org.wikitide.wikiportal.resources.tabs_grid_title_one
import org.wikitide.wikiportal.resources.tabs_no_open_tabs
import org.wikitide.wikiportal.resources.tabs_viewing
import org.wikitide.wikiportal.ui.components.DestructiveConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    onBack: () -> Unit,
    onSelectTab: (String) -> Unit,
    tabsRepository: TabsRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val previews by tabsRepository.previews.collectAsState()
    val activeTabId by tabsRepository.activeTabId.collectAsState()
    val gridState = rememberLazyGridState()
    var showCloseAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index >= 0) gridState.animateScrollToItem(index)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                    if (tabs.size == 1) stringResource(
                            Res.string.tabs_grid_title_one
                        ) else stringResource(Res.string.tabs_grid_title_n, tabs.size)
                )
                },
                navigationIcon = {
                    IconButton(
                    onClick = onBack
                ) {
                    Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back)
                )
                }
                },
                actions = {
                    if (tabs.isNotEmpty()) {
                        IconButton(onClick = { showCloseAllConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(Res.string.tabs_close_all)
                            )
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        if (tabs.isEmpty()) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.tabs_no_open_tabs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        preview = previews[tab.id],
                        isActive = tab.id == activeTabId,
                        onClick = { onSelectTab(tab.id) },
                        onClose = { tabsRepository.closeTab(tab.id) },
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }

    if (showCloseAllConfirm) {
        DestructiveConfirmDialog(
            title = stringResource(Res.string.tabs_close_all_title),
            text = stringResource(Res.string.tabs_close_all_body),
            confirmLabel = stringResource(Res.string.tabs_close_all_confirm),
            onConfirm = {
                tabsRepository.closeAllTabs();
                onBack()
            },
            onDismiss = { showCloseAllConfirm = false },
        )
    }
}

@Composable
private fun TabCard(
    tab: ArticleTab,
    preview: ImageBitmap?,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick)
            .then(
                if (isActive) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            ),
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
                if (isActive) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            stringResource(Res.string.tabs_viewing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.matchParentSize()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.tabs_close_tab),
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
