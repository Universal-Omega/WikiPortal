package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.ui.components.DragReorderState
import org.wikitide.wikiportal.ui.components.rememberDragReorderState
import org.wikitide.wikiportal.ui.components.trackDragPosition

/**
 * Renders one folder as a collapsible header followed, when expanded,
 * by its wikis. Shared between preset folders, see [PresetFolders], and
 * the person's own custom folders, with rename, delete, and drag
 * reorder only wired up for the latter, through [onRenameFolder],
 * [onDeleteFolder], and [dragState], since a preset folder is not the
 * person's to reorganize.
 */
@Composable
internal fun FolderSectionContent(
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
    dragState: DragReorderState<*>? = null,
    reorderMode: Boolean = false,
    onEnterReorderMode: (() -> Unit)? = null,
    wikisReorderable: Boolean = false,
    onExitFolder: ((wikiId: String, direction: Int) -> Unit)? = null,
) {
    Column {
        FolderHeaderRow(
            folder = folder,
            count = wikis.size,
            expanded = expanded,
            onToggle = onToggle,
            onRename = onRenameFolder,
            onDelete = onDeleteFolder,
            dragState = dragState,
            reorderMode = reorderMode,
            onEnterReorderMode = onEnterReorderMode,
        )
        if (expanded) {
            if (wikisReorderable) {
                val wikiDragState = rememberDragReorderState(
                    items = wikis,
                    id = { it.id },
                    rank = { it.rank },
                    key = folder.id,
                    onMove = { wikiId, newRank -> repository.setCustomWikiRank(wikiId, newRank) },
                    onExitBounds = onExitFolder,
                )
                wikiDragState.items.forEach { wiki ->
                    WikiRow(
                        wiki, wiki.id == activeWikiId,
                        onClick = { onClickWiki(wiki) },
                        onRemove = onRemoveWiki?.let { { it(wiki) } },
                        onEditSkin = { onEditSkin(wiki) },
                        onMoveToFolder = onMoveWikiToFolder?.let { { it(wiki) } },
                        repository = repository,
                        indented = true,
                        dragState = wikiDragState,
                        reorderMode = reorderMode,
                        onEnterReorderMode = onEnterReorderMode,
                    )
                }
            } else {
                wikis.forEach { wiki ->
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
    }
}

internal fun LazyListScope.folderSection(
    folder: WikiFolder,
    wikis: List<WikiSite>,
    expanded: Boolean,
    activeWikiId: String,
    repository: AppRepository,
    onToggle: () -> Unit,
    onClickWiki: (WikiSite) -> Unit,
    onEditSkin: (WikiSite) -> Unit,
) {
    item(key = "folder-${folder.id}") {
        FolderSectionContent(
            folder = folder,
            wikis = wikis,
            expanded = expanded,
            activeWikiId = activeWikiId,
            repository = repository,
            onToggle = onToggle,
            onClickWiki = onClickWiki,
            onEditSkin = onEditSkin,
        )
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
    dragState: DragReorderState<*>? = null,
    reorderMode: Boolean = false,
    onEnterReorderMode: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDragging = dragState?.draggingId == folder.id
    val dragOffsetY = if (isDragging) dragState.dragOffsetY else 0f
    val isDropTarget = dragState?.hoverContainerId == folder.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (dragState != null) it.trackDragPosition(dragState, folder.id) else it }
            .graphicsLayer { translationY = dragOffsetY }
            .zIndex(if (isDragging) 1f else 0f)
            // A wiki hovering here to be dropped in gets a visible
            // highlight across the whole row, not just the icon, so
            // it's obvious which folder is about to receive it.
            .background(if (isDropTarget) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onToggle)
            .let {
                if (dragState == null) {
                    it
                } else {
                    it.pointerInput(folder.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onEnterReorderMode?.invoke(); dragState.onDragStart(folder.id) },
                            onDrag = { change, delta -> change.consume(); dragState.onDrag(folder.id, delta.y) },
                            onDragEnd = { dragState.onDragEnd() },
                            onDragCancel = { dragState.onDragEnd() },
                        )
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (dragState != null && reorderMode) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(Res.string.wiki_picker_drag_reorder, folder.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Same 40dp circular badge treatment as WikiRow's favicon, so a
        // folder row carries the same visual weight as a wiki row
        // instead of reading as two small bare icons crowded together.
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = if (isDropTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) stringResource(Res.string.wiki_picker_one_wiki) else stringResource(Res.string.wiki_picker_n_wikis, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) stringResource(Res.string.wiki_picker_collapse) else stringResource(Res.string.wiki_picker_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.wiki_picker_folder_options, folder.name))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (onRename != null) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.wiki_picker_rename)) }, onClick = { showMenu = false; onRename() })
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.common_delete)) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
 * A wiki's extra actions, changing its skin, toggling safe mode,
 * moving it to a folder, removing it, sit behind one overflow menu
 * rather than as separate icons in a row, since that many icon buttons
 * next to a favicon and two lines of text would be cramped enough to be
 * hard to tap accurately. Every wiki, preset or custom, has at least a
 * skin and a safe mode toggle to offer, so this menu is never down to
 * a single item.
 */
@Composable
internal fun WikiRow(
    wiki: WikiSite,
    selected: Boolean,
    onClick: () -> Unit,
    onEditSkin: () -> Unit,
    repository: AppRepository,
    onRemove: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    indented: Boolean = false,
    dragState: DragReorderState<*>? = null,
    reorderMode: Boolean = false,
    onEnterReorderMode: (() -> Unit)? = null,
) {
    LaunchedEffect(wiki.id) { repository.refreshFaviconOnly(wiki) }
    var showMenu by remember { mutableStateOf(false) }
    val isDragging = dragState?.draggingId == wiki.id
    val dragOffsetY = if (isDragging) dragState.dragOffsetY else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (dragState != null) it.trackDragPosition(dragState, wiki.id) else it }
            .graphicsLayer { translationY = dragOffsetY }
            .zIndex(if (isDragging) 1f else 0f)
            .clickable(onClick = onClick)
            .let {
                if (dragState == null) {
                    it
                } else {
                    it.pointerInput(wiki.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onEnterReorderMode?.invoke(); dragState.onDragStart(wiki.id) },
                            onDrag = { change, delta -> change.consume(); dragState.onDrag(wiki.id, delta.y) },
                            onDragEnd = { dragState.onDragEnd() },
                            onDragCancel = { dragState.onDragEnd() },
                        )
                    }
                }
            }
            .padding(horizontal = if (indented) 32.dp else 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (dragState != null && reorderMode) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(Res.string.wiki_picker_drag_reorder, wiki.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.common_selected), tint = MaterialTheme.colorScheme.primary)
        // Every wiki has at least "Change skin" and "Disable safe mode"
        // to offer here, custom or preset, so this always goes behind
        // one overflow menu rather than ever falling back to a single
        // bare icon.
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.wiki_picker_options_for, wiki.name))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.wiki_picker_change_skin)) },
                    leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = { showMenu = false; onEditSkin() },
                )
                DropdownMenuItem(
                    text = { Text(if (wiki.disableSafeMode) stringResource(Res.string.wiki_picker_enable_safe_mode) else stringResource(Res.string.settings_disable_safe_mode_title)) },
                    leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        repository.setWikiDisableSafeMode(wiki.id, !wiki.disableSafeMode)
                    },
                )
                if (onMoveToFolder != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.wiki_picker_move_to_folder)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                        onClick = { showMenu = false; onMoveToFolder() },
                    )
                }
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.common_remove)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onRemove() },
                    )
                }
            }
        }
    }
}
