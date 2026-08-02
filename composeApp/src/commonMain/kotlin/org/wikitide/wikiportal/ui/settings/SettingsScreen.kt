package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.ThemeMode
import org.wikitide.wikiportal.util.AppVersionProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenWikiPicker: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenFeedback: () -> Unit,
    repository: AppRepository = koinInject(),
    versionProvider: AppVersionProvider = koinInject(),
) {
    val activeWiki by repository.activeWiki.collectAsState()
    val themeMode by repository.themeMode.collectAsState()
    val dynamicColor by repository.dynamicColor.collectAsState()
    val textScale by repository.textScale.collectAsState()
    val showImages by repository.showImages.collectAsState()
    val openLinksExternally by repository.openLinksExternally.collectAsState()
    val confirmExternalNavigation by repository.confirmExternalNavigation.collectAsState()
    val disableSafeMode by repository.disableSafeMode.collectAsState()
    val openBlankInNewTab by repository.openBlankInNewTab.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel("Wiki") }
            item { SettingsRow(icon = Icons.Filled.Public, title = "Current wiki", subtitle = activeWiki.name, onClick = onOpenWikiPicker) }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    RowLabel(icon = Icons.Filled.DarkMode, text = "Theme")
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { repository.setThemeMode(mode) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = { repository.setThemeMode(mode) })
                            Text(mode.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Dynamic color",
                    subtitle = "Match app colors to your device wallpaper (Android 12+)",
                    checked = dynamicColor,
                    onCheckedChange = repository::setDynamicColor,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    RowLabel(icon = Icons.Filled.TextFields, text = "Article text size")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = textScale,
                            onValueChange = repository::setTextScale,
                            valueRange = 0.8f..1.6f,
                            steps = 7,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text("A", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Reading") }
            item {
                SwitchRow(
                    icon = Icons.Filled.Image,
                    title = "Show images",
                    subtitle = "Show thumbnails in Dashboard, Search, Tabs, and Saved",
                    checked = showImages,
                    onCheckedChange = repository::setShowImages,
                )
            }
            item {
                SwitchRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open outside links in browser",
                    subtitle = "Links to sites outside your saved wikis open in your browser instead of the in-app reader",
                    checked = openLinksExternally,
                    onCheckedChange = repository::setOpenLinksExternally,
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Confirm before leaving a site",
                    subtitle = "Ask before a tab loads a link to a site outside your wikis",
                    checked = confirmExternalNavigation,
                    onCheckedChange = repository::setConfirmExternalNavigation,
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Disable safe mode",
                    subtitle = "Load pages without MediaWiki's safemode restrictions on gadgets and scripts",
                    checked = disableSafeMode,
                    onCheckedChange = repository::setDisableSafeMode,
                )
            }
            item {
                SwitchRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open new-tab links in a real tab",
                    subtitle = "Links that open in a new window open a new WikiPortal tab instead of replacing the page",
                    checked = openBlankInNewTab,
                    onCheckedChange = repository::setOpenBlankInNewTab,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Beta") }
            item {
                SettingsRow(
                    icon = Icons.Filled.Feedback,
                    title = "Send feedback",
                    subtitle = "Report a bug, share an idea, or flag what's confusing",
                    onClick = onOpenFeedback,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Diagnostics") }
            item {
                SettingsRow(
                    icon = Icons.Filled.Description,
                    title = "App logs",
                    subtitle = "Recent app activity, useful when troubleshooting a problem",
                    onClick = onOpenLogs,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("About") }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp).padding(top = 2.dp),
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("WikiPortal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A universal MediaWiki client. Version ${versionProvider.versionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub repository",
                    subtitle = "View the source, file an issue, or see what's changed",
                    onClick = { uriHandler.openUri("https://github.com/Universal-Omega/WikiPortal") },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun RowLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 16.dp, end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
