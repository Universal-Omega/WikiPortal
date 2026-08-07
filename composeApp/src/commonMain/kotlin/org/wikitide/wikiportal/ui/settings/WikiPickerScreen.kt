package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.PresetFolders
import org.wikitide.wikiportal.data.model.Rank
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.ui.components.rememberDragReorderState
import org.wikitide.wikiportal.util.RankUtil

/**
 * A folder or an ungrouped custom wiki, whichever sits at the root of
 * "Your wikis". Wrapping both in one type is what lets them share a
 * single sorted list and a single DragReorderState, see
 * WikiPickerScreen's rootItems and rootDragState, so a drag can freely
 * move a folder past a wiki or the other way around.
 */
private sealed interface RootItem {
    val id: String
    val rank: Rank

    data class FolderRoot(val folder: WikiFolder) : RootItem {
        override val id get() = folder.id
        override val rank get() = folder.rank
    }

    data class WikiRoot(val wiki: WikiSite) : RootItem {
        override val id get() = wiki.id
        override val rank get() = wiki.rank
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiPickerScreen(onBack: () -> Unit, onAddCustomWiki: () -> Unit, onBrowseWikis: () -> Unit, repository: AppRepository = koinInject()) {
    val activeWiki by repository.activeWiki.collectAsState()
    val presetWikis by repository.presetWikis.collectAsState()
    val customWikis by repository.customWikis.collectAsState()
    val customFolders by repository.customFolders.collectAsState()

    // The id of the wiki currently being offered a skin change, or null
    // when the dialog is closed. This is an id, not the WikiSite itself,
    // since a frozen snapshot wouldn't reflect the metadata refresh
    // SkinPickerDialog triggers below, where real availableSkins and
    // display names arrive after the dialog is already open. So this is
    // looked up live against presetWikis and customWikis on every
    // recomposition instead.
    var editingSkinForId by remember { mutableStateOf<String?>(null) }
    val editingSkinForWiki = editingSkinForId?.let { id -> (presetWikis + customWikis).firstOrNull { it.id == id } }

    // The id of the custom wiki currently being offered a folder to
    // move into, or null when that dialog is closed. Same shape as
    // editingSkinForId above, an id rather than a snapshot, for the
    // same reason.
    var movingWikiId by remember { mutableStateOf<String?>(null) }
    val movingWiki = movingWikiId?.let { id -> customWikis.firstOrNull { it.id == id } }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<WikiFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<WikiFolder?>(null) }

    // Which folders are currently expanded, keyed by folder id. Every
    // folder starts collapsed. That is the entire point of grouping
    // wikis into folders in the first place, so a farm this app
    // supports can grow to many entries over time without the picker
    // turning back into one long list the person has to scroll past.
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(folderId: String) = expandedFolders[folderId] == true
    fun toggleExpanded(folderId: String) { expandedFolders[folderId] = !isExpanded(folderId) }

    val presetsByFolder = remember(presetWikis) { presetWikis.groupBy { it.folderId } }
    val ungroupedPresetWikis = presetsByFolder[null] ?: emptyList()
    val listState = rememberLazyListState()
    val customByFolder = remember(customWikis) { customWikis.groupBy { it.folderId } }
    val ungroupedCustomWikis = customByFolder[null] ?: emptyList()

    val rootItems = remember(customFolders, ungroupedCustomWikis) {
        (customFolders.map { RootItem.FolderRoot(it) } + ungroupedCustomWikis.map { RootItem.WikiRoot(it) }).sortedBy { it.rank }
    }

    // A rank for a wiki that's about to land inside [folderId], placed
    // after every wiki already in there. Used both when a drag drops a
    // wiki directly onto a folder row and, further down, isn't needed
    // for dragging one back out, since exiting always targets the root
    // list instead, see onWikiExitFolder.
    fun nextRankInFolder(folderId: String): Rank {
        val highest = customByFolder[folderId].orEmpty().maxByOrNull { it.rank }?.rank?.value ?: ""
        return Rank(RankUtil.between(highest, null))
    }

    // Called when a wiki is dragged past the very top or bottom edge of
    // the folder it's currently in, past where reordering inside that
    // folder has anywhere left to put it, see DragReorderState's
    // onExitBounds. [direction] -1 means it was dragged out upward, so
    // it lands right before [folder] at the root level; +1 means
    // dragged out downward, landing right after it instead.
    fun onWikiExitFolder(folder: WikiFolder, wikiId: String, direction: Int) {
        val folderIndex = rootItems.indexOfFirst { it.id == folder.id }
        val newRank = if (direction < 0) {
            val beforeRank = rootItems.getOrNull(folderIndex - 1)?.rank?.value ?: ""
            Rank(RankUtil.between(beforeRank, folder.rank.value))
        } else {
            val afterRank = rootItems.getOrNull(folderIndex + 1)?.rank?.value
            Rank(RankUtil.between(folder.rank.value, afterRank))
        }
        repository.moveWikiToFolder(wikiId, null, newRank)
    }

    val rootDragState = rememberDragReorderState(
        items = rootItems,
        id = { it.id },
        rank = { it.rank },
        key = "root-items",
        isContainer = { it is RootItem.FolderRoot },
        onMove = { itemId, newRank ->
            when (val item = rootItems.firstOrNull { it.id == itemId }) {
                is RootItem.FolderRoot -> repository.setFolderRank(itemId, newRank)
                is RootItem.WikiRoot -> repository.setCustomWikiRank(itemId, newRank)
                null -> Unit
            }
        },
        onDropIntoContainer = { wikiId, folderId -> repository.moveWikiToFolder(wikiId, folderId, nextRankInFolder(folderId)) },
    )

