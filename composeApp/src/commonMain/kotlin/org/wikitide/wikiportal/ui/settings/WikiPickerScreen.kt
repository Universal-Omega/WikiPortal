package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.WikiSite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiPickerScreen(onBack: () -> Unit, onAddCustomWiki: () -> Unit, repository: AppRepository = koinInject()) {
    val activeWiki by repository.activeWiki.collectAsState()
    val presetWikis by repository.presetWikis.collectAsState()
    val customWikis by repository.customWikis.collectAsState()

    // The id of the wiki currently being offered a skin change, or null
    // when the dialog is closed. This is an id, not the WikiSite itself,
    // since a frozen snapshot wouldn't reflect the metadata refresh
    // SkinPickerDialog triggers below, where real availableSkins and
    // display names arrive after the dialog is already open. So this is
    // looked up live against presetWikis and customWikis on every
    // recomposition instead.
    var editingSkinForId by remember { mutableStateOf<String?>(null) }
    val editingSkinForWiki = editingSkinForId?.let { id -> (presetWikis + customWikis).firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a wiki") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomWiki) {
                Icon(Icons.Filled.Add, contentDescription = "Add a wiki by URL")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { GroupLabel("Featured wikis") }
            items(presetWikis, key = { it.id }) { wiki ->
                WikiRow(
                    wiki, wiki.id == activeWiki.id,
                    onClick = { repository.setActiveWiki(wiki); onBack() },
                    onEditSkin = { editingSkinForId = wiki.id },
                    repository = repository,
                )
            }
            if (customWikis.isNotEmpty()) {
                item { GroupLabel("Your wikis") }
                items(customWikis, key = { it.id }) { wiki ->
                    WikiRow(
                        wiki, wiki.id == activeWiki.id,
                        onClick = { repository.setActiveWiki(wiki); onBack() },
                        onRemove = { repository.removeCustomWiki(wiki) },
                        onEditSkin = { editingSkinForId = wiki.id },
                        repository = repository,
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
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
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
 */
@Composable
private fun WikiRow(
    wiki: WikiSite,
    selected: Boolean,
    onClick: () -> Unit,
    onEditSkin: () -> Unit,
    repository: AppRepository,
    onRemove: (() -> Unit)? = null,
) {
    LaunchedEffect(wiki.id) { repository.refreshFaviconOnly(wiki) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
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
        IconButton(onClick = onEditSkin) {
            Icon(Icons.Filled.Palette, contentDescription = "Change skin for ${wiki.name}")
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove wiki") }
        }
    }
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
 * actually empty. See its comment.
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
            } else {
                Column {
                    wiki.skinChoices.forEach { choice ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSkinSelected(choice.code) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice.code == wiki.skin, onClick = { onSkinSelected(choice.code) })
                            Text(choice.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
