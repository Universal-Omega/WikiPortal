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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wikitide.wikiportal.BuildKonfig
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenWikiPicker: () -> Unit, onOpenLogs: () -> Unit, onOpenFeedback: () -> Unit, repository: AppRepository = koinInject()) {
    val activeWiki by repository.activeWiki.collectAsState()
    val themeMode by repository.themeMode.collectAsState()
    val dynamicColor by repository.dynamicColor.collectAsState()
    val textScale by repository.textScale.collectAsState()
    val showImages by repository.showImages.collectAsState()
    val openLinksExternally by repository.openLinksExternally.collectAsState()
    val confirmExternalNavigation by repository.confirmExternalNavigation.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel("Wiki") }
            item { SettingsRow(title = "Current wiki", subtitle = activeWiki.name, onClick = onOpenWikiPicker) }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
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
                    title = "Dynamic color",
                    subtitle = "Match app colors to your device wallpaper (Android 12+)",
                    checked = dynamicColor,
                    onCheckedChange = repository::setDynamicColor,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Article text size", style = MaterialTheme.typography.titleMedium)
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
                    title = "Show images",
                    subtitle = "Show thumbnails in Dashboard, Search, Tabs, and Saved",
                    checked = showImages,
                    onCheckedChange = repository::setShowImages,
                )
            }
            item {
                SwitchRow(
                    title = "Open links in browser",
                    subtitle = "Always open article links outside the app instead of in the reader",
                    checked = openLinksExternally,
                    onCheckedChange = repository::setOpenLinksExternally,
                )
            }
            item {
                SwitchRow(
                    title = "Confirm before leaving a site",
                    subtitle = "Ask before a tab loads a link to a site outside your wikis",
                    checked = confirmExternalNavigation,
                    onCheckedChange = repository::setConfirmExternalNavigation,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Beta") }
            item {
                SettingsRow(
                    title = "Send feedback",
                    subtitle = "Report a bug, share an idea, or flag what's confusing",
                    onClick = onOpenFeedback,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("About") }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("WikiPortal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A universal MediaWiki client. Version ${BuildKonfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsRow(
                    title = "App logs",
                    subtitle = "Recent app activity, useful when troubleshooting a problem",
                    onClick = onOpenLogs,
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
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
