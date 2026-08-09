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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.common_cannot_be_undone
import org.wikitide.wikiportal.resources.saved_cancel_selection
import org.wikitide.wikiportal.resources.saved_n_selected
import org.wikitide.wikiportal.resources.tabs_close
import org.wikitide.wikiportal.resources.tabs_close_all
import org.wikitide.wikiportal.resources.tabs_close_all_body
import org.wikitide.wikiportal.resources.tabs_close_all_confirm
import org.wikitide.wikiportal.resources.tabs_close_all_title
import org.wikitide.wikiportal.resources.tabs_close_n
import org.wikitide.wikiportal.resources.tabs_close_one
import org.wikitide.wikiportal.resources.tabs_close_selected
import org.wikitide.wikiportal.resources.tabs_last_viewed
import org.wikitide.wikiportal.resources.tabs_no_open_tabs
import org.wikitide.wikiportal.resources.tabs_title
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
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val selectionActive = selectedIds.isNotEmpty()

    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LaunchedEffect(tabs) {
        selectedIds = selectedIds.filter { id -> tabs.any { it.id == id } }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (selectionActive) stringResource(
                            Res.string.saved_n_selected,
                            selectedIds.size
                        ) else stringResource(Res.string.tabs_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            navigationIcon = {
                if (selectionActive) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.saved_cancel_selection))
                    }
                }
            },
            actions = {
                if (selectionActive) {
                    IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.tabs_close_selected))
                    }
                } else if (tabs.isNotEmpty()) {
                    IconButton(onClick = { showCloseAllConfirm = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(Res.string.tabs_close_all))
                    }
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.tabs_no_open_tabs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val isSelected = tab.id in selectedIds
                    ArticleCard(
                        title = tab.title,
                        extract = tab.extract.orEmpty(),
                        thumbnailUrl = tab.thumbnailUrl,
                        showImages = showImages,
                        previewBitmap = previews[tab.id],
                        wikiLabel = tab.wikiName.takeIf { it.isNotBlank() },
                        selectionModeActive = selectionActive,
                        selected = isSelected,
                        modifier = if (isActive) {
                            Modifier.border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                RoundedCornerShape(12.dp)
                            )
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (selectionActive) {
                                selectedIds = if (isSelected) selectedIds - tab.id else selectedIds + tab.id
                            } else {
                                onOpenTab(tab.wikiId, tab.title)
                            }
                        },
                        onLongClick = {
                            selectedIds = if (isSelected) selectedIds - tab.id else selectedIds + tab.id
                        },
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
                                        stringResource(Res.string.tabs_last_viewed),
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
            title = stringResource(Res.string.tabs_close_all_title),
            text = stringResource(Res.string.tabs_close_all_body),
            confirmLabel = stringResource(Res.string.tabs_close_all_confirm),
            onConfirm = tabsRepository::closeAllTabs,
            onDismiss = { showCloseAllConfirm = false },
        )
    }

    if (showDeleteSelectedConfirm) {
        val count = selectedIds.size
        DestructiveConfirmDialog(
            title = if (count == 1) stringResource(
                    Res.string.tabs_close_one
                ) else stringResource(Res.string.tabs_close_n, count),
            text = stringResource(Res.string.common_cannot_be_undone),
            confirmLabel = stringResource(Res.string.tabs_close),
            onConfirm = {
                selectedIds.forEach { id -> tabsRepository.closeTab(id) }
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteSelectedConfirm = false },
        )
    }
}
