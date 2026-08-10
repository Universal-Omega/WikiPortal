package org.wikitide.wikiportal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.TabsRepository
import org.wikitide.wikiportal.navigation.ArticleRoute
import org.wikitide.wikiportal.navigation.DashboardRoute
import org.wikitide.wikiportal.navigation.Navigator
import org.wikitide.wikiportal.navigation.Route
import org.wikitide.wikiportal.navigation.SavedRoute
import org.wikitide.wikiportal.navigation.SettingsRoute
import org.wikitide.wikiportal.navigation.SystemBackInterceptor
import org.wikitide.wikiportal.navigation.TabsRoute
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.dashboard_saved
import org.wikitide.wikiportal.resources.dashboard_title
import org.wikitide.wikiportal.resources.settings_title
import org.wikitide.wikiportal.resources.tabs_title
import org.wikitide.wikiportal.ui.theme.WikiPortalTheme

@Immutable
private data class BottomDestination(val route: Route, val labelRes: StringResource, val selected: ImageVector, val unselected: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(DashboardRoute, Res.string.dashboard_title, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomDestination(TabsRoute, Res.string.tabs_title, Icons.Default.Tab, Icons.Outlined.Tab),
    BottomDestination(SavedRoute, Res.string.dashboard_saved, Icons.Filled.Bookmark, Icons.Filled.BookmarkBorder),
    BottomDestination(SettingsRoute, Res.string.settings_title, Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * Shared by the portrait NavigationBar and the landscape NavigationRail
 * below. Only the icon itself is shared here, not the NavigationBarItem
 * or NavigationRailItem calls. Those need to stay separate, since each
 * one must be called from its own matching scope, RowScope for
 * NavigationBar and ColumnScope for NavigationRail, and that scope is
 * what makes their internal weight() work correctly. This helper does
 * not need any scope. It is just an Icon, optionally wrapped in a
 * badge, so it works fine from either place.
 */
@Composable
private fun DestinationIcon(destination: BottomDestination, isSelected: Boolean, openTabsCount: Int) {
    val icon = @Composable {
        Icon(
            imageVector = if (isSelected) destination.selected else destination.unselected,
            contentDescription = stringResource(destination.labelRes),
        )
    }
    // Only the Tabs icon shows a count. This is just a small detail in
    // how it renders. Clicking and selecting still work the same way
    // for all four destinations.
    if (destination.route == TabsRoute) {
        BadgedBox(badge = { if (openTabsCount > 0) Badge { Text("$openTabsCount") } }) { icon() }
    } else {
        icon()
    }
}

// Polymorphic serialization setup for the Route sealed hierarchy. This is
// needed so the back stack works on non-JVM targets like wasmJs since
// they can't use the reflection-based serializer that Android and the
// JVM would normally rely on.
private val navConfig = SavedStateConfiguration {
    @OptIn(ExperimentalSerializationApi::class)
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<Route>()
        }
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
fun WikiPortalApp(modifier: Modifier = Modifier, onDarkThemeResolve: (Boolean) -> Unit = {}) {
    // Coil3 caches images by default even without this setup, but adding
    // crossfade avoids a blank flash before the image pops in, which can
    // look like it is still loading even when it was actually a cache
    // hit. setSingletonImageLoaderFactory is a Composable function and
    // it is safe to call it like a plain statement here. It just
    // registers a factory, and the actual singleton loader is created
    // later on first use, so calling this again on recomposition causes
    // no problems.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).crossfade(true).build()
    }

    val repository = koinInject<AppRepository>()
    val tabsRepository = koinInject<TabsRepository>()
    val navigator = koinInject<Navigator>()
    val themeMode by repository.themeMode.collectAsState()
    val dynamicColor by repository.dynamicColor.collectAsState()

    val backStack = rememberNavBackStack(navConfig, DashboardRoute)
    navigator.backStack = backStack

    WikiPortalTheme(themeMode = themeMode, useDynamicColor = dynamicColor, onDarkThemeResolve = onDarkThemeResolve) {
        val current = backStack.lastOrNull()
        // ArticleRoute is not one of the bottomDestinations on purpose, so
        // the nav stays hidden during normal article reading. That
        // includes when the in-reader tab switcher overlay is open on top
        // of it, see TabsScreen.kt. This is computed once here and shared
        // by both branches below instead of being recomputed in each one.
        val showNav = bottomDestinations.any { it.route == current }
        val openTabs by tabsRepository.tabs.collectAsState()
        val entryProvider = koinEntryProvider<NavKey>()

        // NavDisplay itself is the same either way. Only the surrounding
        // chrome, a bottom bar or a side rail, differs between the two
        // branches below.
        val navDisplayContent = remember {
            movableContentOf { modifier: Modifier ->
                NavDisplay(
                    backStack = backStack,
                    onBack = { navigator.handleBack() },
                    modifier = modifier,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider,
                )
            }
        }

        // Comparing width to height instead of using a platform
        // orientation API. There isn't one available in commonMain that
        // works the same way across Android, IOS, Desktop, and WasmJs. This
        // also gives a sensible result for a wide desktop window or a
        // tablet or foldable, not just a rotated phone, which a strict
        // orientation check would miss.
        AppNavigationLayout(
            current = current,
            showNav = showNav,
            openTabsCount = openTabs.size,
            navigator = navigator,
            navDisplayContent = navDisplayContent,
            modifier = modifier,
        )

        // This is registered after Scaffold and NavDisplay above. See
        // SystemBackInterceptor's own comment for why. That way, on
        // Android, while it is enabled, this is what the system checks
        // for the back gesture instead of NavDisplay's own predictive
        // back callback. It is only enabled when we are showing
        // ArticleRoute and there is something for handleBack() to
        // intercept besides a normal back stack pop, meaning the switcher
        // overlay or in-page WebView history. When neither of those
        // applies, this stays disabled and the gesture falls through to
        // NavDisplay's normal predictive pop, which is the correct
        // behavior in that case.
        val isSwitcherOpen by tabsRepository.isSwitcherOpen.collectAsState()
        val activeTabCanGoBack by tabsRepository.activeTabCanGoBack.collectAsState()
        SystemBackInterceptor(
            enabled = backStack.lastOrNull() == ArticleRoute && (isSwitcherOpen || activeTabCanGoBack),
            onBack = { navigator.handleBack() },
        )
    }
}

/**
 * Picks between [LandscapeAppLayout] and [PortraitAppLayout] based on
 * the available size, and owns the incoming modifier so WikiPortalApp
 * itself only ever references it once, passed straight through here,
 * rather than applying it directly to whichever of the two branches
 * happens to be active. Comparing width to height instead of using a
 * platform orientation API. There isn't one available in commonMain
 * that works the same way across Android, IOS, Desktop, and WasmJs.
 * This also gives a sensible result for a wide desktop window or a
 * tablet or foldable, not just a rotated phone, which a strict
 * orientation check would miss.
 */
@Composable
private fun AppNavigationLayout(
    current: NavKey?,
    showNav: Boolean,
    openTabsCount: Int,
    navigator: Navigator,
    navDisplayContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            LandscapeAppLayout(current, showNav, openTabsCount, navigator, navDisplayContent)
        } else {
            PortraitAppLayout(current, showNav, openTabsCount, navigator, navDisplayContent)
        }
    }
}

