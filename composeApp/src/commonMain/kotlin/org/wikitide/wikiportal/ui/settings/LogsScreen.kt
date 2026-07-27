package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Newest entries land at the end of readDeviceLogs's own list, so
    // this is reversed once here to read newest-first, top of screen.
    val displayed = remember(entries) { entries.asReversed() }

    suspend fun load() {
        isLoading = true
        entries = readDeviceLogs()
        isLoading = false
        selectionMode = false
        selectedIndices = emptySet()
    }

    LaunchedEffect(Unit) { load() }

    fun exitSelection() {
        selectionMode = false
        selectedIndices = emptySet()
    }

    fun formatted(list: List<LogEntry>): String =
        list.joinToString("\n") { entry -> "${entry.displayTime ?: formatLogTime(entry.timestampEpochMillis)}  ${entry.level.name}  ${entry.tag}: ${entry.message}" }

    fun copyToClipboard(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar("Copied") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
        },
    ) { innerPadding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayed.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
                Text(
                    "Nothing logged yet this session.",
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
            Text(text = entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

private fun formatLogTime(epochMillis: Long): String {
    val totalSeconds = epochMillis / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
}

private fun Long.pad(): String = toString().padStart(2, '0')
