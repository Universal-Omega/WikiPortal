package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.data.model.IndieWikiLanguages
import org.wikitide.wikiportal.data.model.IndieWikiSite
import org.wikitide.wikiportal.network.iwbFaviconUrl
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.browse_wikis_all_languages
import org.wikitide.wikiportal.resources.browse_wikis_alternative_to
import org.wikitide.wikiportal.resources.browse_wikis_clear_search
import org.wikitide.wikiportal.resources.browse_wikis_load_failed
import org.wikitide.wikiportal.resources.browse_wikis_no_matches
import org.wikitide.wikiportal.resources.browse_wikis_official
import org.wikitide.wikiportal.resources.browse_wikis_refresh_list
import org.wikitide.wikiportal.resources.browse_wikis_search_placeholder
import org.wikitide.wikiportal.resources.browse_wikis_title
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_retry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseWikisScreen(
    onDone: () -> Unit,
    browseViewModel: BrowseWikisViewModel = koinViewModel(),
    addWikiViewModel: AddWikiViewModel = koinViewModel(),
) {
    val sites by browseViewModel.sites.collectAsState()
    val isRefreshing by browseViewModel.isRefreshing.collectAsState()
    val searchQuery by browseViewModel.searchQuery.collectAsState()
    val languageFilter by browseViewModel.languageFilter.collectAsState()
    val officialOnly by browseViewModel.officialOnly.collectAsState()
    val addState by addWikiViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(addState.done) { if (addState.done) onDone() }
    LaunchedEffect(addState.errorMessage) {
        addState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // Every language actually present, most common first, so the chip
    // row leads with whatever's most useful to narrow down by rather
    // than a fixed or alphabetical order that could bury English, the
    // vast majority of entries, several chips deep.
    val availableLanguages = remember(sites) {
        sites.groupingBy { it.language }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    // Search matches against the wiki's own name, its origin label
    // ("1000xRESIST Fandom Wiki"), and its language, so searching a
    // wiki's old Fandom name still finds its independent replacement.
    // Sorted by name and then language, not just name, so a wiki with
    // more than one language edition, which otherwise reads as the
    // same title repeated with no way to tell entries apart, groups
    // together with its language badges lined up right next to each
    // other rather than scattered through the list.
    val filtered = remember(sites, searchQuery, languageFilter, officialOnly) {
        val query = searchQuery.trim()
        sites
            .asSequence()
            .filter { languageFilter == null || it.language == languageFilter }
            .filter { !officialOnly || it.isOfficial }
            .filter {
                query.isBlank() ||
                    it.destinationName.contains(query, ignoreCase = true) ||
                    it.originsLabel.contains(query, ignoreCase = true) ||
                    IndieWikiLanguages.displayName(it.language).contains(query, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.destinationName.lowercase() }, { it.language }))
            .toList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.browse_wikis_title)) },
                    navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back)) } },
                    actions = {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = browseViewModel::refresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.browse_wikis_refresh_list))
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = browseViewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(Res.string.browse_wikis_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { browseViewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.browse_wikis_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                BrowseFilterRow(
                    officialOnly = officialOnly,
                    onOfficialOnlyChange = browseViewModel::setOfficialOnly,
                    languages = availableLanguages,
                    selectedLanguage = languageFilter,
                    onLanguageSelected = browseViewModel::setLanguageFilter,
                )
            }
        },
    ) { innerPadding ->
        when {
            sites.isEmpty() && isRefreshing -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            sites.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(Res.string.browse_wikis_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = browseViewModel::refresh, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(Res.string.common_retry)) }
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(Res.string.browse_wikis_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filtered, key = { it.id + it.language }) { site ->
                        IndieWikiRow(
                            site = site,
                            enabled = !addState.isChecking,
                            onClick = {
                                scope.launch { addWikiViewModel.submit("https://${site.destinationBaseUrl}", skipIndieWikiCheck = true) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseFilterRow(
    officialOnly: Boolean,
    onOfficialOnlyChange: (Boolean) -> Unit,
    languages: List<String>,
    selectedLanguage: String?,
    onLanguageSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = officialOnly,
            onClick = { onOfficialOnlyChange(!officialOnly) },
            label = { Text(stringResource(Res.string.browse_wikis_official)) },
            leadingIcon = if (officialOnly) {
                { Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
        )
        FilterChip(
            selected = selectedLanguage == null,
            onClick = { onLanguageSelected(null) },
            label = { Text(stringResource(Res.string.browse_wikis_all_languages)) },
        )
        languages.forEach { language ->
            FilterChip(
                selected = selectedLanguage == language,
                onClick = { onLanguageSelected(if (selectedLanguage == language) null else language) },
                label = { Text(IndieWikiLanguages.displayName(language)) },
            )
        }
    }
}

@Composable
private fun IndieWikiRow(site: IndieWikiSite, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = site.destinationIcon?.let { iwbFaviconUrl(site.language, it) },
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(CircleShape),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.Public),
                placeholder = rememberVectorPainter(Icons.Filled.Public),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(site.destinationName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LanguageBadge(site.language)
                if (site.isOfficial) OfficialBadge()
                Text(
                    stringResource(Res.string.browse_wikis_alternative_to, site.originsLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** A small pill showing which language edition a row is, so an identically-named wiki in more than one language doesn't just read as a repeated entry. */
@Composable
private fun LanguageBadge(language: String) {
    Text(
        text = language,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A small pill marking a wiki Indie Wiki Buddy tags as the game or franchise's own official wiki, not just a fan-run one. */
@Composable
private fun OfficialBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(11.dp),
        )
        Text(
            stringResource(Res.string.browse_wikis_official),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
