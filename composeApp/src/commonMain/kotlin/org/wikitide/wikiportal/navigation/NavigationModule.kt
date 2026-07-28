package org.wikitide.wikiportal.navigation

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.wikitide.wikiportal.ui.article.ArticleHostScreen
import org.wikitide.wikiportal.ui.dashboard.DashboardScreen
import org.wikitide.wikiportal.ui.saved.SavedScreen
import org.wikitide.wikiportal.ui.settings.AddWikiScreen
import org.wikitide.wikiportal.ui.settings.LogsScreen
import org.wikitide.wikiportal.ui.settings.SettingsScreen
import org.wikitide.wikiportal.ui.settings.WikiPickerScreen
import org.wikitide.wikiportal.ui.tabs.TabsListScreen

@OptIn(KoinExperimentalAPI::class)
val navigationModule = module {
    navigation<DashboardRoute> {
        val navigator = get<Navigator>()
        DashboardScreen(
            onArticleClick = { wikiId, title -> navigator.openArticle(wikiId, title) },
            onOpenWikiPicker = { navigator.navigateTo(WikiPickerRoute) },
        )
    }

    navigation<TabsRoute> {
        val navigator = get<Navigator>()
        TabsListScreen(
            onOpenTab = { wikiId, title -> navigator.openArticle(wikiId, title) },
        )
    }

    navigation<SavedRoute> {
        val navigator = get<Navigator>()
        SavedScreen(
            onArticleClick = { wikiId, title -> navigator.openArticle(wikiId, title) },
        )
    }

    navigation<SettingsRoute> {
        val navigator = get<Navigator>()
        SettingsScreen(
            onOpenWikiPicker = { navigator.navigateTo(WikiPickerRoute) },
            onOpenLogs = { navigator.navigateTo(LogsRoute) },
        )
    }

    navigation<WikiPickerRoute> {
        val navigator = get<Navigator>()
        WikiPickerScreen(
            onBack = { navigator.backStack.removeLastOrNull() },
            onAddCustomWiki = { navigator.navigateTo(AddWikiRoute) },
        )
    }

    navigation<AddWikiRoute> {
        val navigator = get<Navigator>()
        AddWikiScreen(onDone = { navigator.backStack.removeLastOrNull() })
    }

    navigation<LogsRoute> {
        val navigator = get<Navigator>()
        LogsScreen(onBack = { navigator.backStack.removeLastOrNull() })
    }

    navigation<ArticleRoute> {
        val navigator = get<Navigator>()
        ArticleHostScreen(onBack = { navigator.backStack.removeLastOrNull() })
    }
}
