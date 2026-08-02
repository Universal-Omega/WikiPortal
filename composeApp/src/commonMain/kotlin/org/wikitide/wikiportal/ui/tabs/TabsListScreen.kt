package org.wikitide.wikiportal.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.DestructiveConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListScreen(
    onOpenTab: (wikiId: String, title: String) -> Unit,
    tabsRepository: TabsRepository = koinInject(),
    repository: AppRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val showImages by repository.showImages.collectAsState()
    val activeTabId by tabsRepository.activeTabId.collectAsState()
    val previews by tabsRepository.previews.collectAsState()
    val listState = rememberLazyListState()
    var showCloseAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tabs", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                if (tabs.isNotEmpty()) {
                    IconButton(onClick = { showCloseAllConfirm = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Close all tabs")
                    }
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    ArticleCard(
                        title = tab.title,
                        extract = tab.extract.orEmpty(),
                        thumbnailUrl = tab.thumbnailUrl,
                        showImages = showImages,
                        previewBitmap = previews[tab.id],
                        wikiLabel = tab.wikiName.takeIf { it.isNotBlank() },
                        modifier = if (isActive) {
                            Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                        onClick = { onOpenTab(tab.wikiId, tab.title) },
                        onDismiss = { tabsRepository.closeTab(tab.id) },
                        dismissContentDescription = "Close tab",
                        trailingContent = if (isActive) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Last viewed",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showCloseAllConfirm) {
        DestructiveConfirmDialog(
            title = "Close all tabs?",
            text = "This closes every open tab. It can't be undone.",
            confirmLabel = "Close all",
            onConfirm = tabsRepository::closeAllTabs,
            onDismiss = { showCloseAllConfirm = false },
        )
    }
}
