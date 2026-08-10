package org.wikitide.wikiportal.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.add_wiki_browse_indie
import org.wikitide.wikiportal.resources.add_wiki_checking
import org.wikitide.wikiportal.resources.add_wiki_continue_original
import org.wikitide.wikiportal.resources.add_wiki_indie_found_body
import org.wikitide.wikiportal.resources.add_wiki_indie_found_title
import org.wikitide.wikiportal.resources.add_wiki_intro
import org.wikitide.wikiportal.resources.add_wiki_script_path_label
import org.wikitide.wikiportal.resources.add_wiki_script_path_placeholder
import org.wikitide.wikiportal.resources.add_wiki_submit
import org.wikitide.wikiportal.resources.add_wiki_title
import org.wikitide.wikiportal.resources.add_wiki_url_label
import org.wikitide.wikiportal.resources.add_wiki_url_placeholder
import org.wikitide.wikiportal.resources.add_wiki_use_indie
import org.wikitide.wikiportal.resources.common_back

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWikiScreen(onDone: () -> Unit, onBrowseWikis: () -> Unit, modifier: Modifier = Modifier, viewModel: AddWikiViewModel = koinViewModel()) {
    var urlInput by remember { mutableStateOf("") }
    var scriptPathInput by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    // onDone is read inside this effect without being a key, which is
    // fine here since the effect is keyed on state.done and is never
    // meant to restart just because onDone itself changed identity.
    // rememberUpdatedState keeps the call using whatever the latest
    // onDone is instead of the one captured the first time this
    // composed.
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.done) { if (state.done) currentOnDone() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.add_wiki_title)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back)) } },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        AddWikiForm(
            urlInput = urlInput,
            onUrlInputChange = { urlInput = it },
            scriptPathInput = scriptPathInput,
            onScriptPathInputChange = { scriptPathInput = it },
            state = state,
            onSubmit = { viewModel.submit(urlInput, scriptPathInput) },
            onBrowseWikis = onBrowseWikis,
            modifier = Modifier.padding(innerPadding),
        )
    }

    state.indieWikiSuggestion?.let { suggestion ->
        IndieWikiSuggestionCard(
            suggestion = suggestion,
            isChecking = state.isChecking,
            onUseIndieWiki = viewModel::useSuggestedIndieWiki,
            onContinueAnyway = viewModel::continueWithOriginalUrl,
        )
    }
}

/**
 * The main add-a-wiki form: URL field, optional script path field,
 * error text, submit button, and the "browse indie wikis" link below
 * it.
 */
@Composable
private fun AddWikiForm(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    scriptPathInput: String,
    onScriptPathInputChange: (String) -> Unit,
    state: AddWikiUiState,
    onSubmit: () -> Unit,
    onBrowseWikis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.add_wiki_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlInputChange,
            label = { Text(stringResource(Res.string.add_wiki_url_label)) },
            placeholder = { Text(stringResource(Res.string.add_wiki_url_placeholder)) },
            singleLine = true,
            isError = state.errorMessage != null,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = state.showScriptPathField) {
            OutlinedTextField(
                value = scriptPathInput,
                onValueChange = onScriptPathInputChange,
                label = { Text(stringResource(Res.string.add_wiki_script_path_label)) },
                placeholder = { Text(stringResource(Res.string.add_wiki_script_path_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onSubmit,
            enabled = !state.isChecking && urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isChecking) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
            }
            Text(if (state.isChecking) stringResource(Res.string.add_wiki_checking) else stringResource(Res.string.add_wiki_submit))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onBrowseWikis) {
                Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.add_wiki_browse_indie), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * A dismissible prompt shown in place of the normal form once
 * AddWikiViewModel finds that the typed address is a known origin
 * Indie Wiki Buddy tracks as having moved elsewhere. Shown as an
 * overlay card rather than a dialog, so the URL just entered stays
 * visible underneath while deciding.
 */
@Composable
private fun IndieWikiSuggestionCard(
    suggestion: IndieWikiSuggestion,
    isChecking: Boolean,
    onUseIndieWiki: () -> Unit,
    onContinueAnyway: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            IndieWikiSuggestionCardContent(
                suggestion = suggestion,
                isChecking = isChecking,
                onUseIndieWiki = onUseIndieWiki,
                onContinueAnyway = onContinueAnyway,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun IndieWikiSuggestionCardContent(
    suggestion: IndieWikiSuggestion,
    isChecking: Boolean,
    onUseIndieWiki: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            stringResource(Res.string.add_wiki_indie_found_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            stringResource(Res.string.add_wiki_indie_found_body, suggestion.destinationName, suggestion.destinationBaseUrl),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            overflow = TextOverflow.Ellipsis,
        )
        Button(onClick = onUseIndieWiki, enabled = !isChecking, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.add_wiki_use_indie, suggestion.destinationName))
        }
        OutlinedButton(onClick = onContinueAnyway, enabled = !isChecking, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.add_wiki_continue_original))
        }
    }
}