    var reorderMode by remember { mutableStateOf(false) }
    val enterReorderMode: () -> Unit = { reorderMode = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Choose a wiki") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomWiki) {
                Icon(Icons.Filled.Add, contentDescription = "Add a wiki by URL")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
        ) {
            item { GroupLabel("Featured wikis") }
            items(ungroupedPresetWikis, key = { it.id }) { wiki ->
                WikiRow(
                    wiki, wiki.id == activeWiki.id,
                    onClick = { repository.setActiveWiki(wiki); onBack() },
                    onEditSkin = { editingSkinForId = wiki.id },
                    repository = repository,
                )
            }
            PresetFolders.all.forEach { folder ->
                val wikisInFolder = presetsByFolder[folder.id].orEmpty()
                if (wikisInFolder.isEmpty()) return@forEach
                folderSection(
                    folder = folder,
                    wikis = wikisInFolder,
                    expanded = isExpanded(folder.id),
                    activeWikiId = activeWiki.id,
                    repository = repository,
                    onToggle = { toggleExpanded(folder.id) },
                    onClickWiki = { wiki -> repository.setActiveWiki(wiki); onBack() },
                    onEditSkin = { wiki -> editingSkinForId = wiki.id },
                )
            }

            if (customWikis.isNotEmpty() || customFolders.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupLabel("Your wikis", modifier = Modifier.weight(1f))
                        TextButton(onClick = { reorderMode = !reorderMode }) {
                            Text(if (reorderMode) "Done" else "Reorder")
                        }
                    }
                }
                items(rootDragState.items, key = { item -> if (item is RootItem.FolderRoot) "folder-${item.id}" else "wiki-${item.id}" }) { item ->
                    when (item) {
                        is RootItem.WikiRoot -> {
                            val wiki = item.wiki
                            WikiRow(
                                wiki, wiki.id == activeWiki.id,
                                onClick = { if (!reorderMode) { repository.setActiveWiki(wiki); onBack() } },
                                onRemove = { repository.removeCustomWiki(wiki) },
                                onEditSkin = { editingSkinForId = wiki.id },
                                onMoveToFolder = { movingWikiId = wiki.id },
                                repository = repository,
                                dragState = rootDragState,
                                reorderMode = reorderMode,
                                onEnterReorderMode = enterReorderMode,
                            )
                        }
                        is RootItem.FolderRoot -> {
                            val folder = item.folder
                            val wikisInFolder = customByFolder[folder.id].orEmpty()
                            FolderSectionContent(
                                folder = folder,
                                wikis = wikisInFolder,
                                expanded = isExpanded(folder.id),
                                activeWikiId = activeWiki.id,
                                repository = repository,
                                onToggle = { toggleExpanded(folder.id) },
                                onClickWiki = { wiki -> if (!reorderMode) { repository.setActiveWiki(wiki); onBack() } },
                                onEditSkin = { wiki -> editingSkinForId = wiki.id },
                                onRemoveWiki = { wiki -> repository.removeCustomWiki(wiki) },
                                onMoveWikiToFolder = { wiki -> movingWikiId = wiki.id },
                                onRenameFolder = { renamingFolder = folder },
                                onDeleteFolder = { deletingFolder = folder },
                                dragState = rootDragState,
                                reorderMode = reorderMode,
                                onEnterReorderMode = enterReorderMode,
                                wikisReorderable = true,
                                onExitFolder = { wikiId, direction -> onWikiExitFolder(folder, wikiId, direction) },
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddCustomWiki).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Add a wiki by URL",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onBrowseWikis).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Browse wikis",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showNewFolderDialog = true }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "New folder",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }

    editingSkinForWiki?.let { wiki ->
        SkinPickerDialog(
            wiki = wiki,
            repository = repository,
            onDismiss = { editingSkinForId = null },
            onSkinSelected = { skin ->
                repository.setWikiSkin(wiki.id, skin)
                editingSkinForId = null
            },
        )
    }

    movingWiki?.let { wiki ->
        MoveToFolderDialog(
            wiki = wiki,
            folders = customFolders,
            onDismiss = { movingWikiId = null },
            onSelectFolder = { folderId ->
                repository.moveWikiToFolder(wiki.id, folderId)
                movingWikiId = null
            },
            onCreateFolder = { name ->
                val created = repository.createFolder(name)
                repository.moveWikiToFolder(wiki.id, created.id)
                movingWikiId = null
            },
        )
    }

    if (showNewFolderDialog) {
        FolderNameDialog(
            title = "New folder",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name -> repository.createFolder(name); showNewFolderDialog = false },
        )
    }

    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = "Rename folder",
            initialName = folder.name,
            confirmLabel = "Save",
            onDismiss = { renamingFolder = null },
            onConfirm = { name -> repository.renameFolder(folder.id, name); renamingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = { Text("The wikis inside stay, they just won't be grouped in a folder anymore.") },
            confirmButton = {
                TextButton(onClick = { repository.deleteFolder(folder.id); deletingFolder = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
        )
    }
}
