package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.PresetFolders
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiPickerScreen(onBack: () -> Unit, onAddCustomWiki: () -> Unit, repository: AppRepository = koinInject()) {
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
    val listState = rememberLazyListState()

    // A local copy of the custom folder order, mutated live while a
    // drag is in progress so the list visibly reorders as the finger
    // moves, and only written back to the repository once the drag
    // ends. Falls back to whatever the repository has whenever nothing
    // is being dragged, so external changes, for example a folder
    // deleted from another dialog, are still picked up.
    var localCustomFolders by remember { mutableStateOf(customFolders) }
    var draggingFolderId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(customFolders) {
        if (draggingFolderId == null) localCustomFolders = customFolders
    }
    val customByFolder = remember(customWikis) { customWikis.groupBy { it.folderId } }
    val ungroupedCustomWikis = customByFolder[null] ?: emptyList()

    fun onFolderDragStart(folderId: String) {
        draggingFolderId = folderId
        dragOffsetY = 0f
    }

    fun onFolderDragEnd() {
        draggingFolderId = null
        dragOffsetY = 0f
        repository.reorderFolders(localCustomFolders.map { it.id })
    }

    // Moves the dragged folder one slot at a time as the accumulated
    // drag distance crosses the midpoint of the neighbor in that
    // direction, the same rule most drag to reorder lists use. This
    // reads actual on screen positions from the LazyColumn's own
    // layout info, keyed by the same "folder-<id>" key each header item
    // uses below, rather than assuming a fixed row height, since a
    // folder's own height differs depending on whether it is expanded.
    fun onFolderDrag(folderId: String, deltaY: Float) {
        dragOffsetY += deltaY
        val order = localCustomFolders
        val currentIndex = order.indexOfFirst { it.id == folderId }
        if (currentIndex < 0) return
        val draggedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "folder-$folderId" } ?: return
        val draggedCenter = draggedInfo.offset + draggedInfo.size / 2f + dragOffsetY

        val neighborIndex = when {
            dragOffsetY < 0 && currentIndex > 0 -> currentIndex - 1
            dragOffsetY > 0 && currentIndex < order.lastIndex -> currentIndex + 1
            else -> return
        }
        val neighborInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "folder-${order[neighborIndex].id}" } ?: return
        val neighborCenter = neighborInfo.offset + neighborInfo.size / 2f
        val crossedNeighbor = if (neighborIndex > currentIndex) draggedCenter > neighborCenter else draggedCenter < neighborCenter
        if (crossedNeighbor) {
            val reordered = order.toMutableList()
            val moved = reordered.removeAt(currentIndex)
            reordered.add(neighborIndex, moved)
            localCustomFolders = reordered
            dragOffsetY -= (neighborCenter - (draggedInfo.offset + draggedInfo.size / 2f))
        }
    }

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
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { GroupLabel("Featured wikis") }
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

            if (customWikis.isNotEmpty() || localCustomFolders.isNotEmpty()) {
                item { GroupLabel("Your wikis") }
                items(ungroupedCustomWikis, key = { it.id }) { wiki ->
                    WikiRow(
                        wiki, wiki.id == activeWiki.id,
                        onClick = { repository.setActiveWiki(wiki); onBack() },
                        onRemove = { repository.removeCustomWiki(wiki) },
                        onEditSkin = { editingSkinForId = wiki.id },
                        onMoveToFolder = { movingWikiId = wiki.id },
                        repository = repository,
                    )
                }
                localCustomFolders.forEach { folder ->
                    val wikisInFolder = customByFolder[folder.id].orEmpty()
                    folderSection(
                        folder = folder,
                        wikis = wikisInFolder,
                        expanded = isExpanded(folder.id),
                        activeWikiId = activeWiki.id,
                        repository = repository,
                        onToggle = { toggleExpanded(folder.id) },
                        onClickWiki = { wiki -> repository.setActiveWiki(wiki); onBack() },
                        onEditSkin = { wiki -> editingSkinForId = wiki.id },
                        onRemoveWiki = { wiki -> repository.removeCustomWiki(wiki) },
                        onMoveWikiToFolder = { wiki -> movingWikiId = wiki.id },
                        onRenameFolder = { renamingFolder = folder },
                        onDeleteFolder = { deletingFolder = folder },
                        isDragging = draggingFolderId == folder.id,
                        dragOffsetY = dragOffsetY,
                        onDragStart = { onFolderDragStart(folder.id) },
                        onDrag = { delta -> onFolderDrag(folder.id, delta) },
                        onDragEnd = { onFolderDragEnd() },
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
            folders = localCustomFolders,
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

/**
 * Renders one folder as a collapsible header followed, when expanded,
 * by its wikis. Shared between preset folders, see [PresetFolders], and
 * the person's own custom folders, with rename, delete, and drag
 * reorder only wired up for the latter, through [onRenameFolder],
 * [onDeleteFolder], and [onDragStart]/[onDrag]/[onDragEnd], since a
 * preset folder is not the person's to reorganize.
 */
private fun LazyListScope.folderSection(
    folder: WikiFolder,
    wikis: List<WikiSite>,
    expanded: Boolean,
    activeWikiId: String,
    repository: AppRepository,
    onToggle: () -> Unit,
    onClickWiki: (WikiSite) -> Unit,
    onEditSkin: (WikiSite) -> Unit,
    onRemoveWiki: ((WikiSite) -> Unit)? = null,
    onMoveWikiToFolder: ((WikiSite) -> Unit)? = null,
    onRenameFolder: (() -> Unit)? = null,
    onDeleteFolder: (() -> Unit)? = null,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    item(key = "folder-${folder.id}") {
        FolderHeaderRow(
            folder = folder,
            count = wikis.size,
            expanded = expanded,
            onToggle = onToggle,
            onRename = onRenameFolder,
            onDelete = onDeleteFolder,
            isDragging = isDragging,
            dragOffsetY = dragOffsetY,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
    if (expanded) {
        items(wikis, key = { it.id }) { wiki ->
            WikiRow(
                wiki, wiki.id == activeWikiId,
                onClick = { onClickWiki(wiki) },
                onRemove = onRemoveWiki?.let { { it(wiki) } },
                onEditSkin = { onEditSkin(wiki) },
                onMoveToFolder = onMoveWikiToFolder?.let { { it(wiki) } },
                repository = repository,
                indented = true,
            )
        }
    }
}

@Composable
private fun FolderHeaderRow(
    folder: WikiFolder,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: (() -> Unit)?,
    onDrag: ((Float) -> Unit)?,
    onDragEnd: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    val draggable = onDragStart != null && onDrag != null && onDragEnd != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
            .zIndex(if (isDragging) 1f else 0f)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (draggable) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder ${folder.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pointerInput(folder.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart.invoke() },
                        onDrag = { change, delta -> change.consume(); onDrag.invoke(delta.y) },
                        onDragEnd = { onDragEnd.invoke() },
                        onDragCancel = { onDragEnd.invoke() },
                    )
                },
            )
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 wiki" else "$count wikis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options for ${folder.name}")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (onRename != null) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * Each row triggers its own favicon refresh rather than the screen
 * doing it for the whole list up front. LazyColumn only composes rows
 * actually on screen, plus a small overscan buffer, so tying this to
 * WikiRow's own composition lifecycle naturally limits concurrent
 * requests to roughly what is visible right now, regardless of whether
 * the full preset and custom list has five entries or five hundred, and
 * more get triggered lazily, a few at a time, as the person scrolls.
 *
 * This uses [AppRepository.refreshFaviconOnly], not the full
 * [AppRepository.refreshWikiMetadata], since a row only shows the
 * favicon, so there is no reason to also pay for script path
 * re-probing or article path, skin, and main page title resolution
 * here. That full refresh still happens exactly when it is actually
 * needed, when activating a wiki or opening its skin dialog below, and
 * is skipped by refreshFaviconOnly automatically once it has, since the
 * full refresh already covers favicon as a side effect.
 *
 * A custom wiki's extra actions, changing its skin, moving it to a
 * folder, removing it, sit behind one overflow menu rather than as
 * separate icons in a row, since four icon buttons next to a favicon
 * and two lines of text was cramped enough to be hard to tap
 * accurately. A preset wiki only ever has a skin to change, so it keeps
 * a plain standalone icon instead, see hasExtraActions below. A menu
 * that only ever opens to one item is just an extra tap in front of
 * that same one item.
 */
@Composable
private fun WikiRow(
    wiki: WikiSite,
    selected: Boolean,
    onClick: () -> Unit,
    onEditSkin: () -> Unit,
    repository: AppRepository,
    onRemove: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    indented: Boolean = false,
) {
    LaunchedEffect(wiki.id) { repository.refreshFaviconOnly(wiki) }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = if (indented) 32.dp else 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = wiki.faviconUrl,
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(CircleShape),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.Public),
                placeholder = rememberVectorPainter(Icons.Filled.Public),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(wiki.name, style = MaterialTheme.typography.titleMedium)
            Text(
                wiki.description.ifBlank { wiki.baseUrl },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        val hasExtraActions = onMoveToFolder != null || onRemove != null
        if (hasExtraActions) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${wiki.name}")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Change skin") },
                        leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                        onClick = { showMenu = false; onEditSkin() },
                    )
                    if (onMoveToFolder != null) {
                        DropdownMenuItem(
                            text = { Text("Move to folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = { showMenu = false; onMoveToFolder() },
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { showMenu = false; onRemove() },
                        )
                    }
                }
            }
        } else {
            // Presets have nothing besides a skin change to offer here,
            // no move, no remove, so a one item overflow menu would
            // just be an extra tap hiding the only thing it ever shows.
            // A plain icon is more honest about there being exactly one
            // action.
            IconButton(onClick = onEditSkin) {
                Icon(Icons.Filled.Palette, contentDescription = "Change skin for ${wiki.name}")
            }
        }
    }
}

