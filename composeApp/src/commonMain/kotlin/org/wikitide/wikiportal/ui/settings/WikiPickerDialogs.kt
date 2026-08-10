package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_cancel
import org.wikitide.wikiportal.resources.common_close
import org.wikitide.wikiportal.resources.common_create
import org.wikitide.wikiportal.resources.wiki_picker_folder_name_label
import org.wikitide.wikiportal.resources.wiki_picker_move_title
import org.wikitide.wikiportal.resources.wiki_picker_new_folder
import org.wikitide.wikiportal.resources.wiki_picker_no_folder
import org.wikitide.wikiportal.resources.wiki_picker_no_skins
import org.wikitide.wikiportal.resources.wiki_picker_skin_title
import kotlin.math.roundToInt

/**
 * Lets the person file a custom wiki into one of their own folders,
 * pull it back out to ungrouped, or spin up a brand new folder on the
 * spot rather than having to back out to the "New folder" action first.
 * Only offered for custom wikis. Presets stay in whichever
 * [PresetFolders] entry [PresetWikis] already assigned them.
 */
@Composable
internal fun MoveToFolderDialog(
    wiki: WikiSite,
    folders: ImmutableList<WikiFolder>,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    var creatingNew by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.wiki_picker_move_title, wiki.name)) },
        text = {
            if (creatingNew) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.replace("\n", "") },
                    label = { Text(stringResource(Res.string.wiki_picker_folder_name_label)) },
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
                        Text(stringResource(Res.string.wiki_picker_no_folder), modifier = Modifier.padding(start = 4.dp))
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
                        Text(stringResource(Res.string.wiki_picker_new_folder), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                TextButton(onClick = { if (newFolderName.isNotBlank()) onCreateFolder(newFolderName.trim()) }) { Text(stringResource(Res.string.common_create)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
            }
        },
        dismissButton = {
            if (creatingNew) TextButton(onClick = { creatingNew = false }) { Text(stringResource(Res.string.common_back)) }
        },
    )
}

/** A plain name-entry dialog, shared by folder creation and renaming. */
@Composable
internal fun FolderNameDialog(
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
                label = { Text(stringResource(Res.string.wiki_picker_folder_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
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
internal fun SkinPickerDialog(
    wiki: WikiSite,
    repository: AppRepository,
    onDismiss: () -> Unit,
    onSelectSkin: (String) -> Unit,
) {
    var isRefreshing by remember(wiki.id) { mutableStateOf(true) }
    LaunchedEffect(wiki.id) {
        repository.refreshWikiMetadata(wiki)
        isRefreshing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.wiki_picker_skin_title, wiki.name)) },
        text = {
            if (isRefreshing && wiki.availableSkins == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (wiki.hasNoSkinData) {
                Text(
                    stringResource(Res.string.wiki_picker_no_skins, wiki.name),
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
                                .clickable { onSelectSkin(choice.code) }
                                .padding(vertical = 4.dp)
                                .onGloballyPositioned { coordinates ->
                                    if (choice.code == wiki.skin) {
                                        selectedOffset = coordinates.positionInParent().y.roundToInt()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice.code == wiki.skin, onClick = { onSelectSkin(choice.code) })
                            Text(choice.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                LaunchedEffect(selectedOffset) {
                    selectedOffset?.let { offset -> scrollState.animateScrollTo(offset) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) } },
    )
}
