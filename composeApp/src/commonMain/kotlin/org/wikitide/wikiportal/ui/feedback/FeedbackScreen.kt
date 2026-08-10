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
fun FeedbackScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: FeedbackViewModel = koinViewModel()) {
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
        modifier = modifier,
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
            FeedbackSentContent(onBack = onBack, modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        FeedbackForm(
            state = state,
            onSelectCategory = viewModel::setCategory,
            onMessageChange = viewModel::setMessage,
            onDisplayNameChange = viewModel::setDisplayName,
            onContactChange = viewModel::setContact,
            onIncludeLogsChange = viewModel::setIncludeLogs,
            onSubmit = viewModel::submit,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** The "thanks, feedback sent" success view, shown by [FeedbackScreen] once [FeedbackUiState.sent] is true. */
@Composable
private fun FeedbackSentContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(48.dp))
        Text(stringResource(Res.string.feedback_thanks_sent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(Res.string.common_done)) }
    }
}

/** The main feedback form: category chips, message, display name, contact, "include logs", and send. */
@Composable
private fun FeedbackForm(
    state: FeedbackUiState,
    onSelectCategory: (FeedbackCategory) -> Unit,
    onMessageChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onIncludeLogsChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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

        Text(stringResource(Res.string.feedback_about_label), style = MaterialTheme.typography.titleMedium)
        FeedbackCategoryChips(selectedCategory = state.category, onSelectCategory = onSelectCategory)

        OutlinedTextField(
            value = state.message,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            label = { Text(stringResource(Res.string.feedback_message_label)) },
            isError = state.errorMessage != null,
        )

        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.feedback_display_name_label)) },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.contact,
            onValueChange = onContactChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.feedback_contact_label)) },
            singleLine = true,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.includeLogs, onCheckedChange = onIncludeLogsChange)
            IncludeLogsLabel()
        }

        state.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        FeedbackSubmitButton(isSubmitting = state.isSubmitting, onSubmit = onSubmit)
    }
}

/** The send button, and its in-flight spinner. */
@Composable
private fun FeedbackSubmitButton(isSubmitting: Boolean, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSubmit, enabled = !isSubmitting) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(Res.string.feedback_send))
            }
        }
    }
}

/** The row of category chips under "About", see [FeedbackCategory]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackCategoryChips(selectedCategory: FeedbackCategory, onSelectCategory: (FeedbackCategory) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(stringResource(category.labelRes)) },
            )
        }
    }
}

/** The title and hint text next to the "include logs" checkbox. */
@Composable
private fun IncludeLogsLabel(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(Res.string.feedback_include_logs), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(Res.string.feedback_include_logs_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
