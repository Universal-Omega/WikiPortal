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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.wikitide.wikiportal.util.LogEntry
import org.wikitide.wikiportal.util.LogExporter
import org.wikitide.wikiportal.util.LogLevel
import org.wikitide.wikiportal.util.clearDeviceLogs
import org.wikitide.wikiportal.util.readDeviceLogs
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(onBack: () -> Unit, logExporter: LogExporter = koinInject()) {
    var entries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var appOnly by remember { mutableStateOf(true) }
    var visibleLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun exitSelection() {
        selectionMode = false
        selectedIndices = emptySet()
    }

    suspend fun load() {
        isLoading = true
        entries = readDeviceLogs()
        isLoading = false
        exitSelection()
    }

    LaunchedEffect(Unit) { load() }

    // Newest entries land at the end of readDeviceLogs's own list, so
    // this reverses to read newest-first, then applies whatever the
    // filter chips currently say. Changing a filter reflows which
    // indices point at which row, so any in-progress selection is
    // cleared rather than risk it silently pointing at the wrong line.
    val displayed = remember(entries, appOnly, visibleLevels) {
        entries.asReversed().filter { (!appOnly || it.isAppSource) && it.level in visibleLevels }
    }
    LaunchedEffect(appOnly, visibleLevels) { exitSelection() }

    fun formatted(list: List<LogEntry>): String =
        list.joinToString("\n") { entry -> "${entry.displayTime ?: formatLogTime(entry.timestampEpochMillis)}  ${entry.level.name}  ${entry.tag}: ${entry.message}" }

    fun copyToClipboard(text: String) {
        scope.launch {
            clipboard.setClipEntry(
                ClipData.newPlainText(
                    "plain text",
                    AnnotatedString(text).convertToCharSequence()
                 ).toClipEntry()
            )
            snackbarHostState.showSnackbar("Copied")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (selectionMode) "${selectedIndices.size} selected" else "App logs") },
                    navigationIcon = {
                        IconButton(onClick = { if (selectionMode) exitSelection() else onBack() }) {
                            Icon(
                                imageVector = if (selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (selectionMode) "Cancel selection" else "Back",
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
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selected")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { load() } }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = { menuOpen = false; selectionMode = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy all") },
                                    onClick = {
                                        menuOpen = false
                                        copyToClipboard(formatted(displayed))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to file") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            val fileName = "wikiportal-logs-${Clock.System.now().toEpochMilliseconds()}.txt"
                                            val result = logExporter.export(fileName, formatted(displayed))
                                            snackbarHostState.showSnackbar(result.getOrElse { "Couldn't export logs" })
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear logs") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch { clearDeviceLogs(); load() }
                                    },
                                )
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                )
                if (!selectionMode) {
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
                Text(
                    if (entries.isEmpty()) "Nothing logged yet this session." else "Nothing matches the current filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(displayed) { index, entry ->
                    LogRow(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = index in selectedIndices,
                        onClick = {
                            if (selectionMode) {
                                selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
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
}

@Composable
private fun FilterRow(
    appOnly: Boolean,
    onAppOnlyChange: (Boolean) -> Unit,
    visibleLevels: Set<LogLevel>,
    onToggleLevel: (LogLevel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = appOnly,
            onClick = { onAppOnlyChange(!appOnly) },
            label = { Text(if (appOnly) "App only" else "All") },
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
private fun LogRow(entry: LogEntry, selectionMode: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${entry.displayTime ?: formatLogTime(entry.timestampEpochMillis)}  ${entry.level.name}  ${entry.tag}",
                style = MaterialTheme.typography.labelSmall,
                color = levelColor(entry.level),
                fontFamily = FontFamily.Monospace,
            )
            Text(text = highlightedMessage(entry.message), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
// CamelCase word ending in Exception or Error, then a bare number.
// Group order below matters and mirrors the branches in
// highlightedMessage. Positional groups, not named ones, since named
// capture group support isn't consistent across every Kotlin
// Multiplatform target this file compiles for, JVM, iOS, and wasmJs.
private val highlightPattern = Regex(
    "(https?://\\S+)" +
        "|(\"[^\"]*\")" +
        "|(\\b[A-Za-z][A-Za-z0-9]*(?:Exception|Error)\\b)" +
        "|(\\b\\d+(?:\\.\\d+)?\\b)",
)

/**
 * A deliberately lightweight pass over a log message, not a real
 * tokenizer, just enough to make URLs, quoted values, exception type
 * names, and numbers visually pop out of a wall of monospace text the
 * way logcat viewers in an IDE typically do.
 */
@Composable
private fun highlightedMessage(message: String): AnnotatedString {
    val urlColor = MaterialTheme.colorScheme.primary
    val quotedColor = MaterialTheme.colorScheme.tertiary
    val exceptionColor = MaterialTheme.colorScheme.error
    val numberColor = MaterialTheme.colorScheme.secondary
    val plainColor = MaterialTheme.colorScheme.onSurface

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in highlightPattern.findAll(message)) {
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = plainColor)) { append(message.substring(lastIndex, match.range.first)) }
            }
            val groups = match.groups
            val style = when {
                groups[1] != null -> SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)
                groups[2] != null -> SpanStyle(color = quotedColor)
                groups[3] != null -> SpanStyle(color = exceptionColor, fontWeight = FontWeight.Bold)
                else -> SpanStyle(color = numberColor)
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
