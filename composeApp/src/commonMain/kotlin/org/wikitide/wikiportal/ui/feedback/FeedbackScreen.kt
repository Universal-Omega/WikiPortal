package org.wikitide.wikiportal.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_done
import org.wikitide.wikiportal.resources.feedback_about_label
import org.wikitide.wikiportal.resources.feedback_contact_label
import org.wikitide.wikiportal.resources.feedback_copied_message
import org.wikitide.wikiportal.resources.feedback_copy_failed
import org.wikitide.wikiportal.resources.feedback_display_name_label
import org.wikitide.wikiportal.resources.feedback_include_logs
import org.wikitide.wikiportal.resources.feedback_include_logs_hint
import org.wikitide.wikiportal.resources.feedback_intro
import org.wikitide.wikiportal.resources.feedback_message_label
import org.wikitide.wikiportal.resources.feedback_send
import org.wikitide.wikiportal.resources.feedback_thanks_sent
import org.wikitide.wikiportal.resources.feedback_title
import org.wikitide.wikiportal.util.copyPlainText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit, viewModel: FeedbackViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    val copiedMessage = stringResource(Res.string.feedback_copied_message)
    val copyFailedMessage = stringResource(Res.string.feedback_copy_failed)

    fun copyFeedback() {
        scope.launch {
            val ok = copyPlainText(clipboard, viewModel.composeShareText())
            snackbarHostState.showSnackbar(if (ok) copiedMessage else copyFailedMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.feedback_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back)) } },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        if (state.sent) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(48.dp))
                Text(stringResource(Res.string.feedback_thanks_sent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(Res.string.common_done)) }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.feedback_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column {
                Text(stringResource(Res.string.feedback_about_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text(stringResource(category.labelRes)) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text(stringResource(Res.string.feedback_message_label)) },
                isError = state.errorMessage != null,
            )

            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::setDisplayName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.feedback_display_name_label)) },
                singleLine = true,
            )

            OutlinedTextField(
                value = state.contact,
                onValueChange = viewModel::setContact,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.feedback_contact_label)) },
                singleLine = true,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.includeLogs, onCheckedChange = viewModel::setIncludeLogs)
                Column {
                    Text(stringResource(Res.string.feedback_include_logs), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(Res.string.feedback_include_logs_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::submit, enabled = !state.isSubmitting) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(Res.string.feedback_send))
                    }
                }
                // OutlinedButton(onClick = { copyFeedback() }) { Text("Copy feedback") }
            }
        }
    }
}
