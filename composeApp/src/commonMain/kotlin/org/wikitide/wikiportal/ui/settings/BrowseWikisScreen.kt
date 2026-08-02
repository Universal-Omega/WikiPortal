package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.data.model.IndieWikiSite
import org.wikitide.wikiportal.network.iwbFaviconUrl

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
    val addState by addWikiViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(addState.done) { if (addState.done) onDone() }
    LaunchedEffect(addState.errorMessage) {
        addState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // Search matches against the wiki's own name, its origin label
    // ("1000xRESIST Fandom Wiki"), and its language, so searching a
    // wiki's old Fandom name still finds its independent replacement.
    val filtered = remember(sites, searchQuery) {
        val query = searchQuery.trim()
        val matched = if (query.isBlank()) {
            sites
        } else {
            sites.filter {
                it.destinationName.contains(query, ignoreCase = true) ||
                    it.originsLabel.contains(query, ignoreCase = true) ||
                    it.language.contains(query, ignoreCase = true)
            }
        }
        matched.sortedBy { it.destinationName.lowercase() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Browse wikis") },
                    navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = browseViewModel::refresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh wiki list")
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = browseViewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Search wikis") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { browseViewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
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
                        "Couldn't load the wiki directory. Check your connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = browseViewModel::refresh, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "No wikis match \"$searchQuery\".",
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
                    items(filtered, key = { it.id }) { site ->
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
            Text(site.destinationName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Independent alternative to ${site.originsLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
