package org.wikitide.wikiportal.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.runtime.Immutable
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
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.common_cannot_be_undone
import org.wikitide.wikiportal.resources.common_clear_action
import org.wikitide.wikiportal.resources.common_deselect_all
import org.wikitide.wikiportal.resources.common_remove
import org.wikitide.wikiportal.resources.common_select_all
import org.wikitide.wikiportal.resources.dashboard_saved
import org.wikitide.wikiportal.resources.history_days_ago
import org.wikitide.wikiportal.resources.history_hours_ago
import org.wikitide.wikiportal.resources.history_just_now
import org.wikitide.wikiportal.resources.history_minutes_ago
import org.wikitide.wikiportal.resources.history_today
import org.wikitide.wikiportal.resources.history_yesterday
import org.wikitide.wikiportal.resources.saved_cancel_selection
import org.wikitide.wikiportal.resources.saved_clear_history
import org.wikitide.wikiportal.resources.saved_clear_history_body
import org.wikitide.wikiportal.resources.saved_clear_history_title
import org.wikitide.wikiportal.resources.saved_empty_history
import org.wikitide.wikiportal.resources.saved_empty_offline
import org.wikitide.wikiportal.resources.saved_empty_saved
import org.wikitide.wikiportal.resources.saved_n_selected
import org.wikitide.wikiportal.resources.saved_remove_n_history
import org.wikitide.wikiportal.resources.saved_remove_n_offline
import org.wikitide.wikiportal.resources.saved_remove_n_saved
import org.wikitide.wikiportal.resources.saved_remove_one_history
import org.wikitide.wikiportal.resources.saved_remove_one_offline
import org.wikitide.wikiportal.resources.saved_remove_one_saved
import org.wikitide.wikiportal.resources.saved_remove_selected
import org.wikitide.wikiportal.resources.saved_tab_history
import org.wikitide.wikiportal.resources.saved_tab_offline
import org.wikitide.wikiportal.resources.saved_tab_saved
import org.wikitide.wikiportal.ui.components.ArticleCard
import org.wikitide.wikiportal.ui.components.DestructiveConfirmDialog
import org.wikitide.wikiportal.ui.components.OpenTabIndicator
import org.wikitide.wikiportal.util.HistoryDayBucket
import org.wikitide.wikiportal.util.RelativeTime
import org.wikitide.wikiportal.util.epochDayFromMillis
import org.wikitide.wikiportal.util.formatHistorySectionDate
import org.wikitide.wikiportal.util.historyDayBucket
import org.wikitide.wikiportal.util.nowEpochMillis
import org.wikitide.wikiportal.util.relativeTimeSince

/** Index of the "Saved" tab in [SavedScreen]'s [SecondaryTabRow]. */
private const val TAB_SAVED = 0

/** Index of the "Offline" tab in [SavedScreen]'s [SecondaryTabRow]. */
private const val TAB_OFFLINE = 1

