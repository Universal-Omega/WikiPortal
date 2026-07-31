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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowseScreen(
    onArticleClick: (title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryBrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selectedCategory = state.selectedCategory

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.removePrefix("Category:") ?: "Browse by category") },
                navigationIcon = {
                    IconButton(onClick = { if (selectedCategory != null) viewModel.clearSelection() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            placeholder = { Text("Search categories on ${state.wikiName}") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
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
                    "No categories found for \"${state.query}\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Type a topic to find its category",
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
                "This category is empty",
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
