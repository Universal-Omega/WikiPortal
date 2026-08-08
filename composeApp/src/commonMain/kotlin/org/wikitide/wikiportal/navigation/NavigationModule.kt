package org.wikitide.wikiportal.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.ui.article.ArticleHostScreen
import org.wikitide.wikiportal.ui.dashboard.CategoryBrowseScreen
import org.wikitide.wikiportal.ui.dashboard.DashboardScreen
import org.wikitide.wikiportal.ui.dashboard.TrendingScreen
import org.wikitide.wikiportal.ui.feedback.FeedbackScreen
import org.wikitide.wikiportal.ui.saved.SavedScreen
import org.wikitide.wikiportal.ui.settings.AddWikiScreen
import org.wikitide.wikiportal.ui.settings.BrowseWikisScreen
import org.wikitide.wikiportal.ui.settings.LogsScreen
import org.wikitide.wikiportal.ui.settings.SettingsScreen
import org.wikitide.wikiportal.ui.settings.WikiPickerScreen
import org.wikitide.wikiportal.ui.tabs.TabsListScreen

@Composable
private fun EntryBackground(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        content()
    }
}

@OptIn(KoinExperimentalAPI::class)
val navigationModule = module {
    navigation<DashboardRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            DashboardScreen(
                onArticleClick = { wikiId, title -> navigator.openArticle(wikiId, title) },
                onOpenWikiPicker = { navigator.navigateTo(WikiPickerRoute) },
                onOpenCategoryBrowse = { navigator.navigateTo(CategoryBrowseRoute) },
                onOpenTrending = { navigator.navigateTo(TrendingRoute) },
            )
        }
    }

    navigation<TrendingRoute> {
        val navigator = get<Navigator>()
        val repository = get<AppRepository>()
        EntryBackground {
            TrendingScreen(
                onArticleClick = { title -> navigator.openArticle(repository.activeWiki.value.id, title) },
                onBack = { navigator.backStack.removeLastOrNull() },
            )
        }
    }

    navigation<CategoryBrowseRoute> {
        val navigator = get<Navigator>()
        val repository = get<AppRepository>()
        EntryBackground {
            CategoryBrowseScreen(
                onArticleClick = { title -> navigator.openArticle(repository.activeWiki.value.id, title) },
                onBack = { navigator.backStack.removeLastOrNull() },
            )
        }
    }

    navigation<TabsRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            TabsListScreen(
                onOpenTab = { wikiId, title -> navigator.openArticle(wikiId, title) },
            )
        }
    }

    navigation<SavedRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            SavedScreen(
                onArticleClick = { wikiId, title -> navigator.openArticle(wikiId, title) },
                onOfflineArticleClick = { wikiId, title -> navigator.openArticle(wikiId, title, openedFromOffline = true) },
            )
        }
    }

    navigation<SettingsRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            SettingsScreen(
                onOpenWikiPicker = { navigator.navigateTo(WikiPickerRoute) },
                onOpenLogs = { navigator.navigateTo(LogsRoute) },
                onOpenFeedback = { navigator.navigateTo(FeedbackRoute) },
            )
        }
    }

    navigation<WikiPickerRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            WikiPickerScreen(
                onBack = { navigator.backStack.removeLastOrNull() },
                onAddCustomWiki = { navigator.navigateTo(AddWikiRoute) },
                onBrowseWikis = { navigator.navigateTo(BrowseWikisRoute) },
            )
        }
    }

    navigation<AddWikiRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            AddWikiScreen(
                onDone = { navigator.backStack.removeLastOrNull() },
                onBrowseWikis = { navigator.navigateTo(BrowseWikisRoute) },
            )
        }
    }

    navigation<BrowseWikisRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            BrowseWikisScreen(onDone = { navigator.backStack.removeLastOrNull() })
        }
    }

    navigation<LogsRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            LogsScreen(onBack = { navigator.backStack.removeLastOrNull() })
        }
    }

    navigation<FeedbackRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            FeedbackScreen(onBack = { navigator.backStack.removeLastOrNull() })
        }
    }

    navigation<ArticleRoute> {
        val navigator = get<Navigator>()
        EntryBackground {
            ArticleHostScreen(onBack = { navigator.backStack.removeLastOrNull() })
        }
    }
}
