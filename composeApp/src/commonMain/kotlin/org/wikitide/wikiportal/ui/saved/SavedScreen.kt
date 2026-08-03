package org.wikitide.wikiportal.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.DestructiveConfirmDialog
import org.wikitide.wikiportal.ui.components.OpenTabIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOfflineArticleClick: (wikiId: String, title: String) -> Unit,
    repository: AppRepository = koinInject(),
    tabsRepository: TabsRepository = koinInject(),
) {
    val showImages by repository.showImages.collectAsState()
    val saved by repository.savedPages.collectAsState()
    val history by repository.history.collectAsState()
    val offline by repository.offlineArticles.collectAsState()
    val tabs by tabsRepository.tabs.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var selectedKeys by remember(tab) { mutableStateOf(setOf<String>()) }
    var showDeleteSelectedConfirm by remember(tab) { mutableStateOf(false) }
    val selectionActive = selectedKeys.isNotEmpty()
    val selectionAllowed = tab != 2

    val openKeys = remember(tabs) {
        tabs.map { it.wikiId to it.title }.toSet()
    }

    fun keyOf(page: SavedPage) = page.wikiId + "|" + page.title + page.timestampEpochMillis

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (selectionActive) "${selectedKeys.size} selected" else "Saved",
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            navigationIcon = {
                if (selectionActive) {
                    IconButton(onClick = { selectedKeys = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                    }
                }
            },
            actions = {
                if (selectionActive) {
                    IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove selected")
                    }
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saved (${saved.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Offline (${offline.size})") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("History") })
        }

        val list = when (tab) {
            0 -> saved
            1 -> offline
            else -> history
        }
        val emptyLabel = when (tab) {
            0 -> "Articles you save will show up here"
            1 -> "Articles you download for offline reading will show up here"
            else -> "Articles you read will show up here"
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { keyOf(it) }) { page: SavedPage ->
                    val isOpen = (page.wikiId to page.title) in openKeys
                    val itemKey = keyOf(page)
                    val isSelected = itemKey in selectedKeys
                    ArticleCard(
                        title = page.title,
                        extract = page.extract,
                        thumbnailUrl = page.thumbnailUrl,
                        showImages = showImages,
                        wikiLabel = page.wikiName,
                        selectionModeActive = selectionAllowed && selectionActive,
                        selected = isSelected,
                        onClick = {
                            if (selectionAllowed && selectionActive) {
                                selectedKeys = if (isSelected) selectedKeys - itemKey else selectedKeys + itemKey
                            } else if (tab == 1) {
                                onOfflineArticleClick(page.wikiId, page.title)
                            } else {
                                onArticleClick(page.wikiId, page.title)
                            }
                        },
                        onLongClick = if (selectionAllowed) {
                            { selectedKeys = if (isSelected) selectedKeys - itemKey else selectedKeys + itemKey }
                        } else {
                            null
                        },
                        trailingContent = if (isOpen) {
                            { OpenTabIndicator() }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        val count = selectedKeys.size
        val confirmText = when (tab) {
            0 -> if (count == 1) "Remove 1 saved article?" else "Remove $count saved articles?"
            else -> if (count == 1) "Remove 1 offline copy?" else "Remove $count offline copies?"
        }
        DestructiveConfirmDialog(
            title = confirmText,
            text = "This can't be undone.",
            confirmLabel = "Remove",
            onConfirm = {
                val source = if (tab == 0) saved else offline
                val toRemove = source.filter { keyOf(it) in selectedKeys }
                toRemove.forEach { page ->
                    if (tab == 0) repository.toggleSaved(page) else repository.removeOfflineArticle(page.wikiId, page.title)
                }
                selectedKeys = emptySet()
            },
            onDismiss = { showDeleteSelectedConfirm = false },
        )
    }
}
