package org.wikitide.wikiportal.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.ui.components.ArticleCard

/**
 * A plain, Saved-style list of currently open tabs. This is the bottom
 * nav's Tabs destination, distinct from the rich grid-of-thumbnail-cards
 * switcher in TabsScreen.kt, which stays reachable from inside the
 * article reader itself, unchanged. This shows each tab's real
 * thumbnail and article summary, see ArticleTab.extract, rather than
 * the wiki-name subtitle Saved, History, and Offline use. Those span
 * many wikis at once, where "which wiki" is the more useful line at a
 * glance, but every open tab already shows its own title prominently,
 * so a content preview is more useful here than repeating the wiki
 * name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListScreen(
    onOpenTab: (wikiId: String, title: String) -> Unit,
    tabsRepository: TabsRepository = koinInject(),
    repository: AppRepository = koinInject(),
) {
    val tabs by tabsRepository.tabs.collectAsState()
    val showImages by repository.showImages.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tabs", style = MaterialTheme.typography.headlineMedium) },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    ArticleCard(
                        title = tab.title,
                        extract = tab.extract.orEmpty(),
                        thumbnailUrl = tab.thumbnailUrl,
                        showImages = showImages,
                        onClick = { onOpenTab(tab.wikiId, tab.title) },
                        trailingContent = {
                            IconButton(onClick = { tabsRepository.closeTab(tab.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close tab")
                            }
                        },
                    )
                }
            }
        }
    }
}
