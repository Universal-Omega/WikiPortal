package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wikitide.wikiportal.data.AppRepository
import org.wikitide.wikiportal.data.model.AppLanguage
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.app_language_search_placeholder
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_clear
import org.wikitide.wikiportal.resources.common_selected
import org.wikitide.wikiportal.resources.settings_app_language
import org.wikitide.wikiportal.resources.theme_mode_system

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageScreen(
    onBack: () -> Unit,
    repository: AppRepository = koinInject(),
) {
    val appLanguageTag by repository.appLanguageTag.collectAsState()
    val selected = AppLanguage.fromTag(appLanguageTag)
    var query by remember { mutableStateOf("") }

    val matches = AppLanguage.entries.filter { language ->
        language != AppLanguage.SYSTEM && language.nativeName.orEmpty().contains(query, ignoreCase = true)
    }

    fun select(language: AppLanguage) {
        repository.setAppLanguage(language.tag)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_app_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(Res.string.app_language_search_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_clear))
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                    )
                }
                item {
                    LanguageRow(
                        label = stringResource(Res.string.theme_mode_system),
                        isSelected = selected == AppLanguage.SYSTEM,
                        onClick = { select(AppLanguage.SYSTEM) },
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                items(matches, key = { it.name }) { language ->
                    LanguageRow(
                        label = language.nativeName.orEmpty(),
                        isSelected = selected == language,
                        onClick = { select(language) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.common_selected), tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
