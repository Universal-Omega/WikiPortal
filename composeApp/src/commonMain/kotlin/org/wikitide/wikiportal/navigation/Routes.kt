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
