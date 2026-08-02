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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWikiScreen(onDone: () -> Unit, onBrowseWikis: () -> Unit, viewModel: AddWikiViewModel = koinViewModel()) {
    var urlInput by remember { mutableStateOf("") }
    var scriptPathInput by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Add a wiki") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Enter the address of any MediaWiki-powered site. We'll look for its API automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Wiki URL") },
                placeholder = { Text("https://mywiki.example.com") },
                singleLine = true,
                isError = state.errorMessage != null,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(visible = state.showScriptPathField) {
                OutlinedTextField(
                    value = scriptPathInput,
                    onValueChange = { scriptPathInput = it },
                    label = { Text("Script path") },
                    placeholder = { Text("e.g. /w, /wiki, /mediawiki") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { viewModel.submit(urlInput, scriptPathInput) },
                enabled = !state.isChecking && urlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                }
                Text(if (state.isChecking) "Checking..." else "Add wiki")
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onBrowseWikis) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Or browse independent wikis", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
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
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "This site has an independent wiki",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "${suggestion.destinationName} (${suggestion.destinationBaseUrl}) is a fan-run replacement not tied to a for-profit host, and is likely more up to date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = onUseIndieWiki, enabled = !isChecking, modifier = Modifier.fillMaxWidth()) {
                    Text("Add ${suggestion.destinationName} instead")
                }
                OutlinedButton(onClick = onContinueAnyway, enabled = !isChecking, modifier = Modifier.fillMaxWidth()) {
                    Text("Add the original site anyway")
                }
            }
        }
    }
}
