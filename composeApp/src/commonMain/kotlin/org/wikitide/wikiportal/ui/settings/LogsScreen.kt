package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.browse_wikis_clear_search
import org.wikitide.wikiportal.resources.common_back
import org.wikitide.wikiportal.resources.common_clear_action
import org.wikitide.wikiportal.resources.common_more_options
import org.wikitide.wikiportal.resources.common_refresh
import org.wikitide.wikiportal.resources.logs_all
import org.wikitide.wikiportal.resources.logs_app_only
import org.wikitide.wikiportal.resources.logs_clear
import org.wikitide.wikiportal.resources.logs_clear_body
import org.wikitide.wikiportal.resources.logs_clear_title
import org.wikitide.wikiportal.resources.logs_copied
import org.wikitide.wikiportal.resources.logs_copy_all
import org.wikitide.wikiportal.resources.logs_copy_failed
import org.wikitide.wikiportal.resources.logs_copy_selected
import org.wikitide.wikiportal.resources.logs_empty_session
import org.wikitide.wikiportal.resources.logs_export
import org.wikitide.wikiportal.resources.logs_export_failed
import org.wikitide.wikiportal.resources.logs_no_filter_match
import org.wikitide.wikiportal.resources.logs_no_match
import org.wikitide.wikiportal.resources.logs_search_placeholder
import org.wikitide.wikiportal.resources.logs_select
import org.wikitide.wikiportal.resources.logs_title
import org.wikitide.wikiportal.resources.saved_cancel_selection
import org.wikitide.wikiportal.resources.saved_n_selected
import org.wikitide.wikiportal.ui.components.DestructiveConfirmDialog
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.LogEntry
import org.wikitide.wikiportal.util.LogExporter
import org.wikitide.wikiportal.util.LogLevel
import org.wikitide.wikiportal.util.clearDeviceLogs
import org.wikitide.wikiportal.util.copyPlainText
import org.wikitide.wikiportal.util.nowEpochMillis
import org.wikitide.wikiportal.util.readDeviceLogs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(onBack: () -> Unit, logExporter: LogExporter = koinInject()) {
    val appEntries by AppLog.entries.collectAsState()
    var deviceEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var appOnly by remember { mutableStateOf(true) }
    var visibleLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(Res.string.logs_copied)
    val copyFailedMessage = stringResource(Res.string.logs_copy_failed)
    val exportFailedMessage = stringResource(Res.string.logs_export_failed)

    fun exitSelection() {
        selectionMode = false
        selectedIndices = emptySet()
    }

    // Only "All" actually needs a fresh device-log dump; App only reads
    // AppLog.entries reactively above and never touches this. Still
    // safe, and simplest, to refresh this unconditionally, so it's
    // ready the moment someone switches to "All".
    suspend fun load() {
        isLoading = true
        deviceEntries = readDeviceLogs()
        isLoading = false
        exitSelection()
    }

    LaunchedEffect(Unit) { load() }

    // Newest entries land at the end of each source's own list, so
    // this reverses to read newest-first, then applies whatever the
    // filter chips and search box currently say. Search matches
    // against both the tag and the message, so searching "Ktor" finds
    // everything from that source regardless of what each individual
    // line says. Changing a filter, the search text, or which source
    // is active, reflows which indices point at which row, so any
    // in-progress selection is cleared rather than risk it silently
    // pointing at the wrong line.
    val displayed = remember(appEntries, deviceEntries, appOnly, visibleLevels, searchQuery) {
        val source = if (appOnly) appEntries else deviceEntries
        source.asReversed().filter { entry ->
            (!appOnly || entry.isAppSource) &&
                entry.level in visibleLevels &&
                (
                    searchQuery.isBlank() || entry.tag.contains(
                    searchQuery,
                    ignoreCase = true
                ) || entry.message.contains(searchQuery, ignoreCase = true)
                )
        }
    }
    LaunchedEffect(appOnly, visibleLevels, searchQuery) { exitSelection() }

    fun formatted(list: List<LogEntry>): String =
        list.joinToString(
            "\n"
        ) { entry ->
            "${entry.displayTime ?: formatLogTime(
            entry.timestampEpochMillis
        )}  ${entry.level.name}  ${entry.tag}: ${entry.message}"
        }

    fun copyToClipboard(text: String) {
        scope.launch {
            val ok = copyPlainText(clipboard, text)
            snackbarHostState.showSnackbar(if (ok) copiedMessage else copyFailedMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                        if (selectionMode) stringResource(
                                Res.string.saved_n_selected,
                                selectedIndices.size
                            ) else stringResource(Res.string.logs_title)
                    )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (selectionMode) exitSelection() else onBack() }) {
                            Icon(
                                imageVector = if (selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (selectionMode) stringResource(
                                        Res.string.saved_cancel_selection
                                    ) else stringResource(Res.string.common_back),
                            )
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            IconButton(
                                onClick = {
                                    copyToClipboard(formatted(selectedIndices.sorted().map { displayed[it] }))
                                    exitSelection()
                                },
                                enabled = selectedIndices.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(Res.string.logs_copy_selected)
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { load() } }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(Res.string.common_refresh)
                                )
                            }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.common_more_options)
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logs_select)) },
                                    onClick = {
                                        menuOpen = false;
                                        selectionMode = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logs_copy_all)) },
                                    onClick = {
                                        menuOpen = false
                                        copyToClipboard(formatted(displayed))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logs_export)) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            val fileName = "wikiportal-logs-${nowEpochMillis()}.txt"
                                            val result = logExporter.export(fileName, formatted(displayed))
                                            snackbarHostState.showSnackbar(result.getOrElse { exportFailedMessage })
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logs_clear)) },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        showClearConfirm = true
                                    },
                                )
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                )
                if (!selectionMode) {
                    SearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                    FilterRow(
                        appOnly = appOnly,
                        onAppOnlyChange = { appOnly = it },
                        visibleLevels = visibleLevels,
                        onToggleLevel = { level ->
                            visibleLevels = if (level in visibleLevels) visibleLevels - level else visibleLevels + level
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayed.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
                val message = when {
                    (if (appOnly) appEntries else deviceEntries).isEmpty() -> stringResource(
                            Res.string.logs_empty_session
                        )
                    searchQuery.isNotBlank() -> stringResource(Res.string.logs_no_match, searchQuery)
                    else -> stringResource(Res.string.logs_no_filter_match)
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(displayed) { index, entry ->
                    LogRow(
                        entry = entry,
                        searchQuery = searchQuery,
                        selectionMode = selectionMode,
                        selected = index in selectedIndices,
                        onClick = {
                            if (selectionMode) {
                                selectedIndices =
                                    if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIndices = selectedIndices + index
                        },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        DestructiveConfirmDialog(
            title = stringResource(Res.string.logs_clear_title),
            text = stringResource(Res.string.logs_clear_body),
            confirmLabel = stringResource(Res.string.common_clear_action),
            onConfirm = {
                scope.launch {
                clearDeviceLogs();
                load()
            }
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(Res.string.logs_search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.browse_wikis_clear_search))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun FilterRow(
    appOnly: Boolean,
    onAppOnlyChange: (Boolean) -> Unit,
    visibleLevels: Set<LogLevel>,
    onToggleLevel: (LogLevel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(
            rememberScrollState()
        ).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = appOnly,
            onClick = { onAppOnlyChange(!appOnly) },
            label = {
                Text(
                if (appOnly) stringResource(Res.string.logs_app_only) else stringResource(Res.string.logs_all)
            )
            },
        )
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = level in visibleLevels,
                onClick = { onToggleLevel(level) },
                label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: LogEntry,
    searchQuery: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${entry.displayTime ?: formatLogTime(
                    entry.timestampEpochMillis
                )}  ${entry.level.name}  ${entry.tag}",
                style = MaterialTheme.typography.labelSmall,
                color = levelColor(entry.level),
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = highlightedMessage(entry.message, searchQuery),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun levelColor(level: LogLevel) = when (level) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
}

// Matches, in order of precedence: a URL, a quoted string, a
// CamelCase word ending in Exception or Error, a bare number, then
// whatever's currently typed in the search box. Group order below
// matters and mirrors the branches in highlightedMessage. Positional
// groups, not named ones, since named capture group support isn't
// consistent across every Kotlin Multiplatform target this file
// compiles for, JVM, iOS, and wasmJs. The search text is escaped
// before going into the pattern, since it's arbitrary person-typed
// text, not something that should be interpreted as regex syntax; an
// empty search uses (?!), a group that can never match, rather than
// leaving group 5 out entirely, so the group count stays fixed at 5
// whether or not a search is active.
private fun buildHighlightPattern(searchQuery: String): Regex {
    val searchAlternative = if (searchQuery.isNotBlank()) Regex.escape(searchQuery) else "(?!)"
    return Regex(
        "(https?://\\S+)" +
            "|(\"[^\"]*\")" +
            "|(\\b[A-Za-z][A-Za-z0-9]*(?:Exception|Error)\\b)" +
            "|(\\b\\d+(?:\\.\\d+)?\\b)" +
            "|($searchAlternative)",
        RegexOption.IGNORE_CASE,
    )
}

/**
 * A deliberately lightweight pass over a log message, not a real
 * tokenizer, just enough to make URLs, quoted values, exception type
 * names, and numbers visually pop out of a wall of monospace text the
 * way logcat viewers in an IDE typically do, plus a background
 * highlight over whatever currently matches the search box.
 */
@Composable
private fun highlightedMessage(message: String, searchQuery: String): AnnotatedString {
    val urlColor = MaterialTheme.colorScheme.primary
    val quotedColor = MaterialTheme.colorScheme.tertiary
    val exceptionColor = MaterialTheme.colorScheme.error
    val numberColor = MaterialTheme.colorScheme.secondary
    val plainColor = MaterialTheme.colorScheme.onSurface
    val searchHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val pattern = remember(searchQuery) { buildHighlightPattern(searchQuery) }

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in pattern.findAll(message)) {
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = plainColor)) { append(message.substring(lastIndex, match.range.first)) }
            }
            val groups = match.groups
            val style = when {
                groups[1] != null -> SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)
                groups[2] != null -> SpanStyle(color = quotedColor)
                groups[3] != null -> SpanStyle(color = exceptionColor, fontWeight = FontWeight.Bold)
                groups[4] != null -> SpanStyle(color = numberColor)
                else -> SpanStyle(color = plainColor, background = searchHighlight)
            }
            withStyle(style) { append(match.value) }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < message.length) {
            withStyle(SpanStyle(color = plainColor)) { append(message.substring(lastIndex)) }
        }
    }
}

private fun formatLogTime(epochMillis: Long): String {
    val totalSeconds = epochMillis / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
}

private fun Long.pad(): String = toString().padStart(2, '0')