/** Index of the "History" tab in [SavedScreen]'s [SecondaryTabRow]. */
private const val TAB_HISTORY = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onArticleClick: (wikiId: String, title: String) -> Unit,
    onOfflineArticleClick: (wikiId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    repository: AppRepository = koinInject(),
    tabsRepository: TabsRepository = koinInject(),
) {
    val showImages by repository.showImages.collectAsState()
    val saved by repository.savedPages.collectAsState()
    val history by repository.history.collectAsState()
    val offline by repository.offlineArticles.collectAsState()
    val tabs by tabsRepository.tabs.collectAsState()
    var tab by remember { mutableStateOf(TAB_SAVED) }
    var selectedKeys by remember(tab) { mutableStateOf(setOf<String>()) }
    var showDeleteSelectedConfirm by remember(tab) { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    val selectionActive = selectedKeys.isNotEmpty()

    val openKeys = remember(tabs) {
        tabs.map { it.wikiId to it.title }.toSet()
    }

    fun keyOf(page: SavedPage) = page.wikiId + "|" + page.title + page.timestampEpochMillis

    val list = when (tab) {
        TAB_SAVED -> saved
        TAB_OFFLINE -> offline
        else -> history
    }
    val allSelected = list.isNotEmpty() && selectedKeys.size == list.size

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (selectionActive) stringResource(Res.string.saved_n_selected, selectedKeys.size) else stringResource(Res.string.dashboard_saved),
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            navigationIcon = {
                if (selectionActive) {
                    IconButton(onClick = { selectedKeys = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.saved_cancel_selection))
                    }
                }
            },
            actions = {
                if (selectionActive) {
                    IconButton(onClick = { selectedKeys = if (allSelected) emptySet() else list.map { keyOf(it) }.toSet() }) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(if (allSelected) Res.string.common_deselect_all else Res.string.common_select_all),
                            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.saved_remove_selected))
                    }
                } else if (tab == TAB_HISTORY && history.isNotEmpty()) {
                    IconButton(onClick = { showClearHistoryConfirm = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(Res.string.saved_clear_history))
                    }
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == TAB_SAVED, onClick = { tab = TAB_SAVED }, text = { Text(stringResource(Res.string.saved_tab_saved, saved.size)) })
            Tab(selected = tab == TAB_OFFLINE, onClick = { tab = TAB_OFFLINE }, text = { Text(stringResource(Res.string.saved_tab_offline, offline.size)) })
            Tab(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, text = { Text(stringResource(Res.string.saved_tab_history)) })
        }

        val emptyLabel = when (tab) {
            TAB_SAVED -> stringResource(Res.string.saved_empty_saved)
            TAB_OFFLINE -> stringResource(Res.string.saved_empty_offline)
            else -> stringResource(Res.string.saved_empty_history)
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (tab == TAB_HISTORY) {
            HistoryList(
                history = history,
                showImages = showImages,
                openKeys = openKeys,
                selectionActive = selectionActive,
                selectedKeys = selectedKeys,
                keyOf = ::keyOf,
                onToggleSelect = { key -> selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key },
                onArticleClick = onArticleClick,
            )
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
                        selectionModeActive = selectionActive,
                        selected = isSelected,
                        onClick = {
                            if (selectionActive) {
                                selectedKeys = if (isSelected) selectedKeys - itemKey else selectedKeys + itemKey
                            } else if (tab == TAB_OFFLINE) {
                                onOfflineArticleClick(page.wikiId, page.title)
                            } else {
                                onArticleClick(page.wikiId, page.title)
                            }
                        },
                        onLongClick = {
                            selectedKeys = if (isSelected) selectedKeys - itemKey else selectedKeys + itemKey
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
            TAB_SAVED -> if (count == 1) stringResource(Res.string.saved_remove_one_saved) else stringResource(Res.string.saved_remove_n_saved, count)
            TAB_OFFLINE -> if (count == 1) stringResource(Res.string.saved_remove_one_offline) else stringResource(Res.string.saved_remove_n_offline, count)
            else -> if (count == 1) stringResource(Res.string.saved_remove_one_history) else stringResource(Res.string.saved_remove_n_history, count)
        }
        DestructiveConfirmDialog(
            title = confirmText,
            text = stringResource(Res.string.common_cannot_be_undone),
            confirmLabel = stringResource(Res.string.common_remove),
            onConfirm = {
                val source = when (tab) {
                    TAB_SAVED -> saved
                    TAB_OFFLINE -> offline
                    else -> history
                }
                val toRemove = source.filter { keyOf(it) in selectedKeys }
                toRemove.forEach { page ->
                    when (tab) {
                        TAB_SAVED -> repository.toggleSaved(page)
                        TAB_OFFLINE -> repository.removeOfflineArticle(page.wikiId, page.title)
                        else -> repository.removeHistoryEntry(page.wikiId, page.title)
                    }
                }
                selectedKeys = emptySet()
            },
            onDismiss = { showDeleteSelectedConfirm = false },
        )
    }

    if (showClearHistoryConfirm) {
        DestructiveConfirmDialog(
            title = stringResource(Res.string.saved_clear_history_title),
            text = stringResource(Res.string.saved_clear_history_body),
            confirmLabel = stringResource(Res.string.common_clear_action),
            onConfirm = repository::clearHistory,
            onDismiss = { showClearHistoryConfirm = false },
        )
    }
}

