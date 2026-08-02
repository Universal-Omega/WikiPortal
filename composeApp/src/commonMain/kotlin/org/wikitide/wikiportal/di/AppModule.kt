package org.wikitide.wikiportal.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.IndieWikiDirectory
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.TrendingLoader
import org.wikitide.wikiportal.data.WikiMetadataRefresher
import org.wikitide.wikiportal.navigation.Navigator
import org.wikitide.wikiportal.navigation.navigationModule
import org.wikitide.wikiportal.network.ActionApiClient
import org.wikitide.wikiportal.network.FeedbackApi
import org.wikitide.wikiportal.network.IndieWikiBuddyApi
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.RestApiClient
import org.wikitide.wikiportal.network.WikimediaFeaturedFeedApi
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.createHttpClient
import org.wikitide.wikiportal.ui.dashboard.CategoryBrowseViewModel
import org.wikitide.wikiportal.ui.dashboard.FeedViewModel
import org.wikitide.wikiportal.ui.dashboard.RelevantLinksViewModel
import org.wikitide.wikiportal.ui.dashboard.SearchViewModel
import org.wikitide.wikiportal.ui.dashboard.TrendingViewModel
import org.wikitide.wikiportal.ui.feedback.FeedbackViewModel
import org.wikitide.wikiportal.ui.settings.AddWikiViewModel
import org.wikitide.wikiportal.ui.settings.BrowseWikisViewModel
import org.wikitide.wikiportal.util.AppVersionProvider

/**
 * Provided per platform. This constructs the right SqlDriver,
 * AndroidSqliteDriver, JdbcSqliteDriver, or the async WebWorkerDriver, and
 * wraps it in a SqlDelightWikiPortalStore. This is the one place platform
 * differences enter the DI graph.
 */
expect fun platformModule(): Module

val commonModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createHttpClient() }

    singleOf(::ActionApiClient)
    singleOf(::RestApiClient)
    singleOf(::MediaWikiApi)
    singleOf(::WikimediaPageviewsApi)
    singleOf(::WikimediaFeaturedFeedApi)
    singleOf(::MatomoAnalyticsApi)
    singleOf(::FeedbackApi)
    singleOf(::IndieWikiBuddyApi)
    singleOf(::IndieWikiDirectory)
    singleOf(::WikiMetadataRefresher)
    singleOf(::AppRepository)
    singleOf(::TrendingLoader)
    singleOf(::TabsRepository)
    singleOf(::AppVersionProvider)
    singleOf(::Navigator)

    viewModelOf(::FeedViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::RelevantLinksViewModel)
    viewModelOf(::CategoryBrowseViewModel)
    viewModelOf(::TrendingViewModel)
    viewModelOf(::AddWikiViewModel)
    viewModelOf(::BrowseWikisViewModel)
    viewModelOf(::FeedbackViewModel)
}

fun appModules(): List<Module> = listOf(commonModule, navigationModule, platformModule())