/**
 * Lets the person file a custom wiki into one of their own folders,
 * pull it back out to ungrouped, or spin up a brand new folder on the
 * spot rather than having to back out to the "New folder" action first.
 * Only offered for custom wikis. Presets stay in whichever
 * [PresetFolders] entry [org.wikitide.wikiportal.data.model.PresetWikis]
 * already assigned them.
 */
@Composable
private fun MoveToFolderDialog(
    wiki: WikiSite,
    folders: List<WikiFolder>,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    var creatingNew by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${wiki.name}\"") },
        text = {
            if (creatingNew) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.replace("\n", "") },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectFolder(null) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = wiki.folderId == null, onClick = { onSelectFolder(null) })
                        Text("No folder", modifier = Modifier.padding(start = 4.dp))
                    }
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectFolder(folder.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = wiki.folderId == folder.id, onClick = { onSelectFolder(folder.id) })
                            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { creatingNew = true }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("New folder", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                TextButton(onClick = { if (newFolderName.isNotBlank()) onCreateFolder(newFolderName.trim()) }) { Text("Create") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (creatingNew) TextButton(onClick = { creatingNew = false }) { Text("Back") }
        },
    )
}

/** A plain name-entry dialog, shared by folder creation and renaming. */
@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "") },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Lets the person override which MediaWiki skin a wiki renders with,
 * see WikiSite.skin. This offers wiki.skinChoices, not the full curated
 * list in WikiSkins. That full list is everything this app has been
 * tested against in principle, but a given wiki may not have every one
 * of those skins actually installed, see WikiSite.availableSkins, so
 * the choices shown here are always a subset, narrowed by that wiki's
 * own siteinfo, except the current skin itself, which is always
 * included even if it fell out of that list, so skinChoices is never
 * actually empty when there's any skin data at all. See its comment.
 *
 * When siprop=skins has genuinely never resolved for this wiki at all,
 * see WikiSite.hasNoSkinData, this shows a dedicated failure message
 * instead of any list. Falling back to this app's full curated list in
 * that case would offer skins that were never actually confirmed to
 * exist on this specific wiki.
 *
 * A wiki's availableSkins is only filled in once it has actually been
 * revalidated, see AppRepository.refreshWikiMetadata.
 */