/**
 * Wide layout, a side NavigationRail next to the content instead of a
 * bottom bar.
 */
@Composable
private fun LandscapeAppLayout(
    current: NavKey?,
    showNav: Boolean,
    openTabsCount: Int,
    navigator: Navigator,
    navDisplayContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The background is set here, not just on the Scaffold below,
    // because of the cutout side. When a side display cutout eats into
    // this Row, the sliver of screen the system reserves for it shows
    // this Row's own background before Compose draws the rail or
    // Scaffold inside it. Without this, that sliver shows the theme's
    // default window background, a light gray, instead of the app's
    // actual background.
    Row(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showNav) {
            NavigationRail(
                // The rail owns the Start edge of this Row, so it should
                // absorb a Start-side cutout or inset, not the Scaffold
                // below. If the Scaffold also claimed the Start inset,
                // the side without a cutout would still get padded for
                // no reason.
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            ) {
                NavigationRailItems(current = current, openTabsCount = openTabsCount, navigator = navigator)
            }
        }
        // Bottom and End are always this Scaffold's job. Start is too,
        // except when the rail is showing. In that case the rail
        // already absorbed it above, and adding it again here would
        // double pad that side on top of the rail's own width.
        val scaffoldInsetSides = if (showNav) {
            WindowInsetsSides.Bottom + WindowInsetsSides.End
        } else {
            WindowInsetsSides.Bottom + WindowInsetsSides.End + WindowInsetsSides.Start
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            // Each screen's own TopAppBar already reserves space for the
            // status bar by default. Reserving it here too would double
            // pad everything, so top is never included above.
            contentWindowInsets = WindowInsets.safeDrawing.only(scaffoldInsetSides),
        ) { innerPadding ->
            navDisplayContent(Modifier.padding(innerPadding))
        }
    }
}

/**
 * The rail's own list of destinations. NavigationRail does not
 * center its items by default. Left alone, they stack from the top and
 * leave the rest of the rail's height as empty space below them. This
 * Column is what centers the group within the rail's full height.
 * Using spacedBy also gives the items some breathing room, since
 * Center alone would pack them edge to edge with no gap.
 */
@Composable
private fun NavigationRailItems(current: NavKey?, openTabsCount: Int, navigator: Navigator, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        bottomDestinations.forEach { destination ->
            val isSelected = destination.route == current
            NavigationRailItem(
                selected = isSelected,
                onClick = { navigator.switchTab(destination.route) },
                icon = { DestinationIcon(destination, isSelected, openTabsCount) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

/**
 * Narrow layout, a bottom NavigationBar under the content instead of a
 * side rail.
 */
@Composable
private fun PortraitAppLayout(
    current: NavKey?,
    showNav: Boolean,
    openTabsCount: Int,
    navigator: Navigator,
    navDisplayContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        bottomBar = {
            if (showNav) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val isSelected = destination.route == current
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navigator.switchTab(destination.route) },
                            icon = { DestinationIcon(destination, isSelected, openTabsCount) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        navDisplayContent(Modifier.padding(innerPadding))
    }
}
