package org.wikitide.wikiportal.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import org.wikitide.wikiportal.resources.common_search
import org.wikitide.wikiportal.ui.components.CollapsedHeaderIconButton
import org.wikitide.wikiportal.ui.components.CollapsibleSearchFieldHost
import org.wikitide.wikiportal.ui.components.rememberCollapsibleHeaderState

/** Height reserved for the category search field while fully expanded. */
private val SEARCH_FIELD_HEIGHT = 72.dp

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

    val searchCollapseState = rememberCollapsibleHeaderState(fullHeight = SEARCH_FIELD_HEIGHT)
    LaunchedEffect(selectedCategory) { searchCollapseState.expand() }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(searchCollapseState.nestedScrollConnection),
    ) {
        Column(Modifier.fillMaxSize()) {
            CategoryBrowseHeader(
                title = selectedCategory?.removePrefix("Category:") ?: stringResource(Res.string.category_browse_title),
                showSearchIcon = selectedCategory == null,
                searchIconVisibleFraction = if (selectedCategory == null) searchCollapseState.collapseFraction else 0f,
                onBack = { if (selectedCategory != null) viewModel.clearSelection() else onBack() },
                onOpenSearch = { /* Search field already sits inline below; the icon just scrolls it back into view. */ searchCollapseState.expand() },
            )

            if (selectedCategory == null) {
                CollapsibleSearchFieldHost(collapseFraction = searchCollapseState.collapseFraction, fullHeight = SEARCH_FIELD_HEIGHT) {
                    CategorySearchField(query = state.query, wikiName = state.wikiName, onQueryChange = viewModel::onQueryChange)
                }
                CategorySearchResults(state, onSelect = viewModel::selectCategory, modifier = Modifier.weight(1f))
            } else {
                CategoryMembersContent(state, onArticleClick = onArticleClick, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryBrowseHeader(
    title: String,
    showSearchIcon: Boolean,
    searchIconVisibleFraction: Float,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top))
            .heightIn(min = 64.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showSearchIcon) {
            CollapsedHeaderIconButton(
                visibleFraction = searchIconVisibleFraction,
                icon = Icons.Filled.Search,
                contentDescription = stringResource(Res.string.common_search),
                onClick = onOpenSearch,
            )
        }
    }
}

@Composable
private fun CategorySearchField(query: String, wikiName: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(Res.string.category_browse_search_placeholder, wikiName)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_clear))
                }
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun CategorySearchResults(state: CategoryBrowseUiState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.isSearching && state.matches.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.query.isNotBlank() && state.matches.isEmpty() && !state.isSearching -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.category_browse_no_results, state.query),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.query.isBlank() && state.isLoadingSuggestions && state.suggestedCategories.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.query.isBlank() && state.suggestedCategories.isNotEmpty() -> LazyColumn(modifier.fillMaxSize()) {
            item { SectionLabel(stringResource(Res.string.category_browse_popular)) }
            itemsIndexed(state.suggestedCategories) { index, category ->
                if (index > 0) CategoryRowDivider()
                CategoryRow(category = category, onClick = { onSelect(category) })
            }
        }
        state.query.isBlank() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.category_browse_type_to_search),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(modifier.fillMaxSize()) {
            itemsIndexed(state.matches) { index, category ->
                if (index > 0) CategoryRowDivider()
                CategoryRow(category = category, onClick = { onSelect(category) })
            }
        }
    }
}

@Composable
private fun CategoryRow(category: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(category.removePrefix("Category:"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Filled.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CategoryRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
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
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoadingMembers -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.members.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.category_browse_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(state.members) { index, title ->
                if (index > 0) CategoryRowDivider()
                ListItem(
                    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onArticleClick(title) },
                )
            }
        }
    }
}