@Composable
private fun SkinPickerDialog(
    wiki: WikiSite,
    repository: AppRepository,
    onDismiss: () -> Unit,
    onSkinSelected: (String) -> Unit,
) {
    var isRefreshing by remember(wiki.id) { mutableStateOf(true) }
    LaunchedEffect(wiki.id) {
        repository.refreshWikiMetadata(wiki)
        isRefreshing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skin for ${wiki.name}") },
        text = {
            if (isRefreshing && wiki.availableSkins == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (wiki.hasNoSkinData) {
                Text(
                    "Couldn't find any skins for ${wiki.name}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val scrollState = rememberScrollState()
                // Where the selected row sits within the scrollable
                // Column, in pixels, captured as it's laid out. Null
                // until the selected row has actually been measured
                // once.
                var selectedOffset by remember(wiki.id) { mutableStateOf<Int?>(null) }
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    wiki.skinChoices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSkinSelected(choice.code) }
                                .padding(vertical = 4.dp)
                                .onGloballyPositioned { coordinates ->
                                    if (choice.code == wiki.skin) {
                                        selectedOffset = coordinates.positionInParent().y.roundToInt()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice.code == wiki.skin, onClick = { onSkinSelected(choice.code) })
                            Text(choice.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                LaunchedEffect(selectedOffset) {
                    selectedOffset?.let { offset -> scrollState.animateScrollTo(offset) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
