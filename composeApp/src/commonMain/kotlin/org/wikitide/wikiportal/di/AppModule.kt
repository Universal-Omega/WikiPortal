package org.wikitide.wikiportal.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.data.WikiMetadataRefresher
import org.wikitide.wikiportal.navigation.Navigator
import org.wikitide.wikiportal.navigation.navigationModule
import org.wikitide.wikiportal.network.ActionApiClient
import org.wikitide.wikiportal.network.MatomoAnalyticsApi
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.RestApiClient
import org.wikitide.wikiportal.network.WikimediaPageviewsApi
import org.wikitide.wikiportal.network.createHttpClient
import org.wikitide.wikiportal.ui.dashboard.ExploreViewModel
import org.wikitide.wikiportal.ui.dashboard.RelevantLinksViewModel
import org.wikitide.wikiportal.ui.dashboard.SearchViewModel
import org.wikitide.wikiportal.ui.settings.AddWikiViewModel

/**
 * Provided per platform. This constructs the right SQLDelight
 * [SqlDriver], AndroidSqliteDriver, JdbcSqliteDriver, or the
 * async WebWorkerDriver, and wraps it in a [SqlDelightWikiPortalStore].
 * This is the one place platform differences enter the DI graph.
 */
expect fun platformModule(): Module

val commonModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createHttpClient() }
    single { ActionApiClient(get()) }
    single { RestApiClient(get()) }
    single { MediaWikiApi(get(), get()) }
    single { WikimediaPageviewsApi(get()) }
    single { MatomoAnalyticsApi(get(), get()) }
    single { WikiMetadataRefresher(get()) }
    single { AppRepository(get(), get(), get()) }
    single { TabsRepository(get(), get()) }
    single { Navigator(get(), get()) }

    viewModel { ExploreViewModel(get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { RelevantLinksViewModel(get(), get()) }
    viewModel { AddWikiViewModel(get(), get()) }
}

fun appModules(): List<Module> = listOf(commonModule, navigationModule, platformModule())