/** One contiguous run of same-day history entries, oldest field first since that's what the header needs. */
@Immutable
private data class HistorySection(
    val bucket: HistoryDayBucket,
    val epochMillis: Long,
    val pages: List<SavedPage>,
)

/**
 * Groups [history] into same-day runs. History is already newest first,
 * see AppRepository.recordVisit, so entries for the same day are always
 * contiguous and this only needs a single pass, no sorting.
 */
private fun groupHistoryByDay(history: List<SavedPage>, nowMillis: Long): List<HistorySection> {
    val sections = mutableListOf<HistorySection>()
    for (page in history) {
        val bucket = historyDayBucket(page.timestampEpochMillis, nowMillis)
        val current = sections.lastOrNull()
        val continuesCurrentSection = current != null && current.bucket == bucket &&
            (bucket != HistoryDayBucket.OLDER || epochDayFromMillis(current.epochMillis) == epochDayFromMillis(page.timestampEpochMillis))
        if (continuesCurrentSection && current != null) {
            sections[sections.lastIndex] = current.copy(pages = current.pages + page)
        } else {
            sections += HistorySection(bucket, page.timestampEpochMillis, listOf(page))
        }
    }
    return sections
}

/**
 * The History tab's own list, grouped into "Today", "Yesterday", and
 * dated sections, each card showing how long ago it was last read. This
 * replaces SavedScreen's plain flat list only for history, since Saved
 * and Offline have no timestamp worth grouping by.
 */
@Composable
private fun HistoryList(
    history: List<SavedPage>,
    showImages: Boolean,
    openKeys: Set<Pair<String, String>>,
    selectionActive: Boolean,
    selectedKeys: Set<String>,
    keyOf: (SavedPage) -> String,
    onToggleSelect: (String) -> Unit,
    onArticleClick: (wikiId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMillis = remember(history) { nowEpochMillis() }
    val sections = remember(history, nowMillis) { groupHistoryByDay(history, nowMillis) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.bucket}-${epochDayFromMillis(section.epochMillis)}") {
                HistorySectionHeader(section)
            }
            items(section.pages, key = { keyOf(it) }) { page ->
                val isOpen = (page.wikiId to page.title) in openKeys
                val itemKey = keyOf(page)
                val isSelected = itemKey in selectedKeys
                ArticleCard(
                    title = page.title,
                    extract = page.extract,
                    thumbnailUrl = page.thumbnailUrl,
                    showImages = showImages,
                    wikiLabel = page.wikiName,
                    selectionModeActive = selectionActive,
                    selected = isSelected,
                    onClick = {
                        if (selectionActive) onToggleSelect(itemKey) else onArticleClick(page.wikiId, page.title)
                    },
                    onLongClick = { onToggleSelect(itemKey) },
                    trailingContent = {
                        HistoryTimestamp(page.timestampEpochMillis, nowMillis, isOpen)
                    },
                )
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(section: HistorySection, modifier: Modifier = Modifier) {
    val label = when (section.bucket) {
        HistoryDayBucket.TODAY -> stringResource(Res.string.history_today)
        HistoryDayBucket.YESTERDAY -> stringResource(Res.string.history_yesterday)
        HistoryDayBucket.OLDER -> formatHistorySectionDate(section.epochMillis)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** The "last read" duration, and the open-tab indicator when the article also has an open tab, as an ArticleCard's trailing content. */
@Composable
private fun HistoryTimestamp(epochMillis: Long, nowMillis: Long, isOpen: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        val relative = relativeTimeSince(epochMillis, nowMillis)
        val text = when (relative) {
            is RelativeTime.JustNow -> stringResource(Res.string.history_just_now)
            is RelativeTime.MinutesAgo -> stringResource(Res.string.history_minutes_ago, relative.minutes)
            is RelativeTime.HoursAgo -> stringResource(Res.string.history_hours_ago, relative.hours)
            is RelativeTime.DaysAgo -> stringResource(Res.string.history_days_ago, relative.days)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isOpen) {
            OpenTabIndicator(modifier = Modifier.padding(top = 4.dp))
        }
    }
}
