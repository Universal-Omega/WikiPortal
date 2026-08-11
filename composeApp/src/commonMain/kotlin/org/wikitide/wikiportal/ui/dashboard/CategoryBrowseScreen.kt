package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.category_browse_empty
import org.wikitide.wikiportal.resources.category_browse_no_results
import org.wikitide.wikiportal.resources.category_browse_popular
import org.wikitide.wikiportal.resources.category_browse_search_placeholder
import org.wikitide.wikiportal.resources.category_browse_title
import org.wikitide.wikiportal.resources.category_browse_type_to_search
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_clear

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowseScreen(
    onArticleClick: (title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryBrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selectedCategory = state.selectedCategory

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.removePrefix("Category:") ?: stringResource(Res.string.category_browse_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedCategory != null) viewModel.clearSelection() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (selectedCategory == null) {
                CategorySearchContent(state, onQueryChange = viewModel::onQueryChange, onSelect = viewModel::selectCategory)
            } else {
                CategoryMembersContent(state, onArticleClick = onArticleClick)
            }
        }
    }
}

@Composable
private fun CategorySearchContent(
    state: CategoryBrowseUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(Res.string.category_browse_search_placeholder, state.wikiName)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_clear))
                    }
                }
            },
            shape = MaterialTheme.shapes.large,
        )
        when {
            state.isSearching && state.matches.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.query.isNotBlank() && state.matches.isEmpty() && !state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.category_browse_no_results, state.query),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.query.isBlank() && state.isLoadingSuggestions && state.suggestedCategories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.query.isBlank() && state.suggestedCategories.isNotEmpty() -> LazyColumn {
                item { SectionLabel(stringResource(Res.string.category_browse_popular)) }
                items(state.suggestedCategories, key = { it }) { category ->
                    ListItem(
                        headlineContent = { Text(category.removePrefix("Category:"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Filled.Category, contentDescription = null) },
                        modifier = Modifier.clickable { onSelect(category) },
                    )
                }
            }
            state.query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.category_browse_type_to_search),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn {
                items(state.matches, key = { it }) { category ->
                    ListItem(
                        headlineContent = { Text(category.removePrefix("Category:"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Filled.Category, contentDescription = null) },
                        modifier = Modifier.clickable { onSelect(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CategoryMembersContent(
    state: CategoryBrowseUiState,
    onArticleClick: (title: String) -> Unit,
) {
    when {
        state.isLoadingMembers -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.members.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.category_browse_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.members, key = { it }) { title ->
                ListItem(
                    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onArticleClick(title) },
                )
            }
        }
    }
}
