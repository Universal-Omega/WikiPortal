package org.wikitide.wikiportal.data.model

import org.jetbrains.compose.resources.StringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.theme_mode_dark
import org.wikitide.wikiportal.resources.theme_mode_light
import org.wikitide.wikiportal.resources.theme_mode_system

/** [labelRes] is resolved with stringResource by the UI; the enum name itself is what's persisted. */
enum class ThemeMode(val labelRes: StringResource) {
    SYSTEM(Res.string.theme_mode_system),
    LIGHT(Res.string.theme_mode_light),
    DARK(Res.string.theme_mode_dark),
}
