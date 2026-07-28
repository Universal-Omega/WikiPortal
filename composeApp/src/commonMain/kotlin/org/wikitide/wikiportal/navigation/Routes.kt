package org.wikitide.wikiportal.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object DashboardRoute : Route

@Serializable
data object TabsRoute : Route

@Serializable
data object SavedRoute : Route

@Serializable
data object SettingsRoute : Route

@Serializable
data object WikiPickerRoute : Route

@Serializable
data object AddWikiRoute : Route

@Serializable
data object ArticleRoute : Route

@Serializable
data object LogsRoute : Route

// The four destinations that live behind the bottom bar or nav rail.
// switchTab in Navigator uses this list to decide which entries in the
// back stack count as tabs versus one-off screens like WikiPickerRoute
// or ArticleRoute. Typed as NavKey rather than Route since that is what
// the back stack itself holds, see Navigator.backStack.
val bottomTabRoutes: List<NavKey> = listOf(DashboardRoute, TabsRoute, SavedRoute, SettingsRoute)
